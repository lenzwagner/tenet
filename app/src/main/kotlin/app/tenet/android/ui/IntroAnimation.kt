package app.tenet.android.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Same colors as the launcher icon (res/drawable/ic_launcher_*).
private val BgTop = Color(0xFF2A2F6B)
private val BgBottom = Color(0xFF11143A)
// Two colors only: lavender crossbar, blue stem and dot.
private val Lavender = Color(0xFFB39CFF)
private val Blue = Color(0xFF6F8BFF)

/**
 * Intro on a cold start from the launcher: the icon builds itself – the
 * stem springs up, the crossbar opens from the middle in its teal→violet
 * gradient, the dot pops in, the name fades in – then the whole thing
 * lifts away and reveals the app. ~2 s, tap skips, off when the system
 * has animations disabled.
 */
@Composable
fun IntroAnimation(onDone: () -> Unit) {
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val stem = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val shine = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun leave() {
        if (leaving) return
        leaving = true
        exit.animateTo(1f, tween(260))
        onDone()
    }

    LaunchedEffect(Unit) {
        if (animationsOff) {
            onDone()
            return@LaunchedEffect
        }
        launch { glow.animateTo(1f, tween(450)) }
        stem.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f))
    }
    LaunchedEffect(Unit) {
        delay(220)
        bar.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 360f))
    }
    LaunchedEffect(Unit) {
        delay(480)
        // Dot pops, the whole mark gives a small beat with it.
        launch { pulse.animateTo(1.07f, tween(110)); pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
        dot.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }
    LaunchedEffect(Unit) {
        delay(560)
        launch { shine.animateTo(1f, tween(380)) }
        word.animateTo(1f, tween(300))
        delay(240)
        leave()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { leave() }
            }
            .semantics { contentDescription = "Tenet" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(BgTop, BgBottom), start = Offset.Zero, end = Offset(size.width, size.height)))
            val r = size.minDimension * 0.55f
            drawCircle(
                Brush.radialGradient(
                    listOf(Blue.copy(alpha = 0.32f * glow.value), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        // Exit: lift and grow slightly while fading.
                        val s = pulse.value * (1f + 0.25f * exit.value)
                        scaleX = s
                        scaleY = s
                    },
            ) {
                // Icon space: the visible 72 units (18..90) of the 108 canvas.
                val unit = size.width / 72f
                fun x(v: Float) = (v - 18f) * unit
                fun y(v: Float) = (v - 18f) * unit
                val radius = CornerRadius(7f * unit, 7f * unit)

                // Stem grows up from its rounded bottom.
                val stemH = 39f * stem.value.coerceAtLeast(0f)
                if (stemH > 0.5f) {
                    drawRoundRect(
                        Blue,
                        topLeft = Offset(x(47f), y(77f - stemH)),
                        size = Size(14f * unit, stemH * unit),
                        cornerRadius = radius,
                    )
                }
                // Crossbar opens from the middle.
                val barW = (14f + 32f * bar.value).coerceAtLeast(0f)
                if (bar.value > 0.01f) {
                    drawRoundRect(
                        Lavender,
                        topLeft = Offset(x(54f - barW / 2f), y(33f)),
                        size = Size(barW * unit, 14f * unit),
                        cornerRadius = radius,
                        alpha = bar.value.coerceIn(0f, 1f),
                    )
                }
                // Light sweeping across the crossbar once.
                if (shine.value in 0.01f..0.99f) {
                    val cx = x(31f) + (x(77f) - x(31f) + 24f * unit) * shine.value - 12f * unit
                    drawRoundRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            startX = cx - 10f * unit,
                            endX = cx + 10f * unit,
                        ),
                        topLeft = Offset(x(31f), y(33f)),
                        size = Size(46f * unit, 14f * unit),
                        cornerRadius = radius,
                    )
                }
                // Dot pops.
                if (dot.value > 0.01f) {
                    drawCircle(Blue, radius = 4.5f * unit * dot.value, center = Offset(x(72f), y(71f)))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Tenet",
                style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp, color = Color.White),
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 24f
                },
            )
        }
    }
}
