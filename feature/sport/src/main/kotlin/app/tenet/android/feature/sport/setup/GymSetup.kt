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
import app.tenet.android.core.common.GymSplits
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.GymPlanBuilder.Goal
import app.tenet.android.core.common.GymPlanBuilder.Level
import app.tenet.android.core.common.GymPlanBuilder.Lift
import app.tenet.android.core.common.GymPlanBuilder.Split
import app.tenet.android.core.common.Sex
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.data.ai.PlanAssistant
import app.tenet.android.core.database.entity.MeasureType
import app.tenet.android.feature.sport.plan.SplitPicker
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapHoriz
import app.tenet.android.core.common.MovementPattern
import app.tenet.android.core.common.RestAdvisor
import app.tenet.android.feature.sport.plan.Stepper
import androidx.compose.material3.IconButton
import app.tenet.android.feature.sport.plan.matching
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
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
    /** Split and muscle-group combination; null = the one recommended for the days. */
    val choice: GymSplits.Choice? = null,
    val bodyweight: String = "",
    val female: Boolean = false,
    /** Lift → (weight, reps) as typed. */
    val lifts: Map<Lift, Pair<String, String>> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val exercises: List<Exercise> = emptyList(),
    /**
     * Exercises chosen in the preview, per routine (index): swapped, added, removed
     * or set by the AI coach. Weights still follow the strength data.
     */
    val previewEdits: Map<Int, List<PreviewExerciseEdit>> = emptyMap(),
    val aiBusy: Boolean = false,
    /** The coach's answer to the last request. */
    val aiNote: String? = null,
    val historyMode: Boolean = false,
    val historyLiftCount: Int? = null,
    val busy: Boolean = false,
    val formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
) {
    val effectiveChoice: GymSplits.Choice get() = choice ?: GymSplits.Choice.recommended(days.size)

    fun input() = GymPlanBuilder.Input(
        goal = goal,
        level = level,
        split = effectiveChoice.builtin ?: Split.CUSTOM,
        days = days.sorted(),
        bodyweightKg = bodyweight.toFloatOrNullDe(),
        female = female,
        formula = formula,
        customUnits = effectiveChoice.units,
        customName = effectiveChoice.sizeLabel,
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

    private fun timed(exerciseId: String): Boolean {
        val measure = exercises.firstOrNull { it.id == exerciseId }?.measureType
        return measure == MeasureType.DURATION || measure == MeasureType.HOLD
    }

    /** Rest by how heavy the exercise is: long for squat or deadlift, short for curls and core. */
    private fun advisedRest(exerciseId: String, reps: Int): Int {
        val ex = exercises.firstOrNull { it.id == exerciseId } ?: return goal.accRestSec
        if (timed(exerciseId)) return 60
        return RestAdvisor.advise(MovementPattern.fromName(ex.pattern), ex.primaryMuscles, reps).seconds
    }

    fun isTimed(exerciseId: String) = timed(exerciseId)

    fun plan(): GymPlanBuilder.Plan {
        val input = input()
        val generated = GymPlanBuilder.build(input)
        return generated.copy(
            routines = generated.routines.mapIndexed { index, routine ->
                val edits = previewEdits[index]
                val chosen = edits?.map { edit ->
                    val base = routine.exercises.firstOrNull { it.exerciseId == edit.exerciseId }
                    if (base != null && (edit.reps == null || edit.reps == base.reps)) return@map base.copy(sets = edit.sets)
                    val timed = timed(edit.exerciseId)
                    val reps = edit.reps ?: if (timed) 45 else goal.accReps
                    GymPlanBuilder.PlannedExercise(
                        exerciseId = edit.exerciseId,
                        sets = edit.sets,
                        reps = reps,
                        restSec = goal.accRestSec,
                        startWeightKg = if (timed) null else GymPlanBuilder.startWeight(input, edit.exerciseId, reps),
                    )
                } ?: routine.exercises
                routine.copy(
                    exercises = chosen.map { e ->
                        val rest = edits?.firstOrNull { it.exerciseId == e.exerciseId }?.rest
                        e.copy(restSec = rest ?: advisedRest(e.exerciseId, e.reps), restFixed = rest != null)
                    },
                )
            },
        )
    }
}

/** [reps]: set by hand or by the AI coach, null = from the goal; [rest]: set by hand, null = advised for the exercise. */
data class PreviewExerciseEdit(val exerciseId: String, val sets: Int, val reps: Int? = null, val rest: Int? = null)

@HiltViewModel
class GymSetupViewModel @Inject constructor(
    private val repository: SportRepository,
    private val settings: UserSettingsRepository,
    private val assistant: PlanAssistant,
) : ViewModel() {
    private val _state = MutableStateFlow(GymSetupState())
    val state: StateFlow<GymSetupState> = _state
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()
    val aiEnabled: Boolean get() = assistant.enabled

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

    /** Changes the exercise list of routine [routine] in the preview. */
    private fun editRoutine(routine: Int, change: (List<PreviewExerciseEdit>) -> List<PreviewExerciseEdit>) {
        _state.update { state ->
            val current = state.plan().routines.getOrNull(routine)?.exercises ?: return@update state
            val edits = state.previewEdits[routine] ?: current.map { PreviewExerciseEdit(it.exerciseId, it.sets) }
            state.copy(previewEdits = state.previewEdits + (routine to change(edits).distinctBy { it.exerciseId }))
        }
    }

    fun swapExercise(routine: Int, index: Int, exerciseId: String) =
        editRoutine(routine) { list -> list.mapIndexed { i, e -> if (i == index) PreviewExerciseEdit(exerciseId, e.sets) else e } }

    fun setSets(routine: Int, index: Int, sets: Int) =
        editRoutine(routine) { list -> list.mapIndexed { i, e -> if (i == index) e.copy(sets = sets.coerceIn(1, 10)) else e } }

    fun setReps(routine: Int, index: Int, reps: Int) =
        editRoutine(routine) { list -> list.mapIndexed { i, e -> if (i == index) e.copy(reps = reps.coerceIn(1, 300)) else e } }

    fun setRest(routine: Int, index: Int, rest: Int) =
        editRoutine(routine) { list -> list.mapIndexed { i, e -> if (i == index) e.copy(rest = rest.coerceIn(15, 600)) else e } }

    fun addExercise(routine: Int, exerciseId: String) = editRoutine(routine) { it + PreviewExerciseEdit(exerciseId, 3) }

    fun removeExercise(routine: Int, index: Int) = editRoutine(routine) { list -> list.filterIndexed { i, _ -> i != index } }

    /** The AI coach picks the exercises for the chosen split from the catalogue; [wish] = extra requests. */
    fun fillWithAi(wish: String) {
        val s = _state.value
        if (s.aiBusy) return
        _state.update { it.copy(aiBusy = true, aiNote = null) }
        viewModelScope.launch {
            val choice = s.effectiveChoice
            val message = "Gym-Plan: Ziel ${s.goal.label} (${s.goal.description}). Erfahrung: ${s.level.label}. " +
                "${s.days.size} Trainingstage pro Woche; die ${choice.units.size} Einheiten wechseln sich fortlaufend ab, unabhängig von der Zahl der Tage. " +
                "${choice.sizeLabel} mit genau dieser Kombination: ${choice.label}." +
                if (wish.isNotBlank()) " Weitere Wünsche: ${wish.trim()}." else ""
            val answer = runCatching { assistant.chat(Discipline.GYM, emptyList(), message, null) }
                .getOrElse { PlanAssistant.Answer("Das ging schief: ${it.message}", failed = true) }
            val workouts = answer.draft?.workouts.orEmpty()
            _state.update { now ->
                // The split changed meanwhile: the answer no longer fits.
                if (now.effectiveChoice != choice) return@update now.copy(aiBusy = false)
                if (answer.failed || workouts.isEmpty()) return@update now.copy(aiBusy = false, aiNote = answer.reply)
                val titles = choice.units.map { it.title }
                val edits = titles.indices.mapNotNull { i ->
                    (workouts.firstOrNull { it.title.equals(titles[i], ignoreCase = true) } ?: workouts.getOrNull(i))
                        ?.let { w -> i to w.exercises.map { PreviewExerciseEdit(it.exerciseId, it.sets, it.reps, it.restSec) } }
                }
                now.copy(aiBusy = false, aiNote = answer.reply, previewEdits = now.previewEdits + edits)
            }
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
            3 -> s.effectiveChoice.valid
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
                // Repetitions follow the goal again.
                onSelect = { viewModel.update { copy(goal = it, previewEdits = previewEdits.mapValues { (_, list) -> list.map { e -> e.copy(reps = null) } }) } },
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
                            // A split chosen by hand stays; the recommendation follows the days.
                            copy(days = selectedDays, previewEdits = if (choice == null) emptyMap() else previewEdits)
                        }
                    },
                )
                SetupHint(
                    if (s.days.size < 2) "Wähle mindestens zwei Tage."
                    else "${s.days.size} Tage pro Woche. Zwischen zwei Einheiten für dieselben Muskeln sollte ein Tag Pause liegen.",
                )
            }
            3 -> {
                SplitPicker(
                    choice = s.effectiveChoice,
                    onChange = { viewModel.update { copy(choice = it, previewEdits = emptyMap(), aiNote = null) } },
                    recommended = GymSplits.Choice.recommended(s.days.size),
                )
                SetupHint(
                    "Split-Länge und Trainingstage sind unabhängig: Die Einheiten wechseln sich fortlaufend ab – nach Push kommt Pull und so weiter. " +
                        "Die Übungen wählst du im letzten Schritt, selbst oder mit dem KI-Coach.",
                )
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
            else -> PlanPreview(
                s = s,
                aiEnabled = viewModel.aiEnabled,
                onAi = viewModel::fillWithAi,
                onSwap = viewModel::swapExercise,
                onSets = viewModel::setSets,
                onReps = viewModel::setReps,
                onRest = viewModel::setRest,
                onAdd = viewModel::addExercise,
                onRemove = viewModel::removeExercise,
            )
        }
    }
}

@Composable
private fun PlanPreview(
    s: GymSetupState,
    aiEnabled: Boolean,
    onAi: (wish: String) -> Unit,
    onSwap: (routine: Int, index: Int, exerciseId: String) -> Unit,
    onSets: (routine: Int, index: Int, sets: Int) -> Unit,
    onReps: (routine: Int, index: Int, reps: Int) -> Unit,
    onRest: (routine: Int, index: Int, rest: Int) -> Unit,
    onAdd: (routine: Int, exerciseId: String) -> Unit,
    onRemove: (routine: Int, index: Int) -> Unit,
) {
    val plan = s.plan()
    val units = s.effectiveChoice.units
    var swapTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var addTarget by remember { mutableStateOf<Int?>(null) }
    var wish by rememberSaveable { mutableStateOf("") }
    Text(plan.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    SetupHint("${s.days.size} Tage pro Woche · ${plan.routines.joinToString(" → ") { it.title }} im Wechsel")
    SetupHint("Sätze, Wiederholungen und Pausen kannst du je Übung anpassen. Schwere Grundübungen bekommen längere Pausen als Isolationsübungen.")
    TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("KI-Coach", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (aiEnabled) "Lass die Übungen für deinen Split von der KI zusammenstellen – oder wähle sie unten selbst."
                else "Die KI ist aus oder ohne Schlüssel. Schalte sie unter Einstellungen → KI ein, dann stellt der Coach die Übungen zusammen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (aiEnabled) {
                app.tenet.android.core.designsystem.component.TenetTextField(
                    value = wish,
                    onValueChange = { wish = it.take(4000) },
                    minLines = 2,
                    maxLines = 12,
                    label = { Text("Wünsche (optional)") },
                    placeholder = { Text("z. B. kein Kreuzheben, viel Volumen, nur Kurzhanteln") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.aiBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        app.tenet.android.core.designsystem.component.TenetSpinner(Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Coach stellt die Übungen zusammen …", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(onClick = { onAi(wish) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (s.aiNote == null) "Übungen von der KI wählen lassen" else "Nochmal von der KI wählen lassen")
                    }
                }
            }
            s.aiNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    plan.routines.forEachIndexed { r, routine ->
        TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(routine.title, style = MaterialTheme.typography.titleMedium)
                if (routine.exercises.isEmpty()) SetupHint("Noch keine Übungen.")
                routine.exercises.forEachIndexed { index, ex ->
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ExerciseThumb(ex.exerciseId, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(s.names[ex.exerciseId] ?: ex.exerciseId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { swapTarget = r to index }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = "Übung wechseln", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onRemove(r, index) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = "Entfernen", modifier = Modifier.size(18.dp))
                            }
                        }
                        val timed = s.isTimed(ex.exerciseId)
                        val step = if (timed) 5 else 1
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stepper("Sätze", "${ex.sets}", { onSets(r, index, ex.sets - 1) }, { onSets(r, index, ex.sets + 1) }, Modifier.weight(1f))
                            Stepper(if (timed) "Sek." else "Wdh.", "${ex.reps}", { onReps(r, index, ex.reps - step) }, { onReps(r, index, ex.reps + step) }, Modifier.weight(1f))
                            Stepper("Pause", "${ex.restSec} s", { onRest(r, index, ex.restSec - 15) }, { onRest(r, index, ex.restSec + 15) }, Modifier.weight(1.2f))
                        }
                    }
                }
                TextButton(onClick = { addTarget = r }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Übung hinzufügen")
                }
            }
        }
    }

    swapTarget?.let { (r, index) ->
        val routine = plan.routines.getOrNull(r)
        val current = routine?.exercises?.getOrNull(index)
        if (routine == null || current == null) {
            swapTarget = null
        } else {
            ExercisePickerSheet(
                title = "Übung wechseln",
                catalog = s.exercises,
                exclude = routine.exercises.map { it.exerciseId }.toSet() - current.exerciseId,
                onPick = { exercise ->
                    onSwap(r, index, exercise.id)
                    swapTarget = null
                },
                onDismiss = { swapTarget = null },
            )
        }
    }
    // Stays open to add several in a row; what is chosen leaves the list.
    addTarget?.let { r ->
        val routine = plan.routines.getOrNull(r) ?: return@let
        val muscles = units.getOrNull(r)?.muscles.orEmpty()
        ExercisePickerSheet(
            title = "Übungen für ${routine.title}",
            catalog = s.exercises,
            exclude = routine.exercises.map { it.exerciseId }.toSet(),
            suggestions = s.exercises.matching(muscles),
            suggestionsTitle = "Passend zur Einheit",
            onPick = { exercise -> onAdd(r, exercise.id) },
            onDismiss = { addTarget = null },
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
