package app.tenet.android.feature.sport.gym

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.WorkoutGuide
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.feature.sport.ExerciseMotion

/**
 * Training mode: one set at a time. Do the set, adjust the prefilled
 * values, "Satz fertig" → the rest counts down (also in the notification)
 * → the next set comes up. Back closes it; the session stays as is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrainingMode(
    next: WorkoutGuide.Next?,
    /** Exercise of [next], for its motion image. */
    exerciseId: String?,
    rest: RestUiState?,
    remaining: Int,
    onLog: (weightKg: Float?, reps: Int?, seconds: Int?) -> Unit,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(Icons.Outlined.Close, "Trainingsmodus schließen", onClose)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Trainingsmodus", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (remaining == 1) "Noch 1 Satz" else "Noch $remaining Sätze",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val phase = when {
                rest != null -> "rest"
                next != null -> "set:${next.set.id}"
                else -> "done"
            }
            AnimatedContent(phase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phase", modifier = Modifier.weight(1f)) { p ->
                when {
                    p == "rest" && rest != null -> RestPhase(rest, next, onAddRest, onSkipRest)
                    p.startsWith("set") && next != null -> SetPhase(next, exerciseId, onLog)
                    else -> DonePhase(onFinish)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestPhase(rest: RestUiState, next: WorkoutGuide.Next?, onAddRest: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pause", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { if (rest.totalSec == 0) 0f else rest.remainingSec.toFloat() / rest.totalSec },
                modifier = Modifier.size(240.dp),
            )
            Text(
                clock(rest.remainingSec),
                style = MaterialTheme.typography.displayLargeEmphasized,
                modifier = Modifier.semantics { contentDescription = "Noch ${rest.remainingSec} Sekunden Pause" },
            )
        }
        Spacer(Modifier.height(24.dp))
        next?.let {
            Text("Als Nächstes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(it.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Satz ${it.number} von ${it.count} · ${it.plannedText}", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onAddRest, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.MediumContentPadding, modifier = Modifier.height(ButtonDefaults.MediumContainerHeight)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("30 s", style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = onSkip, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.MediumContentPadding, modifier = Modifier.height(ButtonDefaults.MediumContainerHeight)) {
                Icon(Icons.Outlined.SkipNext, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Weiter", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetPhase(next: WorkoutGuide.Next, exerciseId: String?, onLog: (Float?, Int?, Int?) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var kg by remember(next.set.id) { mutableFloatStateOf(next.plannedKg) }
    var reps by remember(next.set.id) { mutableIntStateOf(next.plannedReps) }
    var sec by remember(next.set.id) { mutableIntStateOf(next.plannedSec ?: 30) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        exerciseId?.let { ExerciseMotion(it, contentDescription = null, modifier = Modifier.size(160.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(next.name, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        Text(
            (if (next.set.warmup) "Aufwärmsatz · " else "") + "Satz ${next.number} von ${next.count}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(28.dp))
        if (next.timed) {
            Stepper("Sekunden", sec.toString(), onMinus = { sec = (sec - 5).coerceAtLeast(5) }, onPlus = { sec += 5 })
        } else {
            if (!next.bodyweight || kg > 0f) {
                Stepper("kg", WorkoutGuide.fmt(kg), onMinus = { kg = (kg - 2.5f).coerceAtLeast(0f) }, onPlus = { kg += 2.5f })
                Spacer(Modifier.height(16.dp))
            }
            Stepper("Wiederholungen", reps.toString(), onMinus = { reps = (reps - 1).coerceAtLeast(0) }, onPlus = { reps += 1 })
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (next.supersetSwitch) "Supersatz: danach direkt zur nächsten Übung"
                else "Danach ${clock(next.restSec)} Pause (${next.advice.range} empfohlen)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                if (next.timed) onLog(null, null, sec) else onLog(kg, reps, null)
            },
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.LargeContentPadding,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.LargeContainerHeight),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.LargeIconSize))
            Spacer(Modifier.width(ButtonDefaults.LargeIconSpacing))
            Text("Satz fertig", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMinus, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Remove, contentDescription = "$label weniger")
            }
            Text(
                value,
                style = MaterialTheme.typography.displayMediumEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(150.dp),
            )
            FilledTonalIconButton(onClick = onPlus, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "$label mehr")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DonePhase(onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Alle Sätze geschafft", style = MaterialTheme.typography.headlineMediumEmphasized)
        Spacer(Modifier.height(8.dp))
        Text("Stark! Beende das Training, um es zu speichern.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.MediumContentPadding, modifier = Modifier.height(ButtonDefaults.MediumContainerHeight)) {
            Text("Training beenden", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun clock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)
