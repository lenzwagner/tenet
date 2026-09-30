package app.tenet.android.feature.sport.calisthenics

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.tenet.android.core.designsystem.component.VolumeKeyHandler
import app.tenet.android.feature.sport.TimerSnapshot
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.IntervalMath
import app.tenet.android.core.data.CsStrengthInfo
import app.tenet.android.core.data.StrengthStation
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.feature.sport.ExercisePickerSheet
import app.tenet.android.feature.sport.ChangeScopeDialog
import androidx.compose.material.icons.outlined.SwapHoriz
import app.tenet.android.core.database.entity.MeasureType
import app.tenet.android.core.database.entity.SessionMode
import app.tenet.android.feature.sport.CsWorkoutViewModel

/**
 * Circuit/EMOM strength session (App_Konzept.md 5.2.2: "Zirkel- und
 * EMOM-Modus mit Runden- und Intervalltimer"). Completed work phases and
 * minutes are logged automatically by the view model; this screen derives
 * the visible state from the pure [IntervalMath] functions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsWorkoutScreen(
    sessionId: String,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: CsWorkoutViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    var showEndDialog by remember { mutableStateOf(false) }
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    var swapTarget by remember { mutableStateOf<StrengthStation?>(null) }
    var pendingSwap by remember { mutableStateOf<Pair<StrengthStation, Exercise>?>(null) }

    val tone = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    LaunchedEffect(Unit) {
        viewModel.signals.collect {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 300) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TooltipIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück (Timer läuft weiter)", onClick = onBack)
                },
                title = {
                    val mode = state.info?.session?.mode ?: SessionMode.CIRCUIT
                    Text(
                        when (mode) {
                            SessionMode.EMOM -> "EMOM"
                            SessionMode.AMRAP -> "AMRAP"
                            else -> "Zirkel"
                        },
                    )
                },
                subtitle = { Text(state.info?.title.orEmpty()) },
                actions = {
                    FilledIconButton(onClick = { showEndDialog = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Check, contentDescription = "Session beenden")
                    }
                },
            )
        },
    ) { padding ->
        val info = state.info
        if (!state.loaded || info == null) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (info.session.mode == SessionMode.EMOM) {
                    EmomCard(info, timer.elapsedSec, timer.running,
                        viewModel::toggle, viewModel::reset)
                } else if (info.session.mode == SessionMode.AMRAP) {
                    AmrapCard(
                        info = info,
                        timer = timer,
                        onToggle = viewModel::toggle,
                        onRound = viewModel::completeRound,
                        onUndo = viewModel::undoRound,
                    )
                } else {
                    CircuitCard(info, timer.elapsedSec, timer.running,
                        viewModel::toggle, viewModel::reset)
                }
            }
            item { StationList(info, timer.elapsedSec, onSwap = { swapTarget = it }) }
        }

        // ---- Swap a station: catalog, then "nur heute" or "dauerhaft" ----
        swapTarget?.let { station ->
            var suggestions by remember(station) { mutableStateOf<List<Exercise>>(emptyList()) }
            LaunchedEffect(station) { suggestions = viewModel.alternatives(station.exerciseId) }
            ExercisePickerSheet(
                title = "${station.name} tauschen",
                catalog = catalog,
                suggestions = suggestions,
                exclude = info.stations.map { it.exerciseId }.toSet(),
                onDismiss = { swapTarget = null },
                onPick = { exercise ->
                    swapTarget = null
                    if (info.session.plannedWorkoutId != null) {
                        pendingSwap = station to exercise
                    } else {
                        viewModel.swapStation(station.sessionExerciseId, exercise, false)
                    }
                },
            )
        }
        pendingSwap?.let { (station, exercise) ->
            ChangeScopeDialog(
                title = "${station.name} → ${exercise.name}",
                text = "Nur für dieses Training oder dauerhaft in deinem Plan? Bereits erfasste Runden bleiben erhalten.",
                onChoose = { permanent ->
                    pendingSwap = null
                    viewModel.swapStation(station.sessionExerciseId, exercise, permanent)
                },
                onDismiss = { pendingSwap = null },
            )
        }
    }
}

private fun mmss(seconds: Int): String =
    "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

@Composable
private fun CircuitCard(
    info: CsStrengthInfo,
    elapsedSec: Long,
    running: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    val session = info.session
    val state = IntervalMath.circuit(
        elapsedSec = elapsedSec,
        stations = info.stations.size,
        workSec = session.workSec ?: 40,
        restSec = session.restSec ?: 20,
        rounds = session.rounds ?: 4,
    )
    val phaseLen = if (state.phase == IntervalMath.Phase.WORK) {
        session.workSec ?: 40
    } else {
        (session.restSec ?: 20).coerceAtLeast(1)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.done) {
                Text("Zirkel geschafft!", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "${state.rounds} Runden · ${state.totalStations} Übungen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onReset) { Text("Zurücksetzen") }
                }
                return@Column
            }

            Text(
                text = if (state.phase == IntervalMath.Phase.WORK) {
                    "Arbeit · ${info.stations[state.station].name}"
                } else {
                    "Pause — gleich ${info.stations[(state.station + 1) % state.totalStations].name}"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = if (state.phase == IntervalMath.Phase.WORK) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
            Text(
                text = mmss(state.remainingSec),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "Runde ${state.round} / ${state.rounds}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(
                progress = {
                    if (state.done) 1f else {
                        state.remainingSec.toFloat() / phaseLen.toFloat()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = ButtonDefaults.shapes(), onClick = onToggle) {
                    Icon(
                        imageVector = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "Pause" else "Start")
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onReset) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun EmomCard(
    info: CsStrengthInfo,
    elapsedSec: Long,
    running: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    val session = info.session
    val minutes = session.rounds ?: 10
    val interval = session.intervalSec ?: 60
    val state = IntervalMath.emom(elapsedSec, minutes, interval)
    val station = info.stations[((state.round - 1).coerceAtLeast(0)) % info.stations.size]

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.done) {
                Text(
                    "EMOM geschafft!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${state.minutes} Minuten",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onReset) { Text("Zurücksetzen") }
                return@Column
            }

            Text(
                text = "Minute ${state.round} / ${state.minutes}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = mmss(state.remainingSec),
                style = MaterialTheme.typography.displayMedium,
            )
            LinearWavyProgressIndicator(
                progress = { state.remainingSec.toFloat() / interval.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = ButtonDefaults.shapes(), onClick = onToggle) {
                    Icon(
                        imageVector = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "Pause" else "Start")
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onReset) { Text("Reset") }
            }
        }
    }
}

/**
 * AMRAP: time cap counts down, the athlete taps "Runde geschafft" after
 * every full round of the station list.
 */
@Composable
private fun AmrapCard(
    info: CsStrengthInfo,
    timer: TimerSnapshot,
    onToggle: () -> Unit,
    onRound: () -> Unit,
    onUndo: () -> Unit,
) {
    val minutes = info.session.rounds ?: 12
    val state = IntervalMath.amrap(timer.elapsedSec, minutes)
    // Volume key counts a round without looking at the screen.
    val haptics = LocalHapticFeedback.current
    val round = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onRound()
    }
    VolumeKeyHandler(enabled = timer.running) { round() }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (state.done) "Zeit um!" else "So viele Runden wie möglich",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(mmss(state.remainingSec), style = MaterialTheme.typography.displayMedium)
            LinearWavyProgressIndicator(
                progress = { state.remainingSec.toFloat() / (state.minutes * 60f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${timer.amrapRounds}",
                style = MaterialTheme.typography.displayLargeEmphasized,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.semantics { contentDescription = "${timer.amrapRounds} Runden geschafft" },
            )
            Text(
                if (timer.amrapRounds == 1) "Runde" else "Runden",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = round,
                enabled = timer.running || state.done || timer.elapsedSec > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
                Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                Text("Runde geschafft", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
            Text(
                "Lautstärketaste zählt eine Runde, solange der Timer läuft.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.done) {
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onToggle) {
                        Icon(
                            imageVector = if (timer.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (timer.running) "Pause" else if (timer.elapsedSec > 0) "Weiter" else "Start")
                    }
                }
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onUndo,
                    enabled = timer.amrapRounds > 0,
                ) { Text("Runde zurücknehmen") }
            }
        }
    }
}

@Composable
private fun StationList(info: CsStrengthInfo, elapsedSec: Long, onSwap: (StrengthStation) -> Unit) {
    val session = info.session
    val mode = session.mode ?: SessionMode.CIRCUIT
    val currentIndex = when (mode) {
        SessionMode.AMRAP -> -1
        SessionMode.EMOM -> {
            val interval = session.intervalSec ?: 60
            val minute = IntervalMath.completedMinutes(elapsedSec, session.rounds ?: 10, interval)
            (minute % info.stations.size)
        }
        else -> {
            val state = IntervalMath.circuit(
                elapsedSec = elapsedSec,
                stations = info.stations.size,
                workSec = session.workSec ?: 40,
                restSec = session.restSec ?: 20,
                rounds = session.rounds ?: 4,
            )
            if (state.phase == IntervalMath.Phase.WORK) state.station else -1
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Stationen", style = MaterialTheme.typography.titleMedium)
            info.stations.forEachIndexed { index, station ->
                val active = index == currentIndex
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = station.name,
                        style = if (active) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = targetText(station.measureType, station.targetReps),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TooltipIconButton(Icons.Outlined.SwapHoriz, "${station.name} tauschen", { onSwap(station) })
                }
            }
        }
    }
}

private fun targetText(measureType: MeasureType, targetReps: Int): String =
    when (measureType) {
        MeasureType.HOLD, MeasureType.NEGATIVE -> "Ziel $targetReps s"
        else -> "Ziel $targetReps Wdh"
    }
