package app.tenet.android.feature.sport

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.rememberSheetState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ButtonDefaults
import app.tenet.android.core.designsystem.component.TooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.database.entity.Exercise

/**
 * Routine editor (App_Konzept.md 5.2.1 "Pläne/Routinen"): edit target
 * sets/reps/rest per exercise, reorder via up/down, add from the library,
 * remove. Changes persist immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var ruleFor by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TooltipIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", onClick = onBack)
                },
                title = { Text(state.workoutTitle) },
                actions = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::openAddSheet) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Übung")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Split: switch between its workouts (Push / Pull / Beine …).
            if (state.workouts.size > 1) {
                item(key = "workouts") {
                    SegmentedSelector(
                        segments = state.workouts.map { Segment(it.title) },
                        selectedIndex = state.workouts.indexOfFirst { it.id == state.workoutId }.coerceAtLeast(0),
                        onSelect = { viewModel.selectWorkout(state.workouts[it].id) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            if (state.routine.isEmpty()) {
                item {
                    Text(
                        text = "Der Plan ist leer. Füge mit „+ Übung“ Übungen aus der " +
                            "Bibliothek hinzu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(state.routine, key = { it.exerciseId }) { target ->
                RoutineRow(
                    target = target,
                    exercise = state.exerciseById[target.exerciseId],
                    isFirst = state.routine.first().exerciseId == target.exerciseId,
                    isLast = state.routine.last().exerciseId == target.exerciseId,
                    onField = { field, value ->
                        viewModel.onTargetChanged(target.exerciseId, field, value)
                    },
                    onMoveUp = { viewModel.move(target.exerciseId, up = true) },
                    onMoveDown = { viewModel.move(target.exerciseId, up = false) },
                    onRemove = { viewModel.remove(target.exerciseId) },
                    onSwap = { viewModel.openSwap(target.exerciseId) },
                    onSuperset = { group ->
                        viewModel.onSupersetToggle(target.exerciseId, group)
                    },
                    onRule = { ruleFor = target.exerciseId },
                )
            }
            item {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::openAddSheet, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Übung hinzufügen")
                }
            }
        }
    }

    ruleFor?.let { id ->
        val target = state.routine.firstOrNull { it.exerciseId == id }
        if (target == null) {
            ruleFor = null
        } else {
            OverloadRuleDialog(
                name = state.exerciseById[id]?.name ?: "Übung",
                target = target,
                onDismiss = { ruleFor = null },
                onSave = { repMax, step, deload ->
                    viewModel.setRule(id, repMax, step, deload)
                    ruleFor = null
                },
            )
        }
    }

    if (state.addSheetVisible) {
        ExercisePickerSheet(
            title = "Übung hinzufügen",
            catalog = state.exercises,
            exclude = state.routine.map { it.exerciseId }.toSet(),
            onPick = { ex ->
                viewModel.add(ex.id)
                viewModel.closeAddSheet()
            },
            onDismiss = viewModel::closeAddSheet,
        )
    }
    state.swapping?.let { exerciseId ->
        ExercisePickerSheet(
            title = "${state.exerciseById[exerciseId]?.name ?: "Übung"} tauschen",
            catalog = state.exercises,
            suggestions = state.suggestions,
            exclude = state.routine.map { it.exerciseId }.toSet(),
            onPick = { ex -> viewModel.swap(ex.id) },
            onDismiss = viewModel::closeSwap,
        )
    }
}

@Composable
private fun RoutineRow(
    target: app.tenet.android.core.database.entity.RoutineExercise,
    exercise: Exercise?,
    isFirst: Boolean,
    isLast: Boolean,
    onField: (RoutineEditorViewModel.TargetField, Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSwap: () -> Unit,
    onSuperset: (Int?) -> Unit,
    onRule: () -> Unit,
) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise?.name ?: target.exerciseId,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TooltipIconButton(icon = Icons.Outlined.ArrowUpward, contentDescription = "Nach oben", onClick = onMoveUp, enabled = !isFirst)
                TooltipIconButton(icon = Icons.Outlined.ArrowDownward, contentDescription = "Nach unten", onClick = onMoveDown, enabled = !isLast)
                TooltipIconButton(icon = Icons.Outlined.SwapHoriz, contentDescription = "Übung tauschen", onClick = onSwap)
                TooltipIconButton(icon = Icons.Outlined.Close, contentDescription = "Aus Plan entfernen", onClick = onRemove)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetField(
                    label = "Sätze",
                    value = target.targetSets,
                    modifier = Modifier.weight(1f),
                    onValue = { onField(RoutineEditorViewModel.TargetField.SETS, it) },
                )
                TargetField(
                    label = "Wdh",
                    value = target.targetReps,
                    modifier = Modifier.weight(1f),
                    onValue = { onField(RoutineEditorViewModel.TargetField.REPS, it) },
                )
                TargetField(
                    label = "Pause (s)",
                    value = target.restSec,
                    modifier = Modifier.weight(1.2f),
                    placeholder = "auto",
                    onValue = { onField(RoutineEditorViewModel.TargetField.REST, it) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Supersatz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChip(
                    selected = target.supersetGroup == null,
                    onClick = { onSuperset(null) },
                    label = { Text("Keiner") },
                )
                FilterChip(
                    selected = target.supersetGroup == 1,
                    onClick = { onSuperset(1) },
                    label = { Text("A") },
                )
                FilterChip(
                    selected = target.supersetGroup == 2,
                    onClick = { onSuperset(2) },
                    label = { Text("B") },
                )
            }
            AssistChip(
                onClick = onRule,
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                label = { Text("Progression: ${ruleText(target)}") },
            )
        }
    }
}

private fun ruleText(t: app.tenet.android.core.database.entity.RoutineExercise): String {
    val parts = listOfNotNull(
        t.repMax?.let { "${t.targetReps}–$it Wdh" },
        t.stepKg?.let { "+${fmtKg(it)} kg" },
        t.deloadPercent?.let { "Deload −$it %" },
    )
    return if (parts.isEmpty()) "Standard" else parts.joinToString(" · ")
}

private fun fmtKg(v: Float) = if (v % 1f == 0f) v.toInt().toString() else v.toString().replace('.', ',')

/** Double progression per exercise: rep range, weight step, deload. */
@Composable
private fun OverloadRuleDialog(
    name: String,
    target: app.tenet.android.core.database.entity.RoutineExercise,
    onDismiss: () -> Unit,
    onSave: (repMax: Int?, stepKg: Float?, deloadPercent: Int?) -> Unit,
) {
    var repMax by remember { mutableStateOf(target.repMax?.toString().orEmpty()) }
    var step by remember { mutableStateOf(target.stepKg?.let(::fmtKg).orEmpty()) }
    var deload by remember { mutableStateOf(target.deloadPercent?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null) },
        title = { Text("Progression · $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Leer = Standard. Mit Obergrenze steigt das Gewicht erst, wenn alle Sätze sie erreichen " +
                        "(z. B. ${target.targetReps}–${target.targetReps + 4} Wdh).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = repMax,
                    onValueChange = { repMax = it.filter(Char::isDigit).take(2) },
                    label = { Text("Wdh bis (ab ${target.targetReps})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(4) },
                    label = { Text("Steigerung in kg") },
                    placeholder = { Text("Standard: 2,5 / Beine 5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = deload,
                    onValueChange = { deload = it.filter(Char::isDigit).take(2) },
                    label = { Text("Deload in %") },
                    placeholder = { Text("Standard: 10") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(repMax.toIntOrNull(), step.replace(',', '.').toFloatOrNull(), deload.toIntOrNull()) },
                shapes = ButtonDefaults.shapes(),
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = { onSave(null, null, null) }, shapes = ButtonDefaults.shapes()) { Text("Standard") }
        },
    )
}

@Composable
private fun TargetField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    /** Shown for 0 (e.g. rest "auto" = advised per exercise). */
    placeholder: String? = null,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(if (value == 0 && placeholder != null) "" else value.toString()) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                (input.filter { it.isDigit() }.toIntOrNull() ?: if (placeholder != null && input.isBlank()) 0 else null)?.let(onValue)
            },
            singleLine = true,
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
