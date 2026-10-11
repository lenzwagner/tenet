package app.tenet.android.feature.sport

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.component.tenetFill
import app.tenet.android.core.designsystem.theme.TenetCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * The three sport pages share one skeleton, top to bottom:
 *   1. Heute        – the session of the day with its one main action
 *   2. Mein Plan    – what the plan is, on which days, what comes next
 *   3. the sport's core (week, skills) and the latest sessions
 *   4. everything else folded away behind section headers (statistics, body)
 * Editing lives one level deeper (plans page, plan editor, week plan), not
 * on the page itself.
 */

/** Opens the editor of a gym or calisthenics plan; set by the sport screen. */
internal val LocalEditPlan = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/** Opens the week plan across all sports (rest days, rules); set by the sport screen. */
internal val LocalOpenWeek = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * "Mein Plan": the plan in one glance – name, one line about it, the week as
 * seven dots, what comes next – and exactly two ways on: open the plan, or
 * the plans page (switch, new, AI coach).
 *
 * @param days ISO weekdays the plan trains on; empty = every day; null = hide the week.
 */
@Composable
internal fun PlanSummaryCard(
    discipline: Discipline,
    name: String?,
    subtitle: String?,
    days: Set<Int>?,
    next: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    /** Tap on the card body (details); null = not clickable. */
    onOpen: (() -> Unit)? = null,
    note: String? = null,
) {
    val openPlans = LocalOpenPlans.current
    val openWeek = LocalOpenWeek.current
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(Icons.Outlined.CalendarMonth, "Mein Plan")
            if (name == null) {
                Text(
                    "Noch kein Plan. Leg einen an – mit ein paar Fragen, selbst oder mit dem KI-Coach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { openPlans?.invoke(discipline) }, modifier = Modifier.fillMaxWidth()) { Text("Plan anlegen") }
                return@Column
            }
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            days?.let { WeekDots(it, onClick = openWeek) }
            next?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary, modifier = Modifier.weight(1f)) { Text(primaryLabel, maxLines = 1) }
                FilledTonalButton(onClick = { openPlans?.invoke(discipline) }, modifier = Modifier.weight(1f)) { Text("Alle Pläne", maxLines = 1) }
            }
        }
    }
    if (onOpen != null && name != null) {
        TenetCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), content = body)
    } else {
        TenetCard(modifier = Modifier.fillMaxWidth(), content = body)
    }
}

/**
 * The week as seven dots: filled = training day, ring = today. Read-only; a
 * tap opens the week plan, where days are set for all sports together.
 */
@Composable
internal fun WeekDots(days: Set<Int>, onClick: (() -> Unit)? = null) {
    val today = LocalDate.now().dayOfWeek.value
    val all = days.isEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "Wochenplan öffnen", onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..7).forEach { day ->
            val on = all || day in days
            val full = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.GERMAN)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else tenetFill)
                        .then(if (day == today) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), CircleShape) else Modifier)
                        .semantics { contentDescription = "$full: " + if (on) "Training" else "frei" },
                )
            }
        }
    }
}

/**
 * Header of a folded section ("Fortschritt", "Statistik", "Körper"): title, a
 * one-line summary and a chevron. The section's cards follow only while open.
 */
@Composable
internal fun SectionToggle(icon: ImageVector, title: String, summary: String?, expanded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    TenetCard(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Einklappen" else "Aufklappen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn),
            )
        }
    }
}

/** A plain row that leads somewhere else (library, history). */
@Composable
internal fun LinkRow(icon: ImageVector, title: String, supporting: String?, onClick: () -> Unit) {
    TenetCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                supporting?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** ISO weekdays of a plan's stored days ("1,3,5"); empty = every day. */
internal fun planDays(value: String?): Set<Int> =
    value.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
