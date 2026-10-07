package app.tenet.android.feature.sport.run

import app.tenet.android.core.designsystem.theme.TenetCard
import kotlinx.coroutines.launch
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.outlined.FileDownload
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.outlined.CheckCircle
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import app.tenet.android.core.designsystem.theme.harmonized
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.rememberGrowIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.database.entity.RunSource
import app.tenet.android.core.designsystem.component.TooltipIconButton
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Run detail (App_Konzept.md 5.2.3 "Lauf-Detail: Karte mit Route, Splits
 * pro Kilometer, Pace- und Höhenprofil, Herzfrequenzzonen" + Bestzeiten).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenPlanned: (String) -> Unit = {},
    viewModel: RunDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val planUnit by viewModel.planUnit.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val gpxSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val id = state.detail?.row?.session?.id
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        scope.launch {
            val xml = viewModel.gpx(id)
            val ok = xml != null && runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) } != null
            }.getOrDefault(false)
            snackbar?.show(if (ok) "GPX gespeichert" else "Export fehlgeschlagen")
        }
    }
    val detail = state.detail
    val session = detail?.row?.session
    val run = detail?.row?.run

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Lauf löschen?") },
            text = {
                Text(
                    if (run?.source == RunSource.HEALTH_CONNECT) {
                        "Aus Health Connect importierte Läufe kommen beim nächsten Abgleich wieder, " +
                            "solange sie dort existieren."
                    } else {
                        "Strecke, Splits und Bestzeiten dieses Laufs werden gelöscht."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onBack)
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Lauf") },
                subtitle = { session?.let { Text(formatStart(it.startedAt)) } },
                actions = {
                    if (state.track.size >= 2) {
                        TooltipIconButton(Icons.Outlined.FileDownload, "Als GPX exportieren", {
                            val start = session?.startedAt ?: 0L
                            gpxSaver.launch("tenet-lauf-${java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.gpx")
                        })
                    }
                    TooltipIconButton(Icons.Outlined.Delete, "Lauf löschen", { confirmDelete = true })
                },
            )
        },
    ) { padding ->
        if (detail == null || session == null || run == null) return@Scaffold
        val route = remember(state.track) { state.track.map { it.lat to it.lon } }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (route.size >= 2) {
                item(key = "map") {
                    RouteMap(
                        route = route,
                        interactive = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .semantics { contentDescription = "Karte mit der Laufstrecke" },
                    )
                }
            }
            // Runna-style: which plan workout this run completed.
            planUnit?.let { (plannedId, label) ->
                item(key = "plan-unit") {
                    TenetCard(
                        onClick = { onOpenPlanned(plannedId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Plan-Training erledigt", style = MaterialTheme.typography.labelLarge)
                                Text(label, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Soll/Ist", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item(key = "stats") {
                TenetCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row {
                            Stat(formatKmDe(run.distanceM.toDouble()), "km", Modifier.weight(1f))
                            Stat(formatDuration(run.durationSec), "Zeit", Modifier.weight(1f))
                            Stat(formatPaceDe(run.avgPaceSecPerKm), "Ø Pace /km", Modifier.weight(1f))
                        }
                        Row {
                            Stat(run.avgHr?.let { "$it" } ?: "–", "Ø Puls", Modifier.weight(1f))
                            Stat(run.elevationGainM?.let { "$it" } ?: "–", "Höhenmeter", Modifier.weight(1f))
                            Stat(
                                when (run.source) {
                                    RunSource.GPS -> "GPS"
                                    RunSource.MANUAL -> "Manuell"
                                    RunSource.HEALTH_CONNECT -> "Import"
                                    RunSource.GPX -> "GPX"
                                },
                                "Quelle",
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (detail.efforts.isNotEmpty()) {
                item(key = "efforts") {
                    SectionCard(Icons.Outlined.EmojiEvents, "Bestzeiten in diesem Lauf") {
                        detail.efforts.forEach { effort ->
                            val record = detail.recordSessionIds[effort.distanceM] == session.id
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(distanceLabel(effort.distanceM), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (record) {
                                    Surface(
                                        shape = MaterialShapes.Cookie6Sided.toShape(),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    ) {
                                        Text("Rekord", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(formatDuration(effort.durationSec), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            if (detail.splits.isNotEmpty()) {
                item(key = "splits") {
                    SectionCard(Icons.Outlined.Timeline, "Splits") {
                        val paces = detail.splits.map { if (it.distanceM > 0) it.durationSec / (it.distanceM / 1000f) else 0f }
                        val fastest = paces.filter { it > 0 }.minOrNull() ?: 1f
                        val grow = rememberGrowIn(detail.splits)
                        val slowest = paces.maxOrNull() ?: 1f
                        detail.splits.forEachIndexed { i, split ->
                            val pace = paces[i]
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    if (split.distanceM >= 999f) "${i + 1}" else formatKmDe(split.distanceM.toDouble()),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(44.dp),
                                )
                                // Longer bar = faster kilometer.
                                val fraction = if (slowest > fastest) 0.35f + 0.65f * ((slowest - pace) / (slowest - fastest)) else 1f
                                Box(Modifier.weight(1f).height(20.dp)) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth((fraction.coerceIn(0.1f, 1f) * grow).coerceAtLeast(0.02f))
                                            .clip(CircleShape)
                                            .background(if (pace == fastest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(formatPaceDe(pace.toInt()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
                            }
                        }
                    }
                }
            }
            val profile = state.profile
            if (profile.count { it.paceSecPerKm != null } >= 3) {
                item(key = "pace") {
                    ProfileChart(Icons.Outlined.Speed, "Pace-Profil", profile.mapNotNull { p -> p.paceSecPerKm?.let { p.distanceM to it.toFloat() } }, ::formatPaceDe)
                }
            }
            if (profile.count { it.altitude != null } >= 3) {
                item(key = "alt") {
                    ProfileChart(Icons.Outlined.Landscape, "Höhenprofil", profile.mapNotNull { p -> p.altitude?.let { p.distanceM to it } }) { "$it m" }
                }
            }
            if (profile.count { it.hr != null } >= 3) {
                item(key = "hr") {
                    ProfileChart(Icons.Outlined.Favorite, "Herzfrequenz", profile.mapNotNull { p -> p.hr?.let { p.distanceM to it.toFloat() } }) { "$it" }
                }
            }
            state.zoneSeconds?.takeIf { it.sum() > 0 }?.let { zones ->
                item(key = "zones") { ZonesCard(zones, state.maxHr) }
            }
            item(key = "notes") { NotesCard(session.notes, viewModel::saveNotes) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLargeEmphasized)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Line chart over the distance axis (km labels). */
@Composable
private fun ProfileChart(icon: ImageVector, title: String, points: List<Pair<Float, Float>>, yLabel: (Int) -> String) {
    SectionCard(icon, title) {
        val producer = remember { CartesianChartModelProducer() }
        LaunchedEffect(points) {
            producer.runTransaction {
                lineModel { series(points.map { it.first / 1000f }, points.map { it.second }) }
            }
        }
        // Charts follow the M3 color scheme (primary, tertiary …).
        ProvideVicoTheme(rememberM3VicoTheme()) {
            CartesianChartHost(
                rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(valueFormatter = CartesianValueFormatter { _, y, _ -> yLabel(y.toInt()) }),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = CartesianValueFormatter { _, x, _ -> String.format(Locale.GERMAN, "%.0f km", x) },
                    ),
                ),
                producer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .semantics { contentDescription = "Diagramm $title über ${formatKmDe(points.last().first.toDouble())} Kilometer" },
            )
        }
    }
}

@Composable
private fun ZonesCard(zones: IntArray, maxHr: Int) {
    val labels = listOf("Z1 Regeneration", "Z2 Grundlage", "Z3 Tempo", "Z4 Schwelle", "Z5 Maximal")
    val colors = listOf(Color(0xFF7FA7D9), Color(0xFF6FBF73), Color(0xFFE6C34A), Color(0xFFE8914A), Color(0xFFD9534F))
        .map { it.harmonized() }
    val total = zones.sum().coerceAtLeast(1)
    val grow = rememberGrowIn(zones.toList())
    SectionCard(Icons.Outlined.Favorite, "Herzfrequenzzonen") {
        Text(
            "Bezogen auf geschätzten Maximalpuls $maxHr (aus deinem Alter).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        zones.forEachIndexed { i, sec ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(labels[i], style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(128.dp))
                Box(Modifier.weight(1f).height(16.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(((sec.toFloat() / total) * grow).coerceAtLeast(0.02f))
                            .clip(CircleShape)
                            .background(colors[i]),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(formatDuration(sec), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp))
            }
        }
    }
}

@Composable
private fun NotesCard(initial: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Notizen zum Lauf") },
        minLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused && text != initial) onSave(text) },
    )
}

internal fun distanceLabel(meters: Int): String = when (meters) {
    21_097 -> "Halbmarathon"
    42_195 -> "Marathon"
    else -> "${meters / 1000} km"
}

private fun formatStart(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy · HH:mm", Locale.GERMAN))
