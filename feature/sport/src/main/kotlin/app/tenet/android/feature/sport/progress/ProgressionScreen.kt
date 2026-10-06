package app.tenet.android.feature.sport.progress

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.ProgressionMath.Better
import app.tenet.android.core.common.ProgressionMath.Series
import app.tenet.android.core.common.ProgressionMath.Unit as MetricUnit
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.component.rememberGrowIn
import app.tenet.android.feature.sport.disciplineColor
import app.tenet.android.feature.sport.label
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SHORT = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
private val LONG = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

private data class Slide(val discipline: Discipline, val icon: ImageVector, val metricHint: String, val empty: String)

private val slides = listOf(
    Slide(Discipline.GYM, Icons.Outlined.FitnessCenter, "Geschätztes 1RM je Übung", "Schließe Gym-Workouts ab, dann siehst du hier, wie deine Kraft wächst."),
    Slide(Discipline.CALISTHENICS, Icons.Outlined.SelfImprovement, "Beste Wiederholungen oder Haltezeit", "Nach deinen ersten Calisthenics-Sessions erscheinen hier Wiederholungen und Haltezeiten."),
    Slide(Discipline.RUNNING, Icons.AutoMirrored.Outlined.DirectionsRun, "Form, Tempo und Umfang", "Nach ein paar Läufen siehst du hier deine 5-km-Form, deine Pace und deine Wochenkilometer."),
)

private enum class Range(val label: String, val months: Long?) {
    M3("3M", 3), M6("6M", 6), Y1("1J", 12), ALL("Alle", null)
}

/**
 * Progression slides (Sport → Fortschritt): one swipeable slide per
 * discipline showing how strength, calisthenics reps/holds and running
 * form improve over time. Range selector applies to all slides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionScreen(
    initialPage: Int,
    onBack: () -> Unit,
    viewModel: ProgressionViewModel = hiltViewModel(),
) {
    val data by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, slides.lastIndex)) { slides.size }
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableIntStateOf(Range.M6.ordinal) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Fortschritt") },
                subtitle = { Text("Wie du dich über die Zeit verbesserst") },
            )
        },
    ) { padding ->
        val d = data ?: return@Scaffold TenetLoading(Modifier.padding(padding))
        val from = Range.entries[range].months?.let { LocalDate.now().minusMonths(it) }
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedSelector(
                segments = Range.entries.map { Segment(it.label) },
                selectedIndex = range,
                onSelect = { range = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f),
            ) { page ->
                val slide = slides[page]
                val series = when (slide.discipline) {
                    Discipline.GYM -> d.gym
                    Discipline.CALISTHENICS -> d.calisthenics
                    Discipline.RUNNING -> d.running
                }.map { it.since(from) }.filter { it.points.isNotEmpty() }
                SlideCard(slide, series, from)
            }
            PagerDots(
                count = slides.size,
                current = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SlideCard(slide: Slide, series: List<Series>, from: LocalDate?) {
    val accent = disciplineColor(slide.discipline)
    var selectedKey by rememberSaveable(slide.discipline) { mutableStateOf<String?>(null) }
    val selected = series.firstOrNull { it.key == selectedKey } ?: series.firstOrNull()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    TenetCard(
        Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(slide.icon, null, tint = accent) }
                Column {
                    Text(slide.discipline.label(), style = MaterialTheme.typography.titleLarge)
                    Text(slide.metricHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (selected == null) {
                Text(
                    if (from == null) slide.empty else "Im gewählten Zeitraum gibt es keine Daten. " + slide.empty,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@Column
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(series, key = { it.key }) { s ->
                    FilterChip(
                        selected = s.key == selected.key,
                        onClick = { selectedKey = s.key },
                        label = { Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it.key to it.points.size },
                label = "series",
            ) { s -> SeriesDetail(s, accent) }

            if (series.size > 1) {
                HorizontalDivider()
                Text("Alle im Überblick", style = MaterialTheme.typography.titleMedium)
                OverviewList(series, selected.key) {
                    selectedKey = it
                    scope.launch { scroll.animateScrollTo(0) }
                }
            }
        }
    }
}

@Composable
private fun SeriesDetail(s: Series, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val last = s.points.last()
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(format(last.value, s.unit), style = MaterialTheme.typography.displaySmall)
                Text("aktuell · ${last.date.format(SHORT)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImprovementBadge(s)
        }

        if (s.points.size >= 2) {
            ProgressChart(s, accent, Modifier.fillMaxWidth().height(200.dp))
            if (s.better == Better.LOWER) {
                Text(
                    "Weniger ist schneller – im Chart zeigt oben die bessere Zeit.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "Ein Datenpunkt bisher – ab dem nächsten Training siehst du den Verlauf.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            Stat("Start", format(s.points.first().value, s.unit), s.points.first().date.format(SHORT), Modifier.weight(1f))
            Stat(if (s.better == Better.NEUTRAL) "Aktuell" else "Bestwert", format(s.best ?: 0f, s.unit), bestDate(s)?.format(SHORT).orEmpty(), Modifier.weight(1f))
            Stat("Einträge", "${s.points.size}", if (s.unit == MetricUnit.KM) (if (s.points.size == 1) "Woche" else "Wochen") else (if (s.points.size == 1) "Tag" else "Tage"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ImprovementBadge(s: Series) {
    val gain = s.improvement ?: return
    val colors = MaterialTheme.colorScheme
    val (icon, container, content) = when {
        // Body weight: neither direction is "good", so no green/red.
        s.better == Better.NEUTRAL -> Triple(
            if (gain > 0f) Icons.AutoMirrored.Outlined.TrendingUp else if (gain < 0f) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingFlat,
            colors.secondaryContainer,
            colors.onSecondaryContainer,
        )
        gain > 0f -> Triple(Icons.AutoMirrored.Outlined.TrendingUp, colors.primaryContainer, colors.onPrimaryContainer)
        gain < 0f -> Triple(Icons.AutoMirrored.Outlined.TrendingDown, colors.errorContainer, colors.onErrorContainer)
        else -> Triple(Icons.AutoMirrored.Outlined.TrendingFlat, colors.surfaceContainerHighest, colors.onSurfaceVariant)
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Column {
                Text(gainText(s, gain), style = MaterialTheme.typography.labelLarge)
                s.improvementPercent?.let {
                    Text("${if (it > 0) "+" else ""}$it %", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Line chart with time-proportional x axis, soft area fill, dashed start
 * line and min/max labels. For "lower is better" metrics the y axis is
 * flipped so that up always means improvement. Tap a point to read it.
 */
@Composable
private fun ProgressChart(s: Series, accent: Color, modifier: Modifier) {
    val grow = rememberGrowIn(s.key to s.points.size)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    var tapped by remember(s.key, s.points.size) { mutableStateOf<Int?>(null) }

    val pts = s.points
    val minV = pts.minOf { it.value }
    val maxV = pts.maxOf { it.value }
    val pad = ((maxV - minV).takeIf { it > 0f } ?: maxOf(abs(maxV) * 0.1f, 1f)) * 0.15f
    val lo = minV - pad
    val hi = maxV + pad
    val firstDay = pts.first().date
    val spanDays = ChronoUnit.DAYS.between(firstDay, pts.last().date).coerceAtLeast(1).toFloat()
    val axisH = 20.dp
    // Moving average over 5 points once the series gets long and jumpy.
    val trend = remember(s.key, pts) {
        if (pts.size < 10) null
        else pts.indices.map { i ->
            val from = (i - 2).coerceAtLeast(0)
            val to = (i + 2).coerceAtMost(pts.lastIndex)
            (from..to).map { pts[it].value }.average().toFloat()
        }
    }

    Canvas(
        modifier
            .semantics {
                contentDescription = "${s.label}: von ${format(pts.first().value, s.unit)} am ${pts.first().date.format(LONG)} " +
                    "auf ${format(pts.last().value, s.unit)} am ${pts.last().date.format(LONG)}"
            }
            .pointerInput(s.key, pts.size) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat()
                    tapped = pts.indices.minByOrNull { i ->
                        abs(ChronoUnit.DAYS.between(firstDay, pts[i].date) / spanDays * w - tap.x)
                    }
                }
            },
    ) {
        val chartH = size.height - axisH.toPx()
        fun x(i: Int) = ChronoUnit.DAYS.between(firstDay, pts[i].date) / spanDays * size.width
        fun y(v: Float): Float {
            val norm = (v - lo) / (hi - lo)
            val up = if (s.better == Better.LOWER) 1f - norm else norm
            return chartH * (1f - up)
        }

        // Grid: three faint lines.
        repeat(3) { g ->
            val gy = chartH * (g + 1) / 4f
            drawLine(grid.copy(alpha = 0.5f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        // Start level as dashed reference.
        val startY = y(pts.first().value)
        drawLine(
            grid,
            Offset(0f, startY),
            Offset(size.width, startY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )

        val visible = (pts.size * grow).roundToInt().coerceIn(1, pts.size)
        val line = Path()
        for (i in 0 until visible) {
            if (i == 0) line.moveTo(x(i), y(pts[i].value)) else line.lineTo(x(i), y(pts[i].value))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(visible - 1), chartH)
            lineTo(x(0), chartH)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0f)), endY = chartH))
        if (trend != null) {
            // Noisy series: thin raw line, bold moving-average trend on top.
            drawPath(line, accent.copy(alpha = 0.4f), style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
            val t = Path()
            for (i in 0 until visible) {
                if (i == 0) t.moveTo(x(i), y(trend[i])) else t.lineTo(x(i), y(trend[i]))
            }
            drawPath(t, accent, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawPath(line, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (pts.size <= 30 && trend == null) {
            for (i in 0 until visible) drawCircle(accent, 3.dp.toPx(), Offset(x(i), y(pts[i].value)))
        }
        val lastI = visible - 1
        val lastY = y(trend?.get(lastI) ?: pts[lastI].value)
        drawCircle(surface, 7.dp.toPx(), Offset(x(lastI), lastY))
        drawCircle(accent, 5.dp.toPx(), Offset(x(lastI), lastY))

        // X axis: first and last date.
        val axisY = chartH + 4.dp.toPx()
        drawText(measurer, pts.first().date.format(SHORT), Offset(0f, axisY), labelStyle)
        val lastLabel = measurer.measure(pts.last().date.format(SHORT), labelStyle)
        drawText(lastLabel, topLeft = Offset(size.width - lastLabel.size.width, axisY))

        // Tapped point: guide line + value bubble.
        tapped?.let { i ->
            val px = x(i)
            val py = y(pts[i].value)
            drawLine(accent.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, chartH), strokeWidth = 1.dp.toPx())
            drawCircle(accent, 6.dp.toPx(), Offset(px, py))
            val text = measurer.measure("${format(pts[i].value, s.unit)} · ${pts[i].date.format(SHORT)}", valueStyle)
            val bx = (px - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            val by = (py - text.size.height - 12.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(
                surface,
                Offset(bx - 6.dp.toPx(), by - 3.dp.toPx()),
                androidx.compose.ui.geometry.Size(text.size.width + 12.dp.toPx(), text.size.height + 6.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
            )
            drawText(text, topLeft = Offset(bx, by))
        }
    }
}

@Composable
private fun OverviewList(series: List<Series>, selectedKey: String, onSelect: (String) -> Unit) {
    val sorted = series.sortedByDescending { it.improvementPercent ?: Int.MIN_VALUE }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        sorted.forEachIndexed { i, s ->
            SegmentedListItem(
                onClick = { onSelect(s.key) },
                shapes = ListItemDefaults.segmentedShapes(i, sorted.size),
                colors = if (s.key == selectedKey) {
                    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                },
                supportingContent = { Text("${format(s.points.last().value, s.unit)} · ${s.points.size} ${if (s.points.size == 1) "Eintrag" else "Einträge"}") },
                trailingContent = {
                    val p = s.improvementPercent
                    Text(
                        when {
                            p == null -> "–"
                            p > 0 -> "+$p %"
                            else -> "$p %"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            p == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            p > 0 -> MaterialTheme.colorScheme.primary
                            p < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            ) { Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    )
                    .pointerInput(i) { detectTapGestures { onSelect(i) } }
                    .semantics { contentDescription = "Seite ${i + 1}: ${slides[i].discipline.label()}" },
            )
        }
    }
}

private fun bestDate(s: Series): LocalDate? {
    val best = s.best ?: return null
    return s.points.lastOrNull { it.value == best }?.date
}

private fun gainText(s: Series, gain: Float): String = when (s.unit) {
    MetricUnit.TIME, MetricUnit.PACE ->
        mmss(abs(gain).roundToInt()) + if (gain >= 0f) " schneller" else " langsamer"
    else -> (if (gain > 0f) "+" else "−") + format(abs(gain), s.unit)
}

private fun format(v: Float, unit: MetricUnit): String = when (unit) {
    MetricUnit.KG -> "${num(v)} kg"
    MetricUnit.REPS -> "${v.roundToInt()} Wdh"
    MetricUnit.SECONDS -> v.roundToInt().let { if (it >= 60) mmss(it) + " min" else "$it s" }
    MetricUnit.PACE -> mmss(v.roundToInt()) + " /km"
    MetricUnit.KM -> "${num(v)} km"
    MetricUnit.RATIO -> String.format(Locale.GERMAN, "%.2f × KG", v)
    MetricUnit.TIME -> v.roundToInt().let {
        if (it >= 3600) "%d:%02d:%02d".format(it / 3600, (it % 3600) / 60, it % 60) else mmss(it)
    }
}

private fun mmss(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

private fun num(v: Float) =
    if (abs(v - v.roundToInt()) < 0.05f) v.roundToInt().toString() else String.format(Locale.GERMAN, "%.1f", v)
