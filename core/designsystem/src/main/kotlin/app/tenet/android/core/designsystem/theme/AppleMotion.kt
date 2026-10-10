package app.tenet.android.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Motion after Apple's "Designing Fluid Interfaces": springs described by
 * damping ratio and response (seconds to the target) instead of durations.
 * Springs start from the value on screen and carry velocity, so every
 * animation can be interrupted and turned around mid-flight.
 */
object AppleSpring {
    /** Stiffness for a spring that answers in [response] seconds (mass 1). */
    fun stiffness(response: Float): Float = (2f * Math.PI.toFloat() / response).let { it * it }

    /** Default UI motion: critically damped, no overshoot. */
    fun <T> smooth(response: Float = 0.35f, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = stiffness(response), visibilityThreshold = visibilityThreshold)

    /** Only after a flick or a drag release: a little bounce (damping 0.8). */
    fun <T> bouncy(response: Float = 0.3f, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = stiffness(response), visibilityThreshold = visibilityThreshold)
}

/**
 * "Klar" motion scheme: every M3 component (switches, sheets, menus, tab
 * indicator, shape morphs) moves on critically damped springs with Apple's
 * responses – quick 0.25 s, default 0.35 s, large 0.5 s – and never bounces
 * on its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AppleMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = AppleSpring.smooth(0.35f)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = AppleSpring.smooth(0.25f)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = AppleSpring.smooth(0.5f)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = AppleSpring.smooth(0.3f)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = AppleSpring.smooth(0.2f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = AppleSpring.smooth(0.45f)
}

/**
 * Press feedback like iOS instead of a ripple: the whole element dims the
 * instant the finger is down and fades back on release – for everything that
 * uses Modifier.clickable (tiles, rows, thumbnails).
 */
class HighlightIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HighlightNode(interactionSource, color)
    override fun equals(other: Any?) = other is HighlightIndication && other.color == color
    override fun hashCode() = color.hashCode()
}

private class HighlightNode(private val source: InteractionSource, private val color: Color) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    // Down: at once, no fade-in (response before anything else).
                    is PressInteraction.Press -> launch {
                        alpha.snapTo(1f)
                        invalidateDraw()
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                        alpha.animateTo(0f, tween(220)) { invalidateDraw() }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (alpha.value > 0f) drawRect(color, alpha = alpha.value)
    }
}

/**
 * Shrinks a touch (97 %) while pressed and springs back: cards and tiles
 * answer the finger like physical buttons. Pass the element's own
 * interaction source.
 */
fun Modifier.pressScale(interactionSource: InteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val down by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (down) pressed else 1f,
        // Down fast, back with a soft spring.
        animationSpec = if (down) AppleSpring.smooth(0.15f) else AppleSpring.smooth(0.3f),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Scroll ends like iOS: past the edge the content follows the finger with
 * growing resistance (rubber band) and springs back on release, carrying the
 * fling's leftover speed – instead of Android's stretch. A new touch catches
 * it mid-bounce.
 */
class RubberBandOverscrollFactory(private val reach: Float) : androidx.compose.foundation.OverscrollFactory {
    override fun createOverscrollEffect(): androidx.compose.foundation.OverscrollEffect = RubberBandOverscroll(reach)
    override fun equals(other: Any?) = other is RubberBandOverscrollFactory && other.reach == reach
    override fun hashCode() = reach.hashCode()
}

private class RubberBandOverscroll(private val reach: Float) : androidx.compose.foundation.OverscrollEffect {
    private var offset by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)

    override val isInProgress: Boolean get() = offset != androidx.compose.ui.geometry.Offset.Zero

    /** The further out already, the less the content follows. */
    private fun pull(current: Float, delta: Float): Float = current + delta * 0.55f / (1f + kotlin.math.abs(current) / (reach * 0.55f))

    /** Moving back towards the edge: the band gives the distance back before the list scrolls. */
    private fun release(current: Float, delta: Float): Pair<Float, Float> {
        if (current == 0f || delta == 0f || (current > 0f) == (delta > 0f)) return current to 0f
        val next = current + delta
        return if ((next > 0f) != (current > 0f)) 0f to -current else next to delta
    }

    override fun applyToScroll(
        delta: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        performScroll: (androidx.compose.ui.geometry.Offset) -> androidx.compose.ui.geometry.Offset,
    ): androidx.compose.ui.geometry.Offset {
        val (x, usedX) = release(offset.x, delta.x)
        val (y, usedY) = release(offset.y, delta.y)
        offset = androidx.compose.ui.geometry.Offset(x, y)
        val before = androidx.compose.ui.geometry.Offset(usedX, usedY)
        val rest = delta - before
        val scrolled = performScroll(rest)
        val left = rest - scrolled
        // Only the finger stretches the band; a fling's leftover goes into the bounce below.
        if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && left != androidx.compose.ui.geometry.Offset.Zero) {
            offset = androidx.compose.ui.geometry.Offset(pull(offset.x, left.x), pull(offset.y, left.y))
            return delta
        }
        return before + scrolled
    }

    override suspend fun applyToFling(
        velocity: androidx.compose.ui.unit.Velocity,
        performFling: suspend (androidx.compose.ui.unit.Velocity) -> androidx.compose.ui.unit.Velocity,
    ) {
        // Stretched: spring home at once, taking the release speed along.
        val left = if (isInProgress) velocity else velocity - performFling(velocity)
        val start = offset
        if (start == androidx.compose.ui.geometry.Offset.Zero && left == androidx.compose.ui.unit.Velocity.Zero) return
        val vertical = kotlin.math.abs(start.y) + kotlin.math.abs(left.y) >= kotlin.math.abs(start.x) + kotlin.math.abs(left.x)
        androidx.compose.animation.core.animate(
            initialValue = if (vertical) start.y else start.x,
            targetValue = 0f,
            // A fling running into the edge tips over a little and comes back.
            initialVelocity = (if (vertical) left.y else left.x).coerceIn(-6000f, 6000f) * 0.5f,
            animationSpec = spring(dampingRatio = 1f, stiffness = AppleSpring.stiffness(0.45f), visibilityThreshold = 0.5f),
        ) { value, _ ->
            offset = if (vertical) androidx.compose.ui.geometry.Offset(0f, value) else androidx.compose.ui.geometry.Offset(value, 0f)
        }
        offset = androidx.compose.ui.geometry.Offset.Zero
    }

    override val node: DelegatableNode = object : Modifier.Node(), androidx.compose.ui.node.LayoutModifierNode {
        override fun androidx.compose.ui.layout.MeasureScope.measure(
            measurable: androidx.compose.ui.layout.Measurable,
            constraints: androidx.compose.ui.unit.Constraints,
        ): androidx.compose.ui.layout.MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    translationX = offset.x
                    translationY = offset.y
                }
            }
        }
    }
}
