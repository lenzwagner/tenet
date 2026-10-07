package app.tenet.android.feature.journal

import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import app.tenet.android.core.designsystem.component.animatedMorphShape
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import app.tenet.android.core.designsystem.component.LocalBackdropBlur
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import app.tenet.android.core.designsystem.component.rememberDictation
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.TenetSlider
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Folder
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.designsystem.theme.JournalReading
import app.tenet.android.core.designsystem.theme.LocalJournalSerif
import app.tenet.android.core.designsystem.theme.DreamAmoledScope
import app.tenet.android.core.designsystem.component.SectionHeader
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TenetSwitch
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.feature.journal.markdown.MarkdownView
import app.tenet.android.feature.journal.markdown.continueList
import app.tenet.android.feature.journal.markdown.toggleCheck
import app.tenet.android.feature.journal.markdown.togglePrefix
import app.tenet.android.feature.journal.markdown.wrapSelection
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Editor for notes, diary entries and dreams (App_Konzept.md 5.3); follows
 * the app theme for all types.
 */
@Composable
fun EntryEditorScreen(
    entryId: String?,
    initialType: EntryType,
    onBack: () -> Unit,
    onOpenEntry: (id: String, type: EntryType) -> Unit = { _, _ -> },
    onCreateNote: (title: String) -> Unit = {},
    initialTitle: String = "",
    initialDate: String = "",
    autoDictate: Boolean = false,
    /** One of [START_LIST], [START_AUDIO], [START_IMAGE], [START_DICTATE] or empty. */
    startAction: String = "",
    /** New entries float as a sheet over the page they were started from. */
    sheet: Boolean = false,
    viewModel: EntryEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(entryId) { viewModel.load(entryId, initialType, initialTitle, initialDate) }
    // 1 = hidden below the screen, 0 = fully open (sheet mode only).
    val sheetOffset = remember { Animatable(if (sheet) 1f else 0f) }
    val scope = rememberCoroutineScope()
    // Closing the sheet: slide down quickly (the blur behind fades with it), then leave.
    fun leave() {
        if (!sheet) return onBack()
        scope.launch {
            sheetOffset.animateTo(1f, tween(180, easing = FastOutLinearInEasing))
            onBack()
        }
    }
    LaunchedEffect(Unit) { viewModel.saved.collect { leave() } }
    LaunchedEffect(Unit) {
        viewModel.links.collect { target ->
            when (target) {
                is LinkTarget.Existing -> onOpenEntry(target.id, target.type)
                is LinkTarget.Create -> onCreateNote(target.title)
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    // ^ immediate: text fields must see their own edits in the same frame,
    // otherwise fast typing can drop characters.

    // Dreams are often written at night: pure black there if set (dark mode only).
    if (initialType == EntryType.DREAM) {
        DreamAmoledScope { EditorContent(sheet, sheetOffset, state, viewModel, ::leave, onOpenEntry, autoDictate, startAction) }
    } else {
        EditorContent(sheet, sheetOffset, state, viewModel, ::leave, onOpenEntry, autoDictate, startAction)
    }
}

@Composable
private fun EditorContent(
    sheet: Boolean,
    sheetOffset: Animatable<Float, AnimationVector1D>,
    state: EntryEditorState,
    viewModel: EntryEditorViewModel,
    leave: () -> Unit,
    onOpenEntry: (String, EntryType) -> Unit,
    autoDictate: Boolean,
    startAction: String,
) {
    if (!sheet) {
        EditorScaffold(state, viewModel, leave, onOpenEntry, autoDictate, startAction)
        return
    }
    // Swipe down, back or a tap next to the sheet: keep what was written, drop an empty entry.
    val close = {
        val empty = state.title.isBlank() && state.body.replace("- [ ]", "").isBlank() && state.attachments.isEmpty()
        if (empty) leave() else viewModel.save()
    }
    EditorSheetFrame(offset = sheetOffset, onClose = close) {
        EditorScaffold(state, viewModel, close, onOpenEntry, autoDictate, startAction, inSheet = true)
    }
}

/**
 * Frame of the new-entry sheet: 85 % of the screen high, rounded top, drag
 * handle to pull it down. The app behind is blurred in step with the
 * sheet's position (see [LocalBackdropBlur]).
 */
@Composable
private fun EditorSheetFrame(
    offset: Animatable<Float, AnimationVector1D>,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBackdropBlur.current
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.setDimAmount(0f)
        window?.setWindowAnimations(0)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { offset.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) }
    LaunchedEffect(Unit) { snapshotFlow { 1f - offset.value }.collect { backdrop?.floatValue = it } }
    DisposableEffect(Unit) { onDispose { backdrop?.floatValue = 0f } }
    BackHandler { onClose() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * 0.85f
        val sheetPx = constraints.maxHeight * 0.85f
        val progress = 1f - offset.value
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f * progress))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        val drag = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta / sheetPx).coerceIn(0f, 1f)) }
        }
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, (offset.value * sheetPx).roundToInt()) },
        ) {
            Column {
                // Drag handle: pull the sheet down to close it.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = drag,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (offset.value > 0.25f || velocity > 1800f) {
                                    onClose()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.9f))
                                }
                            },
                        )
                        .padding(vertical = 12.dp)
                        .semantics { contentDescription = "Zum Schließen nach unten ziehen" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                    )
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable
private fun EditorScaffold(
    state: EntryEditorState,
    viewModel: EntryEditorViewModel,
    onBack: () -> Unit,
    onOpenEntry: (String, EntryType) -> Unit,
    autoDictate: Boolean = false,
    startAction: String = "",
    inSheet: Boolean = false,
) {
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.aiResult.collect { message ->
            if (message.startsWith("KI hat")) {
                snackbar?.showUndo(message, onUndo = viewModel::undoAi)
            } else {
                snackbar?.show(message)
            }
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val speech = rememberDictation(
        when (state.type) {
            EntryType.DREAM -> "Erzähl deinen Traum – so ausführlich du willst"
            EntryType.DIARY -> "Erzähl, was du erlebt hast"
            EntryType.NOTE -> "Sprich deine Notiz"
        },
        viewModel::appendDictation,
    )
    fun dictate() = speech.launch()
    // ---- Voice memo recording ----
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var recordingMs by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(recording) {
        val start = System.currentTimeMillis()
        while (recording) {
            recordingMs = (System.currentTimeMillis() - start).toInt()
            delay(200)
        }
    }
    fun startRecording() {
        recording = recorder.start()
        if (!recording) snackbar?.show("Aufnahme nicht möglich – Mikrofon belegt?")
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }
    fun toggleRecording() {
        if (recording) {
            recording = false
            recorder.stop()?.let { viewModel.addAttachment(it, VOICE_MIME) }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Keep access across restarts.
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addAttachment(uri.toString(), context.contentResolver.getType(uri) ?: "image/*")
        }
    }

    // Start action from the FAB menu / Heute ("Traum erzählen", Liste, Audio,
    // Bild): runs once, as soon as the entry is ready.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (!started && !state.loading) {
            started = true
            when {
                autoDictate || startAction == START_DICTATE -> dictate()
                startAction == START_LIST -> if (state.body.isBlank()) viewModel.onBody("- [ ] ")
                startAction == START_AUDIO -> toggleRecording()
                startAction == START_IMAGE ->
                    runCatching { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // In the sheet the status bar is far away; only keyboard and nav bar matter.
        contentWindowInsets = if (inSheet) WindowInsets.navigationBars.union(WindowInsets.ime) else ScaffoldDefaults.contentWindowInsets,
        // In the sheet everything shares the sheet's surface.
        // Over the area's colour wash; AMOLED dreams stay pure black.
        containerColor = if (!inSheet && MaterialTheme.colorScheme.background == Color.Black) Color.Black else Color.Transparent,
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                windowInsets = if (inSheet) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                colors = if (inSheet) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                } else {
                    app.tenet.android.core.designsystem.header.washTopBarColors()
                },
                navigationIcon = {
                    if (inSheet) {
                        TooltipIconButton(Icons.Outlined.KeyboardArrowDown, "Schließen", onBack)
                    } else {
                        TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack)
                    }
                },
                title = {
                    Text(
                        when (state.type) {
                            EntryType.NOTE -> if (state.isNew) "Neue Notiz" else "Notiz"
                            EntryType.DIARY -> if (state.isNew) "Neuer Tagebucheintrag" else "Tagebucheintrag"
                            EntryType.DREAM -> if (state.isNew) "Neuer Traum" else "Traum"
                        },
                        // Collapsed next to the actions it must stay on one line.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = { Text(formatDate(state.entryDate)) },
                actions = {
                    IconToggleButton(
                        checked = state.pinned,
                        onCheckedChange = viewModel::onPinned,
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(
                            if (state.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (state.pinned) "Lösen" else "Anheften",
                        )
                    }
                    TooltipIconButton(Icons.Outlined.Mic, "Diktieren", ::dictate)
                    if (viewModel.aiAvailable) {
                        if (aiBusy) {
                            LoadingIndicator(Modifier.size(40.dp).padding(4.dp))
                        } else {
                            // Empty entry: dictate first, the dictation then fills the fields by itself.
                            TooltipIconButton(Icons.Outlined.AutoAwesome, "Mit KI ausfüllen", {
                                if (state.body.isBlank()) dictate() else viewModel.aiFill()
                            })
                        }
                    }
                    if (!state.isNew) {
                        TooltipIconButton(Icons.Outlined.Delete, "Löschen", {
                            val title = state.title.ifBlank { "Eintrag" }
                            viewModel.delete()?.let { id ->
                                snackbar?.showUndo(
                                    "„$title“ gelöscht",
                                    onUndo = { viewModel.restore(id) },
                                    onCommit = { viewModel.commitDelete(id) },
                                )
                            }
                        })
                    }
                    FilledIconButton(
                        onClick = { viewModel.save() },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 4.dp),
                    ) { Icon(Icons.Outlined.Check, contentDescription = "Speichern") }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            TenetLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val backlinks by viewModel.backlinks.collectAsStateWithLifecycle()
        val formatBridge = remember { FormatBridge() }
        val tagSuggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()
        val symbolSuggestions by viewModel.symbolSuggestions.collectAsStateWithLifecycle()
        val dayContext by viewModel.dayContext.collectAsStateWithLifecycle()
        val sleepNight by viewModel.sleepNight.collectAsStateWithLifecycle()

        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = if (formatBridge.active) 96.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Title like Google Keep: large text, no box.
            JournalReading(enabled = state.type != EntryType.NOTE && LocalJournalSerif.current) {
                BasicTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitle,
                    singleLine = false,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (state.title.isEmpty()) {
                            Text(
                                if (state.type == EntryType.NOTE) "Titel" else "Titel (optional)",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            when (state.type) {
                EntryType.DIARY -> DiarySection(state, viewModel, dayContext) { prompt ->
                    viewModel.onBody(
                        (if (state.body.isBlank()) "" else state.body.trimEnd() + "\n\n") + "**$prompt**\n",
                    )
                }
                EntryType.DREAM -> DreamSection(state, viewModel, symbolSuggestions, sleepNight, ::dictate)
                EntryType.NOTE -> Unit
            }

            JournalReading(enabled = state.type != EntryType.NOTE && LocalJournalSerif.current) {
                BodyEditor(
                    body = state.body,
                    onBody = viewModel::onBody,
                    onLink = viewModel::openLink,
                    label = if (state.type == EntryType.DREAM) "Erinnerung" else "Eintrag",
                    bridge = formatBridge,
                    startChecklist = startAction == START_LIST,
                )
            }

            TagEditor(
                label = "Tag",
                values = state.tags,
                suggestions = tagSuggestions,
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
                icon = Icons.AutoMirrored.Outlined.Label,
            )

            if (state.type == EntryType.NOTE) {
                val folders by viewModel.folders.collectAsStateWithLifecycle()
                FolderField(value = state.folder, folders = folders, onChange = viewModel::onFolder)
                ColorRow(selected = state.color, onSelect = viewModel::onColor)
            }

            Attachments(
                items = state.attachments,
                onAddImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                recording = recording,
                recordingMs = recordingMs,
                onToggleRecording = ::toggleRecording,
                onRemove = viewModel::removeAttachment,
            )

            if (backlinks.isNotEmpty()) {
                SectionHeader("Verlinkt von · ${backlinks.size}")
                Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                    backlinks.forEachIndexed { index, entry ->
                        SegmentedListItem(
                            colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                            onClick = { onOpenEntry(entry.id, entry.type) },
                            shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, backlinks.size),
                            leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            supportingContent = { Text(formatDate(entry.entryDate)) },
                        ) { Text(entry.title.ifBlank { "Ohne Titel" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = formatBridge.active,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(bottom = 12.dp),
        ) {
            FormatToolbar(onAction = { formatBridge.apply(it) })
        }
        }
    }
}

// ---- Body: text or checklist ----------------------------------------------

@Composable
private fun BodyEditor(
    body: String,
    onBody: (String) -> Unit,
    onLink: (String) -> Unit,
    label: String,
    bridge: FormatBridge,
    startChecklist: Boolean = false,
) {
    // A note is either a checklist or text, fixed when it opens (no switcher,
    // no preview). An existing note's text arrives after the first frame, so
    // decide once it is there.
    var checklist by rememberSaveable { mutableStateOf(startChecklist || isChecklist(body)) }
    var modeDecided by rememberSaveable { mutableStateOf(startChecklist || body.isNotBlank()) }
    LaunchedEffect(body) {
        if (!modeDecided && body.isNotBlank()) {
            checklist = isChecklist(body)
            modeDecided = true
        }
    }
    // Local TextFieldValue so toolbar actions can work on the selection.
    var field by remember { mutableStateOf(TextFieldValue(body)) }
    // Only adopt body changes that did not come from this field (dictation,
    // prompt, checkbox toggles); echoing our own edits back would race with
    // fast typing and drop characters.
    // The state flow may hand back an older intermediate value, so remember
    // everything recently sent instead of only the last text.
    val sent = remember { ArrayDeque<String>().apply { add(body) } }
    LaunchedEffect(body) {
        if (body != field.text && body !in sent) {
            sent.clear()
            sent.add(body)
            field = TextFieldValue(body, TextRange(body.length))
        }
    }
    fun apply(value: TextFieldValue) {
        field = value
        if (sent.lastOrNull() != value.text) {
            sent.addLast(value.text)
            if (sent.size > 64) sent.removeFirst()
        }
        onBody(value.text)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (checklist) {
            ChecklistEditor(body = body, onBody = onBody)
        } else {
            bridge.apply = { apply(it(field)) }
            // Writing surface without a box: a calm tonal sheet (M3), placeholder instead of a label.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = { apply(it.continueList(field)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (field.text.isEmpty()) {
                            Text(
                                if (label == "Erinnerung") "Was hast du geträumt?" else "Notiz schreiben …",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .padding(16.dp)
                        .onFocusChanged { bridge.active = it.isFocused },
                )
            }
        }
    }
}

/**
 * Connects the body field with the floating format toolbar of the screen:
 * the toolbar shows while the Markdown field has focus and edits its
 * selection.
 */
internal class FormatBridge {
    var active by mutableStateOf(false)
    var apply: ((TextFieldValue) -> TextFieldValue) -> Unit = {}
}

/**
 * Markdown formatting as a floating toolbar (M3 Expressive) that sits
 * above the keyboard while writing; acts on the current selection.
 */
@Composable
private fun FormatToolbar(onAction: ((TextFieldValue) -> TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TooltipIconButton(Icons.Outlined.FormatBold, "Fett", { onAction { it.wrapSelection("**") } })
            TooltipIconButton(Icons.Outlined.FormatItalic, "Kursiv", { onAction { it.wrapSelection("*") } })
            TooltipIconButton(Icons.Outlined.Title, "Überschrift", { onAction { it.togglePrefix("## ") } })
            TooltipIconButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Liste", { onAction { it.togglePrefix("- ") } })
            TooltipIconButton(Icons.Outlined.CheckBox, "Checkliste", { onAction { it.togglePrefix("- [ ] ") } })
            TooltipIconButton(Icons.Outlined.FormatQuote, "Zitat", { onAction { it.togglePrefix("> ") } })
            TooltipIconButton(Icons.Outlined.Code, "Code", { onAction { it.wrapSelection("`") } })
            TooltipIconButton(Icons.Outlined.Link, "Notiz verlinken", { onAction { it.wrapSelection("[[", "]]") } })
        }
    }
}

// ---- Diary ----------------------------------------------------------------

@Composable
private fun DiarySection(
    state: EntryEditorState,
    viewModel: EntryEditorViewModel,
    context: DayContext,
    onUsePrompt: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScaleSelector("Stimmung", state.mood, MoodEmojis) { it?.let(viewModel::onMood) }
        ScaleSelector("Energie", state.energy, listOf("1", "2", "3", "4", "5"), optional = true) { viewModel.onEnergy(it) }
        ScaleSelector("Schlafqualität", state.sleepQuality, listOf("1", "2", "3", "4", "5"), optional = true) {
            viewModel.onSleep(it)
        }

        var promptOffset by rememberSaveable { mutableIntStateOf(0) }
        val prompt = promptOfDay(promptOffset)
        TenetCard(
            colors = app.tenet.android.core.designsystem.theme.tenetAccentCardColors(MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardHeader(Icons.Outlined.AutoAwesome, "Impuls", color = app.tenet.android.core.designsystem.theme.cardTint(app.tenet.android.core.designsystem.theme.HealthTint.MIND, androidx.compose.material3.LocalContentColor.current))
                Text(prompt, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onUsePrompt(prompt) }, shapes = ButtonDefaults.shapes()) {
                        Text("Übernehmen")
                    }
                    OutlinedButton(onClick = { promptOffset++ }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Anderer")
                    }
                }
            }
        }

        if (context.meals > 0 || context.workouts.isNotEmpty()) {
            Text("An diesem Tag", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.meals > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${context.kcal} kcal · ${context.meals} Mahlzeiten") },
                        leadingIcon = { Icon(Icons.Outlined.Restaurant, contentDescription = null) },
                    )
                }
                context.workouts.forEach { workout ->
                    AssistChip(
                        onClick = {},
                        label = { Text(workout) },
                        leadingIcon = { Icon(Icons.Outlined.FitnessCenter, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** 1..5 as a connected single-select button group; [optional] allows deselecting. */
@Composable
private fun ScaleSelector(
    label: String,
    value: Int?,
    labels: List<String>,
    optional: Boolean = false,
    onValue: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            labels.forEachIndexed { index, text ->
                val v = index + 1
                ToggleButton(
                    checked = value == v,
                    onCheckedChange = { onValue(if (optional && value == v) null else v) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                ) { Text(text, style = if (text.length > 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ---- Dream --------------------------------------------------------------

@Composable
private fun DreamSection(
    state: EntryEditorState,
    viewModel: EntryEditorViewModel,
    symbolSuggestions: List<app.tenet.android.core.database.dao.TagCount>,
    sleepNight: app.tenet.android.core.common.SleepNight?,
    onDictate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sleepNight?.let { SleepNightCard(it, title = "Schlaf dieser Nacht") }

        // Speak first, sort later: the dream fades within minutes.
        FilledTonalButton(
            onClick = onDictate,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight, hasStartIcon = true),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Traum erzählen", style = MaterialTheme.typography.titleMedium)
        }

        Text("Klarheit: ${state.clarity} von 5", style = MaterialTheme.typography.labelLarge)
        TenetSlider(
            value = state.clarity.toFloat(),
            onValueChange = viewModel::onClarity,
            valueRange = 1f..5f,
            steps = 3,
        )

        Text("Emotionen", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DreamEmotions.forEach { emotion ->
                val selected = emotion in state.emotions
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleEmotion(emotion) },
                    label = { Text(emotion) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }

        TagEditor(
            label = "Symbol hinzufügen (Person, Ort, Objekt)",
            values = state.symbols,
            suggestions = symbolSuggestions,
            onAdd = viewModel::addSymbol,
            onRemove = viewModel::removeSymbol,
            icon = Icons.Outlined.Category,
        )

        val options = listOf(
            Triple("Luzider Traum", state.lucid, viewModel::onLucid),
            Triple("Albtraum", state.nightmare, viewModel::onNightmare),
            Triple("Wiederkehrend", state.recurring, viewModel::onRecurring),
        )
        Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
            options.forEachIndexed { index, (label, checked, onChange) ->
                SegmentedListItem(
                    colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                    checked = checked,
                    onCheckedChange = onChange,
                    shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, options.size),
                    trailingContent = { TenetSwitch(checked = checked, onCheckedChange = null) },
                ) { Text(label) }
            }
        }
    }
}

// ---- Note color & attachments -----------------------------------------

@Composable
private fun ColorRow(selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Farbe", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NoteColors.forEach { color ->
                val isSelected = color == selected
                Surface(
                    onClick = { onSelect(color) },
                    shape = animatedMorphShape(isSelected, MaterialShapes.Circle, MaterialShapes.Cookie9Sided),
                    color = noteContainer(color),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.size(36.dp),
                ) {
                    if (color == null) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = "Keine Farbe", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Note folder as a chip: pick an existing one or create a new one. */
@Composable
private fun FolderField(value: String, folders: List<String>, onChange: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf<String?>(null) }
    Box {
        FilterChip(
            selected = value.isNotBlank(),
            onClick = { menu = true },
            label = { Text(value.ifBlank { "Ordner" }) },
            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (value.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Kein Ordner") },
                    leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                    onClick = { onChange(""); menu = false },
                )
            }
            folders.filter { it != value }.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder) },
                    leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    onClick = { onChange(folder); menu = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Neuer Ordner …") },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = { newFolder = ""; menu = false },
            )
        }
    }
    newFolder?.let { name ->
        AlertDialog(
            onDismissRequest = { newFolder = null },
            title = { Text("Neuer Ordner") },
            text = {
                OutlinedTextField(value = name, onValueChange = { newFolder = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(
                    onClick = { if (name.isNotBlank()) onChange(name.trim()); newFolder = null },
                    enabled = name.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { newFolder = null }, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Attachments(
    items: List<AttachmentUi>,
    onAddImage: () -> Unit,
    recording: Boolean,
    recordingMs: Int,
    onToggleRecording: () -> Unit,
    onRemove: (AttachmentUi) -> Unit,
) {
    val images = items.filter { !it.mimeType.startsWith("audio") }
    val memos = items.filter { it.mimeType.startsWith("audio") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        memos.forEachIndexed { index, memo ->
            VoiceMemoRow(uri = memo.uri, index = index + 1, onRemove = { onRemove(memo) })
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            images.forEach { item ->
                Box {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = "Bild",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(MaterialTheme.shapes.large),
                    )
                    FilledIconButton(
                        onClick = { onRemove(item) },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp),
                    ) { Icon(Icons.Outlined.Close, contentDescription = "Bild entfernen", modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddImage, shapes = ButtonDefaults.shapes(), enabled = !recording) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Bild")
            }
            if (recording) {
                Button(
                    onClick = onToggleRecording,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Stopp · ${formatMs(recordingMs)}")
                }
            } else {
                OutlinedButton(onClick = onToggleRecording, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.KeyboardVoice, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Sprachmemo")
                }
            }
        }
    }
}

internal fun formatDate(isoDate: String): String = runCatching {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMAN))
}.getOrDefault(isoDate)

/** Start actions for a new entry (FAB menu). */
const val START_LIST = "list"
const val START_AUDIO = "audio"
const val START_IMAGE = "image"
const val START_DICTATE = "dictate"
