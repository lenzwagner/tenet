package app.tenet.android.feature.sport.gym

import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import app.tenet.android.core.designsystem.component.SegmentedRows
import app.tenet.android.core.designsystem.component.TooltipIconButton
import androidx.compose.material.icons.outlined.RestartAlt
import app.tenet.android.feature.sport.setup.SportSetupViewModel
import app.tenet.android.feature.sport.setup.SetupIntroCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.common.TrainingDays
import app.tenet.android.feature.sport.TrainingDaysRow
import app.tenet.android.feature.sport.trainingDayStatus
import java.util.Locale
import app.tenet.android.core.designsystem.navigation.rememberReselectListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.data.GymOverview
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.feature.sport.GymViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Weekly training goal: how many sessions per week the user aims for. */
private const val WEEKLY_SESSION_GOAL = 3

/**
 * Gym discipline page: today's plan, routine, recent sessions, progress
 * (App_Konzept.md 5.2.1 structure "Heute geplant -> Trainingsplan ->
 * Letzte Sessions -> Fortschritt").
 */
@Composable
fun GymPage(
    onOpenSession: (String) -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenRoutineEditor: () -> Unit = {},
    onOpenPlan: (String) -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onOpenSummary: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: GymViewModel = hiltViewModel(),
    setupViewModel: SportSetupViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val muscleWeek by viewModel.muscleWeek.collectAsStateWithLifecycle()
    val setupDone by setupViewModel.done.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()

    // The play FAB below the list starts or resumes the workout; long press
    // discards the active session.
    val hasActiveSession = overview.activeSession != null
    var showDiscardDialog by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Session verwerfen?") },
            text = {
                Text(
                    "Die laufende Session und alle erfassten Sätze werden gelöscht. " +
                        "Abgeschlossene Sessions bleiben erhalten.",
                )
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardActiveSession()
                    },
                ) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showDiscardDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.sessionEvents.collect { sessionId -> onOpenSession(sessionId) }
    }

    Box(Modifier.fillMaxSize()) {
        val listState = rememberReselectListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = TenetDimens.bottomFabPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (setupDone?.contains("GYM") == false) {
                item(key = "setup") {
                    SetupIntroCard(
                        icon = Icons.Outlined.FitnessCenter,
                        title = "Richte dein Gym-Training ein",
                        body = "Ziel, Trainingstage, Split und deine aktuellen Kraftwerte – daraus erstellt Tenet " +
                            "deinen Plan mit passenden Startgewichten.",
                        onSetup = onOpenSetup,
                        onSkip = { setupViewModel.skip("GYM") },
                    )
                }
            }
            item {
                TodayCard(
                    overview,
                    onStart = { viewModel.startWorkout() },
                    onTrainingDays = viewModel::setTrainingDays,
                    onFinishStale = { id -> viewModel.finishSession(id) { onOpenSummary(id) } },
                    onDiscardStale = viewModel::discardActiveSession,
                )
            }
            item {
                RoutineCard(
                    overview = overview,
                    onEdit = onOpenRoutineEditor,
                    onOpenLibrary = onOpenLibrary,
                    onOpenPlan = { overview.plan?.id?.let(onOpenPlan) },
                    onSetup = onOpenSetup,
                )
            }
            item { SessionsCard(overview, onOpenSession = onOpenSummary, onOpenHistory = onOpenHistory) }
            item { MuscleWeekCard(muscleWeek) }
            item { PrBadgeCard(personalBests = progressState.personalBests) }
            item { ProgressCard(overview) }
            item {
                OneRmHistoryCard(
                    sets = progressState.sets,
                    exercises = progressState.exercises,
                    formula = progressState.formula,
                )
            }
            item {
                VolumeHistoryCard(
                    sets = progressState.sets,
                    muscleGroups = progressState.muscleGroups,
                )
            }
            item {
                BodyMetricCard(
                    metrics = progressState.bodyMetrics,
                    onSave = viewModel::saveBodyMetric,
                )
            }
        }

        // Gestures live in the innermost node: as children of the FAB's own
        // clickable they win arbitration (long press must not be cancelled by
        // a consuming parent detector).
        FloatingActionButton(
            onClick = { if (!showDiscardDialog) viewModel.startWorkout() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = TenetDimens.bottomTabBarPadding + 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .combinedClickable(
                        onClick = { if (!showDiscardDialog) viewModel.startWorkout() },
                        onLongClick = if (hasActiveSession) {
                            { showDiscardDialog = true }
                        } else {
                            null
                        },
                        onLongClickLabel = "Session verwerfen",
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = if (hasActiveSession) {
                        "Session fortsetzen"
                    } else {
                        "Workout starten"
                    },
                )
            }
        }
    }
}

@Composable
private fun TodayCard(
    overview: GymOverview,
    onStart: () -> Unit,
    onTrainingDays: (String, Set<java.time.DayOfWeek>) -> Unit,
    onFinishStale: (String) -> Unit = {},
    onDiscardStale: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (trainingDayStatus(TrainingDays.parse(overview.plan?.trainingDays)) != null) "Heute Ruhetag" else "Heute geplant",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val plan = overview.plan
            if (plan == null) {
                Text(
                    text = "Bibliothek wird geladen …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                text = overview.mainWorkout?.title ?: plan.name,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = trainingDayStatus(TrainingDays.parse(plan.trainingDays))
                    ?: "${overview.routine.size} Übungen · ${plan.goal ?: "Kraft"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrainingDaysRow(value = plan.trainingDays, onChange = { onTrainingDays(plan.id, it) })
            val active = overview.activeSession
            val hasActive = active != null
            // Forgot to finish? A session open for hours gets an explicit choice.
            if (active != null && System.currentTimeMillis() - active.startedAt > 4 * 60 * 60_000L) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Das Training vom ${formatDate(active.startedAt)} ist noch offen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(shapes = ButtonDefaults.shapes(), onClick = { onFinishStale(active.id) }) { Text("Beenden") }
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDiscardStale) { Text("Verwerfen") }
                        }
                    }
                }
            }
            if (hasActive) {
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Session fortsetzen")
                }
            } else {
                Button(shapes = ButtonDefaults.shapes(), onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Workout starten")
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    overview: GymOverview,
    onEdit: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlan: () -> Unit,
    onSetup: () -> Unit,
) {
    val exerciseNames = overview.exercises.associateBy({ it.id }, { it.name })
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trainingsplan", style = MaterialTheme.typography.titleMedium)
                    // Rotating split: which unit is next, and what follows.
                    if (overview.workouts.size > 1) {
                        val next = overview.mainWorkout
                        val after = overview.workouts.getOrNull((overview.workouts.indexOf(next) + 1) % overview.workouts.size)
                        Text(
                            "Als Nächstes: ${next?.title} · danach ${after?.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TooltipIconButton(Icons.Outlined.RestartAlt, "Plan neu einrichten", onSetup)
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onEdit) { Text("Bearbeiten") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onOpenPlan,
                    modifier = Modifier.weight(1f),
                ) { Text("Fortschritt") }
                OutlinedButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                ) { Text("Bibliothek") }
            }
            if (overview.routine.isEmpty()) {
                Text(
                    text = "Noch keine Übungen im Plan. Füge welche aus der Bibliothek hinzu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            // Plan as an M3 segmented group instead of divider lines.
            SegmentedRows(count = overview.routine.size) { index ->
                val target = overview.routine[index]
                Text(
                    text = exerciseNames[target.exerciseId] ?: target.exerciseId,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${target.targetSets} × ${target.targetReps} · " +
                        "Pause ${target.restSec / 60}:" + (target.restSec % 60).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionsCard(overview: GymOverview, onOpenSession: (String) -> Unit, onOpenHistory: () -> Unit) {
    val titles = overview.workouts.associate { it.id to it.title }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Letzte Trainings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (overview.sessions.size > 5) {
                    TextButton(onClick = onOpenHistory, shapes = ButtonDefaults.shapes()) { Text("Alle anzeigen") }
                }
            }
            if (overview.sessions.isEmpty()) {
                Text(
                    text = "Noch kein abgeschlossenes Training.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val recent = overview.sessions.take(5)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                recent.forEachIndexed { i, session ->
                    val minutes = session.endedAt?.let { (it - session.startedAt) / 60_000L } ?: 0L
                    val volume = overview.volumesBySession[session.id] ?: 0f
                    SegmentedListItem(
                        onClick = { onOpenSession(session.id) },
                        shapes = ListItemDefaults.segmentedShapes(i, recent.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surface),
                        supportingContent = { Text(formatDate(session.startedAt)) },
                        trailingContent = {
                            Text(
                                "$minutes min · ${if (volume >= 1000f) String.format(java.util.Locale.GERMAN, "%.1f t", volume / 1000f) else "${volume.toInt()} kg"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) { Text(session.plannedWorkoutId?.let { titles[it] } ?: "Training") }
                }
            }
        }
    }
}

/** Weekly working sets per muscle group, with the usual 10–20 sets target band. */
@Composable
private fun MuscleWeekCard(muscles: List<Pair<String, Int>>) {
    if (muscles.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sätze pro Muskel", style = MaterialTheme.typography.titleMedium)
                    Text("Diese Woche · Ziel 10–20 Sätze für Muskelaufbau", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            muscles.forEach { (muscle, sets) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(muscle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                    LinearWavyProgressIndicator(
                        progress = { (sets / 20f).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                        color = if (sets >= 10) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("$sets", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(overview: GymOverview) {
    val now = Instant.now()
    val sessionsThisWeek = overview.sessions.count { session ->
        Instant.ofEpochMilli(session.startedAt).isAfter(now.minus(java.time.Duration.ofDays(7)))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Fortschritt", style = MaterialTheme.typography.titleMedium)
            }

            // Weekly training goal as an MD3 progress bar.
            val weekOver = sessionsThisWeek >= WEEKLY_SESSION_GOAL
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Wochenziel: $WEEKLY_SESSION_GOAL Einheiten",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$sessionsThisWeek / $WEEKLY_SESSION_GOAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (weekOver) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearWavyProgressIndicator(
                    progress = { (sessionsThisWeek.toFloat() / WEEKLY_SESSION_GOAL).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (weekOver) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
            }

            if (overview.bestLifts.isEmpty()) {
                Text(
                    text = "Sobald Sätze abgehakt sind, siehst du hier deinen geschätzten 1RM (Epley).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val maxLift = overview.bestLifts.maxOf { it.oneRepMax }
                overview.bestLifts.forEach { lift ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                lift.name,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${lift.oneRepMax.toInt()} kg",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        // 1RM shown relative to the strongest lift of the plan.
                        val fraction = if (maxLift <= 0f) 0f else (lift.oneRepMax / maxLift).coerceIn(0f, 1f)
                        LinearWavyProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN))
}.getOrDefault("")
