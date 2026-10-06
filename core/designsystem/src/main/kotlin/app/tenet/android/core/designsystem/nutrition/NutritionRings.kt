package app.tenet.android.core.designsystem.nutrition

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One ring: consumed [value] against [goal], drawn in [color]. */
@Immutable
data class RingSpec(
    val label: String,
    val value: Float,
    val goal: Float,
    val unit: String,
    val color: Color,
) {
    val progress: Float get() = if (goal <= 0f) 0f else (value / goal).coerceAtLeast(0f)
}

/**
 * Ring palette (Apple-Health style: one vivid hue per ring). Slightly deeper
 * tones in light mode keep the arcs readable on light surfaces.
 */
object RingColors {
    // Follows the app theme (which may override the system setting).
    private val dark: Boolean @Composable get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val kcal: Color @Composable get() = if (dark) Color(0xFFFF4F6D) else Color(0xFFE5304F)
    val protein: Color @Composable get() = if (dark) Color(0xFF4CD964) else Color(0xFF2AA84A)
    val carbs: Color @Composable get() = if (dark) Color(0xFFFFB02E) else Color(0xFFF08C00)
    val fat: Color @Composable get() = if (dark) Color(0xFF3FC6FF) else Color(0xFF1E9BE0)
}

/**
 * Daily nutrition as concentric activity rings: calories outside, then
 * protein, carbs and fat. A ring past 100 % keeps going on a second lap with
 * a shadowed tip, like the Apple Health rings. Legend with values on the right.
 */
@Composable
fun NutritionRings(
    kcal: Float,
    goalKcal: Float,
    protein: Float,
    goalProtein: Float,
    carbs: Float,
    goalCarbs: Float,
    fat: Float,
    goalFat: Float,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    ringSize: Dp = 168.dp,
    onEditGoal: (() -> Unit)? = null,
) {
    val rings = listOf(
        RingSpec("Kalorien", kcal, goalKcal, "kcal", RingColors.kcal),
        RingSpec("Eiweiß", protein, goalProtein, "g", RingColors.protein),
        RingSpec("Kohlenhydrate", carbs, goalCarbs, "g", RingColors.carbs),
        RingSpec("Fett", fat, goalFat, "g", RingColors.fat),
    )

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                if (loading) {
                    ContainedLoadingIndicator(Modifier.size(ringSize * 0.45f))
                } else {
                    ActivityRings(
                        rings = rings,
                        modifier = Modifier
                            .size(ringSize)
                            .semantics {
                                contentDescription = rings.joinToString {
                                    "${it.label} ${it.value.roundToInt()} von ${it.goal.roundToInt()} ${it.unit}"
                                }
                            },
                    )
                }
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rings.forEach { RingLegendRow(it, emphasized = it === rings.first()) }
            }
        }
        if (onEditGoal != null) {
            FilledTonalButton(
                onClick = onEditGoal,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Tagesziel bearbeiten")
            }
        }
    }
}

@Composable
private fun RingLegendRow(ring: RingSpec, emphasized: Boolean) {
    val percent = (ring.progress * 100).roundToInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = ring.color, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                // Long units go into the label line so the value never truncates.
                text = if (ring.unit == "g") ring.label else "${ring.label} · ${ring.unit}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${ring.value.roundToInt()} / ${ring.goal.roundToInt()}" +
                    if (ring.unit == "g") " g" else "",
                style = if (emphasized) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(
            text = "$percent %",
            style = MaterialTheme.typography.labelLarge,
            color = ring.color,
        )
    }
}

/**
 * Concentric rings, outermost first. Progress animates in with the theme's
 * slow spatial spring on first show and on every change.
 */
@Composable
fun ActivityRings(
    rings: List<RingSpec>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 15.dp,
    gap: Dp = 3.dp,
) {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val animated = rings.map { ring ->
        val anim = remember { Animatable(0f) }
        LaunchedEffect(ring.progress) { anim.animateTo(ring.progress, spec) }
        anim
    }
    // Closing a ring (crossing 100 % while watching, not on first show): the
    // rings give a short beat with a glow of that ring's color and a haptic tick,
    // like the Apple Watch rings.
    val pulse = remember { Animatable(0f) }
    var glowColor by remember { androidx.compose.runtime.mutableStateOf(Color.Transparent) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val previous = remember { androidx.compose.runtime.mutableStateOf<List<Float>?>(null) }
    val progresses = rings.map { it.progress }
    LaunchedEffect(progresses) {
        val before = previous.value
        previous.value = progresses
        if (before == null || before.size != progresses.size) return@LaunchedEffect
        val closed = progresses.indices.firstOrNull { before[it] < 1f && progresses[it] >= 1f } ?: return@LaunchedEffect
        glowColor = rings[closed].color
        // Let the ring run up first, then beat.
        kotlinx.coroutines.delay(450)
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
        pulse.snapTo(0f)
        pulse.animateTo(1f, androidx.compose.animation.core.tween(160))
        pulse.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f))
    }

    Canvas(
        modifier.graphicsLayer {
            val k = 1f + 0.06f * pulse.value
            scaleX = k
            scaleY = k
        },
    ) {
        if (pulse.value > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.55f to glowColor.copy(alpha = 0.28f * pulse.value),
                    1f to Color.Transparent,
                    center = center,
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
            )
        }
        val stroke = strokeWidth.toPx()
        val step = stroke + gap.toPx()
        rings.forEachIndexed { index, ring ->
            val radius = size.minDimension / 2f - stroke / 2f - index * step
            if (radius <= stroke / 2f) return@forEachIndexed
            drawActivityRing(
                progress = animated[index].value,
                color = ring.color,
                radius = radius,
                stroke = stroke,
            )
        }
    }
}

private fun DrawScope.drawActivityRing(
    progress: Float,
    color: Color,
    radius: Float,
    stroke: Float,
) {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val start = lerp(color, Color.Black, 0.25f)
    val cap = Stroke(width = stroke, cap = StrokeCap.Round)

    // Track: same hue, low alpha.
    drawCircle(color = color.copy(alpha = 0.18f), radius = radius, center = center, style = Stroke(stroke))
    if (progress <= 0.001f) return

    // Draw in a frame rotated so 0° is 12 o'clock (sweep gradients start at 3 o'clock).
    rotate(-90f) {
        if (progress <= 1f) {
            val sweep = 360f * progress
            drawArc(
                brush = Brush.sweepGradient(
                    0f to start,
                    progress to color,
                    1f to start,
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = cap,
            )
        } else {
            // Full first lap, then the overflow lap on top with a shadowed tip.
            drawCircle(color = color, radius = radius, center = center, style = Stroke(stroke))
            val over = (progress - 1f).coerceAtMost(1f)
            val endAngle = 360f * over
            // Soft shadow just ahead of the tip lifts the second lap off the first.
            val ahead = Math.toRadians((endAngle + 2.5f).toDouble())
            val shadowAt = Offset(
                center.x + radius * cos(ahead).toFloat(),
                center.y + radius * sin(ahead).toFloat(),
            )
            drawCircle(Color.Black.copy(alpha = 0.22f), radius = stroke / 2f, center = shadowAt)
            drawArc(
                brush = Brush.sweepGradient(
                    0f to color,
                    over to lerp(color, Color.White, 0.2f),
                    1f to color,
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = endAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = cap,
            )
        }
    }
}
