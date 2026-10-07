package app.tenet.android.feature.sport.calisthenics

import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AccountTree
import app.tenet.android.core.designsystem.component.CardHeader
import androidx.compose.runtime.derivedStateOf
import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.FilledTonalButton
import app.tenet.android.core.database.entity.MeasureType
import app.tenet.android.core.designsystem.component.SegmentedRows
import androidx.compose.material.icons.outlined.RestartAlt
import app.tenet.android.feature.sport.setup.SportSetupViewModel
import app.tenet.android.feature.sport.setup.SetupIntroCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.rememberSheetState
import app.tenet.android.core.common.TrainingDays
import app.tenet.android.feature.sport.TrainingDaysRow
import app.tenet.android.feature.sport.trainingDayStatus
import java.util.Locale
import app.tenet.android.core.designsystem.navigation.rememberReselectListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ButtonDefaults
import app.tenet.android.core.designsystem.component.TooltipIconButton
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import app.tenet.android.feature.sport.SkillCardUi
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.data.CalisthenicsOverview
import app.tenet.android.core.database.entity.CriterionType
import app.tenet.android.core.database.entity.SessionMode
import app.tenet.android.core.database.entity.SkillStep
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.feature.sport.CalisthenicsUiState
import app.tenet.android.feature.sport.CalisthenicsViewModel
import app.tenet.android.feature.sport.CsNavKind
import app.tenet.android.feature.sport.StepState
import app.tenet.android.feature.sport.StepUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Calisthenics discipline page: today's plan, the skill tree with promotion
 * flow, plan, recent sessions and promotion timeline (App_Konzept.md 5.2.2).
 */
@Composable
fun CalisthenicsPage(
    onOpenSkillSession: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenSetup: () -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
    onOpenSummary: (String) -> Unit = {},
    onOpenPlan: () -> Unit = {},
    viewModel: CalisthenicsViewModel = hiltViewModel(),
    setupViewModel: SportSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formVideos by viewModel.formVideos.collectAsStateWithLifecycle()
    val setupDone by setupViewModel.done.collectAsStateWithLifecycle()
    val overview = uiState.overview

    // The play FAB below the list starts or resumes the session; long press
    // discards the active session.
    val hasActiveSession = overview.activeSession != null
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showPromotionDialog by remember { mutableStateOf(false) }
    var showStrengthSheet by remember { mutableStateOf(false) }
    var strengthMode by remember { mutableStateOf(SessionMode.SETS) }
    var circuitRounds by remember { mutableIntStateOf(4) }
    var circuitWork by remember { mutableIntStateOf(40) }
    var circuitRest by remember { mutableIntStateOf(20) }
    var emomMinutes by remember { mutableIntStateOf(10) }
    var emomInterval by remember { mutableIntStateOf(60) }
    var amrapMinutes by remember { mutableIntStateOf(12) }


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
                        SkillLiveNotification.cancel(context)
                        viewModel.discardActiveSession()
                    },
                ) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showDiscardDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    if (showPromotionDialog) {
        val current = uiState.currentStep
        val next = uiState.nextStep
        if (current != null && next != null) {
            AlertDialog(
                onDismissRequest = { showPromotionDialog = false },
                title = { Text("Aufstieg bestätigen") },
                text = {
                    Text(
                        "Kriterium in zwei Sessions erfüllt. Neue Stufe: " +
                            "„${next.label}“ (Ziel ${criterionText(next)}).",
                    )
                },
                confirmButton = {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            showPromotionDialog = false
                            viewModel.confirmPromotion()
                        },
                    ) { Text("Aufsteigen") }
                },
                dismissButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { showPromotionDialog = false }) { Text("Später") }
                },
            )
        } else {
            showPromotionDialog = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sessionEvents.collect { event ->
            when (event.kind) {
                CsNavKind.SKILL -> onOpenSkillSession(event.sessionId)
                CsNavKind.STRENGTH_SETS -> onOpenSession(event.sessionId)
                CsNavKind.STRENGTH_INTERVAL -> onOpenWorkout(event.sessionId)
            }
        }
    }

    if (showStrengthSheet) {
        StrengthSheet(
            mode = strengthMode,
            onModeChange = { strengthMode = it },
            circuitRounds = circuitRounds,
            onCircuitRounds = { circuitRounds = it },
            circuitWork = circuitWork,
            onCircuitWork = { circuitWork = it },
            circuitRest = circuitRest,
            onCircuitRest = { circuitRest = it },
            emomMinutes = emomMinutes,
            onEmomMinutes = { emomMinutes = it },
            emomInterval = emomInterval,
            onEmomInterval = { emomInterval = it },
            amrapMinutes = amrapMinutes,
            onAmrapMinutes = { amrapMinutes = it },
            onStart = {
                showStrengthSheet = false
                val mode = strengthMode
                viewModel.startStrengthSession(
                    mode = mode,
                    rounds = when (mode) {
                        SessionMode.CIRCUIT -> circuitRounds
                        SessionMode.EMOM -> emomMinutes
                        SessionMode.AMRAP -> amrapMinutes
                        SessionMode.SETS -> null
                    },
                    workSec = if (mode == SessionMode.CIRCUIT) circuitWork else null,
                    restSec = if (mode == SessionMode.CIRCUIT) circuitRest else null,
                    intervalSec = if (mode == SessionMode.EMOM) emomInterval else null,
                )
            },
            onDismiss = { showStrengthSheet = false },
        )
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
            if (setupDone?.contains("CALISTHENICS") == false) {
                item(key = "setup") {
                    SetupIntroCard(
                        icon = Icons.Outlined.SelfImprovement,
                        title = "Richte dein Calisthenics-Training ein",
                        body = "Ein kurzer Max-Test, deine Skill-Stufen und Trainingstage – daraus stellt Tenet " +
                            "deinen Skill- und Kraftblock zusammen.",
                        onSetup = onOpenSetup,
                        onSkip = { setupViewModel.skip("CALISTHENICS") },
                    )
                }
            }
            item(key = "today") {
                TodayCard(
                    uiState,
                    onTrainingDays = viewModel::setTrainingDays,
                    onResume = viewModel::resumeSession,
                    onStartSkill = viewModel::startSkillSession,
                    onStartStrength = { showStrengthSheet = true },
                )
            }
            item {
                SkillTreeCard(
                    uiState = uiState,
                    onSelectSkill = viewModel::selectSkill,
                    onPromoteRequest = { showPromotionDialog = true },
                    onOpenExercise = onOpenExercise,
                    formVideos = formVideos,
                    onDeleteVideo = viewModel::deleteFormVideo,
                )
            }
            item { PlanCard(overview, onSetup = onOpenSetup, onOpenPlan = onOpenPlan, onOpenExercise = onOpenExercise) }
            item { SessionsCard(overview, onOpenSummary) }
            item { TimelineCard(uiState) }
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
            onClick = { if (!showDiscardDialog) viewModel.startOrResume() },
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .combinedClickable(
                        onClick = { if (!showDiscardDialog) viewModel.startOrResume() },
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
                        "Skill trainieren"
                    },
                )
            }
        }
        }
    }
}

private fun criterionText(step: SkillStep): String =
    if (step.criterionType == CriterionType.HOLD) {
        "${step.criterionSets} × ${step.criterionValue} s"
    } else {
        "${step.criterionSets} × ${step.criterionValue} Wdh"
    }

@Composable
private fun TodayCard(
    uiState: CalisthenicsUiState,
    onTrainingDays: (String, Set<java.time.DayOfWeek>) -> Unit,
    onResume: () -> Unit,
    onStartSkill: () -> Unit,
    onStartStrength: () -> Unit,
) {
    val overview = uiState.overview
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.SelfImprovement, if (trainingDayStatus(TrainingDays.parse(overview.plan?.trainingDays)) != null) "Heute Ruhetag" else "Heute geplant")
            if (overview.plan == null) {
                Text(
                    text = "Skill-Tree wird geladen …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val skillName = overview.skills
                .firstOrNull { it.id == uiState.selectedSkillId }?.name
            Text(
                text = skillName ?: "Skill-Tree",
                style = MaterialTheme.typography.headlineSmall,
            )
            val current = uiState.currentStep
            Text(
                text = if (current == null) {
                    "Keine Stufen vorhanden"
                } else {
                    "Aktuelle Stufe: ${current.label} · Ziel ${criterionText(current)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            overview.plan?.let { plan ->
                trainingDayStatus(TrainingDays.parse(plan.trainingDays))?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                TrainingDaysRow(value = plan.trainingDays, onChange = { onTrainingDays(plan.id, it) })
            }
            overview.mainWorkout?.let { workout ->
                Text(
                    text = "${workout.title} · ${overview.routine.size} Übungen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (overview.activeSession != null) {
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text("Session fortsetzen")
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onStartSkill,
                        modifier = Modifier.weight(1f),
                    ) { Text("Skill trainieren") }
                    // One filled main action; the second is tonal (M3 emphasis).
                    FilledTonalButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onStartStrength,
                        modifier = Modifier.weight(1f),
                    ) { Text("Kraft-Block") }
                }
            }
        }
    }
}

@Composable
private fun SkillTreeCard(
    uiState: CalisthenicsUiState,
    onSelectSkill: (String) -> Unit,
    onPromoteRequest: () -> Unit,
    onOpenExercise: (String) -> Unit,
    formVideos: List<app.tenet.android.core.database.entity.FormVideo> = emptyList(),
    onDeleteVideo: (app.tenet.android.core.database.entity.FormVideo) -> Unit = {},
) {
    val overview = uiState.overview
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.AccountTree, "Skill-Tree")

            if (overview.skills.isEmpty()) {
                Text(
                    text = "Skills werden geladen …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            SkillCarousel(uiState.skillCards, uiState.selectedSkillId, onSelectSkill)

            if (uiState.suggestPromotion) {
                val current = uiState.currentStep
                val next = uiState.nextStep
                if (current != null && next != null) {
                    TenetCard(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Aufstieg empfohlen: ${current.label} → ${next.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = onPromoteRequest) { Text("Bestätigen") }
                        }
                    }
                }
            }

            uiState.ladder.forEach { row -> LadderRow(row, onClick = { onOpenExercise(row.step.exerciseId) }) }

            // Technique over the weeks: tap to play, "Vergleichen" for two side by side.
            if (formVideos.isNotEmpty()) {
                val stepLabels = overview.steps.associate { it.id to it.label }
                Text("Formvideos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FormVideoStrip(
                    videos = formVideos,
                    caption = { v -> formatVideoDate(v) + (stepLabels[v.stepId]?.let { " · $it" } ?: "") },
                    onDelete = onDeleteVideo,
                )
            }
        }
    }
}

/** Skills as an M3 multi-browse carousel: current step and progress to the next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillCarousel(cards: List<SkillCardUi>, selectedId: String?, onSelect: (String) -> Unit) {
    if (cards.isEmpty()) return
    val state = rememberCarouselState { cards.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 200.dp,
        itemSpacing = 8.dp,
        modifier = Modifier.fillMaxWidth().height(148.dp),
    ) { i ->
        val card = cards[i]
        val selected = card.skillId == selectedId
        val colors = MaterialTheme.colorScheme
        Surface(
            onClick = { onSelect(card.skillId) },
            color = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
            modifier = Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge),
        ) {
            // Squeezed neighbours showed cut-off words ("dstand", "Klimm:"): keep the
            // text at the visible edge and fade it out as the item gets narrow.
            val info = carouselItemDrawInfo
            Column(
                Modifier
                    .graphicsLayer {
                        val range = info.maxSize - info.minSize
                        val open = if (range <= 0f) 1f else ((info.size - info.minSize) / range).coerceIn(0f, 1f)
                        alpha = (open * 1.6f - 0.6f).coerceIn(0f, 1f)
                        translationX = info.maskRect.left
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                )
                Text(
                    "Stufe ${card.stepNumber}/${card.stepCount} · ${card.stepLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                LinearWavyProgressIndicator(progress = { card.criterionProgress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${card.bestText ?: "–"} / Ziel ${card.targetText}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LadderRow(row: StepUi, onClick: () -> Unit) {
    val isCurrent = row.state == StepState.CURRENT
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (row.state) {
            StepState.MASTERED -> Icons.Outlined.CheckCircle
            StepState.CURRENT -> Icons.Outlined.RadioButtonChecked
            StepState.LOCKED -> Icons.Outlined.Lock
        }
        Icon(
            imageVector = icon,
            contentDescription = when (row.state) {
                StepState.MASTERED -> "Gemeistert"
                StepState.CURRENT -> "Aktuelle Stufe"
                StepState.LOCKED -> "Gesperrt"
            },
            tint = when (row.state) {
                StepState.MASTERED -> MaterialTheme.colorScheme.primary
                StepState.CURRENT -> MaterialTheme.colorScheme.secondary
                StepState.LOCKED -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = row.step.label,
            style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (row.state == StepState.LOCKED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        val detail = when (row.state) {
            StepState.MASTERED -> row.bestText ?: "geschafft"
            StepState.CURRENT -> {
                val best = row.bestText ?: "–"
                "$best / Ziel ${criterionText(row.step)}"
            }
            StepState.LOCKED -> ""
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanCard(
    overview: CalisthenicsOverview,
    onSetup: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val exerciseNames = overview.exercises.associateBy({ it.id }, { it.name })
    TenetCard(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.CalendarMonth, "Trainingsplan", meta = "Kraft-Block", action = {
                TooltipIconButton(Icons.Outlined.RestartAlt, "Plan neu einrichten", onSetup)
                TooltipIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Plan-Details", onOpenPlan)
            })
            if (overview.routine.isEmpty()) {
                Text(
                    text = "Noch keine Übungen im Plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            // Plan as an M3 segmented group instead of divider lines.
            SegmentedRows(count = overview.routine.size, onClick = { onOpenExercise(overview.routine[it].exerciseId) }) { index ->
                val target = overview.routine[index]
                val exercise = overview.exercises.firstOrNull { it.id == target.exerciseId }
                val timed = exercise?.measureType?.let { it == MeasureType.HOLD || it == MeasureType.DURATION } == true
                Text(
                    text = exerciseNames[target.exerciseId] ?: target.exerciseId,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${target.targetSets} × ${target.targetReps}${if (timed) " s" else ""} · " +
                        app.tenet.android.feature.sport.planRestLabel(target.restSec, exercise, target.targetReps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionsCard(overview: CalisthenicsOverview, onOpenSummary: (String) -> Unit) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.History, "Letzte Sessions")
            if (overview.sessions.isEmpty()) {
                Text(
                    text = "Noch keine abgeschlossene Session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val shown = overview.sessions.take(5)
            SegmentedRows(count = shown.size, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, onClick = { onOpenSummary(shown[it].id) }) { i ->
                val session = shown[i]
                val minutes = session.endedAt
                    ?.let { (it - session.startedAt) / 60_000L } ?: 0L
                Text(
                    text = formatDate(session.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$minutes min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(uiState: CalisthenicsUiState) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.MilitaryTech, "Stufenaufstiege")
            if (uiState.timeline.isEmpty()) {
                Text(
                    text = "Erfülle ein Aufstiegskriterium in zwei Sessions, " +
                        "um hier deinen Zeitstrahl zu sehen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            uiState.timeline.take(8).forEach { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = "${entry.skillName} · ${entry.stepLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDate(entry.achievedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Format picker for the strength block (App_Konzept.md 5.2.2: "Formate:
 * klassische Sätze, Zirkel, EMOM").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrengthSheet(
    mode: SessionMode,
    onModeChange: (SessionMode) -> Unit,
    circuitRounds: Int,
    onCircuitRounds: (Int) -> Unit,
    circuitWork: Int,
    onCircuitWork: (Int) -> Unit,
    circuitRest: Int,
    onCircuitRest: (Int) -> Unit,
    emomMinutes: Int,
    onEmomMinutes: (Int) -> Unit,
    emomInterval: Int,
    onEmomInterval: (Int) -> Unit,
    amrapMinutes: Int,
    onAmrapMinutes: (Int) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberSheetState(),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Kraft-Block starten", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionMode.entries.forEach { modeOption ->
                    FilterChip(
                        selected = mode == modeOption,
                        onClick = { onModeChange(modeOption) },
                        label = {
                            Text(
                                when (modeOption) {
                                    SessionMode.SETS -> "Sätze"
                                    SessionMode.CIRCUIT -> "Zirkel"
                                    SessionMode.EMOM -> "EMOM"
                                    SessionMode.AMRAP -> "AMRAP"
                                },
                            )
                        },
                    )
                }
            }
            when (mode) {
                SessionMode.SETS -> Text(
                    text = "Klassische Sätze mit automatischer Pausenuhr – " +
                        "wie im Gym.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SessionMode.CIRCUIT -> {
                    StepperRow("Runden", circuitRounds, 1..10, 1, onCircuitRounds)
                    StepperRow("Arbeit (s)", circuitWork, 10..120, 10, onCircuitWork)
                    StepperRow("Pause (s)", circuitRest, 0..60, 5, onCircuitRest)
                }
                SessionMode.EMOM -> {
                    StepperRow("Minuten", emomMinutes, 1..60, 1, onEmomMinutes)
                    StepperRow("Intervall (s)", emomInterval, 15..120, 15, onEmomInterval)
                }
                SessionMode.AMRAP -> {
                    Text(
                        text = "So viele Runden wie möglich in der Zeit. Nach jeder vollen Runde " +
                            "auf „Runde geschafft“ tippen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StepperRow("Minuten", amrapMinutes, 3..60, 1, onAmrapMinutes)
                }
            }
            Button(shapes = ButtonDefaults.shapes(), onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Starten")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TooltipIconButton(icon = Icons.Outlined.Remove, contentDescription = "$label verringern", onClick = { onChange((value - step).coerceIn(range)) })
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        TooltipIconButton(icon = Icons.Outlined.Add, contentDescription = "$label erhöhen", onClick = { onChange((value + step).coerceIn(range)) })
    }
}

private fun formatDate(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN))
}.getOrDefault("")
