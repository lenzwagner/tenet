package app.tenet.android.feature.sport.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.FormEstimator

/**
 * "Deine Form": race times for 5 km, 10 km, half and full marathon from
 * all runs of the last 8 weeks (incl. Health Connect), heart rate included.
 */
@Composable
fun FormCard(form: FormEstimator.Form) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Deine Form", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "VDOT ${"%.1f".format(java.util.Locale.GERMAN, form.vdot)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            form.times.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { t ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "${t.label}: ${clock(t.timeSec)}, ${pace(t.paceSecPerKm)} pro Kilometer" },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(t.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(clock(t.timeSec), style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("${pace(t.paceSecPerKm)} /km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (form.withHr > 0) {
                    Icon(Icons.Outlined.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "Aus ${form.runs} ${if (form.runs == 1) "Lauf" else "Läufen"} der letzten 8 Wochen" +
                        (if (form.withHr > 0) " · ${form.withHr} mit Puls ausgewertet" else " · ohne Pulsdaten nur nach Tempo"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun clock(sec: Int): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

private fun pace(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
