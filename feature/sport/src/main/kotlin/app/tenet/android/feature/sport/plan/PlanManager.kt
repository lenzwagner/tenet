package app.tenet.android.feature.sport.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.data.PlanRepository
import app.tenet.android.core.data.ai.PlanAssistant
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.MeasureType
import app.tenet.android.core.database.entity.RoutineExercise
import app.tenet.android.core.designsystem.component.TenetFilterChip
import app.tenet.android.core.designsystem.component.TenetSpinner
import app.tenet.android.core.designsystem.component.TenetTextField
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.component.rememberDictation
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.feature.sport.ExercisePickerSheet
import app.tenet.android.feature.sport.TrainingDaysRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun Discipline.title() = when (this) {
    Discipline.GYM -> "Gym"
    Discipline.CALISTHENICS -> "Calisthenics"
    Discipline.RUNNING -> "Laufen"
}

private fun dayNames(days: String?): String? =
    days?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..7 }?.takeIf { it.isNotEmpty() }
        ?.joinToString(" · ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2) }

// ============================================================================
// Plans of a discipline
// ============================================================================

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlansViewModel @Inject constructor(private val plans: PlanRepository) : ViewModel() {
    private val discipline = MutableStateFlow<Discipline?>(null)
    val state: StateFlow<List<PlanRepository.PlanSummary>?> = discipline.filterNotNull()
        .flatMapLatest { plans.observeSummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(d: Discipline) { discipline.value = d }
    fun activate(planId: String) = viewModelScope.launch { discipline.value?.let { plans.activate(it, planId) } }
    fun rename(planId: String, name: String) = viewModelScope.launch { plans.rename(planId, name) }
    fun delete(planId: String) = viewModelScope.launch { plans.delete(planId) }
    fun duplicate(planId: String) = viewModelScope.launch { plans.duplicate(planId) }

    /** An empty plan to fill by hand; opens in the editor. */
    fun createEmpty(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val d = discipline.value ?: return@launch
        val first = if (d == Discipline.GYM) "Einheit A" else "Training"
        onCreated(plans.create(d, PlanRepository.Draft(name, emptyList(), listOf(PlanRepository.DraftWorkout(first, emptyList()))), activate = false))
    }
}

/**
 * All plans of one discipline: what is in each, which one is in use; switch,
 * rename, duplicate, delete – and three ways to a new one: build it yourself,
 * answer the questions, or tell the AI coach what you want.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    discipline: Discipline,
    onBack: () -> Unit,
    onEdit: (planId: String) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenChat: () -> Unit,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    LaunchedEffect(discipline) { viewModel.load(discipline) }
    val plans by viewModel.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<PlanRepository.PlanSummary?>(null) }
    var deleting by remember { mutableStateOf<PlanRepository.PlanSummary?>(null) }
    var naming by remember { mutableStateOf(false) }

    renaming?.let { p ->
        NameDialog("Plan umbenennen", p.plan.name, onDismiss = { renaming = null }) { viewModel.rename(p.plan.id, it); renaming = null }
    }
    if (naming) {
        NameDialog("Neuer Plan", "", confirm = "Anlegen", onDismiss = { naming = false }) { name ->
            naming = false
            viewModel.createEmpty(name, onEdit)
        }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("„${p.plan.name}“ löschen?") },
            text = { Text("Der Plan mit seinen Einheiten wird gelöscht. Deine absolvierten Trainings und Statistiken bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(p.plan.id); deleting = null }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Pläne") },
                subtitle = { Text(discipline.title()) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val list = plans
            if (list == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { TenetSpinner() } }
            } else {
                if (list.isEmpty()) {
                    item { Text("Noch kein Plan. Leg unten deinen ersten an.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(list, key = { it.plan.id }) { p ->
                    PlanCard(
                        p,
                        onActivate = { viewModel.activate(p.plan.id) },
                        onEdit = { onEdit(p.plan.id) },
                        onRename = { renaming = p },
                        onDuplicate = if (discipline == Discipline.RUNNING) null else ({ viewModel.duplicate(p.plan.id); Unit }),
                        onDelete = { deleting = p },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "new") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Neuer Plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                        NewPlanOption(
                            Icons.Outlined.AutoAwesome,
                            "Mit KI-Coach erstellen",
                            when (discipline) {
                                Discipline.RUNNING -> "Sag Ziel, Zeitraum und Lauftage – der Coach stellt den Plan zusammen."
                                else -> "Sag Split, Volumen und Übungen – der Coach baut den Plan, du passt ihn an."
                            },
                            onOpenChat,
                        )
                        if (discipline != Discipline.RUNNING) {
                            NewPlanOption(Icons.Outlined.Edit, "Selbst zusammenstellen", "Leerer Plan: Einheiten und Übungen frei wählen.") { naming = true }
                        }
                        if (discipline == Discipline.CALISTHENICS) {
                            // The calisthenics setup rebuilds the routine of the plan in use from your level.
                            NewPlanOption(Icons.Outlined.Quiz, "Aktiven Plan neu einstellen", "Ein paar Fragen zu deinem Stand und deinen Ziel-Skills – die App passt die Übungen des aktiven Plans an.", onOpenSetup)
                        } else {
                            NewPlanOption(Icons.Outlined.Quiz, "Mit Fragen erstellen", "Ein paar Fragen zu Ziel, Erfahrung und Tagen – die App schlägt einen Plan vor.", onOpenSetup)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    p: PlanRepository.PlanSummary,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val active = p.plan.active
    TenetCard(
        modifier = modifier.fillMaxWidth(),
        border = if (active) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.plan.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        "Aktiv".takeIf { active },
                        dayNames(p.plan.trainingDays),
                        p.plan.endDate?.let { end -> runCatching { "bis " + LocalDate.parse(end).format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)) }.getOrNull() },
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                        onDuplicate?.let { dup ->
                            DropdownMenuItem(text = { Text("Duplizieren") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; dup() })
                        }
                        DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (p.plan.discipline == Discipline.RUNNING) {
                    Text(
                        listOfNotNull(RunPlanMath.RunGoal.fromName(p.plan.goal).label, "${p.datedUnits} Einheiten".takeIf { p.datedUnits > 0 }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    p.workouts.forEach { w ->
                        Text(
                            w.workout.title + " · " + if (w.names.isEmpty()) "noch leer" else w.names.take(4).joinToString(", ") + if (w.names.size > 4) " +${w.names.size - 4}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!active) Button(onClick = onActivate, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Aktivieren") }
                    FilledTonalButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Text(if (p.plan.discipline == Discipline.RUNNING) "Ansehen" else "Bearbeiten")
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPlanOption(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    TenetCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String = "Speichern", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TenetTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ============================================================================
// Editor of one gym / calisthenics plan
// ============================================================================

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanEditorViewModel @Inject constructor(private val plans: PlanRepository) : ViewModel() {
    private val planId = MutableStateFlow<String?>(null)
    val state: StateFlow<PlanRepository.PlanSummary?> = planId.filterNotNull()
        .flatMapLatest { plans.observePlan(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _catalog = MutableStateFlow<List<Exercise>>(emptyList())
    val catalog: StateFlow<List<Exercise>> = _catalog.asStateFlow()

    fun load(id: String, discipline: Discipline?) {
        planId.value = id
        if (discipline != null && _catalog.value.isEmpty()) viewModelScope.launch { _catalog.value = plans.catalog(discipline) }
    }

    private fun id() = planId.value
    fun rename(name: String) = viewModelScope.launch { id()?.let { plans.rename(it, name) } }
    fun setDays(days: Set<DayOfWeek>) = viewModelScope.launch { id()?.let { plans.setDays(it, days.map { d -> d.value }) } }
    fun activate(discipline: Discipline) = viewModelScope.launch { id()?.let { plans.activate(discipline, it) } }
    fun addWorkout(title: String) = viewModelScope.launch { id()?.let { plans.addWorkout(it, title) } }
    fun renameWorkout(workoutId: String, title: String) = viewModelScope.launch { plans.renameWorkout(workoutId, title) }
    fun deleteWorkout(workoutId: String) = viewModelScope.launch { id()?.let { plans.deleteWorkout(it, workoutId) } }
    fun addExercise(workoutId: String, exercise: Exercise) = viewModelScope.launch {
        val timed = exercise.measureType == MeasureType.DURATION || exercise.measureType == MeasureType.HOLD
        plans.addExercise(workoutId, exercise, sets = 3, reps = if (timed) 30 else 10, restSec = if (timed) 60 else 120)
    }
    fun update(entry: RoutineExercise) = viewModelScope.launch { plans.updateExercise(entry) }
    fun remove(workoutId: String, exerciseId: String) = viewModelScope.launch { plans.removeExercise(workoutId, exerciseId) }
    fun move(workoutId: String, exerciseId: String, up: Boolean) = viewModelScope.launch { plans.moveExercise(workoutId, exerciseId, up) }
}

/**
 * A plan to shape by hand – your own or one the AI coach proposed: name,
 * training days, workouts and for each exercise sets, reps and rest. Every
 * change is stored at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(planId: String, onBack: () -> Unit, viewModel: PlanEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    LaunchedEffect(planId, state?.plan?.discipline) { viewModel.load(planId, state?.plan?.discipline) }
    var renamePlan by remember { mutableStateOf(false) }
    var renameWorkout by remember { mutableStateOf<Pair<String, String>?>(null) }
    var addWorkout by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<PlanRepository.WorkoutSummary?>(null) }

    val p = state
    if (renamePlan && p != null) {
        NameDialog("Plan umbenennen", p.plan.name, onDismiss = { renamePlan = false }) { viewModel.rename(it); renamePlan = false }
    }
    renameWorkout?.let { (id, title) ->
        NameDialog("Einheit umbenennen", title, onDismiss = { renameWorkout = null }) { viewModel.renameWorkout(id, it); renameWorkout = null }
    }
    if (addWorkout) {
        NameDialog("Neue Einheit", "", confirm = "Anlegen", onDismiss = { addWorkout = false }) { viewModel.addWorkout(it); addWorkout = false }
    }
    pickFor?.let { w ->
        ExercisePickerSheet(
            title = "Übung für ${w.workout.title}",
            catalog = catalog,
            onPick = { viewModel.addExercise(w.workout.id, it); pickFor = null },
            onDismiss = { pickFor = null },
            exclude = w.routine.map { it.exerciseId }.toSet(),
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text(p?.plan?.name ?: "Plan", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(if (p?.plan?.active == true) "Aktiver Plan · Änderungen werden sofort gespeichert" else "Änderungen werden sofort gespeichert") },
                actions = { TooltipIconButton(Icons.Outlined.Edit, "Plan umbenennen", { renamePlan = true }) },
            )
        },
    ) { padding ->
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { TenetSpinner() }
            return@Scaffold
        }
        val gym = p.plan.discipline == Discipline.GYM
        val byId = remember(catalog) { catalog.associateBy { it.id } }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head") {
                TenetCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TrainingDaysRow(p.plan.trainingDays, viewModel::setDays)
                        if (p.plan.active) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Das ist dein aktiver Plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Button(onClick = { viewModel.activate(p.plan.discipline) }, modifier = Modifier.fillMaxWidth()) { Text("Als aktiven Plan wählen") }
                        }
                    }
                }
            }
            items(p.workouts, key = { it.workout.id }) { w ->
                WorkoutCard(
                    w = w,
                    exercises = byId,
                    canDelete = p.workouts.size > 1,
                    onRename = { renameWorkout = w.workout.id to w.workout.title },
                    onDelete = { viewModel.deleteWorkout(w.workout.id) },
                    onAdd = { pickFor = w },
                    onUpdate = viewModel::update,
                    onRemove = { viewModel.remove(w.workout.id, it) },
                    onMove = { id, up -> viewModel.move(w.workout.id, id, up) },
                    modifier = Modifier.animateItem(),
                )
            }
            // Calisthenics trains one routine per plan; gym rotates through its workouts.
            if (gym && p.workouts.size < 7) {
                item(key = "add-workout") {
                    OutlinedButton(onClick = { addWorkout = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Einheit hinzufügen")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    w: PlanRepository.WorkoutSummary,
    exercises: Map<String, Exercise>,
    canDelete: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAdd: () -> Unit,
    onUpdate: (RoutineExercise) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TenetCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(w.workout.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TooltipIconButton(Icons.Outlined.Edit, "Einheit umbenennen", onRename)
                if (canDelete) TooltipIconButton(Icons.Outlined.Delete, "Einheit löschen", onDelete)
            }
            if (w.routine.isEmpty()) {
                Text("Noch keine Übungen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            w.routine.forEachIndexed { i, entry ->
                val ex = exercises[entry.exerciseId]
                val timed = ex?.measureType == MeasureType.DURATION || ex?.measureType == MeasureType.HOLD
                Column(Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.names.getOrNull(i) ?: "Übung", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onMove(entry.exerciseId, true) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Nach oben")
                        }
                        IconButton(onClick = { onMove(entry.exerciseId, false) }, enabled = i < w.routine.lastIndex, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Nach unten")
                        }
                        IconButton(onClick = { onRemove(entry.exerciseId) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Entfernen", modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stepper("Sätze", "${entry.targetSets}", { onUpdate(entry.copy(targetSets = (entry.targetSets - 1).coerceAtLeast(1))) }, { onUpdate(entry.copy(targetSets = (entry.targetSets + 1).coerceAtMost(10))) }, Modifier.weight(1f))
                        Stepper(
                            if (timed) "Sek." else "Wdh.",
                            "${entry.targetReps}",
                            { onUpdate(entry.copy(targetReps = (entry.targetReps - if (timed) 5 else 1).coerceAtLeast(1))) },
                            { onUpdate(entry.copy(targetReps = (entry.targetReps + if (timed) 5 else 1).coerceAtMost(300))) },
                            Modifier.weight(1f),
                        )
                        Stepper("Pause", "${entry.restSec} s", { onUpdate(entry.copy(restSec = (entry.restSec - 15).coerceAtLeast(15))) }, { onUpdate(entry.copy(restSec = (entry.restSec + 15).coerceAtMost(600))) }, Modifier.weight(1.2f))
                    }
                }
            }
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Übung hinzufügen")
            }
        }
    }
}

/** Compact − value + with a small label on top. */
@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = app.tenet.android.core.designsystem.component.tenetFill, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMinus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("−") }
                Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                TextButton(onClick = onPlus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
            }
        }
    }
}

// ============================================================================
// AI coach chat
// ============================================================================

data class PlanChatState(
    val turns: List<PlanAssistant.Turn> = emptyList(),
    val pending: String? = null,
    val answer: PlanAssistant.Answer? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class PlanChatViewModel @Inject constructor(
    private val assistant: PlanAssistant,
    private val plans: PlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanChatState())
    val state: StateFlow<PlanChatState> = _state.asStateFlow()
    val enabled: Boolean get() = assistant.enabled

    fun send(discipline: Discipline, text: String) {
        val s = _state.value
        if (text.isBlank() || s.pending != null) return
        _state.value = s.copy(pending = text.trim())
        viewModelScope.launch {
            val answer = runCatching { assistant.chat(discipline, s.turns, text.trim(), s.answer) }
                .getOrElse { PlanAssistant.Answer("Das ging schief: ${it.message}", failed = true) }
            _state.value = _state.value.copy(
                turns = s.turns + PlanAssistant.Turn(text.trim(), answer.reply),
                pending = null,
                // A failed call keeps the proposal reached so far.
                answer = if (answer.failed) s.answer else answer,
            )
        }
    }

    /** Stores the proposal; gym and calisthenics open in the editor afterwards. */
    fun accept(discipline: Discipline, activate: Boolean, onCreated: (String) -> Unit) {
        val answer = _state.value.answer ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val id = runCatching {
                when {
                    answer.run != null -> assistant.createRunPlan(answer.run!!)
                    answer.draft != null -> plans.create(discipline, answer.draft!!, activate)
                    else -> null
                }
            }.getOrNull()
            _state.value = _state.value.copy(saving = false)
            id?.let(onCreated)
        }
    }
}

/**
 * Chat with the AI coach: say which split, how much volume, which exercises –
 * the proposal appears under the answer and changes with every further
 * message. Accepting stores it as a plan that stays editable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanChatScreen(
    discipline: Discipline,
    onBack: () -> Unit,
    onCreated: (planId: String) -> Unit,
    viewModel: PlanChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val send = { msg: String ->
        if (msg.isNotBlank()) {
            viewModel.send(discipline, msg)
            text = ""
        }
    }
    val dictation = rememberDictation("Was für einen Plan möchtest du?") { send(it) }
    LaunchedEffect(state.turns.size, state.pending) { listState.animateScrollToItem(Int.MAX_VALUE / 2) }
    val examples = when (discipline) {
        Discipline.GYM -> listOf(
            "3er-Split Push/Pull/Beine, 6 Tage, Muskelaufbau, viel Volumen",
            "Oberkörper/Unterkörper, 4 Tage, Fokus Kraft",
            "Ganzkörper, 3 Tage, kurz und knackig, nur Grundübungen",
            "4er-Split, Schwerpunkt Brust und Schultern, kein Kreuzheben",
        )
        Discipline.CALISTHENICS -> listOf(
            "Ganzkörper, 3 Tage, Fokus Klimmzüge und Dips",
            "Skill-Training: Handstand und L-Sit, dazu Kraft",
            "Anfänger, wenig Volumen, 20 Minuten",
        )
        Discipline.RUNNING -> listOf(
            "10 km unter 50 Minuten in 10 Wochen, 3 Läufe pro Woche",
            "Halbmarathon im April, 4 Läufe, aktuell 5 km in 27:00",
            "Fit bleiben, 2 lockere Läufe pro Woche",
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("KI-Coach") },
                subtitle = { Text("Plan für ${discipline.title()}") },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TenetTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(if (state.answer == null) "Beschreib deinen Plan …" else "Was soll ich ändern?") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(text) }),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (text.isBlank()) dictation.launch() else send(text) }, enabled = state.pending == null) {
                    if (text.isBlank()) Icon(Icons.Outlined.Mic, contentDescription = "Sprechen")
                    else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Senden")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Bubble(
                    when {
                        !viewModel.enabled -> "Die KI ist aus oder ohne Schlüssel. Schalte sie unter Einstellungen → KI ein."
                        discipline == Discipline.RUNNING -> "Sag mir dein Ziel, bis wann, wie oft du laufen willst und – wenn du sie kennst – deine aktuelle Zeit. Ich stelle die Eckdaten zusammen, die App baut daraus den Plan."
                        else -> "Sag mir, welchen Split du willst, an wie vielen Tagen, wie viel Volumen und welche Übungen rein oder raus sollen. Ich baue den Plan, du kannst ihn danach frei anpassen."
                    },
                    user = false,
                )
            }
            if (state.turns.isEmpty() && state.pending == null) {
                item(key = "examples") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(examples) { ex -> TenetFilterChip(selected = false, onClick = { send(ex) }, label = { Text(ex, maxLines = 1) }) }
                    }
                }
            }
            state.turns.forEach { turn ->
                item { Bubble(turn.user, user = true) }
                item { Bubble(turn.assistant, user = false) }
            }
            state.pending?.let { p ->
                item(key = "pending") { Bubble(p, user = true) }
                item(key = "working") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TenetSpinner(Modifier.size(32.dp))
                        Text("Coach baut den Plan …", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val answer = state.answer
            if (answer != null && state.pending == null && (answer.draft != null || answer.run != null)) {
                item(key = "proposal") {
                    Proposal(
                        answer = answer,
                        saving = state.saving,
                        onAccept = { activate -> viewModel.accept(discipline, activate, onCreated) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, user: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Proposal(answer: PlanAssistant.Answer, saving: Boolean, onAccept: (activate: Boolean) -> Unit) {
    TenetCard(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val run = answer.run
            val draft = answer.draft
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(draft?.name ?: run?.goal?.label?.let { "Laufplan · $it" } ?: "Vorschlag", style = MaterialTheme.typography.titleMedium)
            }
            if (run != null) {
                val days = run.days.joinToString(" · ") { DayOfWeek.of(it + 1).getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2) }
                listOfNotNull(
                    "Zieltag " + run.goalDate.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)) + " · ${run.weeks} Wochen",
                    "${run.runsPerWeek} Läufe pro Woche" + if (days.isNotBlank()) " ($days)" else "",
                    run.current5kSec?.let { "Aktuell 5 km in ${it / 60}:${"%02d".format(it % 60)}" },
                    run.targetTimeSec?.let { t -> "Zielzeit " + if (t >= 3600) "${t / 3600}:${"%02d".format(t % 3600 / 60)}:${"%02d".format(t % 60)}" else "${t / 60}:${"%02d".format(t % 60)}" },
                    when {
                        run.volume < 0.9f -> "Umfang: eher wenig"
                        run.volume > 1.1f -> "Umfang: eher viel"
                        else -> null
                    },
                ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("Die App berechnet daraus alle Einheiten mit Tempo. Der Plan wird dein aktiver Laufplan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onAccept(true) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Plan erstellen") }
            } else if (draft != null) {
                dayNames(draft.days.joinToString(","))?.let { Text("Trainingstage: $it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                answer.preview.forEach { w ->
                    Column {
                        Text(w.title, style = MaterialTheme.typography.titleSmall)
                        w.exercises.forEach { (name, e) ->
                            Row {
                                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${e.sets} × ${e.reps} · ${e.restSec} s", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (answer.unknown.isNotEmpty()) {
                    Text("Nicht im Katalog, weggelassen: ${answer.unknown.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text("Nach dem Übernehmen kannst du alles anpassen: Übungen, Sätze, Wiederholungen, Pausen, Einheiten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAccept(true) }, enabled = !saving, modifier = Modifier.weight(1f)) { Text("Übernehmen & aktivieren") }
                    FilledTonalButton(onClick = { onAccept(false) }, enabled = !saving) { Text("Nur speichern") }
                }
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}
