package app.tenet.android.feature.sport.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.GymPlanStats
import app.tenet.android.core.common.OverloadMath
import app.tenet.android.core.common.PlanFit
import app.tenet.android.core.common.RacePrediction
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.PlanUnitUi
import app.tenet.android.core.data.RunPlanData
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.database.dao.RunDao
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---- Running plan ---------------------------------------------------------------

data class PlanWeekVolume(val weekIndex: Int, val start: LocalDate, val plannedKm: Float, val doneKm: Float, val taper: Boolean)

data class DoneUnit(val unit: PlanUnitUi, val run: RunDao.RunSessionRow)

data class RunPlanUi(
    val title: String,
    val goal: RunPlanMath.RunGoal,
    val goalDate: LocalDate?,
    val weeks: Int,
    val currentWeek: Int,
    val daysLeft: Long?,
    val raceDistanceM: Int?,
    val targetTimeSec: Int?,
    val prediction: RacePrediction.Prediction?,
    /** How the prognosis was made ("aus 7 Läufen …"); null = best single effort. */
    val predictionNote: String? = null,
    val predictionHistory: List<Pair<LocalDate, Int>>,
    val taper: Boolean,
    val done: List<DoneUnit>,
    val upcoming: List<PlanUnitUi>,
    val dueCount: Int,
    val weekly: List<PlanWeekVolume>,
    val totalDoneKm: Float,
    val planId: String = "",
    val fit: PlanFit.Assessment? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RunPlanDetailViewModel @Inject constructor(
    private val repository: RunningRepository,
    settings: app.tenet.android.core.datastore.UserSettingsRepository,
) : ViewModel() {
    private val planId = MutableStateFlow<String?>(null)

    val state: StateFlow<RunPlanUi?> = planId.filterNotNull()
        .flatMapLatest { id ->
            kotlinx.coroutines.flow.combine(
                repository.observeRunPlan(id),
                repository.observeOverview(),
                settings.settings,
            ) { d, overview, s ->
                d?.let {
                    // Form from all recent runs (also before the plan, incl. Health Connect), heart rate included.
                    val form = app.tenet.android.core.common.FormEstimator.estimate(
                        overview.runs.map { r -> r.toFormRunForPlan() },
                        LocalDate.now(),
                        s.profile?.age,
                    )
                    build(it, form)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: String) {
        planId.value = id
    }

    /** Recomputes the remaining paces from the current form (and the goal time from the prognosis). */
    fun adjustToForm(onDone: (String) -> Unit) {
        val s = state.value ?: return
        val fit = s.fit ?: return
        val form = fit.form5kSec ?: return
        viewModelScope.launch {
            repository.adjustPlanToForm(s.planId, form, fit.suggestedTargetSec)
            onDone(
                if (fit.suggestedTargetSec != null) "Zielzeit und Tempi an deine Form angepasst"
                else "Tempi an deine Form angepasst",
            )
        }
    }

    private fun build(d: RunPlanData, form: app.tenet.android.core.common.FormEstimator.Form?): RunPlanUi {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val goal = RunPlanMath.RunGoal.fromName(d.detail?.goalId ?: d.plan.goal)
        val start = d.plan.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val goalDate = d.plan.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val weeks = goalDate?.let { RunPlanMath.weekCount(start, it) } ?: (d.units.maxOfOrNull { it.weekIndex } ?: 0) + 1
        val currentWeek = ChronoUnit.WEEKS.between(WeekMath.weekStart(start), WeekMath.weekStart(today)).toInt().coerceIn(0, weeks - 1)
        val raceDistance = RunPlanMath.raceDistanceM(goal)

        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        // Same matching as the week view: linked runs, else runs added later for that day.
        val matches = app.tenet.android.core.common.RunPlanMatcher.match(
            planned = d.units.map { app.tenet.android.core.common.RunPlanMatcher.Planned(it.id, it.date, it.skipped) },
            runs = d.runs.map { r -> app.tenet.android.core.common.RunPlanMatcher.Run(r.session.id, dateOf(r.session.startedAt), r.session.plannedWorkoutId) },
        )
        val runsById = d.runs.associateBy { it.session.id }
        val done = d.units.mapNotNull { u -> matches[u.id]?.let(runsById::get)?.let { DoneUnit(u, it) } }
        val doneIds = done.map { it.unit.id }.toSet()

        // Prognosis from best efforts (+ the 5 km form given at plan creation).
        val efforts = d.efforts.map { RacePrediction.Effort(dateOf(it.achievedAt), it.distanceM, it.durationSec) } +
            listOfNotNull(d.detail?.current5kSec?.let { RacePrediction.Effort(start, 5_000, it) })
        // Prognosis: the form estimate (all runs, heart rate); fallback best effort (Riegel).
        val prediction = raceDistance?.let { dist ->
            form?.time(dist)?.let { RacePrediction.Prediction(it, RacePrediction.Effort(today, dist, it)) }
                ?: RacePrediction.predict(efforts, dist, today, windowDays = 84)
        }
        // Current form only from real runs (not the anchor given at creation).
        val runEfforts = d.efforts.map { RacePrediction.Effort(dateOf(it.achievedAt), it.distanceM, it.durationSec) }
        val form5k = form?.time(5_000) ?: RacePrediction.predict(runEfforts, 5_000, today, windowDays = 42)?.timeSec
        val racePassed = goalDate != null && goalDate.isBefore(today)
        val fit = if (racePassed) null else PlanFit.assess(
            targetSec = d.detail?.targetTimeSec,
            // Judge the goal against race day (today's form plus the training still ahead).
            predictedSec = raceDistance?.let { dist -> form?.time(dist) ?: RacePrediction.predict(runEfforts, dist, today, windowDays = 84)?.timeSec }
                ?.let { now -> RunPlanMath.expectedRaceDaySec(now, goal, (weeks - currentWeek).coerceAtLeast(0), d.detail?.taper == true) },
            anchor5kSec = d.detail?.current5kSec,
            form5kSec = form5k,
        )
        val history = raceDistance?.let { dist ->
            RacePrediction.history(efforts.filter { !it.date.isBefore(start.minusDays(84)) }, dist, 84)
                .filter { !it.first.isBefore(start) }
        }.orEmpty()

        // Planned km per unit: distance, else duration at target pace (easy ≈ 5k pace + 25 %).
        val easyPace = d.detail?.current5kSec?.let { (it / 5 * 1.25).toInt() } ?: 360
        fun plannedKm(u: PlanUnitUi): Float {
            val dist = u.targetDistanceM
            val dur = u.targetDurationSec
            return when {
                dist != null -> dist / 1000f
                dur != null -> dur.toFloat() / (u.targetPaceSecPerKm ?: easyPace)
                else -> 0f
            }
        }
        val weekly = (0 until weeks).map { w ->
            val weekStart = WeekMath.weekStart(start).plusWeeks(w.toLong())
            val weekEnd = weekStart.plusDays(7)
            PlanWeekVolume(
                weekIndex = w,
                start = weekStart,
                plannedKm = d.units.filter { it.weekIndex == w }.sumOf { plannedKm(it).toDouble() }.toFloat(),
                doneKm = d.runs.filter { r -> dateOf(r.session.startedAt).let { !it.isBefore(weekStart) && it.isBefore(weekEnd) } }
                    .sumOf { it.run.distanceM.toDouble() }.toFloat() / 1000f,
                taper = RunPlanMath.isTaperWeek(goal, w, weeks, d.detail?.taper == true),
            )
        }
        return RunPlanUi(
            title = d.plan.name,
            goal = goal,
            goalDate = goalDate,
            weeks = weeks,
            currentWeek = currentWeek,
            daysLeft = goalDate?.let { ChronoUnit.DAYS.between(today, it) },
            raceDistanceM = raceDistance,
            targetTimeSec = d.detail?.targetTimeSec,
            prediction = prediction,
            predictionNote = form?.let { f ->
                "aus ${f.runs} ${if (f.runs == 1) "Lauf" else "Läufen"} der letzten 8 Wochen" + if (f.withHr > 0) ", Puls berücksichtigt" else ""
            },
            predictionHistory = history,
            taper = d.detail?.taper == true,
            done = done.sortedByDescending { it.run.session.startedAt },
            upcoming = d.units.filter { !it.date.isBefore(today) && it.id !in doneIds }.take(8),
            dueCount = d.units.count { !it.date.isAfter(today) },
            weekly = weekly,
            totalDoneKm = weekly.sumOf { it.doneKm.toDouble() }.toFloat(),
            planId = d.plan.id,
            fit = fit,
        )
    }
}

// ---- Gym plan -----------------------------------------------------------------------

data class GymPlanUi(
    val title: String,
    val sessions: List<GymPlanStats.SessionStat>,
    val exercises: List<GymPlanStats.ExerciseStat>,
    /** Proposal for the next session per exercise. */
    val next: Map<String, OverloadMath.Suggestion>,
    val totalVolumeKg: Float,
    val lastDate: LocalDate?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GymPlanDetailViewModel @Inject constructor(
    repository: SportRepository,
) : ViewModel() {
    private val planId = MutableStateFlow<String?>(null)

    val state: StateFlow<GymPlanUi?> = planId.filterNotNull()
        .flatMapLatest { repository.observeGymPlan(it) }
        .map { data ->
            data?.let { d ->
                val sessions = GymPlanStats.sessions(d.sets)
                val exercises = GymPlanStats.exercises(d.sets)
                val next = exercises.associate { ex ->
                    val history = d.sets.filter { it.exercise == ex.name }
                        // Rows arrive in session start order; several sessions can share one day.
                        .groupBy { it.sessionId }.values.reversed()
                        .map { s -> s.map { OverloadMath.WorkSet(it.weightKg, it.reps) } }
                    val (reps, muscles) = d.routine[ex.name] ?: (8 to "")
                    ex.name to OverloadMath.suggest(history, reps, muscles, d.rules[ex.name] ?: OverloadMath.Rule())
                }
                GymPlanUi(
                    title = d.plan.name,
                    sessions = sessions,
                    exercises = exercises,
                    next = next,
                    totalVolumeKg = sessions.sumOf { it.volumeKg.toDouble() }.toFloat(),
                    lastDate = sessions.lastOrNull()?.date,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: String) {
        planId.value = id
    }
}

private fun app.tenet.android.core.database.dao.RunDao.RunSessionRow.toFormRunForPlan() = app.tenet.android.core.common.FormEstimator.Run(
    date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
    distanceM = run.distanceM,
    durationSec = run.durationSec,
    avgHr = run.avgHr,
)
