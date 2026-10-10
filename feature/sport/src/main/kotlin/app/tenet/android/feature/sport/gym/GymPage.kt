package app.tenet.android.feature.sport.gym

import app.tenet.android.feature.sport.PlanSwitchButton
import app.tenet.android.feature.sport.nextTrainingDayLabel
import app.tenet.android.feature.sport.todayState
import app.tenet.android.feature.sport.TodaySessionCard
import app.tenet.android.feature.sport.TodayState
import androidx.compose.material.icons.outlined.CalendarMonth
import app.tenet.android.core.designsystem.component.CardHeader
import androidx.compose.runtime.derivedStateOf
import app.tenet.android.core.designsystem.theme.TenetCard
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
    onOpenSetup: (useTrainingHistory: Boolean) -> Unit = {},
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
    var showPlanSourceDialog by remember { mutableStateOf(false) }

    if (showPlanSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPlanSourceDialog = false },
            title = { Text("Split wechseln") },
            text = { Text("Wie sollen Startgewichte und Kraftwerte für neuen Plan bestimmt werden?") },
            confirmButton = {
                Button(onClick = {
                    showPlanSourceDialog = false
                    onOpenSetup(true)
                }) { Text("Bisherige Trainings verwenden") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPlanSourceDialog = false
                    onOpenSetup(false)
                }) { Text("Komplett neues Setup") }
            },
        )
    }

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
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        showDiscardDialog = false
                        // The live workout notification goes with the session.
                        app.tenet.android.feature.sport.RestTimerService.cancel(context)
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
                    discipline = app.tenet.android.core.database.entity.Discipline.GYM,
                        icon = Icons.Outlined.FitnessCenter,
                        title = "Richte dein Gym-Training ein",
                        body = "Ziel, Trainingstage, Split und deine aktuellen Kraftwerte – daraus erstellt Tenet " +
                            "deinen Plan mit passenden Startgewichten.",
                        onSetup = { onOpenSetup(false) },
                        onSkip = { setupViewModel.skip("GYM") },
                    )
                }
            }
            item(key = "today") {
                TodayCard(
                    overview,
                    onStart = { viewModel.startWorkout() },
                    onOpenPlan = { overview.plan?.id?.let(onOpenPlan) },
                    onFinishStale = { id -> viewModel.finishSession(id) { onOpenSummary(id) } },
                    onDiscardStale = viewModel::discardActiveSession,
                )
            }
            item {
                val plans by viewModel.plans.collectAsStateWithLifecycle()
                RoutineCard(
                    overview = overview,
                    plans = plans,
                    onSwitchPlan = viewModel::switchPlan,
                    onTrainingDays = viewModel::setTrainingDays,
                    onEdit = onOpenRoutineEditor,
                    onOpenLibrary = onOpenLibrary,
                    onOpenPlan = { overview.plan?.id?.let(onOpenPlan) },
                    onSetup = { showPlanSourceDialog = true },
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
                    metricsLoaded = progressState.bodyMetricsLoaded,
                    lastPromptDate = progressState.lastWeightPromptDate,
                    onWeeklyPromptDismiss = viewModel::dismissWeeklyWeightPrompt,
                    onSave = viewModel::saveBodyMetric,
                )
            }
        }

        // Gestures live in the innermost node: as children of the FAB's own
        // clickable they win arbitration (long press must not be cancelled by
        // a consuming parent detector).
        // The today card has its own start button: the FAB only appears once that
        // card is scrolled away (or while a session runs – long press discards it).
        val todayCardVisible by remember {
            derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "today" } }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = hasActiveSession || !todayCardVisible,
            enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = TenetDimens.bottomTabBarPadding + 16.dp),
        ) {
        FloatingActionButton(
                shape = app.tenet.android.core.designsystem.component.tenetFabShape,
            onClick = { if (!showDiscardDialog) viewModel.startWorkout() },
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
}

@Composable
private fun TodayCard(
    overview: GymOverview,
    onStart: () -> Unit,
    onOpenPlan: () -> Unit,
    onFinishStale: (String) -> Unit = {},
    onDiscardStale: () -> Unit = {},
) {
    val plan = overview.plan
    val active = overview.activeSession
    val state = todayState(plan?.trainingDays, active != null, overview.sessions)
    TodaySessionCard(
        icon = Icons.Outlined.FitnessCenter,
        state = state,
        title = overview.mainWorkout?.title ?: plan?.name ?: "Gym",
        subtitle = if (plan == null) "Bibliothek wird geladen …" else "${overview.routine.size} Übungen · ${plan.goal ?: "Kraft"}",
        nextLabel = nextTrainingDayLabel(plan?.trainingDays),
        onClick = onOpenPlan.takeIf { plan != null },
        extra = {
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
        },
    ) {
        if (plan == null) return@TodaySessionCard
        when (state) {
            TodayState.ACTIVE -> Button(shapes = ButtonDefaults.shapes(), onClick = onStart, modifier = Modifier.weight(1f)) {
                Text("Session fortsetzen")
            }
            TodayState.PLANNED -> {
                Button(shapes = ButtonDefaults.shapes(), onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Starten")
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onOpenPlan, modifier = Modifier.weight(1f)) { Text("Details") }
            }
            TodayState.DONE, TodayState.REST -> OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onStart, modifier = Modifier.weight(1f)) {
                Text(if (state == TodayState.DONE) "Noch eine Session" else "Trotzdem trainieren")
            }
        }
    }
}

@Composable
private fun RoutineCard(
    overview: GymOverview,
    plans: List<app.tenet.android.core.database.entity.TrainingPlan>,
    onSwitchPlan: (String) -> Unit,
    onTrainingDays: (String, Set<java.time.DayOfWeek>) -> Unit,
    onEdit: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlan: () -> Unit,
    onSetup: () -> Unit,
) {
    val exerciseNames = overview.exercises.associateBy({ it.id }, { it.name })
    val exercisesById = overview.exercises.associateBy { it.id }
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.CalendarMonth, "Trainingsplan", action = {
                PlanSwitchButton(app.tenet.android.core.database.entity.Discipline.GYM)
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onEdit) { Text("Bearbeiten", maxLines = 1, softWrap = false) }
            })
            overview.plan?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
            overview.plan?.let { plan ->
                TrainingDaysRow(value = plan.trainingDays, onChange = { onTrainingDays(plan.id, it) })
            }
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
            // Switching and creating live on the plans page (own plan, questions or AI coach).
            val openPlans = app.tenet.android.feature.sport.LocalOpenPlans.current
            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { openPlans?.invoke(app.tenet.android.core.database.entity.Discipline.GYM) ?: onSetup() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Plan wechseln oder neu erstellen")
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
                        app.tenet.android.feature.sport.planRestLabel(target.restSec, exercisesById[target.exerciseId], target.targetReps),
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
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.History, "Letzte Trainings", action = if (overview.sessions.size > 5) {
                { TextButton(onClick = onOpenHistory, shapes = ButtonDefaults.shapes()) { Text("Alle anzeigen") } }
            } else {
                null
            })
            if (overview.sessions.isEmpty()) {
                Text(
                    text = "Noch kein abgeschlossenes Training.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val recent = overview.sessions.take(5)
            Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                recent.forEachIndexed { i, session ->
                    val minutes = session.endedAt?.let { (it - session.startedAt) / 60_000L } ?: 0L
                    val volume = overview.volumesBySession[session.id] ?: 0f
                    SegmentedListItem(
                        onClick = { onOpenSession(session.id) },
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(i, recent.size),
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
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(Icons.Outlined.AccessibilityNew, "Sätze pro Muskel", meta = "Diese Woche")
            Text("Ziel 10–20 Sätze für Muskelaufbau", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            muscles.forEach { (muscle, sets) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(muscle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                    app.tenet.android.core.designsystem.component.TenetProgress(
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
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.AutoMirrored.Outlined.TrendingUp, "Fortschritt")

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
                app.tenet.android.core.designsystem.component.TenetProgress(
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
                        app.tenet.android.core.designsystem.component.TenetProgress(
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
