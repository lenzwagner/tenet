package app.tenet.android.feature.today

import kotlinx.coroutines.flow.mapLatest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.BodyProfile
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.common.StreakCalculator
import app.tenet.android.core.common.TrainingDays
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.EntryRepository
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.data.NutritionTotals
import app.tenet.android.core.data.WeekCalendarRepository
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.database.entity.DailyGoal
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Dashboard cards (App_Konzept.md 5.1); order and visibility are user settings. */
enum class TodayCard(val label: String) {
    FEED("Gesundheits-Übersicht"),
    READINESS(app.tenet.android.core.common.Readiness.NAME),
    DREAM("Traum"),
    NUTRITION("Ernährung"),
    SPORT("Sport"),
    JOURNAL("Journal"),
    STREAKS("Serien"),
}

val DefaultCardOrder = listOf(TodayCard.FEED, TodayCard.READINESS, TodayCard.DREAM, TodayCard.NUTRITION, TodayCard.SPORT, TodayCard.JOURNAL, TodayCard.STREAKS)

data class SessionSummary(
    val discipline: Discipline,
    val title: String?,
    val minutes: Int?,
    val running: Boolean,
)

data class Streaks(val diaryDays: Int = 0, val trackingDays: Int = 0, val trainingWeeks: Int = 0)

/** Which areas have something on a day (dots in the week strip). */
data class DayMarks(val diary: Boolean = false, val training: Boolean = false, val nutrition: Boolean = false) {
    val any: Boolean get() = diary || training || nutrition
}

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val totals: NutritionTotals = NutritionTotals(),
    val goal: DailyGoal = FoodRepository.defaultGoal,
    val entries: List<Entry> = emptyList(),
    val diary: Entry? = null,
    val mood: Int? = null,
    val dream: Entry? = null,
    val sessions: List<SessionSummary> = emptyList(),
    /** Units planned for [date] that are not done yet. */
    val planned: List<PlannedWorkout> = emptyList(),
    /** Next dated unit after [date] (only when nothing is planned for today). */
    val nextPlanned: PlannedWorkout? = null,
    val streaks: Streaks = Streaks(),
    /** ISO date → marks, for the week strip. */
    val marks: Map<String, DayMarks> = emptyMap(),
    /** Visible cards in order. */
    val cards: List<TodayCard> = DefaultCardOrder,
    /** All cards in order, for the "Karten anpassen" sheet. */
    val cardOrder: List<TodayCard> = DefaultCardOrder,
    val hidden: Set<TodayCard> = emptySet(),
    val loading: Boolean = true,
) {
    val isToday: Boolean get() = date == LocalDate.now()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val foodRepository: FoodRepository,
    private val weekCalendarRepository: WeekCalendarRepository,
    private val sportRepository: SportRepository,
    private val settingsRepository: UserSettingsRepository,
    private val weatherRepository: app.tenet.android.core.data.WeatherRepository,
    private val readinessRepository: app.tenet.android.core.data.health.ReadinessRepository,
    private val healthConnect: app.tenet.android.core.data.health.HealthConnectRepository,
) : ViewModel() {

    /** Feed on top of "Heute": steps right now, active minutes this week. */
    data class Feed(
        val steps: Long? = null,
        val weekActiveMin: Int = 0,
        val weekGoalMin: Int = 150,
        /** Health Connect permissions the feed needs but does not have. */
        val missing: Set<String> = emptySet(),
    )

    private val feedTick = MutableStateFlow(0)

    /** Steps are read fresh on every return to the app; the rest changes slowly. */
    val feed: StateFlow<Feed> = kotlinx.coroutines.flow.combine(
        feedTick,
        weekCalendarRepository.observeSessionsBetween(WeekMath.weekStart(LocalDate.now()), LocalDate.now().plusDays(1)),
    ) { _, sessions -> sessions }
        .mapLatest { sessions ->
            Feed(
                steps = runCatching { healthConnect.stepsToday() }.getOrNull(),
                missing = runCatching { healthConnect.missingPermissions() }.getOrDefault(emptySet()),
                weekActiveMin = sessions.map { it.session }.filter { it.endedAt != null }
                    .sumOf { ((it.endedAt!! - it.startedAt) / 60_000L).coerceIn(0L, 240L) }.toInt(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Feed())

    /** Today's readiness (Health Connect); refreshed whenever the page comes back. */
    private val readinessTick = MutableStateFlow(0)
    val readiness: StateFlow<app.tenet.android.core.data.health.ReadinessRepository.Today?> =
        readinessTick
            .mapLatest { runCatching { readinessRepository.today() }.getOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val date = MutableStateFlow(LocalDate.now())
    private val weatherTick = MutableStateFlow(0)

    val weeklyWeightPrompt: StateFlow<Boolean> = combine(
        settingsRepository.settings,
        sportRepository.observeBodyMetrics(),
    ) { settings, metrics ->
        val today = LocalDate.now()
        val monday = WeekMath.weekStart(today)
        val enteredThisWeek = metrics.lastOrNull()?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { !it.isBefore(monday) } == true
        today.dayOfWeek == java.time.DayOfWeek.MONDAY &&
            settings.lastWeightPromptDate != today.toString() &&
            !enteredThisWeek
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissWeeklyWeightPrompt() {
        viewModelScope.launch { settingsRepository.markWeightPromptHandled(LocalDate.now().toString()) }
    }

    fun saveWeeklyWeight(weight: Float) {
        if (!weight.isFinite() || weight !in 20f..400f) return
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            sportRepository.saveBodyMetric(today, weight)
            settingsRepository.settings.first().profile?.let {
                settingsRepository.setProfile(it.copy(weightKg = weight))
            }
            settingsRepository.markWeightPromptHandled(today)
        }
    }

    /** Weather of the selected day (last 7 days + today); null without location/network. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val weather: kotlinx.coroutines.flow.StateFlow<app.tenet.android.core.common.WeatherDay?> =
        kotlinx.coroutines.flow.combine(date, weatherTick) { d, _ -> d }
            .mapLatest { runCatching { weatherRepository.forDate(it) }.getOrNull() }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    private data class DayPart(
        val totals: NutritionTotals,
        val goal: DailyGoal,
        val entries: List<Entry>,
        val sessions: List<SessionSummary>,
        val planned: List<PlannedWorkout>,
        val next: PlannedWorkout?,
    )

    private fun dayFlow(d: LocalDate): Flow<DayPart> {
        val iso = d.toString()
        val weekStart = WeekMath.weekStart(d)
        val planned = combine(
            weekCalendarRepository.observePlannedBetween(weekStart, weekStart.plusDays(6)),
            weekCalendarRepository.observePlannedBetween(weekStart.plusWeeks(1), weekStart.plusDays(13)),
            weekCalendarRepository.observeActivePlanHeads(),
        ) { a, b, heads -> (a + b) to heads }
        val sessions = weekCalendarRepository.observeSessionsBetween(d, d.plusDays(1))
        return combine(
            foodRepository.totals(iso),
            foodRepository.goal(iso),
            entryRepository.observeByDate(iso),
            sessions,
            planned,
        ) { totals, goal, entries, rows, (dated, heads) ->
            val done = rows.mapNotNull { it.session.plannedWorkoutId }.toSet()
            val trainedDisciplines = rows.map { it.session.discipline }.toSet()
            val isToday = d == LocalDate.now()
            // Gym/Calisthenics count as done once that discipline was trained that day.
            fun open(p: PlannedWorkout) = p.id !in done &&
                (p.discipline == Discipline.RUNNING || p.discipline !in trainedDisciplines)
            // Planned for the day: dated units incl. plans with fixed training days;
            // plans without fixed days count as "every day" (only shown for today).
            val todayPlanned = if (d < LocalDate.now()) {
                emptyList()
            } else {
                dated.filter { it.date == iso && open(it) } +
                    (
                        if (isToday) {
                            heads.filter { TrainingDays.parse(it.plan.trainingDays).isEmpty() }
                                .map { it.workout }
                                .filter { open(it) }
                        } else {
                            emptyList()
                        }
                    )
            }
            DayPart(
                totals = totals,
                goal = goal,
                entries = entries,
                sessions = rows.map { row ->
                    val s = row.session
                    SessionSummary(
                        discipline = s.discipline,
                        title = row.workoutTitle,
                        minutes = s.endedAt?.let { ((it - s.startedAt) / 60_000).toInt() },
                        running = s.endedAt == null,
                    )
                },
                planned = todayPlanned,
                next = if (d < LocalDate.now() || todayPlanned.isNotEmpty()) {
                    null
                } else {
                    dated.filter { p -> (p.date ?: "") > iso }
                        .minWithOrNull(compareBy({ it.date ?: "" }, { it.sortOrder }))
                },
            )
        }
    }

    private data class History(val streaks: Streaks, val marks: Map<String, DayMarks>)

    private val history: Flow<History> = combine(
        entryRepository.observeDiaryDates(),
        foodRepository.observeLogDates(),
        weekCalendarRepository.observeTrainingDates(),
    ) { diary, food, training ->
        val today = LocalDate.now().toString()
        val diarySet = diary.toSet()
        val foodSet = food.toSet()
        val trainingSet = training.toSet()
        History(
            streaks = Streaks(
                diaryDays = StreakCalculator.currentStreak(diary, today),
                trackingDays = StreakCalculator.currentStreak(food, today),
                trainingWeeks = StreakCalculator.currentWeekStreak(training, today),
            ),
            marks = (diarySet + foodSet + trainingSet).associateWith {
                DayMarks(diary = it in diarySet, training = it in trainingSet, nutrition = it in foodSet)
            },
        )
    }

    val uiState: StateFlow<TodayUiState> = combine(
        date.flatMapLatest { d -> dayFlow(d).map { d to it } },
        entryRepository.observeDiaryMeta(),
        history,
        settingsRepository.settings,
    ) { (d, day), diaryMeta, history, settings ->
        val saved = settings.todayCardOrder.mapNotNull { id -> TodayCard.entries.firstOrNull { it.name == id } }
        // New cards of an update: readiness goes on top, others at the end.
        val order = ((if (saved.isNotEmpty()) listOf(TodayCard.FEED, TodayCard.READINESS).filter { it !in saved } else emptyList()) +
            saved + DefaultCardOrder).distinct()
        val hidden = settings.todayHiddenCards.mapNotNull { id -> TodayCard.entries.firstOrNull { it.name == id } }.toSet()
        val diary = day.entries.firstOrNull { it.type == EntryType.DIARY }
        TodayUiState(
            date = d,
            totals = day.totals,
            goal = day.goal,
            entries = day.entries,
            diary = diary,
            mood = diary?.let { diaryMeta[it.id]?.mood },
            dream = day.entries.firstOrNull { it.type == EntryType.DREAM },
            sessions = day.sessions,
            planned = day.planned,
            nextPlanned = day.next,
            streaks = history.streaks,
            marks = history.marks,
            // Closed today: the readiness card waits at the end until tomorrow.
            cards = order.filter { it !in hidden }.let { visible ->
                if (settings.readinessDismissedDate == d.toString() && TodayCard.READINESS in visible) {
                    visible - TodayCard.READINESS + TodayCard.READINESS
                } else {
                    visible
                }
            },
            cardOrder = order,
            hidden = hidden,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    val profile: StateFlow<BodyProfile?> = settingsRepository.settings.map { it.profile }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectDate(day: LocalDate) { date.value = minOf(day, LocalDate.now()) }
    fun shiftWeek(weeks: Long) { selectDate(date.value.plusWeeks(weeks)) }

    /** Follows midnight: if "today" was selected, move to the new today. */
    private var knownToday = LocalDate.now()
    fun refreshToday() {
        weatherTick.value++ // back in the app: fresh current temperature (cached 30 min)
        readinessTick.value++
        feedTick.value++
        val now = LocalDate.now()
        if (now != knownToday) {
            if (date.value == knownToday) date.value = now
            knownToday = now
        }
    }

    /** After the permission dialog: read everything again. */
    fun refreshHealth() {
        readinessTick.value++
        feedTick.value++
    }

    fun dismissReadiness() {
        viewModelScope.launch { settingsRepository.setReadinessDismissed(LocalDate.now().toString()) }
    }

    fun saveCards(order: List<TodayCard>, hidden: Set<TodayCard>) {
        viewModelScope.launch {
            settingsRepository.setTodayCards(order.map { it.name }, hidden.map { it.name }.toSet())
        }
    }

    fun saveProfile(profile: BodyProfile) {
        viewModelScope.launch { settingsRepository.setProfile(profile) }
    }

    /** Records an explicit no-dream day without creating dream metadata/statistics. */
    fun markNoDream(entryDate: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            entryRepository.save(
                Entry(
                    id = newUuid(),
                    type = EntryType.DREAM,
                    title = "Kein Traum",
                    body = "",
                    createdAt = now,
                    updatedAt = now,
                    entryDate = entryDate,
                ),
                diaryMeta = null,
                dreamMeta = null,
            )
        }
    }

    fun setGoal(kcal: Float, protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            foodRepository.setGoal(
                DailyGoal(
                    dateFrom = LocalDate.now().toString(),
                    kcal = kcal,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                ),
            )
        }
    }
}
