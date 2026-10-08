package app.tenet.android.feature.sport.calisthenics

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.CaliProgression
import app.tenet.android.core.data.GymInsights
import app.tenet.android.core.data.SkillRepository
import app.tenet.android.core.database.entity.CriterionType
import app.tenet.android.core.database.entity.MeasureType
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.component.rememberGrowIn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class CaliExerciseUi(
    val exerciseId: String,
    val name: String,
    val hold: Boolean,
    val sets: Int,
    val target: Int,
    /** Best set per session, oldest first (reps or seconds). */
    val bestPerSession: List<Int>,
    val lastSets: List<Int>,
    val best: Int,
    val next: CaliProgression.Suggestion,
)

data class CaliSkillUi(val name: String, val step: String, val stepNumber: Int, val stepCount: Int, val promotions: Int)

data class CaliPlanUi(
    val title: String,
    val sessions: Int,
    val weeks: Int,
    val exercises: List<CaliExerciseUi>,
    val skills: List<CaliSkillUi>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CaliPlanViewModel @Inject constructor(
    repository: SkillRepository,
    private val insights: GymInsights,
) : ViewModel() {
    val state: StateFlow<CaliPlanUi?> = repository.observeCalisthenicsOverview()
        .mapLatest { ov ->
            val exById = ov.exercises.associateBy { it.id }
            val exercises = ov.routine.sortedBy { it.sortOrder }.mapNotNull { r ->
                val ex = exById[r.exerciseId] ?: return@mapNotNull null
                val hold = ex.measureType == MeasureType.HOLD || ex.measureType == MeasureType.DURATION
                val sessions = insights.exerciseDetail(r.exerciseId)?.sessions.orEmpty()
                fun values(sets: List<app.tenet.android.core.data.DoneSet>) =
                    sets.map { if (hold) it.durationSec ?: 0 else it.reps }
                val best = sessions.map { s -> values(s.sets).maxOrNull() ?: 0 }
                val last = sessions.lastOrNull()?.let { values(it.sets) }.orEmpty()
                CaliExerciseUi(
                    exerciseId = ex.id,
                    name = ex.name,
                    hold = hold,
                    sets = r.targetSets,
                    target = r.targetReps,
                    bestPerSession = best,
                    lastSets = last,
                    best = best.maxOrNull() ?: 0,
                    next = CaliProgression.next(r.targetReps, r.targetSets, last, hold),
                )
            }
            val skills = ov.skills.sortedBy { it.sortOrder }.mapNotNull { sk ->
                val steps = ov.steps.filter { it.skillId == sk.id }.sortedBy { it.sortOrder }
                if (steps.isEmpty()) return@mapNotNull null
                val cur = steps.firstOrNull { st -> st.id == ov.progress.firstOrNull { it.skillId == sk.id }?.currentStepId } ?: steps.first()
                CaliSkillUi(
                    name = sk.name,
                    step = cur.label + " · Ziel ${cur.criterionSets} × ${cur.criterionValue}${if (cur.criterionType == CriterionType.HOLD) " s" else ""}",
                    stepNumber = steps.indexOf(cur) + 1,
                    stepCount = steps.size,
                    promotions = ov.achievements.count { a -> steps.any { it.id == a.stepId } },
                )
            }
            val start = ov.sessions.minOfOrNull { it.startedAt }
            CaliPlanUi(
                title = ov.plan?.name ?: "Calisthenics",
                sessions = ov.sessions.size,
                weeks = start?.let {
                    ChronoUnit.WEEKS.between(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now()).toInt() + 1
                } ?: 0,
                exercises = exercises,
                skills = skills,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Calisthenics plan: strength block with double progression, skill ladder status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaliPlanScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: CaliPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text(state?.title ?: "Trainingsplan") },
                subtitle = { Text("Kraft-Block & Skills") },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold TenetLoading(Modifier.padding(padding))
        if (s.exercises.isEmpty() && s.skills.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SelfImprovement,
                title = "Noch kein Plan",
                body = "Richte Calisthenics ein, dann siehst du hier Ziele und Fortschritt.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp)) {
                        Stat("${s.sessions}", "Trainings", Modifier.weight(1f))
                        Stat("${s.weeks}", "Wochen", Modifier.weight(1f))
                        Stat("${s.skills.sumOf { it.promotions }}", "Aufstiege", Modifier.weight(1f))
                    }
                }
            }
            if (s.exercises.isNotEmpty()) {
                item(key = "ex-h") { Title(Icons.AutoMirrored.Outlined.TrendingUp, "Kraft-Block") }
                s.exercises.forEach { ex -> item(key = "ex-${ex.exerciseId}") { ExerciseCard(ex) { onOpenExercise(ex.exerciseId) } } }
            }
            if (s.skills.isNotEmpty()) {
                item(key = "sk-h") { Title(Icons.Outlined.AccountTree, "Skills") }
                s.skills.forEach { sk ->
                    item(key = "sk-${sk.name}") {
                        TenetCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                app.tenet.android.core.designsystem.component.CardHeader(Icons.Outlined.AccountTree, sk.name, meta = "Stufe ${sk.stepNumber}/${sk.stepCount}")
                                LinearWavyProgressIndicator(progress = { sk.stepNumber.toFloat() / sk.stepCount }, modifier = Modifier.fillMaxWidth())
                                Text(sk.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(ex: CaliExerciseUi, onClick: () -> Unit) {
    val unit = if (ex.hold) " s" else ""
    TenetCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ziel ${ex.sets} × ${ex.target}$unit" +
                            (if (ex.lastSets.isNotEmpty()) " · zuletzt ${ex.lastSets.joinToString(" · ")}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ex.best > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${ex.best}$unit", style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(if (ex.hold) "Längster Halt" else "Bester Satz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (ex.bestPerSession.size >= 2) Sparkline(ex.bestPerSession.map { it.toFloat() }, Modifier.fillMaxWidth().height(48.dp))
            Text(
                CaliProgression.label(ex.next, ex.hold),
                style = MaterialTheme.typography.labelLarge,
                color = if (ex.next.kind == CaliProgression.Kind.HARDER) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Title(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier) {
    val grow = rememberGrowIn(values)
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val y = size.height * (1f - (v - min) / span * grow)
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
        }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(line, 4.dp.toPx(), Offset((values.size - 1) * step, size.height * (1f - (values.last() - min) / span * grow)))
    }
}
