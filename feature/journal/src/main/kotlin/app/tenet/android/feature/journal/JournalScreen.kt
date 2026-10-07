package app.tenet.android.feature.journal

import app.tenet.android.core.designsystem.header.pageWash
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Image
import app.tenet.android.core.designsystem.component.FabMenuAction
import app.tenet.android.core.designsystem.component.TenetFabMenu
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import app.tenet.android.core.designsystem.header.PageTabs
import app.tenet.android.core.designsystem.header.PageTab
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import app.tenet.android.core.designsystem.theme.DreamAmoledScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.core.designsystem.header.HeaderImage
import app.tenet.android.core.designsystem.header.PageHeader
import app.tenet.android.core.designsystem.header.rememberHeaderScrollState
import app.tenet.android.core.designsystem.navigation.ReselectEffect
import kotlinx.coroutines.launch

/**
 * Journal tab (App_Konzept.md 5.3): notes, diary and dreams as swipeable
 * pages sharing one data model.
 */
@Composable
fun JournalScreen(
    onOpenEditor: (type: EntryType, entryId: String?) -> Unit,
    onNewEntry: (type: EntryType, start: String) -> Unit = { type, _ -> onOpenEditor(type, null) },
    onSearch: () -> Unit = {},
    onNewDiaryOn: (date: String) -> Unit = {},
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Last nights from Health Connect for the dream page (each time the journal shows).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshSleep()
        onPauseOrDispose {}
    }
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val headerState = rememberHeaderScrollState()
    ReselectEffect { headerState.animateExpand() }
    val fabExpanded by remember { derivedStateOf { headerState.progress < 0.5f } }
    val snackbar = LocalAppSnackbar.current
    val haptics = LocalHapticFeedback.current
    val segments = listOf("Notizen", "Tagebuch", "Träume")
    val currentType = segmentToEntryType(pagerState.currentPage)

    val actions = EntryActions(
        open = { onOpenEditor(it.type, it.id) },
        create = { type, start -> onNewEntry(type, start) },
        delete = { entry ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.hide(entry.id)
            snackbar?.showUndo(
                "„${entry.title.ifBlank { "Eintrag" }}“ gelöscht",
                onUndo = { viewModel.restore(entry.id) },
                onCommit = { viewModel.commitDelete(entry.id) },
            )
        },
        deleteMany = { entries ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            val ids = entries.map { it.id }
            ids.forEach(viewModel::hide)
            snackbar?.showUndo(
                if (ids.size == 1) "„${entries.first().title.ifBlank { "Eintrag" }}“ gelöscht" else "${ids.size} Einträge gelöscht",
                onUndo = { ids.forEach(viewModel::restore) },
                onCommit = { ids.forEach(viewModel::commitDelete) },
            )
        },
        togglePin = { viewModel.setPinned(it.id, !it.pinned) },
        toggleCheck = { entry, line ->
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            viewModel.toggleCheck(entry, line)
        },
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        floatingActionButton = {
            // Speed dial like Google Keep; options depend on the page.
            val new = { start: String -> onNewEntry(currentType, start) }
            TenetFabMenu(
                actions = when (currentType) {
                    EntryType.NOTE -> listOf(
                        FabMenuAction("Bild", Icons.Outlined.Image) { new(START_IMAGE) },
                        FabMenuAction("Audio", Icons.Outlined.Mic) { new(START_AUDIO) },
                        FabMenuAction("Liste", Icons.Outlined.CheckBox) { new(START_LIST) },
                        FabMenuAction("Text", Icons.Outlined.TextFields) { new("") },
                    )
                    EntryType.DIARY -> listOf(
                        FabMenuAction("Bild", Icons.Outlined.Image) { new(START_IMAGE) },
                        FabMenuAction("Erzählen", Icons.Outlined.Mic) { new(START_DICTATE) },
                        FabMenuAction("Schreiben", Icons.Outlined.EditNote) { new("") },
                    )
                    EntryType.DREAM -> listOf(
                        FabMenuAction("Erzählen", Icons.Outlined.Mic) { new(START_DICTATE) },
                        FabMenuAction("Schreiben", Icons.Outlined.EditNote) { new("") },
                    )
                },
                contentDescription = "Eintrag erstellen",
                modifier = Modifier.padding(bottom = TenetDimens.bottomTabBarPadding),
            )
        },
    ) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .pageWash(HeaderImage.JOURNAL, { headerState.progress })
                .nestedScroll(headerState.nestedScrollConnection),
        ) {
            PageHeader(
                header = HeaderImage.JOURNAL,
                title = "Journal",
                progress = { headerState.progress },
                onSearch = onSearch,
            )
            PageTabs(
                tabs = segments.map { PageTab(it) },
                selectedIndex = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (segmentToEntryType(page)) {
                    EntryType.NOTE -> NotesPage(uiState, actions)
                    EntryType.DIARY -> DiaryPage(uiState, actions, onNewDiaryOn)
                    EntryType.DREAM -> DreamAmoledScope(Modifier.fillMaxSize(), paintBackground = true) {
                        DreamsPage(uiState, actions)
                    }
                }
            }
        }
    }
}

/** Callbacks every entry card offers. */
internal class EntryActions(
    val open: (Entry) -> Unit,
    /** New entry of a type, with a start action (START_DICTATE …) or "". */
    val create: (EntryType, String) -> Unit = { _, _ -> },
    val delete: (Entry) -> Unit,
    /** Several at once (multi-select), one undo for all. */
    val deleteMany: (List<Entry>) -> Unit = { it.forEach(delete) },
    val togglePin: (Entry) -> Unit,
    val toggleCheck: (Entry, Int) -> Unit,
)

/** M3 swipe-to-dismiss (end → start) that asks for deletion instead of removing directly. */
@Composable
internal fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val swipeHaptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            swipeHaptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            scope.launch { state.reset() }
            onDelete()
        },
        backgroundContent = {
            // Only while swiping, with the card's own corners: at rest nothing
            // may peek out behind the rounded card.
            if (state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(app.tenet.android.core.designsystem.theme.tenetCardShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { content() }
}
