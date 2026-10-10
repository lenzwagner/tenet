package app.tenet.android.feature.sport

import androidx.compose.foundation.layout.size
import app.tenet.android.core.database.entity.Discipline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.TrainingDays
import app.tenet.android.core.database.entity.TrainingPlan
import app.tenet.android.core.database.entity.WorkoutSession
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.theme.TenetCard
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** What the top card of a discipline shows today. */
internal enum class TodayState { PLANNED, ACTIVE, DONE, REST }

/** Plan with fixed weekdays: today off → REST; no days = every day counts. */
internal fun todayState(
    trainingDays: String?,
    active: Boolean,
    sessions: List<WorkoutSession>,
    today: LocalDate = LocalDate.now(),
): TodayState {
    val zone = ZoneId.systemDefault()
    val doneToday = sessions.any { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }
    val days: Set<DayOfWeek> = TrainingDays.parse(trainingDays)
    return when {
        active -> TodayState.ACTIVE
        doneToday -> TodayState.DONE
        days.isNotEmpty() && today.dayOfWeek !in days -> TodayState.REST
        else -> TodayState.PLANNED
    }
}

/** "morgen", "Donnerstag" … for the next training day, null without fixed days. */
internal fun nextTrainingDayLabel(trainingDays: String?, today: LocalDate = LocalDate.now()): String? {
    val days = TrainingDays.parse(trainingDays)
    if (days.isEmpty()) return null
    val next = TrainingDays.next(days, today) ?: return null
    return if (next == today.plusDays(1)) "morgen" else next.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
}

/**
 * Hero card on top of Gym and Calisthenics, like the running page: today's
 * session with its start buttons, or "Keine Session heute" with what comes next.
 */
@Composable
internal fun TodaySessionCard(
    icon: ImageVector,
    state: TodayState,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    nextLabel: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = on)
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = on)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (state) {
                        TodayState.PLANNED -> "Heute"
                        TodayState.ACTIVE -> "Session läuft"
                        TodayState.DONE -> "Heute erledigt"
                        TodayState.REST -> "Keine Session heute"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = on,
                )
            }
            if (state == TodayState.REST && nextLabel != null) {
                Text("Als Nächstes · $nextLabel", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.75f))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = on)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f)) }
            extra()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
    if (onClick != null) {
        TenetCard(onClick = onClick, colors = colors, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        TenetCard(colors = colors, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** Opens the plans of a discipline (list, switch, create); set by the sport screen. */
internal val LocalOpenPlans = androidx.compose.runtime.staticCompositionLocalOf<((Discipline) -> Unit)?> { null }

/**
 * "Pläne" in a plan card's header: all plans of the discipline – switch,
 * rename, delete, or create a new one (yourself, with questions, with the AI coach).
 */
@Composable
internal fun PlanSwitchButton(discipline: Discipline) {
    val open = LocalOpenPlans.current ?: return
    androidx.compose.material3.TextButton(onClick = { open(discipline) }) {
        Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        Text("Pläne", maxLines = 1, softWrap = false)
    }
}
