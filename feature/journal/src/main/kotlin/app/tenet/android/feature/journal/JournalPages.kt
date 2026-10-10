package app.tenet.android.feature.journal

import androidx.compose.material3.SegmentedListItem
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.outlined.Bedtime
import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.designsystem.theme.JournalReading
import app.tenet.android.core.designsystem.theme.LocalJournalSerif
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import app.tenet.android.core.designsystem.theme.harmonized
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.rememberGrowIn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tenet.android.core.database.entity.Attachment
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.core.designsystem.navigation.ReselectEffect
import app.tenet.android.core.designsystem.navigation.rememberReselectListState
import app.tenet.android.feature.journal.markdown.checklistProgress
import app.tenet.android.feature.journal.markdown.MarkdownView
import app.tenet.android.feature.journal.markdown.markdownPlain
import coil3.compose.AsyncImage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = TenetDimens.bottomFabPadding)

// ============================================================================
// Notes
// ============================================================================

@Composable
internal fun NotesPage(state: JournalUiState, actions: EntryActions) {
    // Kachel, Liste or – once there are folders – Ordner (folders as a list, tap one to open it).
    var mode by rememberSaveable { mutableStateOf(NotesMode.GRID) }
    if (mode == NotesMode.FOLDERS && state.folders.isEmpty()) mode = NotesMode.GRID
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = mode == NotesMode.FOLDERS && openFolder != null) { openFolder = null }
    val grid = mode == NotesMode.GRID
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var folderFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val inFolders = mode == NotesMode.FOLDERS
    val notes = state.notes.filter {
        if (inFolders) return@filter it.folder.orEmpty() == openFolder.orEmpty() && openFolder != null
        (tagFilter == null || tagFilter in state.tagsByEntry[it.id].orEmpty()) &&
            (folderFilter == null || it.folder == folderFilter)
    }

    // Multi-select: long-press a note, tap more, delete them together.
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selecting = selectedIds.isNotEmpty()
    val toggle = { id: String -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            app.tenet.android.core.designsystem.component.SelectionBar(
                count = selectedIds.size,
                total = notes.size,
                onClose = { selectedIds = emptyList() },
                onSelectAll = { selectedIds = notes.map { it.id } },
                onDelete = {
                    actions.deleteMany(notes.filter { it.id in selectedIds })
                    selectedIds = emptyList()
                },
                modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 8.dp),
            )
        } else Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inFolders) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (openFolder != null) {
                    IconButton(onClick = { openFolder = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Alle Ordner")
                    }
                }
                Text(
                    openFolder?.ifEmpty { "Ohne Ordner" } ?: "Ordner",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    app.tenet.android.core.designsystem.component.TenetFilterChip(
                        selected = tagFilter == null && folderFilter == null,
                        onClick = {
                            tagFilter = null
                            folderFilter = null
                        },
                        label = { Text("Alle") },
                    )
                }
                items(state.folders, key = { "folder-$it" }) { folder ->
                    app.tenet.android.core.designsystem.component.TenetFilterChip(
                        selected = folderFilter == folder,
                        onClick = { folderFilter = if (folderFilter == folder) null else folder },
                        label = { Text(folder) },
                        leadingIcon = {
                            Icon(
                                if (folderFilter == folder) Icons.Filled.Folder else Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }
                items(state.tagCounts, key = { it.id }) { tag ->
                    app.tenet.android.core.designsystem.component.TenetFilterChip(
                        selected = tagFilter == tag.name,
                        onClick = { tagFilter = if (tagFilter == tag.name) null else tag.name },
                        label = { Text("#${tag.name}") },
                    )
                }
            }
            NotesMode.entries.filter { it != NotesMode.FOLDERS || state.folders.isNotEmpty() }.forEach { m ->
                IconToggleButton(
                    checked = mode == m,
                    onCheckedChange = {
                        mode = m
                        openFolder = null
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) { Icon(m.icon, contentDescription = m.label) }
            }
        }

        when {
            inFolders && openFolder == null -> FolderList(
                folders = state.folders,
                counts = state.notes.groupingBy { it.folder.orEmpty() }.eachCount(),
                onOpen = { openFolder = it },
            )

            notes.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.Notes,
                title = when {
                    folderFilter != null -> "Ordner „$folderFilter“ ist leer"
                    tagFilter != null -> "Keine Notizen mit #$tagFilter"
                    else -> "Noch keine Notizen"
                },
                body = "Markdown, Checklisten, Bilder und [[Verlinkungen]] werden unterstützt.",
                actionLabel = if (folderFilter == null && tagFilter == null) "Erste Notiz schreiben" else null,
                onAction = { actions.create(EntryType.NOTE, "") },
                modifier = Modifier.fillMaxSize().wrapContentHeight(),
            )

            grid -> {
                // Like Google Keep: two columns, each tile as tall as its content (masonry).
                val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
                ReselectEffect { gridState.animateScrollToItem(0) }
                androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                    columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    contentPadding = ListPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                ) {
                    items(notes.size, key = { notes[it].id }) { index ->
                        val note = notes[index]
                        // Tile wallpaper: a chosen background photo, else the note's first own photo.
                        val image = state.attachments[note.id]?.let { list ->
                            NoteBackgrounds.wallpaperUri(list.map { it.uri to it.mimeType }, note.color)
                                ?.let { uri -> list.first { it.uri == uri } }
                        }
                        EntryCard(
                            entry = note,
                            actions = actions,
                            tags = state.tagsByEntry[note.id].orEmpty(),
                            image = image,
                            hasVoice = state.attachments[note.id].orEmpty().any { it.mimeType.startsWith("audio") },
                            // Like Keep: at most 8 entries (or 8 lines of text), then "…".
                            previewLines = 8,
                            keepTile = true,
                            showDate = false,
                            modifier = Modifier.animateItem(),
                            selecting = selecting,
                            selected = note.id in selectedIds,
                            onSelect = { toggle(note.id) },
                        )
                    }
                }
            }

            else -> {
                val listState = rememberReselectListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        SwipeToDeleteBox(onDelete = { actions.delete(note) }, modifier = Modifier.animateItem()) {
                            EntryCard(
                                entry = note,
                                actions = actions,
                                tags = state.tagsByEntry[note.id].orEmpty(),
                                image = state.attachments[note.id]?.firstOrNull { it.mimeType.startsWith("image") },
                                hasVoice = state.attachments[note.id].orEmpty().any { it.mimeType.startsWith("audio") },
                                previewLines = 3,
                                showDate = false,
                                selecting = selecting,
                                selected = note.id in selectedIds,
                                onSelect = { toggle(note.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class NotesMode(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GRID("Als Kacheln", Icons.Outlined.GridView),
    LIST("Als Liste", Icons.AutoMirrored.Outlined.ViewList),
    FOLDERS("Nach Ordnern", Icons.Outlined.Folder),
}

/** Folders as a list: name and number of notes; "Ohne Ordner" for the rest. */
@Composable
private fun FolderList(folders: List<String>, counts: Map<String, Int>, onOpen: (String) -> Unit) {
    val rows = folders.sortedBy { it.lowercase() } + listOfNotNull("".takeIf { (counts[""] ?: 0) > 0 })
    val listState = rememberReselectListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
    ) {
        itemsIndexed(rows, key = { _, f -> "folder-row-$f" }) { i, folder ->
            val n = counts[folder] ?: 0
            SegmentedListItem(
                onClick = { onOpen(folder) },
                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(i, rows.size),
                leadingContent = {
                    Icon(
                        if (folder.isEmpty()) Icons.AutoMirrored.Outlined.Notes else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                supportingContent = { Text(if (n == 1) "1 Notiz" else "$n Notizen") },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
            ) { Text(folder.ifEmpty { "Ohne Ordner" }) }
        }
    }
}

// ============================================================================
// Diary
// ============================================================================

@Composable
internal fun DiaryPage(state: JournalUiState, actions: EntryActions, onNewDiaryOn: (String) -> Unit) {
    // The mood calendar is the diary's main view; the list is one tap away.
    var calendar by rememberSaveable { mutableStateOf(true) }
    val listState = rememberReselectListState()
    val weekStart = LocalDate.now().minusDays(WEEKLY_ENTRY_GOAL - 1L)
    val weekCount = state.diary.map { it.entryDate }.distinct()
        .count { runCatching { !LocalDate.parse(it).isBefore(weekStart) }.getOrDefault(false) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "mode") {
            SegmentedSelector(
                segments = listOf(
                    Segment("Kalender", Icons.Outlined.CalendarMonth),
                    Segment("Liste", Icons.AutoMirrored.Outlined.ViewList),
                ),
                selectedIndex = if (calendar) 0 else 1,
                onSelect = { calendar = it == 0 },
            )
        }
        item(key = "week") { WeeklyGoal(weekCount) }

        if (state.onThisDay.isNotEmpty()) {
            item(key = "otd") { OnThisDayCard(state.onThisDay, actions) }
        }

        if (calendar) {
            item(key = "calendar") {
                MoodCalendar(
                    moods = state.dayMoods,
                    onDay = { date ->
                        val existing = state.diary.firstOrNull { it.entryDate == date.toString() }
                        if (existing != null) actions.open(existing) else onNewDiaryOn(date.toString())
                    },
                )
            }
            item(key = "diary-stats") {
                DiaryStatsCard(
                    remember(state.diary, state.diaryMeta) {
                        state.diary.mapNotNull { e ->
                            val m = state.diaryMeta[e.id] ?: return@mapNotNull null
                            val d = runCatching { LocalDate.parse(e.entryDate) }.getOrNull() ?: return@mapNotNull null
                            DiaryDay(d, m.mood, m.energy, m.sleepQuality)
                        }.groupBy { it.date }.map { it.value.first() }
                    },
                )
            }
        } else if (state.diary.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.AutoStories,
                    title = "Noch keine Tagebucheinträge",
                    body = "Stimmung, Energie, Schlaf und ein Impuls pro Tag.",
                    actionLabel = "Tag festhalten",
                    onAction = { actions.create(EntryType.DIARY, "") },
                )
            }
        } else {
            items(state.diary, key = { it.id }) { entry ->
                val meta = state.diaryMeta[entry.id]
                SwipeToDeleteBox(onDelete = { actions.delete(entry) }, modifier = Modifier.animateItem()) {
                    EntryCard(
                        entry = entry,
                        actions = actions,
                        tags = state.tagsByEntry[entry.id].orEmpty(),
                        image = state.attachments[entry.id]?.firstOrNull { it.mimeType.startsWith("image") },
                        hasVoice = state.attachments[entry.id].orEmpty().any { it.mimeType.startsWith("audio") },
                        previewLines = 3,
                        leading = meta?.let { { Text(MoodEmojis[(it.mood - 1).coerceIn(0, 4)], style = MaterialTheme.typography.headlineSmall) } },
                        extra = meta?.let { m ->
                            {
                                val parts = listOfNotNull(
                                    m.weatherCode?.let { code ->
                                        "${app.tenet.android.core.common.WeatherCodes.emoji(code)} ${m.tempMaxC?.let { kotlin.math.round(it).toInt() }}° / ${m.tempMinC?.let { kotlin.math.round(it).toInt() }}°"
                                    },
                                    m.energy?.let { "Energie $it/5" },
                                    m.sleepQuality?.let { "Schlaf $it/5" },
                                )
                                if (parts.isNotEmpty()) {
                                    Text(
                                        parts.joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyGoal(done: Int) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Diese Woche: $done von $WEEKLY_ENTRY_GOAL Tagen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        app.tenet.android.core.designsystem.component.TenetProgress(
            progress = { (done.toFloat() / WEEKLY_ENTRY_GOAL).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "An diesem Tag": diary entries from the same day in earlier years. */
@Composable
private fun OnThisDayCard(entries: List<Entry>, actions: EntryActions) {
    TenetCard(
        colors = app.tenet.android.core.designsystem.theme.tenetAccentCardColors(MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.History, "An diesem Tag", color = app.tenet.android.core.designsystem.theme.cardTint(app.tenet.android.core.designsystem.theme.HealthTint.MIND, LocalContentColor.current))
            entries.take(3).forEach { entry ->
                val years = LocalDate.now().year - runCatching { LocalDate.parse(entry.entryDate).year }.getOrDefault(LocalDate.now().year)
                Surface(
                    onClick = { actions.open(entry) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (years == 1) "Vor einem Jahr" else "Vor $years Jahren",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            entry.title.ifBlank { markdownPlain(entry.body) },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}


/** Month heatmap: each day tinted by the average diary mood. */
@Composable
private fun MoodCalendar(moods: Map<String, Float>, onDay: (LocalDate) -> Unit) {
    // Mood scale harmonized with the theme (M3 custom-color harmonization).
    val moodColors = MoodColors.map { it.harmonized() }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    val today = LocalDate.now()
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh

    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = ym.minusMonths(1).toString() }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Vorheriger Monat")
                }
                Text(
                    ym.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + ym.year,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { month = ym.plusMonths(1).toString() },
                    enabled = ym.isBefore(YearMonth.now()),
                    shapes = IconButtonDefaults.shapes(),
                ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Nächster Monat") }
            }
            Row {
                DayOfWeek.entries.forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, Locale.GERMAN),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val offset = ym.atDay(1).dayOfWeek.value - 1
            val days = ym.lengthOfMonth()
            val cells = offset + days
            (0 until (cells + 6) / 7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).forEach { col ->
                        val day = week * 7 + col - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (day in 1..days) {
                                val date = ym.atDay(day)
                                val mood = moods[date.toString()]
                                val color = mood?.let { moodColors[(it.toInt() - 1).coerceIn(0, 4)] }
                                val future = date.isAfter(today)
                                // Mood day: its colour, fading to white in the middle so the number stays readable.
                                val base = MaterialTheme.colorScheme.surface
                                Surface(
                                    onClick = { onDay(date) },
                                    enabled = !future,
                                    shape = CircleShape,
                                    color = color ?: surface,
                                    border = if (date == today) {
                                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(
                                        Modifier.fillMaxSize().then(
                                            if (color != null) {
                                                Modifier.background(
                                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                                        0f to base.copy(alpha = 0.92f),
                                                        0.45f to base.copy(alpha = 0.7f),
                                                        1f to Color.Transparent,
                                                    ),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$day",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (mood != null) FontWeight.Bold else null,
                                            color = if (future) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Dreams
// ============================================================================

@Composable
internal fun DreamsPage(state: JournalUiState, actions: EntryActions) {
    val listState = rememberReselectListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.dreams.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.NightsStay,
                    title = "Noch keine Träume aufgeschrieben",
                    body = "Morgens per Sprache festhalten, Symbole und Emotionen markieren.",
                    actionLabel = "Traum erzählen",
                    onAction = { actions.create(EntryType.DREAM, START_DICTATE) },
                )
            }
            return@LazyColumn
        }
        state.sleepByDate[LocalDate.now().toString()]?.let { night ->
            item(key = "last-night") { SleepNightCard(night) }
        }
        item(key = "patterns") { DreamPatternsCard(state) }
        items(state.dreams, key = { it.id }) { dream ->
            val meta = state.dreamMeta[dream.id]
            SwipeToDeleteBox(onDelete = { actions.delete(dream) }, modifier = Modifier.animateItem()) {
                EntryCard(
                    entry = dream,
                    actions = actions,
                    tags = state.symbolsByEntry[dream.id].orEmpty() + state.tagsByEntry[dream.id].orEmpty(),
                    image = state.attachments[dream.id]?.firstOrNull { it.mimeType.startsWith("image") },
                    hasVoice = state.attachments[dream.id].orEmpty().any { it.mimeType.startsWith("audio") },
                    previewLines = 3,
                    leading = { ShapeIcon(Icons.Outlined.NightsStay) },
                    extra = if (meta == null && !state.sleepByDate.containsKey(dream.entryDate)) null else {
                        {
                            val m = meta
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Sleep of that night, from Health Connect.
                                state.sleepByDate[dream.entryDate]?.let { night ->
                                    Badge(
                                        "😴 " + night.durationText + (night.remMin?.takeIf { it > 0 }?.let { " · REM " + app.tenet.android.core.common.SleepMath.duration(it) } ?: ""),
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (m != null && m.lucid) Badge("Luzid", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                if (m != null && m.nightmare) Badge("Albtraum", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                if (m != null && m.recurring) Badge("Wiederkehrend", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                if (m != null) Badge("Klarheit ${m.clarity}/5", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                                m?.emotions?.split(',')?.filter { it.isNotBlank() }?.take(3)?.forEach {
                                    Badge(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Last night from Health Connect: duration, times and sleep phases as a bar. */
@Composable
internal fun SleepNightCard(
    night: app.tenet.android.core.common.SleepNight,
    title: String = "Letzte Nacht",
) {
    val zone = java.time.ZoneId.systemDefault()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val colors = MaterialTheme.colorScheme
    val onCard = if (app.tenet.android.core.designsystem.theme.isClearStyle) colors.onSurface else colors.onSecondaryContainer
    TenetCard(colors = app.tenet.android.core.designsystem.theme.tenetAccentCardColors(colors.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(
                Icons.Outlined.Bedtime,
                title,
                color = app.tenet.android.core.designsystem.theme.cardTint(app.tenet.android.core.designsystem.theme.HealthTint.SLEEP, onCard),
                meta = night.start.atZone(zone).format(fmt) + " – " + night.end.atZone(zone).format(fmt),
            )
            Text(night.durationText, style = MaterialTheme.typography.displaySmall, color = onCard)
            val phases = listOfNotNull(
                night.deepMin?.let { Triple("Tief", it, colors.primary) },
                night.lightMin?.let { Triple("Leicht", it, colors.primary.copy(alpha = 0.55f)) },
                night.remMin?.let { Triple("REM", it, colors.tertiary) },
                night.awakeMin?.let { Triple("Wach", it, colors.outline) },
            ).filter { it.second > 0 }
            if (phases.isNotEmpty()) {
                val total = phases.sumOf { it.second }.toFloat()
                Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                    phases.forEach { (_, min, color) -> Box(Modifier.weight(min / total).fillMaxHeight().background(color)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    phases.forEach { (label, min, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(color, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("$label " + app.tenet.android.core.common.SleepMath.duration(min), style = MaterialTheme.typography.labelMedium, color = colors.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

/** Pattern view: totals, lucid share over time, most frequent symbols. */
@Composable
private fun DreamPatternsCard(state: JournalUiState) {
    val total = state.dreams.size
    val lucid = state.dreamMeta.values.count { it.lucid }
    val nightmares = state.dreamMeta.values.count { it.nightmare }
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(Icons.Outlined.Insights, "Muster", color = app.tenet.android.core.designsystem.theme.cardTint(app.tenet.android.core.designsystem.theme.HealthTint.SLEEP), meta = if (total == 1) "1 Traum" else "$total Träume")
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Stat("$total", "Träume")
                Stat("${if (total == 0) 0 else lucid * 100 / total} %", "Luzid")
                Stat("$nightmares", "Albträume")
            }
            val months = state.dreamMonths.takeLast(6)
            if (months.size >= 2) {
                Text("Anteil luzider Träume", style = MaterialTheme.typography.labelLarge)
                LucidBars(months.map { it.month to if (it.total == 0) 0f else it.lucid.toFloat() / it.total })
            }
            if (state.symbolCounts.isNotEmpty()) {
                Text("Häufigste Symbole", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.symbolCounts.take(10).forEach { s ->
                        SuggestionChip(onClick = {}, label = { Text("${s.name} · ${s.count}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LucidBars(values: List<Pair<String, Float>>) {
    val grow = rememberGrowIn(values)
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            val slot = size.width / values.size
            val w = slot * 0.5f
            values.forEachIndexed { i, (_, v) ->
                val x = i * slot + (slot - w) / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                val h = size.height * v.coerceIn(0f, 1f) * grow
                if (h > 0f) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
            }
        }
        Row {
            values.forEach { (month, _) ->
                Text(
                    runCatching { YearMonth.parse(month).month.getDisplayName(TextStyle.SHORT, Locale.GERMAN) }.getOrDefault(month),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ============================================================================
// Shared card
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryCard(
    entry: Entry,
    actions: EntryActions,
    tags: List<String>,
    image: Attachment?,
    previewLines: Int,
    hasVoice: Boolean = false,
    /** Grid tile with a fixed height: tags stay pinned at the bottom, the preview gives way. */
    fixedHeight: Boolean = false,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    /** Multi-select: [onSelect] toggles this card; long-press starts the selection. */
    selecting: Boolean = false,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    /** Notes leave the date out of the overview; it shows inside the note. */
    showDate: Boolean = true,
    /** Google Keep tile: height by content, outline on plain notes, photo as wallpaper. */
    keepTile: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val preview = remember(entry.body) { markdownPlain(entry.body) }
    val checklist = remember(entry.body) { checklistProgress(entry.body) }
    // Diary and dreams in the serif reading font if set; notes stay in the UI font.
    val serif = entry.type != EntryType.NOTE && LocalJournalSerif.current
    val titleInHeader = !showDate && entry.title.isNotBlank()

    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val plain = entry.color == null && image == null
    TenetCard(
        colors = CardDefaults.cardColors(
            containerColor = if (keepTile && plain) MaterialTheme.colorScheme.surface else noteContainer(entry.color),
        ),
        // Keep: plain notes get a thin outline instead of a filled card.
        border = when {
            selected -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            keepTile && plain -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            else -> null
        },
        shape = if (keepTile) androidx.compose.foundation.shape.RoundedCornerShape(16.dp) else app.tenet.android.core.designsystem.theme.tenetCardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Grid tiles: the photo is the tile's wallpaper under a light white (light) or
        // black (dark) veil; lists show no photo (it sits at the end of the note).
        val wallpaper = image?.takeIf { fixedHeight || keepTile }
        Box {
            if (wallpaper != null) {
                AsyncImage(
                    model = NoteBackgrounds.model(wallpaper.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                val veil = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.Black else Color.White
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            if (keepTile) {
                                // Keep shows the picture clearly; a soft veil keeps the text legible.
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to veil.copy(alpha = 0.45f),
                                    1f to veil.copy(alpha = 0.3f),
                                )
                            } else {
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to veil.copy(alpha = 0.55f),
                                    0.45f to veil.copy(alpha = 0.8f),
                                    1f to veil.copy(alpha = 0.92f),
                                )
                            },
                        ),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (fixedHeight) Modifier.fillMaxHeight() else Modifier)
                    .combinedClickable(
                        onClick = { if (selecting && onSelect != null) onSelect() else actions.open(entry) },
                        onLongClick = {
                            if (onSelect != null) {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onSelect()
                            } else {
                                menu = true
                            }
                        },
                    ),
            ) {
                Column(
                    Modifier
                        .then(if (fixedHeight) Modifier.weight(1f) else Modifier)
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        // Notes: title and icons hang from the same top edge in every tile.
                        verticalAlignment = if (showDate) Alignment.CenterVertically else Alignment.Top,
                        modifier = if (showDate) Modifier else Modifier.padding(top = 8.dp, end = 12.dp),
                    ) {
                        if (leading != null) {
                            leading()
                            Spacer(Modifier.width(10.dp))
                        }
                        if (showDate) Text(
                            formatDate(entry.entryDate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f),
                        ) else if (titleInHeader) {
                            // Without a date the title moves up into the header line.
                            Box(Modifier.weight(1f)) {
                                JournalReading(enabled = serif) {
                                    Text(
                                        entry.title,
                                        style = if (keepTile) {
                                            MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else Spacer(Modifier.weight(1f))
                        // Checklist progress up here: the card height is fixed in the grid,
                        // a bar below the preview would be cut off.
                        // Notes (no date) keep the header to the menu alone.
                        checklist?.takeIf { showDate }?.let { (done, total) ->
                            Surface(
                                shape = CircleShape,
                                color = if (done == total) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.semantics { contentDescription = "$done von $total erledigt" },
                            ) {
                                Text(
                                    "$done/$total",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (hasVoice) {
                            Icon(Icons.Outlined.Mic, contentDescription = "Mit Sprachmemo", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        if (entry.pinned) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Angeheftet", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        if (selecting) {
                            app.tenet.android.core.designsystem.component.SelectionCheck(
                                selected,
                                Modifier.padding(12.dp),
                            )
                        // Notes have no ⋮ (like Keep): the title gets the full width; pin in the
                        // editor, delete via long-press selection or swipe.
                        } else if (showDate) Box {
                            IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (entry.pinned) "Lösen" else "Anheften") },
                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    onClick = { menu = false; actions.togglePin(entry) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Löschen") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                    onClick = { menu = false; actions.delete(entry) },
                                )
                            }
                        }
                    }
                    Column(
                        Modifier.then(if (fixedHeight) Modifier.weight(1f) else Modifier).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (entry.title.isNotBlank() && !titleInHeader) {
                            JournalReading(enabled = serif) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (preview.isNotBlank()) {
                            // Rendered Markdown (headings, lists, tappable checkboxes) as preview.
                            // In fixed tiles it takes the space left and is cut there, so tags never are.
                            val lines = if (fixedHeight && tags.isNotEmpty()) (previewLines - 1).coerceAtLeast(1) else previewLines
                            Box(if (fixedHeight) Modifier.weight(1f, fill = false).clipToBounds() else Modifier) {
                                JournalReading(enabled = serif) {
                                    MarkdownView(
                                        body = entry.body,
                                        // While selecting, the checklist is read-only.
                                        onToggleCheck = { line -> if (!selecting) actions.toggleCheck(entry, line) },
                                        onLink = { actions.open(entry) },
                                        compact = true,
                                        maxBlocks = lines,
                                        maxLines = lines,
                                        moreAsEllipsis = keepTile,
                                    )
                                }
                                // Selecting: a tap anywhere on the preview (checkboxes, links) only selects.
                                if (selecting) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .combinedClickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onSelect?.invoke() },
                                            ),
                                    )
                                }
                            }
                        }
                        extra?.invoke()
                        if (tags.isNotEmpty()) {
                            Text(
                                tags.joinToString("  ") { "#$it" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = if (fixedHeight) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
