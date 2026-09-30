package app.tenet.android.core.designsystem.component

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Shape between two (normalized, 0…1) polygons, e.g. MaterialShapes.
 * [progress] 0 = [Morph] start, 1 = end; [rotation] in degrees turns the
 * outline around its centre (for a gentle spin while morphing).
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        if (rotation != 0f) {
            // Rotate the unit shape around its centre before scaling to size.
            matrix.translate(0.5f, 0.5f)
            matrix.rotateZ(rotation)
            matrix.translate(-0.5f, -0.5f)
        }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Shape that morphs from [unselected] to [selected] with the theme's
 * expressive spatial spring whenever [isSelected] flips (MD3 Expressive
 * "shape morph" for chosen items).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animatedMorphShape(isSelected: Boolean, unselected: RoundedPolygon, selected: RoundedPolygon): Shape {
    val morph = remember(unselected, selected) { Morph(unselected, selected) }
    val progress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "shape-morph",
    )
    // A little turn while the shape changes makes the morph readable.
    return MorphShape(morph, progress, rotation = progress * 45f)
}

/**
 * Slowly "breathing" shape: morphs back and forth between [a] and [b] and
 * turns once per [turnMs]. For badges, empty states and waiting states.
 */
@Composable
fun breathingMorphShape(a: RoundedPolygon, b: RoundedPolygon, periodMs: Int = 2_600, turnMs: Int = 16_000): Shape {
    val morph = remember(a, b) { Morph(a, b) }
    val transition = rememberInfiniteTransition(label = "breathing-shape")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs), RepeatMode.Reverse),
        label = "breath",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(turnMs, easing = LinearEasing)),
        label = "turn",
    )
    return MorphShape(morph, progress, rotation)
}

/** Morph driven by an external value (0…1), e.g. the voice level; springs to follow. */
@Composable
fun levelMorphShape(level: Float, a: RoundedPolygon, b: RoundedPolygon, rotation: Float = 0f): Shape {
    val morph = remember(a, b) { Morph(a, b) }
    val progress by animateFloatAsState(level.coerceIn(0f, 1f), spring(dampingRatio = 0.6f, stiffness = 400f), label = "level-morph")
    return MorphShape(morph, progress, rotation)
}

/** Trophy badge for records: a tertiary "breathing" cookie ↔ sun shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    icon: ImageVector = Icons.Outlined.EmojiEvents,
) {
    Box(
        modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                breathingMorphShape(MaterialShapes.Cookie7Sided, MaterialShapes.Sunny),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
