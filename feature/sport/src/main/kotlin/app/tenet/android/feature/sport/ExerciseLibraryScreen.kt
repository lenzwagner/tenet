package app.tenet.android.feature.sport

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import app.tenet.android.core.common.MovementPattern
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import kotlinx.coroutines.Dispatchers
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material.icons.outlined.Close
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.TenetLoading
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ButtonDefaults
import app.tenet.android.core.designsystem.component.TooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.MeasureType

private val MeasureType.label: String
    get() = when (this) {
        MeasureType.REPS -> "Wiederholungen"
        MeasureType.HOLD -> "Haltezeit"
        MeasureType.NEGATIVE -> "Negativ"
        MeasureType.DURATION -> "Dauer"
    }

/**
 * Exercise library (App_Konzept.md 5.2.1): searchable list with muscle and
 * equipment filters, custom exercise creation and editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    // ^ immediate: text fields must see their own edits in the same frame,
    // otherwise fast typing can drop characters.
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var createNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    // M3 search: tapping the bar opens full-screen results.
    val text = rememberTextFieldState(state.query)
    val searchState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.drop(1).collect(viewModel::onQuery)
    }
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = searchState,
            onSearch = {},
            placeholder = { Text("Übung suchen …") },
            leadingIcon = {
                if (searchState.currentValue == SearchBarValue.Expanded) {
                    TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Suche schließen", {
                        scope.launch { searchState.animateToCollapsed() }
                    })
                } else {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            },
            trailingIcon = if (text.text.isNotEmpty()) {
                { TooltipIconButton(Icons.Outlined.Close, "Suche leeren", { text.clearText() }) }
            } else {
                null
            },
        )
    }
    ExpandedFullScreenSearchBar(state = searchState, inputField = inputField) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.filtered, key = { _, it -> it.id }) { index, exercise ->
                ExerciseRow(
                    exercise = exercise,
                    shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, state.filtered.size),
                    onEdit = {
                        scope.launch { searchState.animateToCollapsed() }
                        editing = exercise
                    },
                    onDelete = { pendingDelete = exercise },
                    onOpen = { onOpenExercise(exercise.id) },
                )
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { app.tenet.android.core.designsystem.component.TenetSnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TooltipIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", onClick = onBack)
                },
                title = { Text("Übungsbibliothek") },
                actions = {
                    TooltipIconButton(icon = Icons.Outlined.Add, contentDescription = "Neue Übung", onClick = { createNew = true })
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchBar(
                state = searchState,
                inputField = inputField,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.muscles.isNotEmpty() || state.equipment.isNotEmpty()) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.muscleFilter != null || state.equipmentFilter != null) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = viewModel::clearFilters,
                                label = { Text("Alle löschen") },
                            )
                        }
                    }
                    items(state.muscles) { muscle ->
                        FilterChip(
                            selected = state.muscleFilter == muscle,
                            onClick = { viewModel.toggleMuscle(muscle) },
                            label = { Text(muscle) },
                        )
                    }
                    items(state.equipment) { item ->
                        FilterChip(
                            selected = state.equipmentFilter == item,
                            onClick = { viewModel.toggleEquipment(item) },
                            label = { Text(item) },
                        )
                    }
                }
            }

            val filtered = state.filtered
            if (filtered.isEmpty()) {
                if (state.exercises.isEmpty()) {
                    TenetLoading()
                } else {
                    EmptyState(
                        icon = Icons.Outlined.FitnessCenter,
                        title = "Keine Übung gefunden.",
                        body = "Filter oder Suchbegriff anpassen.",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(filtered, key = { _, it -> it.id }) { index, exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, filtered.size),
                            onEdit = { editing = exercise },
                            onDelete = { pendingDelete = exercise },
                            onOpen = { onOpenExercise(exercise.id) },
                        )
                    }
                }
            }
        }
    }

    if (createNew) {
        ExerciseEditorDialog(
            exercise = null,
            onDismiss = { createNew = false },
            onSave = { name, primary, secondary, equipment, measureType, notes ->
                viewModel.save(null, name, primary, secondary, equipment, measureType, notes)
                createNew = false
            },
        )
    }

    editing?.let { exercise ->
        ExerciseEditorDialog(
            exercise = exercise,
            onDismiss = { editing = null },
            onSave = { name, primary, secondary, equipment, measureType, notes ->
                viewModel.save(
                    exercise.id, name, primary, secondary, equipment, measureType, notes,
                )
                editing = null
            },
            onDelete = if (exercise.custom) {
                {
                    viewModel.delete(exercise)
                    editing = null
                }
            } else {
                null
            },
        )
    }

    pendingDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Übung löschen?") },
            text = { Text("„${exercise.name}“ wird aus der Bibliothek entfernt.") },
            confirmButton = {
                Button(shapes = ButtonDefaults.shapes(), onClick = {
                    viewModel.delete(exercise)
                    pendingDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { pendingDelete = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    shapes: ListItemShapes,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    // M3 segmented list row: tap opens records & history, pencil edits.
    SegmentedListItem(
        onClick = onOpen,
        shapes = shapes,
        colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
        overlineContent = MovementPattern.fromName(exercise.pattern)?.let { p -> { Text(p.label) } },
        leadingContent = { ExerciseThumb(exercise.id, size = 56.dp) },
        supportingContent = {
            Text(
                listOf(
                    exercise.primaryMuscles.replace(",", ", ").ifBlank { "–" },
                    exercise.equipment,
                    exercise.measureType.takeIf { it != MeasureType.REPS }?.label.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row {
                TooltipIconButton(icon = Icons.Outlined.Edit, contentDescription = "${exercise.name} bearbeiten", onClick = onEdit)
                if (exercise.custom) {
                    TooltipIconButton(icon = Icons.Outlined.Delete, contentDescription = "${exercise.name} löschen", onClick = onDelete)
                }
            }
        },
    ) {
        Text(
            if (exercise.custom) "${exercise.name} · Eigen" else exercise.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseEditorDialog(
    exercise: Exercise?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        primary: String,
        secondary: String,
        equipment: String,
        measureType: MeasureType,
        notes: String,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(exercise) { mutableStateOf(exercise?.name ?: "") }
    var primary by remember(exercise) { mutableStateOf(exercise?.primaryMuscles ?: "") }
    var secondary by remember(exercise) { mutableStateOf(exercise?.secondaryMuscles ?: "") }
    var equipment by remember(exercise) { mutableStateOf(exercise?.equipment ?: "") }
    var notes by remember(exercise) { mutableStateOf(exercise?.notes ?: "") }
    var measureType by remember(exercise) {
        mutableStateOf(exercise?.measureType ?: MeasureType.REPS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exercise == null) "Neue Übung" else "Übung bearbeiten") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    label = { Text("Primär (z. B. Brust, Rücken)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = secondary,
                    onValueChange = { secondary = it },
                    label = { Text("Sekundär (kommagetrennt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    label = { Text("Equipment (z. B. Langhantel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Messung", style = MaterialTheme.typography.labelLarge)
                SegmentedSelector(
                    segments = MeasureType.entries.map { Segment(it.label) },
                    selectedIndex = MeasureType.entries.indexOf(measureType),
                    onSelect = { measureType = MeasureType.entries[it] },
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                onDelete?.let { delete ->
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = delete) { Text("Löschen") }
                }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Abbrechen") }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(name, primary, secondary, equipment, measureType, notes)
                        }
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Speichern") }
            }
        },
    )
}
