package app.tenet.android.feature.sport.gym

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.GymInsights
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.data.WorkoutSummary
import app.tenet.android.core.designsystem.component.RecordBadge
import app.tenet.android.core.designsystem.component.SegmentedRows
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val insights: GymInsights,
    private val repository: SportRepository,
) : ViewModel() {
    private val _summary = MutableStateFlow<WorkoutSummary?>(null)
    val summary: StateFlow<WorkoutSummary?> = _summary

    fun load(sessionId: String) {
        if (_summary.value?.sessionId == sessionId) return
        viewModelScope.launch {
            _summary.value = insights.workoutSummary(sessionId, repository.sessionTitle(sessionId))
        }
    }
}

private val DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM · HH:mm", Locale.GERMAN)

/**
 * After finishing (fresh = true) and from the history: duration, volume,
 * sets, records, every exercise vs. last time and the muscle split.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    sessionId: String,
    fresh: Boolean,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    val s by viewModel.summary.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = {
                    TooltipIconButton(if (fresh) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, if (fresh) "Schließen" else "Zurück", onClose)
                },
                title = { Text(if (fresh) "Workout abgeschlossen" else s?.title ?: "Workout") },
                subtitle = { s?.let { Text(Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).format(DATE)) } },
            )
        },
        bottomBar = {
            if (fresh) {
                Surface(tonalElevation = 2.dp) {
                    Button(
                        onClick = onClose,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) { Text("Fertig") }
                }
            }
        },
    ) { padding ->
        val sum = s ?: return@Scaffold TenetLoading(Modifier.padding(padding))
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") { Hero(sum, fresh) }
            val prs = sum.exercises.filter { it.anyPr }
            if (prs.isNotEmpty()) {
                item(key = "prs") {
                    TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RecordBadge()
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (prs.size == 1) "Neuer Rekord" else "${prs.size} neue Rekorde",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            prs.forEach { pr ->
                                Text(
                                    "${pr.exercise.name} · " + listOfNotNull(
                                        "1RM ≈ ${kgText(pr.current.e1rm)}".takeIf { pr.e1rmPr },
                                        "schwerstes Gewicht ${kgText(pr.current.sets.maxOf { it.weightKg })}".takeIf { pr.weightPr },
                                        "${pr.current.bestReps} Wdh am Stück".takeIf { pr.repsPr },
                                        "${pr.current.longestHold} s gehalten".takeIf { pr.holdPr },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "ex-h") { SectionTitle(Icons.Outlined.FitnessCenter, "Übungen") }
            sum.exercises.forEach { ex ->
                item(key = "ex-${ex.exercise.id}") {
                    TenetCard(onClick = { onOpenExercise(ex.exercise.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (ex.anyPr) RecordBadge(size = 28.dp)
                            }
                            val prev = ex.previous
                            Text(
                                buildString {
                                    append(saetze(ex.current.sets.size))
                                    if (ex.current.volumeKg > 0f) append(" · ${tonsText(ex.current.volumeKg)}")
                                    if (ex.current.bodyweight && ex.current.totalReps > 0) {
                                        append(" · ${ex.current.totalReps} Wdh")
                                        prev?.takeIf { it.bodyweight && it.totalReps > 0 }?.let {
                                            val d = ex.current.totalReps - it.totalReps
                                            append(" · ${if (d >= 0) "+" else "−"}${abs(d)} ggü. letztem Mal")
                                        }
                                    }
                                    if (prev != null && prev.volumeKg > 0f && ex.current.volumeKg > 0f) {
                                        val d = ex.current.volumeKg - prev.volumeKg
                                        append(" · ${if (d >= 0) "+" else "−"}${kgText(abs(d))} ggü. letztem Mal")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (ex.note.isNotBlank()) {
                                Text("„${ex.note}“", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                            SegmentedRows(count = ex.current.sets.size) { i ->
                                val set = ex.current.sets[i]
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                Text(
                                    when {
                                        (set.durationSec ?: 0) > 0 && set.reps == 0 -> "${set.durationSec} s"
                                        set.weightKg > 0f -> "${kgText(set.weightKg)} × ${set.reps}"
                                        else -> "${set.reps} Wdh"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                // Mark the best set once (first of equal ones).
                                val bestIndex = ex.current.top?.let { ex.current.sets.indexOf(it) } ?: -1
                                if (i == bestIndex && set.weightKg > 0f && ex.current.sets.size > 1) {
                                    Text("bester Satz", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            if (sum.muscles.isNotEmpty()) {
                item(key = "muscles") {
                    TenetCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(Icons.Outlined.AccessibilityNew, "Muskeln")
                            val max = sum.muscles.maxOf { it.second }.coerceAtLeast(1)
                            sum.muscles.forEach { (muscle, sets) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(muscle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                                    LinearProgressIndicator(
                                        progress = { sets.toFloat() / max },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(saetze(sets), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(sum: WorkoutSummary, fresh: Boolean) {
    TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (fresh) {
                Text(sum.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(Modifier.fillMaxWidth()) {
                Stat(duration(sum.durationSec), "Dauer", Modifier.weight(1f))
                if (sum.volumeKg > 0f) {
                    Stat(tonsText(sum.volumeKg), "Volumen", Modifier.weight(1f))
                } else if (sum.totalReps > 0) {
                    Stat("${sum.totalReps}", "Wdh", Modifier.weight(1f))
                } else {
                    Stat("${sum.holdSec} s", "Gehalten", Modifier.weight(1f))
                }
                Stat("${sum.setCount}", "Sätze", Modifier.weight(1f))
                Stat("${sum.prCount}", "Rekorde", Modifier.weight(1f))
            }
            sum.previousVolumeKg?.takeIf { it > 0f && sum.volumeKg > 0f }?.let { prev ->
                val pct = ((sum.volumeKg / prev - 1f) * 100).roundToInt()
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            "${if (pct >= 0) "+" else ""}$pct % Volumen ggü. letztem Mal",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
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
internal fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

internal fun kgText(v: Float) = if (v % 1f == 0f) "${v.toInt()} kg" else String.format(Locale.GERMAN, "%.1f kg", v)
internal fun tonsText(v: Float) = if (v >= 1000f) String.format(Locale.GERMAN, "%.1f t", v / 1000f) else "${v.roundToInt()} kg"
private fun duration(sec: Long) = if (sec >= 3600) "${sec / 3600} h ${(sec % 3600) / 60} min" else "${sec / 60} min"

private fun saetze(n: Int) = if (n == 1) "1 Satz" else "$n Sätze"
