package app.tenet.android.feature.sport.gym

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.runtime.key
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.History
import app.tenet.android.core.designsystem.component.CompactNumberField
import app.tenet.android.core.designsystem.component.TenetSwitch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.MoreVert
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.feature.sport.ChangeScopeDialog
import app.tenet.android.feature.sport.ExercisePickerSheet
import app.tenet.android.core.designsystem.component.rememberDictation
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import app.tenet.android.core.common.OverloadMath
import androidx.compose.foundation.layout.size
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.TenetSlider
import app.tenet.android.core.designsystem.component.rememberSheetState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ButtonDefaults
import app.tenet.android.core.designsystem.component.TooltipIconButton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.PlateCalculator
import app.tenet.android.core.database.entity.SetType
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Active gym session (App_Konzept.md 5.2.1): fullscreen, tab bar hidden,
 * set table per exercise with the "Vorher" column and an automatic
 * rest timer after each completed set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()
    val appSnackbar = LocalAppSnackbar.current
    LaunchedEffect(Unit) { viewModel.messages.collect { appSnackbar?.show(it) } }
    val speech = rememberDictation("Sag deine Sätze, z. B. „Bankdrücken 3 Sätze à 8 mit 80 Kilo“", viewModel::voiceSets)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    LaunchedEffect(Unit) { viewModel.finished.collect { onFinished() } }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val hasPlan by viewModel.hasPlan.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var pending by remember { mutableStateOf<PendingChange?>(null) }
    fun apply(change: PendingChange, permanent: Boolean) = when (change) {
        is PendingChange.Swap -> viewModel.swapExercise(change.block.sessionExerciseId, change.exercise, permanent)
        is PendingChange.Add -> viewModel.addExercise(change.exercise, permanent)
        is PendingChange.Remove -> viewModel.removeExercise(change.block.sessionExerciseId, change.block.exercise.name, permanent)
        is PendingChange.Superset -> viewModel.setSuperset(change.block.sessionExerciseId, change.link, permanent)
    }
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val endRequested by viewModel.endRequested.collectAsStateWithLifecycle()
    val next by viewModel.next.collectAsStateWithLifecycle()
    val remaining by viewModel.remaining.collectAsStateWithLifecycle()
    var trainingMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionId, trainingMode) { viewModel.setTrainingMode(trainingMode) }

    val context = LocalContext.current
    // The rest-timer notification needs the POST_NOTIFICATIONS permission (API 33+).
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(trainingMode) {
        if (trainingMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var plateSheetVisible by remember { mutableStateOf(false) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val startedAt = state.session?.startedAt
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        TooltipIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück (Session bleibt aktiv)", onClick = onBack)
                    },
                    title = { Text(state.title) },
                    subtitle = {
                        // Live: time · sets done · volume so far (like Hevy/Strong).
                        val working = state.blocks.flatMap { it.rows }.filter { it.set.type != SetType.WARMUP }
                        val done = working.filter { it.set.completed }
                        val volume = done.sumOf { (it.set.weight * it.set.reps).toDouble() }.toFloat()
                        Text(
                            elapsed(startedAt, now) + " · ${done.size}/${working.size} Sätze" +
                                if (volume > 0f) " · " + (if (volume >= 1000f) String.format(java.util.Locale.GERMAN, "%.1f t", volume / 1000f) else "${volume.toInt()} kg") else "",
                        )
                    },
                    actions = {
                        if (viewModel.aiAvailable) {
                            if (aiBusy) {
                                LoadingIndicator(Modifier.size(40.dp).padding(4.dp))
                            } else {
                                TooltipIconButton(icon = Icons.Outlined.Mic, contentDescription = "Sätze per Sprache", onClick = {
                                    speech.launch()
                                })
                            }
                        }
                        TooltipIconButton(icon = Icons.Outlined.Calculate, contentDescription = "Plattenrechner", onClick = { plateSheetVisible = true })
                        TooltipIconButton(icon = Icons.Outlined.PlayCircle, contentDescription = "Trainingsmodus", onClick = {
                            trainingMode = true
                        })
                        FilledIconButton(onClick = { viewModel.requestEnd() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Check, contentDescription = "Workout beenden")
                    }
                    },
                )
            },
        ) { padding ->
            if (state.loading) return@Scaffold

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (remaining > 0) {
                    item(key = "training-mode") {
                        // Guided: one set at a time with timed rest (also in the notification).
                        FilledTonalButton(
                            onClick = {
                                trainingMode = true
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Trainingsmodus · Satz für Satz mit Pausen")
                        }
                    }
                }
                if (state.blocks.isEmpty()) {
                    item {
                        Text(
                            text = "Keine Übungen in dieser Session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(state.blocks, key = { _, b -> b.sessionExerciseId }) { index, block ->
                    val nextBlock = state.blocks.getOrNull(index + 1)
                    ExerciseCard(
                        block = block,
                        supersetLabel = block.supersetGroup?.let { g ->
                            // A, B, C … in order of appearance in this session.
                            val order = state.blocks.mapNotNull { it.supersetGroup }.distinct()
                            "Supersatz " + ('A' + order.indexOf(g).coerceAtLeast(0))
                        },
                        canLinkNext = nextBlock != null && (block.supersetGroup == null || block.supersetGroup != nextBlock.supersetGroup),
                        onLinkNext = {
                            val change = PendingChange.Superset(block, true, nextBlock?.exercise?.name)
                            if (hasPlan) pending = change else apply(change, false)
                        },
                        onUnlink = {
                            val change = PendingChange.Superset(block, false, null)
                            if (hasPlan) pending = change else apply(change, false)
                        },
                        onDropAfter = { setId -> viewModel.addDropSet(block.sessionExerciseId, setId) },
                        onText = viewModel::onSetText,
                        onToggle = { exerciseId, setId ->
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.toggleComplete(exerciseId, setId)
                        },
                        onAddSet = viewModel::addSet,
                        onDeleteSet = viewModel::deleteSet,
                        onSetType = viewModel::onSetType,
                        onSwap = { picker = PickerTarget.Swap(block) },
                        onRemove = { pending = PendingChange.Remove(block) },
                        onHistory = { onOpenExercise(block.exercise.id) },
                        onWarmup = { viewModel.addWarmups(block.sessionExerciseId) },
                        onNote = { viewModel.onNote(block.sessionExerciseId, it) },
                    )
                }
                item(key = "add-exercise") {
                    OutlinedButton(
                        onClick = { picker = PickerTarget.Add },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Übung hinzufügen")
                    }
                }
            }
        }

        // ---- Exercise catalog: swap / add, then "nur heute" or "dauerhaft" ----
        picker?.let { target ->
            var suggestions by remember(target) { mutableStateOf<List<Exercise>>(emptyList()) }
            if (target is PickerTarget.Swap) {
                LaunchedEffect(target) { suggestions = viewModel.alternatives(target.block.exercise.id) }
            }
            ExercisePickerSheet(
                title = if (target is PickerTarget.Swap) "${target.block.exercise.name} tauschen" else "Übung hinzufügen",
                catalog = catalog,
                suggestions = suggestions,
                exclude = state.blocks.map { it.exercise.id }.toSet(),
                onDismiss = { picker = null },
                onPick = { exercise ->
                    picker = null
                    val change = when (target) {
                        is PickerTarget.Swap -> PendingChange.Swap(target.block, exercise)
                        PickerTarget.Add -> PendingChange.Add(exercise)
                    }
                    if (hasPlan) pending = change else apply(change, false)
                },
            )
        }
        pending?.let { change ->
            ChangeScopeDialog(
                title = when (change) {
                    is PendingChange.Swap -> "${change.block.exercise.name} → ${change.exercise.name}"
                    is PendingChange.Add -> "${change.exercise.name} hinzufügen"
                    is PendingChange.Remove -> "${change.block.exercise.name} entfernen"
                    is PendingChange.Superset ->
                        if (change.link) "Supersatz: ${change.block.exercise.name} + ${change.partner}" else "Supersatz lösen"
                },
                text = when (change) {
                    is PendingChange.Remove ->
                        if (hasPlan) "Nur aus dem heutigen Training oder auch dauerhaft aus deinem Plan?"
                        else "Die Übung und ihre Sätze werden aus diesem Training entfernt."
                    is PendingChange.Superset ->
                        if (change.link) "Die beiden Übungen laufen abwechselnd, Pause erst nach jeder Runde. Nur heute oder dauerhaft im Plan?"
                        else "Wieder Übung für Übung mit eigener Pause. Nur heute oder dauerhaft im Plan?"
                    else -> "Nur für dieses Training oder dauerhaft in deinem Plan? Bereits abgehakte Sätze bleiben erhalten."
                },
                showPermanent = hasPlan,
                onChoose = { permanent ->
                    pending = null
                    apply(change, permanent)
                },
                onDismiss = { pending = null },
            )
        }

        if (trainingMode) {
            TrainingMode(
                next = next,
                exerciseId = next?.let { state.blocks.getOrNull(it.blockIndex)?.exercise?.id },
                rest = rest,
                remaining = remaining,
                onLog = viewModel::logNext,
                onAddRest = { viewModel.addRestSeconds(30) },
                onSkipRest = viewModel::cancelRest,
                onFinish = {
                    trainingMode = false
                    viewModel.requestEnd()
                },
                onClose = { trainingMode = false },
            )
        }

        if (!trainingMode) rest?.let { restState ->
            RestBar(
                rest = restState,
                onAddTime = { viewModel.addRestSeconds(30) },
                onCancel = { viewModel.cancelRest() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }

    if (plateSheetVisible) {
        PlateCalculatorSheet(
            defaultKg = state.blocks.firstNotNullOfOrNull { block ->
                block.rows.lastOrNull { it.set.weight > 0f }?.set?.weight
            } ?: 60f,
            onDismiss = { plateSheetVisible = false },
        )
    }

    if (endRequested) {
        EndSessionSheet(
            completedSets = state.blocks.sumOf { block -> block.rows.count { it.set.completed } },
            openSets = state.blocks.sumOf { block -> block.rows.count { !it.set.completed } },
            onCancel = viewModel::cancelEnd,
            onConfirm = { notes, effort -> viewModel.endSession(notes, effort) },
        )
    }
}

/** Bottom sheet: notes + perceived effort, then finish the session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndSessionSheet(
    completedSets: Int,
    openSets: Int,
    onCancel: () -> Unit,
    onConfirm: (notes: String, effort: Int?) -> Unit,
) {
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    var notes by remember { mutableStateOf("") }
    var effort by remember { mutableFloatStateOf(7f) }
    var effortEnabled by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Workout beenden", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "$completedSets abgehakte Sätze werden gespeichert." +
                    if (openSets > 0) " $openSets offene Sätze werden nicht übernommen." else "",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notiz (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anstrengung", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                SwitchLikeToggle(checked = effortEnabled, onToggle = { effortEnabled = it })
            }
            if (effortEnabled) {
                TenetSlider(
                    value = effort,
                    onValueChange = { effort = it },
                    valueRange = 1f..10f,
                    steps = 8,
                )
                Text(
                    text = "RPE ${effort.roundToInt()} von 10",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onCancel) { Text("Zurück") }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        onConfirm(
                            notes,
                            if (effortEnabled) effort.roundToInt() else null,
                        )
                    },
                ) { Text("Beenden") }
            }
        }
    }
}

@Composable
private fun SwitchLikeToggle(checked: Boolean, onToggle: (Boolean) -> Unit) {
    TenetSwitch(checked = checked, onCheckedChange = onToggle)
}

/** Bottom sheet: plate math for the target weight (App_Konzept.md 5.2.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlateCalculatorSheet(
    defaultKg: Float,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    var weightText by remember { mutableStateOf(defaultKg.toInt().toString()) }
    var barText by remember { mutableStateOf(PlateCalculator.STANDARD_BAR_KG.toInt().toString()) }

    val targetKg = weightText.replace(',', '.').toFloatOrNull()
    val barKg = barText.replace(',', '.').toFloatOrNull()
    val result = if (targetKg != null && barKg != null && targetKg > 0f && barKg > 0f) {
        PlateCalculator.calculate(targetKg, barKg)
    } else {
        null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Plattenrechner", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Welche Scheiben brauchst du pro Seite?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Ziel (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = barText,
                    onValueChange = { barText = it },
                    label = { Text("Stange (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            if (result != null) {
                TenetCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Pro Seite", style = MaterialTheme.typography.titleSmall)
                        if (result.plates.isEmpty()) {
                            Text(
                                text = "Zielgewicht ist unterhalb der Stange.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                text = result.plates.joinToString(" + ") { formatKg(it) },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Gesamt ${formatKg(result.achievedKg)} kg " +
                                    "(Stange ${formatKg(result.barKg)} + 2 × Scheiben)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!result.exact) {
                                Text(
                                    text = "Nicht exakt ladbar – Rest ${
                                        formatKg(result.remainderPerSide * 2)
                                    } kg. Runde z. B. auf 2,5 kg.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Schließen") }
            }
        }
    }
}

/** "Pause 2:30" (advised, with range) or the plan's own value. */
private fun restHint(block: BlockUi): String {
    val own = block.target?.restSec?.takeIf { it > 0 }
    if (own != null) return "Pause ${formatSeconds(own)}"
    val reps = block.target?.targetReps ?: block.rows.firstOrNull { it.set.reps > 0 }?.set?.reps ?: 8
    val timed = block.exercise.measureType == app.tenet.android.core.database.entity.MeasureType.HOLD ||
        block.exercise.measureType == app.tenet.android.core.database.entity.MeasureType.DURATION
    val advice = app.tenet.android.core.common.RestAdvisor.advise(
        app.tenet.android.core.common.MovementPattern.fromName(block.exercise.pattern),
        block.exercise.primaryMuscles,
        if (timed) 0 else reps,
    )
    return "Pause ${formatSeconds(advice.seconds)} (${advice.range})"
}

private fun formatKg(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString()
    else value.toString().trimEnd('0').trimEnd('.').replace('.', ',')

@Composable
private fun ExerciseCard(
    block: BlockUi,
    supersetLabel: String?,
    canLinkNext: Boolean,
    onLinkNext: () -> Unit,
    onUnlink: () -> Unit,
    onDropAfter: (setId: String?) -> Unit,
    onText: (String, String, ActiveSessionViewModel.Field, String) -> Unit,
    onToggle: (String, String) -> Unit,
    onAddSet: (String) -> Unit,
    onDeleteSet: (String, String) -> Unit,
    onSetType: (String, String, SetType) -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onHistory: () -> Unit,
    onWarmup: () -> Unit,
    onNote: (String) -> Unit,
) {
    var noteOpen by rememberSaveable(block.sessionExerciseId) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Photo → records & history (like Hevy).
                app.tenet.android.feature.sport.ExerciseThumb(block.exercise.id, Modifier.clickable(onClick = onHistory), size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = block.exercise.name, style = MaterialTheme.typography.titleMedium)
                    supersetLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                    // Target and the ideal rest for this exercise (own value from the plan wins).
                    val rest = remember(block.exercise.id, block.target) { restHint(block) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        block.target?.let {
                            Text(
                                text = "${it.targetSets} × ${it.targetReps} · ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = rest,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { contentDescription = "Pause $rest" },
                        )
                    }
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    TooltipIconButton(Icons.Outlined.MoreVert, "Optionen für ${block.exercise.name}", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Übung tauschen") },
                            leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                            onClick = {
                                menu = false
                                onSwap()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Notiz") },
                            leadingIcon = { Icon(Icons.Outlined.StickyNote2, contentDescription = null) },
                            onClick = {
                                menu = false
                                noteOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Verlauf & Rekorde") },
                            leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                            onClick = {
                                menu = false
                                onHistory()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Aufwärmsätze") },
                            leadingIcon = { Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null) },
                            onClick = {
                                menu = false
                                onWarmup()
                            },
                        )
                        if (canLinkNext) {
                            DropdownMenuItem(
                                text = { Text("Supersatz mit nächster Übung") },
                                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onLinkNext()
                                },
                            )
                        }
                        if (supersetLabel != null) {
                            DropdownMenuItem(
                                text = { Text("Supersatz lösen") },
                                leadingIcon = { Icon(Icons.Outlined.LinkOff, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onUnlink()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Dropsatz anhängen") },
                            leadingIcon = { Icon(Icons.Outlined.TrendingDown, contentDescription = null) },
                            onClick = {
                                menu = false
                                onDropAfter(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Entfernen") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                onRemove()
                            },
                        )
                    }
                }
            }
            // Hevy-style notes: last time's note pinned, today's editable.
            block.previousNote?.takeIf { it.isNotBlank() && it != block.notes }?.let { prev ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.PushPin, contentDescription = "Notiz vom letzten Mal", modifier = Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text(prev, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (noteOpen || block.notes.isNotEmpty()) {
                OutlinedTextField(
                    value = block.notes,
                    onValueChange = onNote,
                    placeholder = { Text("Notiz, z. B. Sitzhöhe 4, Griff eng") },
                    leadingIcon = { Icon(Icons.Outlined.StickyNote2, contentDescription = null) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Progressive overload proposal + strength at a glance.
            block.suggestion?.let { s ->
                val container = when (s.decision) {
                    OverloadMath.Decision.INCREASE -> MaterialTheme.colorScheme.primaryContainer
                    OverloadMath.Decision.DELOAD -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (s.decision) {
                                OverloadMath.Decision.INCREASE -> Icons.AutoMirrored.Outlined.TrendingUp
                                OverloadMath.Decision.DELOAD -> Icons.AutoMirrored.Outlined.TrendingDown
                                else -> Icons.AutoMirrored.Outlined.TrendingFlat
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (s.weightKg > 0f) "Heute ${kg(s.weightKg)} kg" else "Heute",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(OverloadMath.label(s), style = MaterialTheme.typography.bodySmall)
                        }
                        block.lastE1rm?.let { last ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text("1RM ≈ ${kg(last)} kg", style = MaterialTheme.typography.labelLarge)
                                block.bestE1rm?.takeIf { it > last }?.let {
                                    Text("Best ${kg(it)} kg", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HeaderCell("Satz", Modifier.width(28.dp))
                HeaderCell("Vorher", Modifier.weight(1.2f))
                HeaderCell("kg", Modifier.weight(1f))
                HeaderCell("Wdh", Modifier.weight(1f))
                HeaderCell("RPE", Modifier.weight(0.8f))
                Spacer(Modifier.width(44.dp))
            }

            HorizontalDivider()

            var workingNo = 0
            block.rows.forEach { row ->
                if (row.set.type != SetType.WARMUP) workingNo++
                val number = workingNo
                key(row.set.id) {
                    val dismiss = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = dismiss,
                        enableDismissFromStartToEnd = false,
                        onDismiss = { onDeleteSet(block.sessionExerciseId, row.set.id) },
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Satz löschen",
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            SetRow(
                                row = row,
                                number = number,
                                sessionExerciseId = block.sessionExerciseId,
                                onText = onText,
                                onToggle = onToggle,
                                onType = onSetType,
                                onDropAfter = { onDropAfter(it) },
                                onDelete = { onDeleteSet(block.sessionExerciseId, row.set.id) },
                            )
                        }
                    }
                }
            }

            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { onAddSet(block.sessionExerciseId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Satz hinzufügen")
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun SetRow(
    row: SetRowUi,
    number: Int,
    sessionExerciseId: String,
    onText: (String, String, ActiveSessionViewModel.Field, String) -> Unit,
    onToggle: (String, String) -> Unit,
    onType: (String, String, SetType) -> Unit,
    onDropAfter: (setId: String) -> Unit = {},
    onDelete: () -> Unit,
) {
    val setId = row.set.id
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Set type badge: tap to switch between warm-up/working/drop/failure.
        var typeMenuOpen by remember { mutableStateOf(false) }
        Box {
            Text(
                text = row.set.type.abbrev.ifEmpty { number.toString() },
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = row.set.type.tint(),
                modifier = Modifier
                    .width(28.dp)
                    .clickable { typeMenuOpen = true }
                    .padding(vertical = 10.dp),
            )
            DropdownMenu(
                expanded = typeMenuOpen,
                onDismissRequest = { typeMenuOpen = false },
            ) {
                SetType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label) },
                        onClick = {
                            typeMenuOpen = false
                            onType(sessionExerciseId, setId, type)
                        },
                        trailingIcon = {
                            if (type == row.set.type) {
                                Icon(Icons.Outlined.Check, contentDescription = "Aktiv")
                            }
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Satz löschen") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        typeMenuOpen = false
                        onDelete()
                    },
                )
                if (row.set.type != SetType.WARMUP) {
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Dropsatz danach") },
                        leadingIcon = { Icon(Icons.Outlined.TrendingDown, contentDescription = null) },
                        onClick = {
                            typeMenuOpen = false
                            onDropAfter(setId)
                        },
                    )
                }
            }
        }
        Text(
            text = row.previous ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.2f),
        )
        CompactNumberField(
            value = row.kgText,
            onValueChange = { onText(sessionExerciseId, setId, ActiveSessionViewModel.Field.KG, it) },
            done = row.set.completed,
            selectAllOnFocus = true,
            placeholder = "?",
            modifier = Modifier.weight(1f),
        )
        CompactNumberField(
            value = row.repsText,
            onValueChange = {
                onText(sessionExerciseId, setId, ActiveSessionViewModel.Field.REPS, it)
            },
            done = row.set.completed,
            selectAllOnFocus = true,
            modifier = Modifier.weight(1f),
        )
        CompactNumberField(
            value = row.rpeText,
            onValueChange = { onText(sessionExerciseId, setId, ActiveSessionViewModel.Field.RPE, it) },
            done = row.set.completed,
            selectAllOnFocus = true,
            modifier = Modifier.weight(0.8f),
        )
        FilledIconToggleButton(
            checked = row.set.completed,
            onCheckedChange = { onToggle(sessionExerciseId, setId) },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(44.dp).semantics {
                stateDescription = if (row.set.completed) "abgehakt" else "offen"
            },
        ) {
            Icon(
                imageVector = if (row.set.completed) Icons.Outlined.Check else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (row.set.completed) "Satz erledigt – Haken entfernen" else "Satz als erledigt markieren",
                modifier = Modifier.size(if (row.set.completed) 30.dp else 22.dp),
            )
        }
    }
}


@Composable
private fun RestBar(
    rest: RestUiState,
    onAddTime: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Pause ${formatSeconds(rest.remainingSec)}",
                style = MaterialTheme.typography.titleSmall,
            )
            LinearWavyProgressIndicator(
                progress = {
                    if (rest.totalSec == 0) 0f
                    else rest.remainingSec.toFloat() / rest.totalSec
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onAddTime) { Text("+30 s") }
            TooltipIconButton(icon = Icons.Outlined.Close, contentDescription = "Pause überspringen", onClick = onCancel)
        }
    }
}

/** Short badge letter shown in the set-number column. */
private val SetType.abbrev: String
    get() = when (this) {
        SetType.WORKING -> ""
        SetType.WARMUP -> "W"
        SetType.DROP -> "D"
        SetType.FAILURE -> "F"
    }

private val SetType.label: String
    get() = when (this) {
        SetType.WORKING -> "Arbeitssatz"
        SetType.WARMUP -> "Aufwärmen"
        SetType.DROP -> "Drop-Set"
        SetType.FAILURE -> "Failure"
    }

@Composable
private fun SetType.tint(): androidx.compose.ui.graphics.Color = when (this) {
    SetType.WORKING -> MaterialTheme.colorScheme.primary
    SetType.WARMUP -> MaterialTheme.colorScheme.secondary
    SetType.DROP -> MaterialTheme.colorScheme.tertiary
    SetType.FAILURE -> MaterialTheme.colorScheme.error
}

private fun elapsed(startedAt: Long?, now: Long): String {
    if (startedAt == null) return "00:00:00"
    val total = max(0L, (now - startedAt) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun kg(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.GERMAN, "%.1f", v)

/** What the exercise picker was opened for. */
private sealed interface PickerTarget {
    data class Swap(val block: BlockUi) : PickerTarget
    data object Add : PickerTarget
}

/** A picked change waiting for "nur heute" / "dauerhaft". */
private sealed interface PendingChange {
    data class Swap(val block: BlockUi, val exercise: Exercise) : PendingChange
    data class Add(val exercise: Exercise) : PendingChange
    data class Remove(val block: BlockUi) : PendingChange
    data class Superset(val block: BlockUi, val link: Boolean, val partner: String?) : PendingChange
}
