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
