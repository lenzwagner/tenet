package app.tenet.android.feature.sport.calisthenics

import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.MilitaryTech
import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.History
import app.tenet.android.core.designsystem.component.CompactNumberField
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.tenet.android.core.designsystem.component.VolumeKeyHandler
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import app.tenet.android.core.designsystem.component.TooltipIconButton
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.SkillMath
import app.tenet.android.core.database.entity.CriterionType
import app.tenet.android.core.database.entity.FormQuality
import app.tenet.android.core.database.entity.SetEntry
import app.tenet.android.feature.sport.SkillSessionViewModel
import kotlinx.coroutines.delay

/**
 * Active calisthenics skill session (App_Konzept.md 5.2.2): one step at a
 * time with a hold countdown, quality marks per set and live criterion
 * evaluation that feeds the promotion suggestion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
    viewModel: SkillSessionViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEndDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Form video for a set (or the session): the set it belongs to while the camera is open.
    var videoSetId by remember { mutableStateOf<String?>(null) }
    val recordVideo = rememberFormVideoRecorder(
        onRecorded = { uri, duration -> viewModel.addVideo(uri, duration, videoSetId) },
        onUnavailable = { scope.launch { snackbarHostState.showSnackbar("Keine Kamera-App für Videos gefunden") } },
    )

    LaunchedEffect(Unit) {
        viewModel.achievementEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.finishedEvents.collect { onFinished() }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("Session beenden?") },
            text = { Text("Die Session wird gespeichert und in den Verlauf übernommen.") },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        showEndDialog = false
                        viewModel.finish()
                    },
                ) { Text("Beenden") }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showEndDialog = false }) { Text("Weitertrainieren") }
            },
        )
    }

    val step = state.info?.step
    val isHold = step?.criterionType == CriterionType.HOLD

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = {
                    TooltipIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück (Session bleibt aktiv)", onClick = onBack)
                },
                title = {
                    val info = state.info
                    if (info == null) {
                        Text("Skill-Session")
                    } else {
                        Column {
                            Text(
                                text = "${info.skill.name} · ${info.step.label}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = info.exercise.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.info != null) {
                        TooltipIconButton(Icons.Outlined.Videocam, "Formvideo aufnehmen", {
                            videoSetId = null
                            recordVideo()
                        })
                    }
                    state.info?.let { info ->
                        TooltipIconButton(Icons.Outlined.History, "Verlauf & Rekorde: ${info.exercise.name}", { onOpenExercise(info.exercise.id) })
                    }
                    FilledIconButton(onClick = { showEndDialog = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Check, contentDescription = "Session beenden")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.loaded || step == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Session wird geladen …",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val criterion = SkillMath.Criterion(
            isHold = isHold,
            sets = step.criterionSets,
            value = step.criterionValue,
        )
        val attempts = state.sets.map { set ->
            SkillMath.Attempt(
                completed = set.completed,
                seconds = set.durationSec,
                reps = set.reps,
                sloppy = set.formQuality == FormQuality.SLOPPY,
            )
        }
        val qualifying = SkillMath.qualifyingSets(attempts, criterion)
        val criterionMet = qualifying >= criterion.sets

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CriterionCard(
                    criterionText = if (isHold) {
                        "${step.criterionSets} × ${step.criterionValue} s halten"
                    } else {
                        "${step.criterionSets} × ${step.criterionValue} Wdh schaffen"
                    },
                    qualifying = qualifying,
                    required = step.criterionSets,
                    met = criterionMet,
                )
            }
            if (isHold) {
                item {
                    HoldTimerCard(targetSeconds = step.criterionValue, onHoldEnd = viewModel::setHoldEnd)
                }
            }
            items(state.sets, key = { it.id }) { set ->
                SetRow(
                    index = set.sortOrder + 1,
                    set = set,
                    isHold = isHold,
                    onComplete = { viewModel.setCompleted(set, it) },
                    onSeconds = { viewModel.setSeconds(set, it) },
                    onReps = { viewModel.setReps(set, it) },
                    onCycleQuality = { viewModel.cycleQuality(set) },
                    videoCount = videos.count { it.setId == set.id },
                    onRecordVideo = {
                        videoSetId = set.id
                        recordVideo()
                    },
                )
            }
            item {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = viewModel::addSet,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Satz hinzufügen")
                }
            }
            if (videos.isNotEmpty()) {
                item(key = "videos") {
                    val setNumbers = state.sets.associate { it.id to it.sortOrder + 1 }
                    TenetCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardHeader(Icons.Outlined.Videocam, "Formvideos", meta = "${videos.size}")
                            FormVideoStrip(
                                videos = videos.sortedByDescending { it.createdAt },
                                caption = { v -> v.setId?.let { id -> setNumbers[id]?.let { "Satz $it" } } ?: "Ohne Satz" },
                                onDelete = viewModel::deleteVideo,
                            )
                            Text(
                                "Nur auf diesem Gerät gespeichert. Ältere Videos findest du beim Skill.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CriterionCard(
    criterionText: String,
    qualifying: Int,
    required: Int,
    met: Boolean,
) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardHeader(Icons.Outlined.MilitaryTech, "Aufstiegskriterium")
            Text(
                text = criterionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$qualifying / $required Sätze erfüllt" +
                    if (met) " · erfüllt!" else "",
                style = MaterialTheme.typography.titleSmall,
                color = if (met) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (met) {
                Text(
                    text = "In dieser Session erfüllt. Nach der zweiten Session " +
                        "schlägt die App den Aufstieg vor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Big hold countdown with an acoustic signal at zero
 * (App_Konzept.md 5.2.2: "großer Hold-Timer … akustisches Signal").
 */
@Composable
private fun HoldTimerCard(targetSeconds: Int, onHoldEnd: (Long?) -> Unit = {}) {
    var remaining by remember(targetSeconds) { mutableIntStateOf(targetSeconds) }
    var running by remember { mutableStateOf(false) }
    val tone = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    val holdHaptics = LocalHapticFeedback.current
    fun toggle() {
        holdHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (remaining == 0) remaining = targetSeconds
        running = !running
    }
    // Start/stop with a volume key, so the phone can stay on the floor.
    VolumeKeyHandler { toggle() }

    // Live notification: countdown chip while the hold runs.
    LaunchedEffect(running) {
        onHoldEnd(if (running) System.currentTimeMillis() + remaining * 1000L else null)
    }
    LaunchedEffect(running) {
        if (running) {
            while (remaining > 0) {
                delay(1_000)
                remaining--
            }
            running = false
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 500) }
            holdHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    TenetCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(Icons.Outlined.Timer, "Hold-Timer")
            Text(
                text = "%d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.displayMedium,
                color = if (remaining == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = ::toggle,
                ) {
                    Text(if (running) "Pause" else if (remaining == 0) "Nochmal" else "Start")
                }
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        running = false
                        remaining = targetSeconds
                    },
                ) { Text("Reset") }
            }
            Text(
                text = "Tipp: Lautstärketaste startet und stoppt den Timer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (remaining == 0) {
                Text(
                    text = "Zeit abgelaufen – Satz abschließen und Qualität markieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: SetEntry,
    isHold: Boolean,
    onComplete: (Boolean) -> Unit,
    onSeconds: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onCycleQuality: () -> Unit,
    videoCount: Int = 0,
    onRecordVideo: () -> Unit = {},
) {
    TenetCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Satz $index",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(min = 58.dp),
            )
            CompactNumberField(
                value = if (isHold) set.durationSec?.toString().orEmpty() else set.reps.toString(),
                onValueChange = { raw ->
                    val value = raw.filter(Char::isDigit).toIntOrNull() ?: 0
                    if (isHold) onSeconds(value) else onReps(value)
                },
                done = set.completed,
                suffix = if (isHold) "s" else "Wdh",
                modifier = Modifier.width(104.dp),
            )
            Spacer(Modifier.width(8.dp))
            QualityChip(quality = set.formQuality, onClick = onCycleQuality)
            Spacer(Modifier.weight(1f))
            BadgedBox(badge = { if (videoCount > 0) Badge { Text("$videoCount") } }) {
                TooltipIconButton(Icons.Outlined.Videocam, "Video von Satz $index aufnehmen", onRecordVideo)
            }
            FilledIconToggleButton(
                checked = set.completed,
                onCheckedChange = { onComplete(it) },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.semantics {
                    stateDescription = if (set.completed) "abgehakt" else "offen"
                },
            ) {
                Icon(Icons.Outlined.Check, contentDescription = "Satz")
            }
        }
    }
}

@Composable
private fun QualityChip(quality: FormQuality?, onClick: () -> Unit) {
    val label = when (quality) {
        null -> "–"
        FormQuality.CLEAN -> "sauber"
        FormQuality.SLOPPY -> "Fehler"
    }
    val color = when (quality) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        FormQuality.CLEAN -> MaterialTheme.colorScheme.primary
        FormQuality.SLOPPY -> MaterialTheme.colorScheme.error
    }
    TextButton(shapes = ButtonDefaults.shapes(), onClick = onClick) {
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
