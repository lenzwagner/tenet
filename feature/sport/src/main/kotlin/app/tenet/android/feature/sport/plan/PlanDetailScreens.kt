package app.tenet.android.feature.sport.plan

import androidx.compose.foundation.layout.Box
import app.tenet.android.core.common.RunPlanMath
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.GymPlanStats
import app.tenet.android.core.common.OverloadMath
import app.tenet.android.core.data.PlanUnitUi
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.component.rememberGrowIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DAY = DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)
private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

// ============================================================================
// Running plan
// ============================================================================

/**
 * Running plan detail: prognosis vs. goal time, countdown, progress,
 * planned vs. run weekly volume (taper marked), prognosis trend, next and
 * completed units.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunPlanDetailScreen(
    planId: String,
    onBack: () -> Unit,
    onOpenRun: (String) -> Unit,
    /** A planned unit: structure, paces, splits and fueling. */
    onOpenWorkout: (String) -> Unit = {},
    viewModel: RunPlanDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(planId) { viewModel.load(planId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text(state?.title ?: "Laufplan") },
                subtitle = { state?.goalDate?.let { Text("Ziel am ${it.format(DATE)}") } },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold TenetLoading(Modifier.padding(padding))
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") { PrognosisCard(s) }
            s.fit?.takeIf { it.suggestAdjust }?.let { fit ->
                item(key = "fit") {
                    FitCard(s, fit) {
                        viewModel.adjustToForm { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                    }
                }
            }
            if (s.zones.isNotEmpty()) item(key = "zones") { ZonesCard(s.zones) }
            item(key = "progress") { ProgressCard(s) }
            item(key = "weekly") { WeeklyVolumeCard(s) }
            // Only a real trend: at least two different prognoses.
            if (s.predictionHistory.map { it.second }.distinct().size >= 2) item(key = "trend") { PrognosisTrendCard(s.predictionHistory) }
            if (s.upcoming.isNotEmpty()) {
                item(key = "next-h") { SectionTitle(Icons.Outlined.Event, "Nächste Einheiten") }
                item(key = "next") { UnitList(s.upcoming, onOpenWorkout) }
            }
            item(key = "done-h") { SectionTitle(Icons.Outlined.CheckCircle, "Abgeschlossen · ${s.done.size}") }
            item(key = "done") {
                if (s.done.isEmpty()) {
                    Text(
                        "Noch keine Einheit dieses Plans gelaufen. Läufe am geplanten Tag werden automatisch zugeordnet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                        s.done.forEachIndexed { i, d ->
                            SegmentedListItem(
                                colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                                onClick = { onOpenRun(d.run.session.id) },
                                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(i, s.done.size),
                                leadingContent = { Icon(Icons.AutoMirrored.Outlined.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary) },
                                supportingContent = { Text(d.unit.date.format(DAY)) },
                                trailingContent = {
                                    Text(
                                        "${km(d.run.run.distanceM)} km · ${pace(d.run.run.avgPaceSecPerKm)}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                            ) { Text(d.unit.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

/** Goal out of reach or paces off the current form: offer to re-anchor the rest of the plan. */
@Composable
private fun FitCard(s: RunPlanUi, fit: app.tenet.android.core.common.PlanFit.Assessment, onAdjust: () -> Unit) {
    val unrealistic = fit.goal == app.tenet.android.core.common.PlanFit.Goal.UNREALISTIC
    TenetCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (unrealistic) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (unrealistic) "Zielzeit gerade außer Reichweite" else "Deine Form hat sich verändert",
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            val form = fit.form5kSec?.let(::hms)
            Text(
                when {
                    unrealistic && form != null ->
                        "Nach deinen letzten Läufen (5 km in $form) sind eher ${fit.suggestedTargetSec?.let(::hms)} realistisch, " +
                            "deutlich langsamer als ${s.targetTimeSec?.let(::hms)}. Zu schnelle Tempi erhöhen das Verletzungsrisiko."
                    form != null -> "Deine letzten Läufe (5 km in $form) passen nicht mehr zu den Tempi des Plans."
                    else -> "Die Tempi des Plans passen nicht mehr zu deinen letzten Läufen."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (fit.form5kSec != null) {
                androidx.compose.material3.Button(onClick = onAdjust) {
                    Text(if (unrealistic) "Ziel & Tempi anpassen" else "Tempi an Form anpassen")
                }
            }
        }
    }
}

/** E / M / T / I / R with this week's pace ranges (they get faster with the plan). */
@Composable
private fun ZonesCard(zones: List<PaceZoneUi>) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tempo-Zonen diese Woche", style = MaterialTheme.typography.titleMedium)
            zones.forEach { z ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f + 0.12f * zones.indexOf(z)),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(z.code, style = MaterialTheme.typography.labelLarge) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(z.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${clock(z.fastSec)}–${clock(z.slowSec)} /km", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

private fun clock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
private fun PrognosisCard(s: RunPlanUi) {
    TenetCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (s.raceDistanceM == null) "Fit bleiben" else "Prognose ${s.goal.label}",
                style = MaterialTheme.typography.labelLarge,
            )
            val p = s.prediction
            if (s.raceDistanceM != null) {
                Text(
                    p?.let { hms(it.timeSec) } ?: "–:–:–",
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    modifier = Modifier.semantics { contentDescription = "Prognostizierte Zeit ${p?.let { hms(it.timeSec) } ?: "unbekannt"}" },
                )
                Text(
                    s.predictionNote?.takeIf { p != null }
                        ?: p?.let { "aus ${distanceName(it.basis.distanceM)} in ${hms(it.basis.durationSec)} am ${it.basis.date.format(DATE)} (Riegel)" }
                        ?: "Noch keine Prognose – lauf 5 km oder mehr (GPS, nachgetragen oder Health Connect).",
                    style = MaterialTheme.typography.bodySmall,
                )
                // Prognosis follows every logged run; on race day the remaining training counts too.
                val raceDay = p?.let { RunPlanMath.expectedRaceDaySec(it.timeSec, s.goal, (s.weeks - s.currentWeek).coerceAtLeast(0), s.taper) }
                raceDay?.takeIf { it < p.timeSec }?.let {
                    Text(
                        "Am Wettkampftag erwartet ≈ ${hms(it)} (mit dem restlichen Training)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                s.targetTimeSec?.let { target ->
                    val diff = (raceDay ?: p?.timeSec)?.let { it - target }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.EmojiEvents, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Wunschzeit ${hms(target)}" + when {
                                    diff == null -> ""
                                    diff <= 0 -> " · auf Kurs (${hms(abs(diff))} Puffer)"
                                    else -> " · noch ${hms(diff)} schneller"
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            s.daysLeft?.let { days ->
                Text(
                    when {
                        days > 1 -> "noch $days Tage · Woche ${s.currentWeek + 1} von ${s.weeks}" + if (s.taper) " · mit Tapering" else ""
                        days == 1L -> "Morgen ist Wettkampf!"
                        days == 0L -> "Heute ist Wettkampf – viel Erfolg!"
                        else -> "Plan abgeschlossen"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(s: RunPlanUi) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Stat("${s.done.size}/${s.dueCount}", "Einheiten bis heute", Modifier.weight(1f))
                Stat(km(s.totalDoneKm * 1000), "km gelaufen", Modifier.weight(1f))
                Stat("${s.currentWeek + 1}/${s.weeks}", "Woche", Modifier.weight(1f))
            }
            val ratio = if (s.dueCount == 0) 0f else s.done.size.toFloat() / s.dueCount
            LinearWavyProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Planned (track) vs. run (fill) km per plan week; taper weeks in tertiary. */
@Composable
private fun WeeklyVolumeCard(s: RunPlanUi) {
    val grow = rememberGrowIn(s.weekly)
    val colors = MaterialTheme.colorScheme
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(Icons.Outlined.BarChart, "Wochenumfang geplant / gelaufen")
            val max = maxOf(s.weekly.maxOfOrNull { maxOf(it.plannedKm, it.doneKm) } ?: 0f, 1f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics {
                        contentDescription = s.weekly.joinToString { "Woche ${it.weekIndex + 1}: ${km(it.doneKm * 1000)} von ${km(it.plannedKm * 1000)} km" }
                    },
            ) {
                val slot = size.width / s.weekly.size
                val w = slot * 0.62f
                s.weekly.forEachIndexed { i, week ->
                    val x = i * slot + (slot - w) / 2
                    val plannedH = size.height * (week.plannedKm / max) * grow
                    val doneH = size.height * (week.doneKm / max) * grow
                    val track = if (week.taper) colors.tertiary.copy(alpha = 0.25f) else colors.onSurface.copy(alpha = 0.1f)
                    drawRoundRect(track, Offset(x, size.height - plannedH), Size(w, plannedH), CornerRadius(w / 3))
                    if (doneH > 0f) {
                        drawRoundRect(
                            if (week.taper) colors.tertiary else colors.primary,
                            Offset(x, size.height - doneH),
                            Size(w, doneH),
                            CornerRadius(w / 3),
                        )
                    }
                    if (i == s.currentWeek) {
                        drawRoundRect(colors.primary, Offset(x, size.height + 2f), Size(w, 6f), CornerRadius(3f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("W1", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                s.weekly.firstOrNull { it.taper }?.let {
                    Text("Taper ab W${it.weekIndex + 1}", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                }
                Text(
                    if (s.raceDistanceM != null) "W${s.weeks} · Wettkampf" else "W${s.weeks}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PrognosisTrendCard(points: List<Pair<LocalDate, Int>>) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(Icons.AutoMirrored.Outlined.ShowChart, "Prognose-Verlauf")
            Sparkline(
                values = points.map { it.second.toFloat() },
                // Lower time = better: draw inverted so "up" means faster.
                invert = true,
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${points.first().first.format(DATE)} · ${hms(points.first().second)}", style = MaterialTheme.typography.labelSmall)
                Text("${points.last().first.format(DATE)} · ${hms(points.last().second)}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun UnitList(units: List<PlanUnitUi>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
        units.forEachIndexed { i, u ->
            SegmentedListItem(
                onClick = { onOpen(u.id) },
                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(i, units.size),
                colors = if (u.isRace) {
                    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                } else {
                    app.tenet.android.core.designsystem.theme.tenetListColors()
                },
                leadingContent = {
                    Icon(
                        if (u.isRace) Icons.Outlined.EmojiEvents else Icons.AutoMirrored.Outlined.DirectionsRun,
                        null,
                        tint = if (u.isRace) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                },
                supportingContent = { Text(u.date.format(DAY)) },
                trailingContent = {
                    Text(
                        listOfNotNull(
                            u.targetDistanceM?.let { km(it.toFloat()) + " km" },
                            u.targetDurationSec?.let { "${it / 60} min" },
                            u.targetPaceSecPerKm?.let { pace(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            ) { Text(u.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ============================================================================
// Gym plan
// ============================================================================

/** Gym plan detail: volume per session, per exercise progression and next weights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymPlanDetailScreen(
    planId: String,
    onBack: () -> Unit,
    viewModel: GymPlanDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(planId) { viewModel.load(planId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text(state?.title ?: "Trainingsplan") },
                subtitle = { Text("Fortschritt") },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold TenetLoading(Modifier.padding(padding))
        if (s.sessions.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = "Noch keine Trainings",
                body = "Sobald du Workouts dieses Plans abschließt, siehst du hier Volumen und Kraftentwicklung.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp)) {
                        Stat("${s.sessions.size}", "Trainings", Modifier.weight(1f))
                        Stat(tons(s.totalVolumeKg), "Gesamtvolumen", Modifier.weight(1f))
                        Stat(tons(s.totalVolumeKg / s.sessions.size), "Ø pro Training", Modifier.weight(1f))
                    }
                }
            }
            item(key = "volume") { SessionVolumeCard(s.sessions) }
            item(key = "ex-h") { SectionTitle(Icons.AutoMirrored.Outlined.TrendingUp, "Übungen") }
            s.exercises.forEach { ex ->
                item(key = "ex-${ex.name}") { ExerciseProgressCard(ex, s.next[ex.name]) }
            }
        }
    }
}

@Composable
private fun SessionVolumeCard(sessions: List<GymPlanStats.SessionStat>) {
    val grow = rememberGrowIn(sessions)
    val colors = MaterialTheme.colorScheme
    val shown = sessions.takeLast(16)
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(Icons.Outlined.BarChart, "Volumen pro Training")
            val max = maxOf(shown.maxOf { it.volumeKg }, 1f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .semantics { contentDescription = shown.joinToString { "${it.date.format(DATE)}: ${tons(it.volumeKg)}" } },
            ) {
                val slot = size.width / shown.size
                val w = minOf(slot * 0.6f, 28.dp.toPx())
                shown.forEachIndexed { i, sStat ->
                    val x = i * slot + (slot - w) / 2
                    val h = size.height * (sStat.volumeKg / max) * grow
                    drawRoundRect(
                        if (i == shown.lastIndex) colors.primary else colors.primary.copy(alpha = 0.45f),
                        Offset(x, size.height - h),
                        Size(w, h),
                        CornerRadius(minOf(8.dp.toPx(), w / 2, h / 2)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(shown.first().date.format(DATE), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("zuletzt ${tons(shown.last().volumeKg)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseProgressCard(ex: GymPlanStats.ExerciseStat, next: OverloadMath.Suggestion?) {
    val last = ex.points.last()
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Top-Satz ${kg(last.topWeightKg)} × ${last.topReps} · Volumen ${tons(last.volumeKg)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${kg(ex.lastE1rm)} kg", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text("1RM geschätzt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (ex.points.size >= 2) {
                Sparkline(ex.points.map { it.e1rm }, invert = false, modifier = Modifier.fillMaxWidth().height(56.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ex.progressPercent?.let { pct ->
                    Chip(
                        if (pct >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                        "${if (pct > 0) "+" else ""}$pct % seit Start",
                        if (pct >= 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    )
                }
                Chip(Icons.Outlined.EmojiEvents, "Best ${kg(ex.bestE1rm)} kg", MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            next?.takeIf { it.weightKg > 0f }?.let { n ->
                Text(
                    "Nächstes Mal: ${kg(n.weightKg)} kg – ${OverloadMath.label(n)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ============================================================================
// Shared bits
// ============================================================================

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
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
private fun Chip(icon: ImageVector, text: String, color: Color) {
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Minimal line chart; grows in with the expressive spring. */
@Composable
private fun Sparkline(values: List<Float>, invert: Boolean, modifier: Modifier = Modifier) {
    val grow = rememberGrowIn(values)
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val norm = (v - min) / span
            val yNorm = if (invert) norm else 1f - norm
            val y = size.height * 0.1f + size.height * 0.8f * yNorm
            val x = i * stepX * grow
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 6f, cap = StrokeCap.Round))
        val lastNorm = (values.last() - min) / span
        drawCircle(line, 9f, Offset((values.size - 1) * stepX * grow, size.height * 0.1f + size.height * 0.8f * (if (invert) lastNorm else 1f - lastNorm)))
    }
}

private fun hms(sec: Int): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

private fun pace(secPerKm: Int) = "%d:%02d /km".format(secPerKm / 60, secPerKm % 60)

private fun km(meters: Float) = String.format(Locale.GERMAN, "%.1f", meters / 1000f)

private fun kg(v: Float) = if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.GERMAN, "%.1f", v)

private fun tons(kg: Float) = if (kg >= 1000f) String.format(Locale.GERMAN, "%.1f t", kg / 1000f) else "${kg.roundToInt()} kg"

private fun distanceName(m: Int) = when (m) {
    21_097 -> "Halbmarathon"
    42_195 -> "Marathon"
    else -> "${m / 1000} km"
}
