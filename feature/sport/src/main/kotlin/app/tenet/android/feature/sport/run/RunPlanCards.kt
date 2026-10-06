package app.tenet.android.feature.sport.run

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.RunWorkoutStructure
import app.tenet.android.core.common.RunWorkoutStructure.Kind
import app.tenet.android.core.common.RunZone
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.component.rememberGrowIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DAY = DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN)
private val DAY_NAMES = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** Colour of a run zone (M3 roles, readable in light and dark). */
@Composable
internal fun zoneColor(zone: RunZone, race: Boolean = false): Color {
    val c = MaterialTheme.colorScheme
    return when {
        race -> c.tertiary
        else -> when (zone) {
            RunZone.EASY -> c.secondary
            RunZone.RECOVERY -> c.outline
            RunZone.LONG -> c.primary
            RunZone.TEMPO -> c.tertiary
            RunZone.INTERVAL -> c.error
        }
    }
}

/**
 * Workout at a glance: one bar per segment, width ∝ duration, height ∝
 * intensity (warm-up low, work high) – like the Runna workout preview.
 */
@Composable
internal fun StructureBar(workout: RunWorkoutStructure.Workout, modifier: Modifier = Modifier, height: Dp = 36.dp) {
    val grow = rememberGrowIn(workout.segments.size to workout.estDurationSec)
    val main = zoneColor(workout.zone, workout.race)
    val easy = MaterialTheme.colorScheme.secondaryContainer
    val jog = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Ablauf: " + workout.segments.joinToString(", ") { it.kind.label } },
    ) {
        val total = workout.segments.sumOf { it.estSec(360) }.coerceAtLeast(1)
        val gap = 2.dp.toPx()
        var x = 0f
        val usable = size.width - gap * (workout.segments.size - 1)
        workout.segments.forEach { seg ->
            val w = usable * seg.estSec(360) / total
            val level = when (seg.kind) {
                Kind.WARMUP, Kind.COOLDOWN -> 0.45f
                Kind.RECOVERY -> 0.3f
                Kind.STEADY -> if (workout.zone == RunZone.RECOVERY) 0.45f else 0.65f
                Kind.WORK, Kind.RACE -> 1f
            }
            val color = when (seg.kind) {
                Kind.WARMUP, Kind.COOLDOWN -> easy
                Kind.RECOVERY -> jog
                else -> main
            }
            val h = size.height * level * grow
            drawRoundRect(color, Offset(x, size.height - h), Size(w.coerceAtLeast(2f), h), CornerRadius(4.dp.toPx()))
            x += w + gap
        }
    }
}

internal fun kmText(m: Int) = String.format(Locale.GERMAN, "%.1f km", m / 1000f)
internal fun minText(sec: Int) = if (sec >= 3600) "${sec / 3600} h ${(sec % 3600) / 60} min" else "${(sec + 30) / 60} min"
internal fun paceText(sec: Int) = "%d:%02d /km".format(sec / 60, sec % 60)

/** Runna-style hero: today's (or the next) run with its structure and a start button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NextRunCard(unit: PlanRunUnit, onStart: () -> Unit, onOpen: () -> Unit) {
    val today = unit.date == LocalDate.now()
    val done = unit.status == UnitStatus.DONE
    TenetCard(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (unit.race) Icons.Outlined.EmojiEvents else Icons.AutoMirrored.Outlined.DirectionsRun,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        done -> "Heute erledigt"
                        today -> "Heute"
                        else -> "Als Nächstes · ${unit.date.format(DAY)}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(unit.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            StructureBar(unit.workout, height = 44.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric("≈ ${kmText(unit.workout.estDistanceM)}", "Distanz")
                Metric("≈ ${minText(unit.workout.estDurationSec)}", "Dauer")
                unit.targetPaceSecPerKm?.let { Metric(paceText(it), if (unit.zone == RunZone.INTERVAL) "Intervall-Pace" else "Ziel-Pace") }
            }
            if (!done) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStart, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Starten")
                    }
                    OutlinedButton(onClick = onOpen, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Details") }
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
    }
}

/** Plan week by week: km progress, every unit with its status; tap opens the workout. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlanWeekCard(weeks: List<PlanWeekUi>, currentWeek: Int, onOpen: (String) -> Unit) {
    if (weeks.isEmpty()) return
    var index by rememberSaveable(weeks.size) { mutableIntStateOf(currentWeek.coerceIn(0, weeks.lastIndex)) }
    val week = weeks[index.coerceIn(0, weeks.lastIndex)]
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(Icons.Outlined.ChevronLeft, "Vorherige Woche", { index-- }, enabled = index > 0)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (week.index) {
                            currentWeek -> "Diese Woche"
                            else -> "Woche ${week.index + 1}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Woche ${week.index + 1} von ${weeks.size} · ${week.start.format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TooltipIconButton(Icons.Outlined.ChevronRight, "Nächste Woche", { index++ }, enabled = index < weeks.lastIndex)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearWavyProgressIndicator(
                    progress = { if (week.plannedKm <= 0f) 0f else (week.doneKm / week.plannedKm).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    String.format(Locale.GERMAN, "%.1f / %.0f km", week.doneKm, week.plannedKm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                week.units.forEachIndexed { i, unit ->
                    SegmentedListItem(
                        onClick = { onOpen(unit.id) },
                        shapes = ListItemDefaults.segmentedShapes(i, week.units.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = if (unit.status == UnitStatus.TODAY) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                        leadingContent = { DayBadge(unit) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    unit.doneRun?.let { r ->
                                        "${kmText(r.run.distanceM.roundToInt())} · ${paceText(r.run.avgPaceSecPerKm)}"
                                    } ?: listOfNotNull(
                                        "≈ ${kmText(unit.workout.estDistanceM)}",
                                        unit.targetPaceSecPerKm?.let { paceText(it) },
                                    ).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                StructureBar(unit.workout, height = 14.dp)
                            }
                        },
                        trailingContent = { StatusIcon(unit.status) },
                    ) {
                        Text(
                            unit.title,
                            textDecoration = if (unit.status == UnitStatus.SKIPPED) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBadge(unit: PlanRunUnit) {
    val color = zoneColor(unit.zone, unit.race)
    Box(
        Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(DAY_NAMES[unit.dayIndex.coerceIn(0, 6)], style = MaterialTheme.typography.labelSmall, color = color)
            Text("${unit.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
internal fun StatusIcon(status: UnitStatus) {
    val c = MaterialTheme.colorScheme
    when (status) {
        UnitStatus.DONE -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Erledigt", tint = c.primary)
        UnitStatus.MISSED -> Icon(Icons.Outlined.ErrorOutline, contentDescription = "Verpasst", tint = c.error)
        UnitStatus.SKIPPED -> Icon(Icons.Outlined.Block, contentDescription = "Übersprungen", tint = c.outline)
        UnitStatus.TODAY -> Surface(shape = CircleShape, color = c.primary, modifier = Modifier.size(12.dp)) {}
        UnitStatus.PLANNED -> Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "Geplant", tint = c.outlineVariant)
    }
}
