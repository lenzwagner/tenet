package app.tenet.android.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One option of a [SegmentedSelector]. */
data class Segment(val label: String, val icon: ImageVector? = null)

/**
 * Single-choice segmented control (M3 Expressive): a pill track with a
 * filled indicator that slides with the theme's spatial spring from the
 * old to the new option instead of jumping; label colors cross-fade.
 * Used for every single-choice switch in the app (sections, list/calendar,
 * week/month, units …) so they all look and move the same.
 */
@Composable
fun SegmentedSelector(
    segments: List<Segment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    role: Role = Role.RadioButton,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val moveSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val colorSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .padding(4.dp),
    ) {
        val count = segments.size.coerceAtLeast(1)
        val segmentWidth = maxWidth / count
        val indicatorX by animateDpAsState(segmentWidth * selectedIndex.coerceIn(0, count - 1), moveSpec, label = "segment")
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.primary),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            segments.forEachIndexed { index, segment ->
                val selected = index == selectedIndex
                val content by animateColorAsState(
                    if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    colorSpec,
                    label = "segmentContent",
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = selected, role = role) {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            }
                        }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    segment.icon?.let {
                        Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        segment.label,
                        color = content,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
