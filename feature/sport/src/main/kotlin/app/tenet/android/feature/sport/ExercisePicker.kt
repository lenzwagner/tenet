package app.tenet.android.feature.sport

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.MovementPattern
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.designsystem.component.rememberSheetState

/**
 * Exercise catalog as a bottom sheet: search, muscle and equipment filters,
 * suggested alternatives on top (when swapping), then all exercises grouped
 * by movement pattern. [exclude] are hidden (already in the workout).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerSheet(
    title: String,
    catalog: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    suggestions: List<Exercise> = emptyList(),
    exclude: Set<String> = emptySet(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = catalog.filter { it.id !in exclude }
    val muscles = remember(visible) {
        visible.flatMap { it.primaryMuscles.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val equipments = remember(visible) { visible.map { it.equipment }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtering = query.isNotBlank() || muscle != null || equipment != null
    val filtered = visible.filter { ex ->
        (query.isBlank() || ex.name.contains(query, true) || ex.primaryMuscles.contains(query, true) || ex.equipment.contains(query, true)) &&
            (muscle == null || ex.primaryMuscles.split(',').any { it.trim() == muscle }) &&
            (equipment == null || ex.equipment == equipment)
    }
    val groups = filtered
        .groupBy { MovementPattern.fromName(it.pattern) }
        .toList()
        .sortedBy { (p, _) -> p?.ordinal ?: Int.MAX_VALUE }

    // Opens at half height like an iOS sheet; pull up for the whole list.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState(skipPartiallyExpanded = false)) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            app.tenet.android.core.designsystem.component.TenetTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Übung, Muskel oder Gerät suchen") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(muscles) { m ->
                    app.tenet.android.core.designsystem.component.TenetFilterChip(selected = muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(m) })
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(equipments) { e ->
                    app.tenet.android.core.designsystem.component.TenetFilterChip(selected = equipment == e, onClick = { equipment = if (equipment == e) null else e }, label = { Text(e) })
                }
            }
            val listState = rememberLazyListState()
            // Suggestions arrive a moment later: keep them in view at the top.
            LaunchedEffect(suggestions) { if (suggestions.isNotEmpty()) listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f),
            ) {
                val tips = suggestions.filter { it.id !in exclude }
                if (!filtering && tips.isNotEmpty()) {
                    item(key = "tips-h") { GroupHeader("Passende Alternativen") }
                    exerciseGroup("tips", tips, onPick)
                }
                groups.forEach { (pattern, list) ->
                    item(key = "h-${pattern?.name}") { GroupHeader(pattern?.label ?: "Weitere") }
                    exerciseGroup(pattern?.name ?: "other", list, onPick)
                }
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Nichts gefunden. Eigene Übungen legst du in der Bibliothek an.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.exerciseGroup(
    key: String,
    list: List<Exercise>,
    onPick: (Exercise) -> Unit,
) {
    list.forEachIndexed { index, ex ->
        item(key = "$key-${ex.id}") {
            SegmentedListItem(
                onClick = { onPick(ex) },
                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, list.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                leadingContent = { ExerciseThumb(ex.id) },
                supportingContent = {
                    Text(
                        listOf(ex.primaryMuscles.replace(",", ", "), ex.equipment).filter { it.isNotBlank() }.joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.padding(bottom = app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
            ) { Text(ex.name) }
        }
    }
}

/**
 * Asks whether a change in the running workout applies only today or is
 * written to the plan as well.
 */
@Composable
internal fun ChangeScopeDialog(
    title: String,
    text: String,
    onChoose: (permanent: Boolean) -> Unit,
    onDismiss: () -> Unit,
    showPermanent: Boolean = true,
) {
    if (!showPermanent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { Button(onClick = { onChoose(false) }, shapes = ButtonDefaults.shapes()) { Text("OK") } },
            dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = { onChoose(true) }, shapes = ButtonDefaults.shapes()) { Text("Dauerhaft im Plan") }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(false) }, shapes = ButtonDefaults.shapes()) { Text("Nur heute") }
        },
    )
}
