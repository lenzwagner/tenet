package app.tenet.android.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.SectionHeader
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.component.TooltipIconButton

/**
 * Global search over notes, diary, dreams (incl. tags and dream symbols),
 * exercises and logged meals, grouped by type (App_Konzept.md 6).
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenEntry: (Entry) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenRecipe: (String) -> Unit = {},
    /** Journal lock active: journal hits stay hidden. */
    hideEntries: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    // New M3 search: TextFieldState (no lost keystrokes) + full-screen results.
    val text = rememberTextFieldState()
    val searchState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val scope = rememberCoroutineScope()
    val query = text.text.toString()
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.collect(viewModel::onQuery)
    }
    // Collapsing the full-screen search (back gesture / arrow) leaves the screen.
    LaunchedEffect(searchState) {
        var wasExpanded = false
        snapshotFlow { searchState.currentValue }.collect { value ->
            if (value == SearchBarValue.Expanded) wasExpanded = true
            else if (wasExpanded) onBack()
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = searchState,
            onSearch = {},
            placeholder = { Text("Alles durchsuchen …") },
            leadingIcon = {
                TooltipIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Zurück",
                    onClick = { scope.launch { searchState.animateToCollapsed() } },
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    TooltipIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Suche leeren",
                        onClick = { text.clearText() },
                    )
                }
            } else {
                null
            },
        )
    }

    // Collapsed bar underneath (visible only during the collapse animation).
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        SearchBar(state = searchState, inputField = inputField, modifier = Modifier.padding(horizontal = 16.dp))
    }

    ExpandedFullScreenSearchBar(state = searchState, inputField = inputField) {
        when {
            query.trim().length < 2 -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "Suche in allen Modulen",
                body = "Notizen, Tagebuch, Träume, Tags, Übungen, Rezepte und Mahlzeiten.",
            )

            results.isEmpty -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "Nichts gefunden",
                body = "Für „${query.trim()}“ gibt es keine Treffer.",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
            ) {
                EntryType.entries.forEach { type ->
                    val hits = if (hideEntries) emptyList() else results.entries.filter { it.type == type }
                    group(
                        key = type.name,
                        title = type.label,
                        items = hits,
                        icon = { type.icon },
                        headline = { it.title.ifBlank { it.body.lineSequence().firstOrNull().orEmpty() } },
                        supporting = { germanDate(it.entryDate) + (it.body.takeIf { b -> b.isNotBlank() }?.let { b -> " · " + b.replace('\n', ' ') } ?: "") },
                        onClick = onOpenEntry,
                    )
                }
                group(
                    key = "exercises",
                    title = "Übungen",
                    items = results.exercises,
                    icon = { Icons.Outlined.FitnessCenter },
                    headline = { it.name },
                    supporting = { it.primaryMuscles.ifBlank { it.equipment } },
                    onClick = { onOpenExercises() },
                )
                group(
                    key = "recipes",
                    title = "Rezepte",
                    items = results.recipes,
                    icon = { Icons.AutoMirrored.Outlined.MenuBook },
                    headline = { it.title },
                    supporting = { listOfNotNull(it.minutes?.let { m -> "$m Min" }, it.tags.replace(",", ", ").ifBlank { null }).joinToString(" · ") },
                    onClick = { onOpenRecipe(it.id) },
                )
                group(
                    key = "foods",
                    title = "Mahlzeiten",
                    items = results.foods,
                    icon = { Icons.Outlined.Restaurant },
                    headline = { it.label },
                    supporting = { "${it.kcal.toInt()} kcal · zuletzt ${it.date}" },
                    onClick = { onOpenNutrition() },
                )
            }
        }
    }
}

private fun <T> LazyListScope.group(
    key: String,
    title: String,
    items: List<T>,
    icon: (T) -> ImageVector,
    headline: (T) -> String,
    supporting: (T) -> String,
    onClick: (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "h-$key") { SectionHeader("$title · ${items.size}", Modifier.padding(top = 8.dp)) }
    itemsIndexed(items, key = { i, _ -> "$key-$i" }) { index, item ->
        SegmentedListItem(
            colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
            onClick = { onClick(item) },
            shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, items.size),
            leadingContent = { ShapeIcon(icon(item)) },
            supportingContent = {
                Text(supporting(item), maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
        ) {
            Text(headline(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val EntryType.label: String
    get() = when (this) {
        EntryType.NOTE -> "Notizen"
        EntryType.DIARY -> "Tagebuch"
        EntryType.DREAM -> "Träume"
    }

private val EntryType.icon: ImageVector
    get() = when (this) {
        EntryType.NOTE -> Icons.AutoMirrored.Outlined.Notes
        EntryType.DIARY -> Icons.Outlined.AutoStories
        EntryType.DREAM -> Icons.Outlined.NightsStay
    }

private val GermanDate = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", java.util.Locale.GERMAN)

private fun germanDate(iso: String): String =
    runCatching { java.time.LocalDate.parse(iso).format(GermanDate) }.getOrDefault(iso)
