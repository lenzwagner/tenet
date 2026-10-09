package app.tenet.android.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.SleepNight
import app.tenet.android.core.data.health.HealthConnectRepository
import app.tenet.android.core.data.health.HealthOverviewRepository
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.theme.HealthTint
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.designsystem.theme.healthTint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val overview: HealthOverviewRepository,
    private val health: HealthConnectRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<HealthOverviewRepository.Overview?>(null)
    val state: StateFlow<HealthOverviewRepository.Overview?> = _state.asStateFlow()
    private val _missing = MutableStateFlow<Set<String>>(emptySet())
    val missing: StateFlow<Set<String>> = _missing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _missing.value = runCatching { health.missingPermissions() }.getOrDefault(emptySet())
            _state.value = runCatching { overview.load() }.getOrNull()
        }
    }
}

/**
 * "Gesundheit": all Health Connect data in one place – readiness, sleep with
 * phases and two weeks of nights, resting heart rate, HRV and today's pulse,
 * steps and active minutes, weight. Opened from the summary on "Heute".
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthScreen(onBack: () -> Unit, viewModel: HealthViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Gesundheit") },
                subtitle = { Text("Schlaf, Herz, Aktivität und Körper") },
            )
        },
    ) { padding ->
        val o = state
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (missing.isNotEmpty() || !o.connected) {
                item(key = "perm") { HealthPermissionHint(onGranted = viewModel::refresh) }
            }
            o.readiness?.let { r -> item(key = "ready") { ReadinessSection(r) } }
            item(key = "sleep") { SleepSection(o) }
            item(key = "heart") { HeartSection(o) }
            item(key = "activity") { ActivitySection(o) }
            item(key = "body") { BodySection(o) }
        }
    }
}

// ---- Sections -------------------------------------------------------------------

@Composable
private fun ReadinessSection(r: app.tenet.android.core.data.health.ReadinessRepository.Today) {
    val dark = isDark()
    Section(Icons.Outlined.MonitorHeart, app.tenet.android.core.common.Readiness.NAME, HealthTint.BODY, meta = "Heute") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReadinessRing(r.result.score, readinessColor(r.result.level, dark), Modifier.size(76.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(r.result.headline, style = MaterialTheme.typography.titleMedium)
                Text(r.result.advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Tiles(r.result.contributors.map { Triple(it.kind.label, it.value, it.detail) }) { i ->
            readinessColor(partLevel(r.result.contributors[i].score), dark)
        }
        FormExplainer()
    }
}

@Composable
private fun SleepSection(o: HealthOverviewRepository.Overview) {
    val color = healthTint(HealthTint.SLEEP, isDark())
    val last = o.nights.firstOrNull()
    Section(Icons.Outlined.Bedtime, "Schlaf", HealthTint.SLEEP, meta = last?.let { if (it.date == LocalDate.now()) "Letzte Nacht" else "Nacht auf ${dayName(it.date)}" }) {
        if (last == null) {
            Empty("Keine Schlafdaten. Eine Uhr oder Schlaf-App, die in Health Connect schreibt, liefert sie.")
            return@Section
        }
        BigValue(duration(last.asleepMin), "${time(last.start)} – ${time(last.end)}" + (o.sleepAvgMin?.let { " · Schnitt ${duration(it)}" } ?: ""))
        if (last.deepMin != null) {
            SleepStages(last)
        }
        val nights = o.nights.reversed()
        if (nights.size >= 2) {
            Caption("Letzte ${nights.size} Nächte")
            BarChart(
                values = nights.map { it.asleepMin / 60f },
                labels = nights.map { dayLetter(it.date) },
                color = color,
                guide = 8f,
                format = { "%.1f h".format(Locale.GERMAN, it) },
            )
        }
    }
}

@Composable
private fun SleepStages(n: SleepNight) {
    val dark = isDark()
    val stages = listOf(
        Triple("Tief", n.deepMin ?: 0, Color(if (dark) 0xFF7D7AFF else 0xFF3A37B8)),
        Triple("REM", n.remMin ?: 0, Color(if (dark) 0xFFB6A4FF else 0xFF7C6CE0)),
        Triple("Leicht", n.lightMin ?: 0, Color(if (dark) 0xFF5AC8FA else 0xFF64B5F6)),
        Triple("Wach", n.awakeMin ?: 0, Color(if (dark) 0xFFFF9F7A else 0xFFF28B66)),
    ).filter { it.second > 0 }
    val total = stages.sumOf { it.second }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
        stages.forEach { (_, min, c) -> Box(Modifier.weight(min.toFloat()).fillMaxSize().background(c)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        stages.forEach { (label, min, c) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(duration(min), style = MaterialTheme.typography.titleSmall)
                Text("${min * 100 / total} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeartSection(o: HealthOverviewRepository.Overview) {
    val dark = isDark()
    val red = Color(if (dark) 0xFFFF6B6B else 0xFFE5484D)
    Section(Icons.Outlined.FavoriteBorder, "Herz", HealthTint.BODY) {
        // The latest value, with its day when it is not today's (watch not synced yet).
        val rhrLast = o.restingHr.lastOrNull()
        val hrvLast = o.hrv.lastOrNull()
        if (o.restingHr.isEmpty() && o.hrv.isEmpty() && o.heartToday == null) {
            Empty("Keine Herzdaten. Ruhepuls, HRV und Puls kommen von deiner Uhr über Health Connect.")
            return@Section
        }
        Tiles(
            listOfNotNull(
                Triple(
                    "Ruhepuls" + (rhrLast?.date?.takeIf { it != LocalDate.now() }?.let { " · ${dayName(it)}" } ?: ""),
                    rhrLast?.let { "${it.value} bpm" } ?: "–",
                    o.restingHrAvg?.let { "Schnitt $it bpm" } ?: "noch kein Schnitt",
                ),
                Triple(
                    "HRV" + (hrvLast?.date?.takeIf { it != LocalDate.now() }?.let { " · ${dayName(it)}" } ?: ""),
                    hrvLast?.let { "${it.value.roundToInt()} ms" } ?: "–",
                    o.hrvAvg?.let { "Schnitt ${it.roundToInt()} ms" } ?: "noch kein Schnitt",
                ),
                o.heartToday?.let { Triple("Puls heute", "${it.avg} bpm", "${it.min} – ${it.max} bpm") },
            ),
        ) { MaterialTheme.colorScheme.onSurfaceVariant }
        if (o.restingHr.size >= 2) {
            Caption("Ruhepuls · 14 Tage")
            LineChart(o.restingHr.map { it.value.toFloat() }, o.restingHr.map { dayLetter(it.date) }, red, unit = "bpm")
        }
        if (o.hrv.size >= 2) {
            Caption("HRV · 14 Tage")
            LineChart(o.hrv.map { it.value.toFloat() }, o.hrv.map { dayLetter(it.date) }, healthTint(HealthTint.MIND, dark), unit = "ms")
        }
    }
}

@Composable
private fun ActivitySection(o: HealthOverviewRepository.Overview) {
    val dark = isDark()
    val orange = healthTint(HealthTint.ACTIVITY, dark)
    val fmt = java.text.NumberFormat.getIntegerInstance(Locale.GERMAN)
    Section(Icons.AutoMirrored.Outlined.DirectionsWalk, "Aktivität", HealthTint.ACTIVITY, meta = "7 Tage") {
        val today = o.steps.lastOrNull()?.value
        Tiles(
            listOf(
                Triple("Schritte heute", today?.let { fmt.format(it) } ?: "–", o.steps.takeIf { it.isNotEmpty() }?.let { "Schnitt ${fmt.format(it.map { s -> s.value }.average().roundToInt())}" } ?: "keine Daten"),
                Triple("Woche aktiv", "${o.weekActiveMin} min", "Ziel 150 min (WHO)"),
            ),
        ) { MaterialTheme.colorScheme.onSurfaceVariant }
        if (o.steps.isNotEmpty()) {
            Caption("Schritte")
            BarChart(
                values = o.steps.map { it.value.toFloat() },
                labels = o.steps.map { dayLetter(it.date) },
                color = healthTint(HealthTint.MIND, dark),
                guide = 10_000f,
                format = { fmt.format(it.roundToInt()) },
            )
        }
        Caption("Aktive Minuten (Training)")
        BarChart(
            values = o.activeMin.map { it.value.toFloat() },
            labels = o.activeMin.map { dayLetter(it.date) },
            color = orange,
            format = { "${it.roundToInt()} min" },
        )
    }
}

@Composable
private fun BodySection(o: HealthOverviewRepository.Overview) {
    Section(Icons.Outlined.MonitorWeight, "Körper", HealthTint.BODY, meta = "90 Tage") {
        val last = o.weights.lastOrNull()
        if (last == null) {
            Empty("Noch kein Gewicht eingetragen – auf „Heute“ oder sag es deinem Begleiter.")
            return@Section
        }
        val first = o.weights.first()
        val diff = last.weight - first.weight
        BigValue(
            "%.1f kg".format(Locale.GERMAN, last.weight),
            "am ${LocalDate.parse(last.date).format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN))}" +
                if (o.weights.size >= 2) " · ${if (diff >= 0) "+" else "−"}${"%.1f".format(Locale.GERMAN, kotlin.math.abs(diff))} kg seit ${LocalDate.parse(first.date).format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN))}" else "",
        )
        if (o.weights.size >= 2) {
            LineChart(
                o.weights.map { it.weight },
                o.weights.map { runCatching { LocalDate.parse(it.date).dayOfMonth.toString() }.getOrDefault("") },
                healthTint(HealthTint.BODY, isDark()),
                unit = "kg",
                labelEvery = (o.weights.size / 6).coerceAtLeast(1),
            )
        }
    }
}

// ---- Building blocks --------------------------------------------------------------

@Composable
private fun Section(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: HealthTint,
    meta: String? = null,
    content: @Composable () -> Unit,
) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(icon, title, tint, meta = meta)
            content()
        }
    }
}

@Composable
private fun BigValue(value: String, detail: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Empty(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Metric tiles two per row, like Health's summary tiles. */
@Composable
private fun Tiles(items: List<Triple<String, String, String>>, detailColor: @Composable (Int) -> Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.withIndex().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (i, t) ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(t.first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(t.second, style = MaterialTheme.typography.titleMedium)
                            Text(t.third, style = MaterialTheme.typography.labelSmall, color = detailColor(i))
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Bars per day, today's (last) one full colour, the rest softer; optional dashed goal line. */
@Composable
private fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    guide: Float? = null,
    format: (Float) -> String,
) {
    val max = (values + listOfNotNull(guide)).maxOrNull()?.takeIf { it > 0f } ?: 1f
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Column {
        Text(
            "Heute ${format(values.lastOrNull() ?: 0f)} · max ${format(values.maxOrNull() ?: 0f)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val w = (slot * 0.55f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - w) / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                val h = (v / max * size.height).coerceAtLeast(if (v > 0f) w else 0f)
                drawRoundRect(
                    if (i == values.lastIndex) color else color.copy(alpha = 0.55f),
                    Offset(x, size.height - h),
                    Size(w, h),
                    CornerRadius(w / 2),
                )
            }
            guide?.let { g ->
                val y = size.height - g / max * size.height
                drawLine(
                    guideColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
        }
        DayLabels(labels)
    }
}

/** A smooth-ish line with dots, scaled to its own range (resting HR, HRV, weight). */
@Composable
private fun LineChart(values: List<Float>, labels: List<String>, color: Color, unit: String, labelEvery: Int = 1) {
    val lo = values.minOrNull() ?: 0f
    val hi = values.maxOrNull() ?: 1f
    val range = (hi - lo).takeIf { it > 0.01f } ?: 1f
    Column {
        Text(
            "${fmt(lo)} – ${fmt(hi)} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 6.dp, vertical = 6.dp)) {
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            fun p(i: Int) = Offset(step * i, size.height - (values[i] - lo) / range * size.height)
            val path = Path().apply {
                moveTo(p(0).x, p(0).y)
                for (i in 1 until values.size) lineTo(p(i).x, p(i).y)
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            values.indices.forEach { i ->
                drawCircle(color, radius = if (i == values.lastIndex) 4.5.dp.toPx() else 2.5.dp.toPx(), center = p(i))
            }
        }
        DayLabels(labels.mapIndexed { i, l -> if (i % labelEvery == 0 || i == labels.lastIndex) l else "" })
    }
}

@Composable
private fun DayLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun isDark() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun fmt(v: Float) = if (v >= 100f || v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(Locale.GERMAN, v)

private fun duration(min: Int) = "${min / 60} h ${min % 60} min"

private fun time(i: java.time.Instant) = i.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun dayName(d: LocalDate) = when (d) {
    LocalDate.now().minusDays(1) -> "gestern"
    else -> d.format(DateTimeFormatter.ofPattern("EEE d.M.", Locale.GERMAN))
}

private fun dayLetter(d: LocalDate) =
    if (d == LocalDate.now()) "Heute" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2)
