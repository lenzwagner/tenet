package app.tenet.android.feature.sport

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.TrainingDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Status line for a plan's training days, e.g. "Ruhetag · nächstes Training Mi". */
internal fun trainingDayStatus(days: Set<DayOfWeek>, today: LocalDate = LocalDate.now()): String? {
    if (days.isEmpty() || today.dayOfWeek in days) return null
    val next = TrainingDays.next(days, today) ?: return null
    val label = if (next == today.plusDays(1)) "morgen" else next.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    return "Heute Ruhetag · nächstes Training $label"
}

/**
 * Fixed training weekdays of a plan as connected multi-select toggle buttons
 * (M3 Expressive button group). No selection = every day.
 */
@Composable
internal fun TrainingDaysRow(
    value: String?,
    onChange: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = TrainingDays.parse(value)
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (days.isEmpty()) "Trainingstage · keine festen Tage (jeden Tag)" else "Trainingstage",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            DayOfWeek.entries.forEachIndexed { index, day ->
                val checked = day in days
                val name = day.getDisplayName(TextStyle.FULL, Locale.GERMAN)
                ToggleButton(
                    checked = checked,
                    onCheckedChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onChange(if (it) days + day else days - day)
                    },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        6 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Checkbox
                            contentDescription = name
                        },
                ) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
