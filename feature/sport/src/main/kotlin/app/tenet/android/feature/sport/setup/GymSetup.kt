package app.tenet.android.feature.sport.setup

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import app.tenet.android.feature.sport.ExerciseThumb
import app.tenet.android.feature.sport.ExercisePickerSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.GymPlanBuilder
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.GymPlanBuilder.Goal
import app.tenet.android.core.common.GymPlanBuilder.Level
import app.tenet.android.core.common.GymPlanBuilder.Lift
import app.tenet.android.core.common.GymPlanBuilder.Split
import app.tenet.android.core.common.Sex
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
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
    val customRoutineTitles: List<String> = emptyList(),
    val bodyweight: String = "",
    val female: Boolean = false,
    /** Lift → (weight, reps) as typed. */
    val lifts: Map<Lift, Pair<String, String>> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val exercises: List<Exercise> = emptyList(),
    /** Preview-only changes; generated reps/weights still follow selected goal and strength data. */
    val previewEdits: Map<String, PreviewExerciseEdit> = emptyMap(),
    val historyMode: Boolean = false,
    val historyLiftCount: Int? = null,
    val busy: Boolean = false,
    val formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
) {
    val effectiveSplit: Split get() = split ?: GymPlanBuilder.recommendedSplit(days.size)

    fun input() = GymPlanBuilder.Input(
        goal = goal,
        level = level,
        split = effectiveSplit,
        days = days.sorted(),
        bodyweightKg = bodyweight.toFloatOrNullDe(),
        female = female,
        formula = formula,
        customRoutineTitles = customRoutineTitles,
        lifts = lifts.mapNotNull { (lift, v) ->
            val r = v.second.toIntOrNull()
            // Pull-ups/dips: empty weight with reps = bodyweight only.
            val w = v.first.toFloatOrNullDe()
                ?: if (lift.load == GymPlanBuilder.Load.BODYWEIGHT_PLUS && r != null) 0f else null
            if (w != null && r != null && r > 0 && (w > 0f || lift.load == GymPlanBuilder.Load.BODYWEIGHT_PLUS)) {
                lift to GymPlanBuilder.LiftInput(w, r)
            } else {
                null
            }
        }.toMap(),
    )

    fun plan(): GymPlanBuilder.Plan {
        val generated = GymPlanBuilder.build(input())
        return generated.copy(
            routines = generated.routines.map { routine ->
                routine.copy(
                    exercises = routine.exercises.mapIndexed { index, exercise ->
                        previewEdits["${routine.title}:$index"]?.let { edit ->
                            exercise.copy(
                                exerciseId = edit.exerciseId,
                                sets = edit.sets,
                                startWeightKg = if (edit.exerciseId == exercise.exerciseId) exercise.startWeightKg else null,
                            )
                        } ?: exercise
                    },
                )
            },
        )
    }
}

data class PreviewExerciseEdit(val exerciseId: String, val sets: Int)

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
            val preferences = settings.settings.first()
            val profile = preferences.profile
            val names = repository.exerciseNames()
            val exercises = repository.observeExercisesOnce(Discipline.GYM)
            _state.update {
                it.copy(
                    bodyweight = profile?.weightKg?.let { w -> num(w) }.orEmpty(),
                    female = profile?.sex == Sex.FEMALE,
                    names = names,
                    exercises = exercises,
                    formula = preferences.oneRepMaxFormula,
                )
            }
        }
    }

    fun update(block: GymSetupState.() -> GymSetupState) = _state.update(block)

    private var historyLoaded = false

    fun useTrainingHistory(enabled: Boolean) {
        if (!enabled || historyLoaded) return
        historyLoaded = true
        viewModelScope.launch {
            _state.update { it.copy(historyMode = true) }
            val formula = settings.settings.first().oneRepMaxFormula
            val history = repository.setupLiftsFromHistory(formula)
            val latestWeight = repository.latestBodyMetric()?.weight
            _state.update { state ->
                state.copy(
                    bodyweight = latestWeight?.let(::num) ?: state.bodyweight,
                    lifts = history.mapValues { (_, value) -> num(value.weightKg) to value.reps.toString() },
                    formula = formula,
                    historyLiftCount = history.size,
                )
            }
        }
    }

    fun editPreview(routineTitle: String, index: Int, exerciseId: String? = null, sets: Int? = null) {
        _state.update { state ->
            val planExercise = state.plan().routines.firstOrNull { it.title == routineTitle }?.exercises?.getOrNull(index)
                ?: return@update state
            val key = "$routineTitle:$index"
            state.copy(
                previewEdits = state.previewEdits + (key to PreviewExerciseEdit(
                    exerciseId = exerciseId ?: planExercise.exerciseId,
                    sets = (sets ?: planExercise.sets).coerceIn(1, 10),
                )),
            )
        }
    }

    fun finish() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repository.applyGymSetup(s.plan(), s.days.toList())
            settings.markSportSetupDone("GYM")
            _done.emit(Unit)
        }
    }
}

private val STEPS = listOf(
    "Kraft oder Muskelaufbau?",
    "Wie viel Erfahrung hast du?",
    "An welchen Tagen trainierst du?",
    "Welcher Split passt?",
    "Deine aktuellen Kraftwerte",
    "Dein Plan",
)

private val CUSTOM_SPLIT_EXAMPLES = listOf(
    "Brust", "Rücken", "Arme", "Beine", "Schultern", "Ganzkörper A", "Ganzkörper B",
)

private fun customSplitTitles(current: List<String>, count: Int): List<String> =
    List(count.coerceIn(2, 7)) { index ->
        current.getOrNull(index)?.takeIf(String::isNotBlank) ?: CUSTOM_SPLIT_EXAMPLES[index]
    }

private fun defaultCustomRoutineCount(trainingDays: Int): Int = trainingDays.coerceIn(2, 4)

@Composable
fun GymSetupScreen(
    onDone: () -> Unit,
    useTrainingHistory: Boolean = false,
    viewModel: GymSetupViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }
    LaunchedEffect(useTrainingHistory) { viewModel.useTrainingHistory(useTrainingHistory) }
    SetupScaffold(
        title = "Gym einrichten",
        step = s.step,
        stepTitles = STEPS,
        canContinue = when (s.step) {
            2 -> s.days.size >= 2
            3 -> s.effectiveSplit != Split.CUSTOM ||
                (s.customRoutineTitles.size in 2..7 && s.customRoutineTitles.all(String::isNotBlank))
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
                    onChange = { set ->
                        viewModel.update {
                            val selectedDays = set.map { it.value }.toSet()
                            copy(
                                days = selectedDays,
                                split = null,
                                customRoutineTitles = customRoutineTitles.ifEmpty {
                                    customSplitTitles(emptyList(), defaultCustomRoutineCount(selectedDays.size))
                                },
                            )
                        }
                    },
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
                    onSelect = { selected ->
                        viewModel.update {
                            copy(
                                split = selected,
                                customRoutineTitles = if (selected == Split.CUSTOM) {
                                    customRoutineTitles.ifEmpty {
                                        customSplitTitles(emptyList(), defaultCustomRoutineCount(days.size))
                                    }
                                } else {
                                    customRoutineTitles
                                },
                            )
                        }
                    },
                    title = { it.label },
                    description = { it.description },
                    badge = { if (it == recommended) "Empfohlen" else null },
                )
                if (s.effectiveSplit == Split.CUSTOM) {
                    val routineCount = s.customRoutineTitles.size.coerceIn(2, 7)
                    SetupHint("Trainingstage und Split-Länge sind unabhängig. Einheiten rotieren fortlaufend über Wochengrenzen.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Einheiten im Zyklus", modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = routineCount > 2,
                            onClick = {
                                viewModel.update {
                                    copy(customRoutineTitles = customSplitTitles(customRoutineTitles, routineCount - 1))
                                }
                            },
                        ) { Text("−") }
                        Text("$routineCount", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            enabled = routineCount < 7,
                            onClick = {
                                viewModel.update {
                                    copy(customRoutineTitles = customSplitTitles(customRoutineTitles, routineCount + 1))
                                }
                            },
                        ) { Text("+") }
                    }
                    customSplitTitles(s.customRoutineTitles, routineCount).forEachIndexed { index, title ->
                        app.tenet.android.core.designsystem.component.TenetTextField(
                            value = title,
                            onValueChange = { value ->
                                viewModel.update {
                                    val titles = customSplitTitles(customRoutineTitles, routineCount).toMutableList()
                                    titles[index] = value
                                    copy(customRoutineTitles = titles)
                                }
                            },
                            label = { Text("Einheit ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                SetupHint("Die Einheiten wechseln sich ab: nach A kommt B, nach Push kommt Pull und so weiter.")
            }
            4 -> {
                if (s.historyMode) {
                    SetupHint(
                        when (s.historyLiftCount) {
                            null -> "Bisherige Trainings werden ausgewertet …"
                            0 -> "Keine passenden abgeschlossenen Sätze gefunden. Fehlende Werte werden geschätzt oder können hier eingetragen werden."
                            else -> "${s.historyLiftCount} Kraftwerte aus abgeschlossenen Trainings übernommen. Du kannst sie hier korrigieren."
                        },
                    )
                }
                SetupHint(
                    "Trag einen schweren Satz der letzten Wochen ein, möglichst 1–10 Wiederholungen nahe am Limit. Kurzhanteln: Gewicht pro Hand. " +
                        "Ohne flache Bench nutzt Tenet optional deine 30°-Schrägbank für eine grobe Bench-Schätzung. " +
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
            else -> PlanPreview(s, viewModel::editPreview)
        }
    }
}

@Composable
private fun PlanPreview(
    s: GymSetupState,
    onEdit: (routineTitle: String, index: Int, exerciseId: String?, sets: Int?) -> Unit,
) {
    val plan = s.plan()
    var swapTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    Text(plan.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    SetupHint("${s.days.size} Tage pro Woche · ${plan.routines.joinToString(" → ") { it.title }} im Wechsel")
    plan.routines.forEach { routine ->
        TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(routine.title, style = MaterialTheme.typography.titleMedium)
                routine.exercises.forEachIndexed { index, ex ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(ex.exerciseId, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.names[ex.exerciseId] ?: ex.exerciseId, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { swapTarget = routine.title to index }) { Text("Übung wechseln") }
                        }
                        TextButton(onClick = { onEdit(routine.title, index, null, ex.sets - 1) }, enabled = ex.sets > 1) { Text("−") }
                        Text("${ex.sets} × ${ex.reps}", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { onEdit(routine.title, index, null, ex.sets + 1) }, enabled = ex.sets < 10) { Text("+") }
                    }
                }
            }
        }
    }

    swapTarget?.let { (routineTitle, index) ->
        val routine = plan.routines.first { it.title == routineTitle }
        ExercisePickerSheet(
            title = "Übung wechseln",
            catalog = s.exercises,
            exclude = routine.exercises.map { it.exerciseId }.toSet() - routine.exercises[index].exerciseId,
            onPick = { exercise ->
                onEdit(routineTitle, index, exercise.id, null)
                swapTarget = null
            },
            onDismiss = { swapTarget = null },
        )
    }
    TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Geschätztes Maximum (1RM) · ${s.formula.label}", style = MaterialTheme.typography.titleSmall)
            plan.benchEstimate?.let { Text(it.label, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            Lift.entries.filter { it !in setOf(Lift.INCLINE_BENCH, Lift.INCLINE_DUMBBELL) || it in s.input().lifts }.forEach { lift ->
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
