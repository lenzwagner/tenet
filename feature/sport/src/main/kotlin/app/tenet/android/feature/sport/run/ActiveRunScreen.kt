package app.tenet.android.feature.sport.run

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.RunGuidance
import app.tenet.android.core.designsystem.component.TenetSwitch
import app.tenet.android.core.designsystem.component.TooltipIconButton

/**
 * Live run (App_Konzept.md 5.2.3 "Aktiver Lauf"): setup (planned unit,
 * voice, auto-pause) → big live numbers with map → finish → run detail.
 * Recording itself lives in [RunTrackingService]; this screen only shows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRunScreen(
    plannedWorkoutId: String,
    onBack: () -> Unit,
    onFinished: (sessionId: String) -> Unit,
    viewModel: ActiveRunViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(plannedWorkoutId) { viewModel.load(plannedWorkoutId) }
    val live by RunTrackingService.state.collectAsStateWithLifecycle()
    val planned by viewModel.planned.collectAsStateWithLifecycle()
    var voice by rememberSaveable { mutableStateOf(true) }
    var autoPause by rememberSaveable { mutableStateOf(true) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }

    fun startService() {
        val p = planned
        RunTrackingService.start(
            context,
            plannedWorkoutId = p?.id,
            intervalsJson = p?.intervalsJson,
            targetPaceSecPerKm = p?.targetPaceSecPerKm,
            voice = voice,
            autoPause = autoPause,
            tempoSec = p?.tempoSec ?: 0,
            targetDistanceM = p?.targetDistanceM,
        )
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startService()
        } else {
            snackbar?.show("Ohne genauen Standort kann kein Lauf aufgezeichnet werden.")
        }
    }
    fun requestStart() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine) startService() else permissions.launch(needed.toTypedArray())
    }

    // Finished → detail (or back when the run was too short).
    LaunchedEffect(live?.status, live?.savedSessionId) {
        val s = live ?: return@LaunchedEffect
        if (s.status == RunStatus.FINISHED) {
            val saved = s.savedSessionId
            RunTrackingService.clear()
            if (saved != null) {
                onFinished(saved)
            } else {
                snackbar?.show("Lauf zu kurz – nicht gespeichert.")
                onBack()
            }
        }
    }

    val recording = live != null && live?.status != RunStatus.FINISHED
    // Back keeps the run going in the background.
    BackHandler { onBack() }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            icon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
            title = { Text("Lauf beenden?") },
            text = { Text("Der Lauf wird gespeichert und ausgewertet.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmStop = false
                        RunTrackingService.stop(context)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Beenden") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }, shapes = ButtonDefaults.shapes()) { Text("Weiterlaufen") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TooltipIconButton(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        if (recording) "Zurück (Lauf läuft weiter)" else "Zurück",
                        onBack,
                    )
                },
                title = { Text(if (recording) "Lauf" else "Lauf starten") },
                subtitle = { planned?.let { Text(it.title) } },
            )
        },
    ) { padding ->
        val s = live
        if (s == null) {
            SetupContent(
                planned = planned,
                voice = voice,
                onVoice = { voice = it },
                autoPause = autoPause,
                onAutoPause = { autoPause = it },
                onSkipPlanned = viewModel::skipPlanned,
                onStart = ::requestStart,
                modifier = Modifier.padding(padding),
            )
        } else {
            LiveContent(
                state = s,
                onPause = { RunTrackingService.pause(context) },
                onResume = { RunTrackingService.resume(context) },
                onStop = { confirmStop = true },
                onToggleVoice = { RunTrackingService.toggleVoice(context) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SetupContent(
    planned: PlannedRunInfo?,
    voice: Boolean,
    onVoice: (Boolean) -> Unit,
    autoPause: Boolean,
    onAutoPause: (Boolean) -> Unit,
    onSkipPlanned: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (planned != null) {
            TenetCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Heute geplant", style = MaterialTheme.typography.labelLarge)
                    Text(planned.title, style = MaterialTheme.typography.headlineSmall)
                    val phases = RunGuidance.parseIntervals(planned.intervalsJson, planned.targetPaceSecPerKm)
                        .ifEmpty { RunGuidance.tempoPhases(planned.tempoSec, planned.targetPaceSecPerKm, 600) }
                    val target = listOfNotNull(
                        planned.targetDistanceM?.takeIf { phases.isEmpty() }?.let { "%.1f km".format(java.util.Locale.GERMAN, it / 1000f) },
                        planned.targetDurationSec?.let { "${it / 60} min" },
                        planned.targetPaceSecPerKm?.let { "Ziel ${formatPaceDe(it)} /km" },
                    ).joinToString(" · ")
                    if (target.isNotEmpty()) Text(target, style = MaterialTheme.typography.bodyMedium)
                    if (phases.isNotEmpty()) {
                        Text(
                            "Geführtes Training: 10 min Einlaufen, dann sagt die App jedes Intervall und jede Pause an und vibriert beim Wechsel.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = onSkipPlanned, shapes = ButtonDefaults.shapes()) { Text("Freier Lauf stattdessen") }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
            SegmentedListItem(
                colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                checked = voice,
                onCheckedChange = onVoice,
                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(0, 2),
                leadingContent = { Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null) },
                supportingContent = { Text("Kilometer-Ansagen und Intervall-Kommandos") },
                trailingContent = { TenetSwitch(checked = voice, onCheckedChange = null) },
            ) { Text("Sprachansagen") }
            SegmentedListItem(
                colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                checked = autoPause,
                onCheckedChange = onAutoPause,
                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(1, 2),
                leadingContent = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                supportingContent = { Text("Pausiert automatisch, wenn du stehen bleibst") },
                trailingContent = { TenetSwitch(checked = autoPause, onCheckedChange = null) },
            ) { Text("Auto-Pause") }
        }
        Text(
            "Die Aufzeichnung läuft auch bei gesperrtem Bildschirm weiter. Tipp: Warte kurz unter freiem Himmel, bis GPS gefunden ist.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStart,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.LargeContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
        ) {
            Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, modifier = Modifier.size(ButtonDefaults.LargeIconSize))
            Spacer(Modifier.width(ButtonDefaults.LargeIconSpacing))
            Text("Los", style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
        }
    }
}

@Composable
private fun LiveContent(
    state: RunLiveState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val paused = state.status == RunStatus.PAUSED || state.status == RunStatus.AUTO_PAUSED
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status line: GPS quality / pause.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val acc = state.gpsAccuracyM
            val good = acc != null && acc <= 20f
            Icon(
                if (good) Icons.Outlined.GpsFixed else Icons.Outlined.GpsNotFixed,
                contentDescription = null,
                tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    state.status == RunStatus.STARTING -> "Starte …"
                    state.status == RunStatus.SAVING -> "Speichere …"
                    state.status == RunStatus.AUTO_PAUSED -> "Auto-Pause – lauf einfach weiter"
                    state.status == RunStatus.PAUSED -> "Pausiert"
                    acc == null -> "GPS wird gesucht …"
                    else -> "GPS ± ${acc.toInt()} m"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.interval?.let { interval ->
            Surface(
                color = if (interval.isRest) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (interval.isRest) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(interval.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(interval.remaining, style = MaterialTheme.typography.headlineMediumEmphasized)
                }
            }
        }

        // Big numbers.
        Text(
            formatDuration((state.movingMs / 1000).toInt()),
            style = MaterialTheme.typography.displayLargeEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Zeit ${RunGuidance.spokenDuration((state.movingMs / 1000).toInt())}" },
        )
        Row(Modifier.fillMaxWidth()) {
            BigStat(formatKmDe(state.distanceM), "km", Modifier.weight(1f))
            BigStat(state.currentPaceSecPerKm?.let(::formatPaceDe) ?: "–:–", "Pace /km", Modifier.weight(1f))
            BigStat(state.avgPaceSecPerKm?.let(::formatPaceDe) ?: "–:–", "Ø Pace", Modifier.weight(1f))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(MaterialTheme.shapes.extraLarge),
        ) {
            RouteMap(route = state.route, follow = true, modifier = Modifier.fillMaxSize())
            if (state.route.isEmpty()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
        }

        // Floating toolbar (M3 Expressive): voice, pause/resume, stop as FAB.
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStop()
                    },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) { Icon(Icons.Outlined.Stop, contentDescription = "Lauf beenden") }
            },
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp),
        ) {
            IconToggleButton(
                checked = state.voice,
                onCheckedChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onToggleVoice()
                },
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    if (state.voice) Icons.Outlined.RecordVoiceOver else Icons.Outlined.VoiceOverOff,
                    contentDescription = if (state.voice) "Sprachansagen aus" else "Sprachansagen an",
                )
            }
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (paused) onResume() else onPause()
                },
                enabled = state.status == RunStatus.RUNNING || paused,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(56.dp, 48.dp),
            ) {
                Icon(
                    if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (paused) "Weiter" else "Pause",
                )
            }
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMediumEmphasized)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
