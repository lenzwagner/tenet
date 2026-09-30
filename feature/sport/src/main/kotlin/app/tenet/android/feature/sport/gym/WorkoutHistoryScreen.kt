package app.tenet.android.feature.sport.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.feature.sport.GymViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
private val DAY = DateTimeFormatter.ofPattern("EEE, d. MMM · HH:mm", Locale.GERMAN)

/** Every finished gym workout, grouped by month; tap opens the summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: GymViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val titles = overview.workouts.associate { it.id to it.title }
    val zone = ZoneId.systemDefault()
    val months = overview.sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().withDayOfMonth(1) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Trainingsverlauf") },
                subtitle = { Text("${overview.sessions.size} Trainings") },
            )
        },
    ) { padding ->
        if (overview.sessions.isEmpty()) {
            EmptyState(Icons.Outlined.History, "Noch keine Trainings", Modifier.padding(padding), "Abgeschlossene Workouts erscheinen hier.")
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            months.forEach { (month, sessions) ->
                item(key = "m-$month") {
                    Text(
                        "${month.format(MONTH)} · ${sessions.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                sessions.forEachIndexed { i, session ->
                    item(key = session.id) {
                        val minutes = session.endedAt?.let { (it - session.startedAt) / 60_000L } ?: 0L
                        val volume = overview.volumesBySession[session.id] ?: 0f
                        SegmentedListItem(
                            onClick = { onOpenSession(session.id) },
                            shapes = ListItemDefaults.segmentedShapes(i, sessions.size),
                            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            supportingContent = { Text(Instant.ofEpochMilli(session.startedAt).atZone(zone).format(DAY)) },
                            trailingContent = {
                                Text("$minutes min · ${tonsText(volume)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                        ) { Text(session.plannedWorkoutId?.let { titles[it] } ?: "Training") }
                    }
                }
            }
        }
    }
}
