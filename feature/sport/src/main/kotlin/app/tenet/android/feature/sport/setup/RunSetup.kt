package app.tenet.android.feature.sport.setup

import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.designsystem.component.TenetSwitch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.PaceAnchor
import app.tenet.android.core.common.RacePrediction
import app.tenet.android.core.common.RunPaceMath
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.common.RunPlanMath.RunGoal
import app.tenet.android.core.common.RunZone
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.datastore.UserSettingsRepository
import app.tenet.android.feature.sport.TrainingDaysRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RunnerLevel(val label: String, val description: String, val volume: Float) {
    BEGINNER("Einsteiger", "Bis 10 km pro Woche oder gerade erst angefangen", 0.8f),
    REGULAR("Regelmäßig", "Etwa 15–30 km pro Woche", 1f),
    AMBITIOUS("Ambitioniert", "Mehr als 30 km pro Woche", 1.15f),
}

/** Recent result the 5 km form is derived from. */
enum class RecentRace(val label: String, val distanceM: Int?) {
    NONE("Keine Zeit", null),
    FIVE("5 km", 5_000),
    TEN("10 km", 10_000),
    HALF("Halbmarathon", 21_097),
}

data class RunSetupState(
    val step: Int = 0,
    val goal: RunGoal = RunGoal.TEN_K,
    val goalDate: LocalDate = LocalDate.now().plusWeeks(12),
    val level: RunnerLevel = RunnerLevel.REGULAR,
    val recent: RecentRace = RecentRace.NONE,
    val recentTime: String = "",
    /** ISO weekdays 1–7. */
    val days: Set<Int> = setOf(2, 4, 7),
    val targetTime: String = "",
    val taper: Boolean = true,
    val busy: Boolean = false,
) {
    /** 5 km equivalent of the recent result (Riegel). */
    val form5kSec: Int?
        get() {
            val d = recent.distanceM ?: return null
            val t = parseHms(recentTime) ?: return null
            return if (d == 5_000) t else RacePrediction.riegel(d, t, 5_000)
        }
    val runs: Int get() = days.size.coerceIn(2, 6)
}

@HiltViewModel
class RunSetupViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val settings: UserSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RunSetupState())
    val state: StateFlow<RunSetupState> = _state
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    fun update(block: RunSetupState.() -> RunSetupState) = _state.update(block)

    fun finish() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repository.createPlan(
                goal = s.goal,
                goalDate = s.goalDate,
                runsPerWeek = s.runs,
                current5kSec = s.form5kSec,
                paceMethod = settings.settings.first().paceMethod,
                taper = s.taper && RunPlanMath.raceDistanceM(s.goal) != null,
                targetTimeSec = parseHms(s.targetTime),
                days = s.days.map { it - 1 },
                volume = s.level.volume,
            )
            settings.markSportSetupDone("RUNNING")
            _done.emit(Unit)
        }
    }
}

private val STEPS = listOf(
    "Was ist dein Ziel?",
    "Bis wann?",
    "Wo stehst du gerade?",
    "An welchen Tagen läufst du?",
    "Zielzeit und Tapering",
    "Dein Plan",
)

private val DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSetupScreen(onDone: () -> Unit, viewModel: RunSetupViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }
    val race = RunPlanMath.raceDistanceM(s.goal) != null
    SetupScaffold(
        title = "Laufplan einrichten",
        step = s.step,
        stepTitles = STEPS,
        canContinue = when (s.step) {
            2 -> s.recent == RecentRace.NONE || s.form5kSec != null
            3 -> s.days.size in 2..6
            else -> true
        },
        busy = s.busy,
        finishLabel = "Plan erstellen",
        onStep = { viewModel.update { copy(step = it) } },
        onClose = onDone,
        onFinish = viewModel::finish,
    ) { step ->
        when (step) {
            0 -> ChoiceCards(
                options = RunGoal.entries,
                selected = s.goal,
                onSelect = { viewModel.update { copy(goal = it) } },
                title = { it.label },
                description = {
                    when (it) {
                        RunGoal.GENERAL -> "Ohne Wettkampf, locker und regelmäßig"
                        else -> "Wettkampfvorbereitung"
                    }
                },
            )
            1 -> {
                SetupHint(if (race) "Wähle den Tag deines Wettkampfs." else "Wie lange soll der Plan laufen?")
                val today = LocalDate.now()
                val picker = rememberDatePickerState(
                    initialSelectedDateMillis = s.goalDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    initialDisplayMode = DisplayMode.Picker,
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today.plusWeeks(3))
                    },
                )
                LaunchedEffect(picker.selectedDateMillis) {
                    picker.selectedDateMillis?.let { ms ->
                        viewModel.update { copy(goalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    }
                }
                DatePicker(state = picker, title = null, headline = null, showModeToggle = false)
                val weeks = RunPlanMath.weekCount(WeekMath.weekStart(today), s.goalDate)
                SetupHint("${s.goalDate.format(DATE)} · $weeks Wochen Plan")
            }
            2 -> {
                ChoiceCards(
                    options = RunnerLevel.entries,
                    selected = s.level,
                    onSelect = { viewModel.update { copy(level = it) } },
                    title = { it.label },
                    description = { it.description },
                )
                Text("Letzte Bestzeit (optional)", style = MaterialTheme.typography.titleSmall)
                SetupHint("Daraus berechnet Tenet deine Trainingstempi und eine Prognose.")
                ChoiceCards(
                    options = RecentRace.entries,
                    selected = s.recent,
                    onSelect = { viewModel.update { copy(recent = it) } },
                    title = { it.label },
                )
                if (s.recent != RecentRace.NONE) {
                    NumberFieldText(
                        value = s.recentTime,
                        onValue = { viewModel.update { copy(recentTime = it) } },
                        label = "Zeit (h:mm:ss oder mm:ss)",
                    )
                    s.form5kSec?.let { SetupHint("Entspricht etwa ${hms(it)} auf 5 km.") }
                }
            }
            3 -> {
                TrainingDaysRow(
                    value = s.days.sorted().joinToString(","),
                    onChange = { set -> viewModel.update { copy(days = set.map { it.value }.toSet()) } },
                )
                SetupHint(
                    when {
                        s.days.size < 2 -> "Wähle mindestens zwei Tage."
                        s.days.size > 6 -> "Höchstens sechs Lauftage – ein Ruhetag muss sein."
                        else -> "${s.days.size} Läufe pro Woche. Der lange Lauf liegt am Wochenende, wenn du dort einen Tag wählst."
                    },
                )
            }
            4 -> {
                if (race) {
                    NumberFieldText(
                        value = s.targetTime,
                        onValue = { viewModel.update { copy(targetTime = it) } },
                        label = "Zielzeit (optional, h:mm:ss)",
                    )
                    val prognosis = s.form5kSec?.let { f -> RacePrediction.riegel(5_000, f, RunPlanMath.raceDistanceM(s.goal)!!) }
                    prognosis?.let { SetupHint("Prognose nach deiner Bestzeit: ${hms(it)}") }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tapering", style = MaterialTheme.typography.titleMedium)
                            SetupHint("Weniger Umfang in den letzten Wochen vor dem Wettkampf, damit du ausgeruht startest.")
                        }
                        TenetSwitch(checked = s.taper, onCheckedChange = { viewModel.update { copy(taper = it) } })
                    }
                } else {
                    SetupHint("Ohne Wettkampf gibt es keine Zielzeit und kein Tapering. Der Umfang steigt langsam, jede vierte Woche ist leichter.")
                }
            }
            else -> RunPlanPreview(s)
        }
    }
}

@Composable
private fun RunPlanPreview(s: RunSetupState) {
    val weeks = RunPlanMath.weekCount(WeekMath.weekStart(LocalDate.now()), s.goalDate)
    val week1 = RunPlanMath.planWeek(s.goal, 0, s.runs, totalWeeks = weeks, taper = s.taper, days = s.days.map { it - 1 }, volume = s.level.volume)
    val anchor = s.form5kSec?.let { PaceAnchor(5_000, it) }
    val dayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    Text("${s.goal.label} · $weeks Wochen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Woche 1", style = MaterialTheme.typography.titleSmall)
            week1.sortedBy { it.dayIndex }.forEach { unit ->
                Row(Modifier.fillMaxWidth()) {
                    Text(dayNames[unit.dayIndex], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                    Text(unit.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        listOfNotNull(
                            unit.targetDurationSec?.let { "${it / 60} min" },
                            anchor?.let { a -> pace(RunPaceMath.targetPaceSecPerKm(unit.zone, a, app.tenet.android.core.common.PaceMethod.VDOT, s.goal == RunGoal.MARATHON)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (anchor == null) {
        SetupHint("Ohne Bestzeit läufst du nach Gefühl. Nach ein paar Läufen berechnet Tenet deine Tempi aus deinen Läufen.")
    } else {
        SetupHint("Lockerlauf ≈ ${pace(RunPaceMath.targetPaceSecPerKm(RunZone.EASY, anchor, app.tenet.android.core.common.PaceMethod.VDOT))}, Tempolauf ≈ ${pace(RunPaceMath.targetPaceSecPerKm(RunZone.TEMPO, anchor, app.tenet.android.core.common.PaceMethod.VDOT))}")
    }
    if (s.taper && RunPlanMath.raceDistanceM(s.goal) != null) {
        SetupHint("Die letzten ${RunPlanMath.taperWeeks(s.goal)} Woche(n) sind Tapering, am Ende steht der Wettkampf im Plan.")
    }
}

@Composable
private fun NumberFieldText(value: String, onValue: (String) -> Unit, label: String) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() || it == ':' }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "1:45:30", "45:30" or "45" (minutes) → seconds. */
internal fun parseHms(text: String): Int? {
    val parts = text.trim().split(':').map { it.toIntOrNull() ?: return null }
    return when (parts.size) {
        1 -> parts[0] * 60
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }?.takeIf { it > 0 }
}

private fun hms(sec: Int) = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

private fun pace(sec: Int) = "%d:%02d /km".format(sec / 60, sec % 60)
