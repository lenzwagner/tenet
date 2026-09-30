package app.tenet.android.feature.sport.setup

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import app.tenet.android.feature.sport.ExerciseThumb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.tenet.android.core.common.GymPlanBuilder
import app.tenet.android.core.common.GymPlanBuilder.Goal
import app.tenet.android.core.common.GymPlanBuilder.Level
import app.tenet.android.core.common.GymPlanBuilder.Lift
import app.tenet.android.core.common.GymPlanBuilder.Split
import app.tenet.android.core.common.Sex
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.datastore.UserSettingsRepository
import app.tenet.android.feature.sport.TrainingDaysRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GymSetupState(
    val step: Int = 0,
    val goal: Goal = Goal.HYPERTROPHY,
    val level: Level = Level.BEGINNER,
    /** ISO weekdays 1–7. */
    val days: Set<Int> = setOf(1, 3, 5),
    val split: Split? = null,
    val bodyweight: String = "",
    val female: Boolean = false,
    /** Lift → (weight, reps) as typed. */
    val lifts: Map<Lift, Pair<String, String>> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
) {
    val effectiveSplit: Split get() = split ?: GymPlanBuilder.recommendedSplit(days.size)

    fun input() = GymPlanBuilder.Input(
        goal = goal,
        level = level,
        split = effectiveSplit,
        days = days.sorted(),
        bodyweightKg = bodyweight.toFloatOrNullDe(),
        female = female,
        lifts = lifts.mapNotNull { (lift, v) ->
            val r = v.second.toIntOrNull()
            // Pull-ups/dips: empty weight with reps = bodyweight only.
            val w = v.first.toFloatOrNullDe()
                ?: if (lift.load == GymPlanBuilder.Load.BODYWEIGHT_PLUS && r != null) 0f else null
            val ok = w != null && r != null && r > 0 && (w > 0f || lift.load == GymPlanBuilder.Load.BODYWEIGHT_PLUS)
            if (ok) lift to GymPlanBuilder.LiftInput(w!!, r!!) else null
        }.toMap(),
    )
}

@HiltViewModel
class GymSetupViewModel @Inject constructor(
    private val repository: SportRepository,
    private val settings: UserSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GymSetupState())
    val state: StateFlow<GymSetupState> = _state
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    init {
        viewModelScope.launch {
            val profile = settings.settings.first().profile
            val names = repository.exerciseNames()
            _state.update {
                it.copy(
                    bodyweight = profile?.weightKg?.let { w -> num(w) }.orEmpty(),
                    female = profile?.sex == Sex.FEMALE,
                    names = names,
                )
            }
        }
    }

    fun update(block: GymSetupState.() -> GymSetupState) = _state.update(block)

    fun finish() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repository.applyGymSetup(GymPlanBuilder.build(s.input()), s.days.toList())
            settings.markSportSetupDone("GYM")
            _done.emit(Unit)
        }
    }
}

private val STEPS = listOf(
    "Was ist dein Ziel?",
    "Wie viel Erfahrung hast du?",
    "An welchen Tagen trainierst du?",
    "Welcher Split passt?",
    "Deine aktuellen Kraftwerte",
    "Dein Plan",
)

@Composable
fun GymSetupScreen(onDone: () -> Unit, viewModel: GymSetupViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }
    SetupScaffold(
        title = "Gym einrichten",
        step = s.step,
        stepTitles = STEPS,
        canContinue = s.step != 2 || s.days.size >= 2,
        busy = s.busy,
        finishLabel = "Plan erstellen",
        onStep = { viewModel.update { copy(step = it) } },
        onClose = onDone,
        onFinish = viewModel::finish,
    ) { step ->
        when (step) {
            0 -> ChoiceCards(
                options = Goal.entries,
                selected = s.goal,
                onSelect = { viewModel.update { copy(goal = it) } },
                title = { it.label },
                description = { it.description },
            )
            1 -> ChoiceCards(
                options = Level.entries,
                selected = s.level,
                onSelect = { viewModel.update { copy(level = it) } },
                title = { it.label },
                description = { it.description },
            )
            2 -> {
                TrainingDaysRow(
                    value = s.days.sorted().joinToString(","),
                    onChange = { set -> viewModel.update { copy(days = set.map { it.value }.toSet(), split = null) } },
                )
                SetupHint(
                    if (s.days.size < 2) "Wähle mindestens zwei Tage."
                    else "${s.days.size} Tage pro Woche. Zwischen zwei Einheiten für dieselben Muskeln sollte ein Tag Pause liegen.",
                )
            }
            3 -> {
                val recommended = GymPlanBuilder.recommendedSplit(s.days.size)
                ChoiceCards(
                    options = Split.entries,
                    selected = s.effectiveSplit,
                    onSelect = { viewModel.update { copy(split = it) } },
                    title = { it.label },
                    description = { it.description },
                    badge = { if (it == recommended) "Empfohlen" else null },
                )
                SetupHint("Die Einheiten wechseln sich ab: nach A kommt B, nach Push kommt Pull und so weiter.")
            }
            4 -> {
                SetupHint(
                    "Trag einen guten Satz der letzten Wochen ein, z. B. 80 kg × 5. Kurzhanteln: Gewicht pro Hand. " +
                        "Klimmzüge und Dips: nur das Zusatzgewicht (0 = ohne). " +
                        "Leere Übungen schätzt Tenet aus Körpergewicht und Erfahrung.",
                )
                NumberField(
                    value = s.bodyweight,
                    onValue = { viewModel.update { copy(bodyweight = it) } },
                    label = "Körpergewicht",
                    suffix = "kg",
                    decimal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Lift.entries.forEach { lift ->
                    val (w, r) = s.lifts[lift] ?: ("" to "")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(lift.exerciseId, size = 40.dp)
                        Column {
                            Text(lift.label, style = MaterialTheme.typography.titleSmall)
                            when (lift.load) {
                                GymPlanBuilder.Load.DUMBBELL -> Text("Gewicht pro Kurzhantel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                GymPlanBuilder.Load.BODYWEIGHT_PLUS -> Text("Zusatzgewicht (Gürtel/Weste), leer = ohne", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> {}
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberField(
                            value = w,
                            onValue = { v -> viewModel.update { copy(lifts = lifts + (lift to (v to r))) } },
                            label = when (lift.load) {
                                GymPlanBuilder.Load.BODYWEIGHT_PLUS -> "+ Gewicht"
                                GymPlanBuilder.Load.DUMBBELL -> "je Hand"
                                else -> "Gewicht"
                            },
                            suffix = "kg",
                            decimal = true,
                            modifier = Modifier.weight(1f),
                        )
                        Text("×")
                        NumberField(
                            value = r,
                            onValue = { v -> viewModel.update { copy(lifts = lifts + (lift to (w to v))) } },
                            label = "Wdh",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            else -> PlanPreview(s)
        }
    }
}

@Composable
private fun PlanPreview(s: GymSetupState) {
    val plan = GymPlanBuilder.build(s.input())
    Text(plan.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    SetupHint("${s.days.size} Tage pro Woche · ${plan.routines.joinToString(" → ") { it.title }} im Wechsel")
    plan.routines.forEach { routine ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(routine.title, style = MaterialTheme.typography.titleMedium)
                routine.exercises.forEach { ex ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(ex.exerciseId, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(s.names[ex.exerciseId] ?: ex.exerciseId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            buildString {
                                append("${ex.sets} × ${ex.reps}")
                                if (ex.exerciseId == "ex-plank") append(" s")
                                ex.startWeightKg?.let {
                                    val prefix = if (ex.exerciseId == "ex-klimmzuege" || ex.exerciseId == "ex-g-dips") "+" else ""
                                    append(" · $prefix${num(it)} kg")
                                }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Geschätztes Maximum (1RM)", style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            Lift.entries.forEach { lift ->
                Row(Modifier.fillMaxWidth()) {
                    Text(lift.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        when (lift.load) {
                            GymPlanBuilder.Load.DUMBBELL -> "${plan.oneRepMax.getValue(lift).roundToInt()} kg je Hand"
                            // Total load incl. body; shown as the extra part when positive.
                            GymPlanBuilder.Load.BODYWEIGHT_PLUS -> {
                                val extra = plan.oneRepMax.getValue(lift) - (s.bodyweight.toFloatOrNullDe() ?: 75f)
                                if (extra >= 0f) "Körper + ${extra.roundToInt()} kg" else "unter Körpergewicht"
                            }
                            else -> "${plan.oneRepMax.getValue(lift).roundToInt()} kg"
                        } + if (lift in plan.estimated) " · geschätzt" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    SetupHint(
        "Die Startgewichte lassen etwa zwei Wiederholungen Reserve. Danach passt der Overload-Vorschlag " +
            "das Gewicht nach jedem Training an.",
    )
}

private fun num(v: Float) = if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.GERMAN, "%.1f", v)
