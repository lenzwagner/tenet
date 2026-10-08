package app.tenet.android.feature.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.designsystem.theme.harmonized
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One diary day for the evaluation. */
internal data class DiaryDay(val date: LocalDate, val mood: Int, val energy: Int?, val sleep: Int?)

private enum class StatsRange(val label: String) { WEEK("Woche"), MONTH("Monat"), ALL("Gesamt"), CUSTOM("Zeitraum") }

private enum class Metric(val label: String) { MOOD("Stimmung"), ENERGY("Energie"), SLEEP("Schlaf") }

private fun DiaryDay.value(m: Metric): Int? = when (m) {
    Metric.MOOD -> mood
    Metric.ENERGY -> energy
    Metric.SLEEP -> sleep
}

/**
 * "Auswertung" under the mood calendar, folded by default: averages of mood,
 * energy and sleep quality for the last 7 / 30 days, all time or a chosen
 * range, how they went day by day, and on which weekdays you felt best and worst.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryStatsCard(days: List<DiaryDay>) {
    var open by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val from: LocalDate? = when (range) {
        StatsRange.WEEK -> today.minusDays(6)
        StatsRange.MONTH -> today.minusDays(29)
        StatsRange.ALL -> null
        StatsRange.CUSTOM -> customFrom?.let(LocalDate::parse)
    }
    val to: LocalDate = if (range == StatsRange.CUSTOM) customTo?.let(LocalDate::parse) ?: today else today
    val shown = days.filter { (from == null || !it.date.isBefore(from)) && !it.date.isAfter(to) }.sortedBy { it.date }
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "fold")
    val fmt = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().clickable { open = !open }) {
                CardHeader(
                    Icons.Outlined.Insights,
                    "Auswertung",
                    meta = if (open) null else "${days.size} ${if (days.size == 1) "Eintrag" else "Einträge"}",
                    action = {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (open) "Einklappen" else "Aufklappen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(arrow),
                        )
                    },
                )
            }
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SegmentedSelector(
                        segments = StatsRange.entries.map { Segment(it.label) },
                        selectedIndex = range.ordinal,
                        onSelect = {
                            range = StatsRange.entries[it]
                            if (range == StatsRange.CUSTOM) picker = true
                        },
                    )
                    if (range == StatsRange.CUSTOM && customFrom != null) {
                        Text(
                            "${LocalDate.parse(customFrom).format(fmt)} – ${to.format(fmt)} · ändern",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { picker = true },
                        )
                    }
                    if (shown.isEmpty()) {
                        Text(
                            "Keine Einträge in diesem Zeitraum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Metric.entries.forEach { m -> MetricBlock(m, shown) }
                        WeekdayBlock(shown)
                    }
                }
            }
        }
    }

    if (picker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customFrom?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            initialSelectedEndDateMillis = customTo?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        DatePickerDialog(
            onDismissRequest = {
                picker = false
                if (customFrom == null) range = StatsRange.MONTH
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = state.selectedStartDateMillis
                        if (s != null) {
                            customFrom = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            customTo = (state.selectedEndDateMillis ?: s).let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                        }
                        picker = false
                        if (customFrom == null) range = StatsRange.MONTH
                    },
                ) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    picker = false
                    if (customFrom == null) range = StatsRange.MONTH
                }) { Text("Abbrechen") }
            },
        ) {
            DateRangePicker(state = state, title = null, modifier = Modifier.height(480.dp))
        }
    }
}

private fun colorFor(avg: Double): Color = MoodColors[(avg.toInt() - 1).coerceIn(0, 4)]

/** Average with a red→green bar and the course over the period as a small line. */
@Composable
private fun MetricBlock(m: Metric, days: List<DiaryDay>) {
    val values = days.mapNotNull { d -> d.value(m)?.let { d.date to it } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (values.isEmpty()) {
                Text("nicht erfasst", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val avg = values.map { it.second }.average()
                Text(
                    "Ø " + "%.1f".format(Locale.GERMAN, avg) + " / 5" + if (m == Metric.MOOD) "  " + MoodEmojis[(Math.round(avg).toInt() - 1).coerceIn(0, 4)] else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorFor(avg).harmonized(),
                )
            }
        }
        if (values.isNotEmpty()) {
            val avg = values.map { it.second }.average()
            // Average as a filled bar on the red → green scale.
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(
                    Modifier
                        .fillMaxWidth(((avg - 1) / 4).toFloat().coerceIn(0.04f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(MoodColors.map { it.harmonized() })),
                )
            }
            if (values.size >= 2) Sparkline(values.map { it.second }, colorFor(avg).harmonized())
        }
    }
}

@Composable
private fun Sparkline(values: List<Int>, color: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Int) = size.height - (v - 1) / 4f * size.height
        drawLine(grid, Offset(0f, y(3)), Offset(size.width, y(3)), 1.dp.toPx())
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        if (values.size <= 31) values.forEachIndexed { i, v -> drawCircle(color, 3.dp.toPx(), Offset(i * step, y(v))) }
    }
}

/** Mood per weekday: when it went best and worst. */
@Composable
private fun WeekdayBlock(days: List<DiaryDay>) {
    val byDay = DayOfWeek.entries.associateWith { dow -> days.filter { it.date.dayOfWeek == dow }.map { it.mood } }
    val avgs = byDay.mapValues { (_, v) -> if (v.isEmpty()) null else v.average() }
    val best = avgs.filterValues { it != null }.maxByOrNull { it.value!! }
    val worst = avgs.filterValues { it != null }.minByOrNull { it.value!! }
    fun name(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stimmung nach Wochentag", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            DayOfWeek.entries.forEach { dow ->
                val a = avgs[dow]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height((8 + ((a ?: 1.0) - 1) / 4 * 48).dp)
                            .clip(CircleShape)
                            .background(a?.let { colorFor(it).harmonized() } ?: MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(dow.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (best != null && worst != null && best.key != worst.key) {
            Text(
                "Am besten ging es dir ${name(best.key)}s, am schwersten ${name(worst.key)}s.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
