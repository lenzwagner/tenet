package app.tenet.android.feature.sport.gym

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.MovementPattern
import app.tenet.android.feature.sport.ExerciseImages
import app.tenet.android.feature.sport.ExerciseMotion
import androidx.compose.ui.platform.LocalContext
import app.tenet.android.core.data.ExerciseDetail
import app.tenet.android.core.data.GymInsights
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.component.rememberGrowIn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(private val insights: GymInsights) : ViewModel() {
    private val _detail = MutableStateFlow<ExerciseDetail?>(null)
    val detail: StateFlow<ExerciseDetail?> = _detail

    fun load(exerciseId: String) {
        viewModelScope.launch { _detail.value = insights.exerciseDetail(exerciseId) }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)

/** Hevy-style exercise page: records, 1RM curve and every session with its sets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(exerciseId) { viewModel.load(exerciseId) }
    val d by viewModel.detail.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text(d?.exercise?.name ?: "Übung") },
                subtitle = {
                    d?.exercise?.let { ex ->
                        // Pattern labels may already name the muscle ("Drücken · Brust"): no doubles.
                        val parts = (listOfNotNull(MovementPattern.fromName(ex.pattern)?.label) + ex.primaryMuscles.split(",") + listOf(ex.equipment))
                            .flatMap { it.split(" · ") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                        Text(parts.joinToString(" · "))
                    }
                },
            )
        },
    ) { padding ->
        val detail = d ?: return@Scaffold TenetLoading(Modifier.padding(padding))
        if (detail.sessions.isEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExerciseMotion(detail.exercise.id, "${detail.exercise.name}: Start- und Endposition", Modifier.fillMaxWidth())
                if (ExerciseImages.has(LocalContext.current, detail.exercise.id)) {
                    Text(ExerciseImages.CREDIT, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EmptyState(
                icon = Icons.Outlined.History,
                title = "Noch kein Verlauf",
                body = "Sobald du ${detail.exercise.name} in einem Training machst, siehst du hier Rekorde und Fortschritt.",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "motion") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExerciseMotion(detail.exercise.id, "${detail.exercise.name}: Start- und Endposition", Modifier.fillMaxWidth())
                    if (ExerciseImages.has(LocalContext.current, detail.exercise.id)) {
                        Text(ExerciseImages.CREDIT, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            detail.lastNote?.let { note ->
                item(key = "note") {
                    TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                            Icon(Icons.Outlined.PushPin, contentDescription = "Letzte Notiz", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            item(key = "records") {
                TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(Icons.Outlined.EmojiEvents, "Rekorde")
                        if (detail.bodyweight && detail.mostReps == 0 && detail.longestHold > 0) {
                            // Pure hold (lever, planche …): time is what counts.
                            val held = detail.sessions.sumOf { st -> st.sets.sumOf { it.durationSec ?: 0 } }
                            Row(Modifier.fillMaxWidth()) {
                                Record("${detail.longestHold} s", "Längster Halt", Modifier.weight(1f))
                                Record(if (held >= 60) "${held / 60} min ${held % 60} s" else "$held s", "Gehalten gesamt", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth()) {
                                Record("${detail.sessions.size}", "Trainings", Modifier.weight(1f))
                                Record("${detail.totalSets}", "Sätze gesamt", Modifier.weight(1f))
                            }
                        } else if (detail.bodyweight) {
                            Row(Modifier.fillMaxWidth()) {
                                Record(if (detail.mostReps > 0) "${detail.mostReps}" else "–", "Meiste Wdh am Stück", Modifier.weight(1f))
                                Record(if (detail.longestHold > 0) "${detail.longestHold} s" else "–", "Längster Halt", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth()) {
                                Record(if (detail.bestTotalReps > 0) "${detail.bestTotalReps}" else "–", "Meiste Wdh pro Training", Modifier.weight(1f))
                                Record("${detail.totalSets}", "Sätze gesamt", Modifier.weight(1f))
                            }
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                Record(if (detail.bestE1rm > 0f) kgText(detail.bestE1rm) else "–", "1RM geschätzt", Modifier.weight(1f))
                                Record(if (detail.heaviest > 0f) kgText(detail.heaviest) else "–", "Schwerstes Gewicht", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth()) {
                                Record("${detail.mostReps}", "Meiste Wdh", Modifier.weight(1f))
                                Record(if (detail.bestVolume > 0f) tonsText(detail.bestVolume) else "–", "Bestes Volumen", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            // Weighted: e1RM curve. Bodyweight: best set in reps, or the hold in seconds.
            val series: Pair<String, List<Float>>? = when {
                !detail.bodyweight && detail.bestE1rm > 0f -> "1RM-Verlauf" to detail.sessions.map { it.e1rm }
                detail.mostReps > 0 -> "Bester Satz (Wdh)" to detail.sessions.map { it.bestReps.toFloat() }
                detail.longestHold > 0 -> "Längster Halt (s)" to detail.sessions.map { it.longestHold.toFloat() }
                else -> null
            }
            if (detail.sessions.size >= 2 && series != null) {
                item(key = "chart") {
                    TenetCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle(Icons.AutoMirrored.Outlined.ShowChart, series.first)
                            E1rmChart(series.second, Modifier.fillMaxWidth().height(140.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(detail.sessions.first().date.format(DAY), style = MaterialTheme.typography.labelSmall)
                                Text(detail.sessions.last().date.format(DAY), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item(key = "hist-h") { SectionTitle(Icons.Outlined.History, "Verlauf · ${detail.sessions.size} ${if (detail.sessions.size == 1) "Training" else "Trainings"} · ${detail.totalSets} ${if (detail.totalSets == 1) "Satz" else "Sätze"}") }
            val sessions = detail.sessions.reversed()
            sessions.forEachIndexed { i, s ->
                item(key = "s-${s.sessionId}") {
                    SegmentedListItem(
                        onClick = { onOpenSession(s.sessionId) },
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(i, sessions.size),
                        colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                        supportingContent = {
                            Text(
                                s.sets.joinToString("  ·  ") { set ->
                                    when {
                                        set.weightKg > 0f -> "${kgText(set.weightKg).removeSuffix(" kg")}×${set.reps}"
                                        (set.durationSec ?: 0) > 0 && set.reps == 0 -> "${set.durationSec} s"
                                        else -> "${set.reps}"
                                    }
                                },
                            )
                        },
                        trailingContent = {
                            if (s.bodyweight && s.totalReps > 0) Text("${s.totalReps} Wdh", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            else if (s.e1rm > 0f) Text("1RM ${kgText(s.e1rm)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        },
                    ) { Text(s.date.format(DAY)) }
                }
            }
        }
    }
}

@Composable
private fun Record(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
private fun E1rmChart(values: List<Float>, modifier: Modifier) {
    val grow = rememberGrowIn(values)
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val min = values.min() * 0.97f
        val max = values.max() * 1.02f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height * (1f - (v - min) / span * grow)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), line.copy(alpha = 0f))))
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v ->
            drawCircle(line, 4.dp.toPx(), Offset(i * step, size.height * (1f - (v - min) / span * grow)))
        }
    }
}
