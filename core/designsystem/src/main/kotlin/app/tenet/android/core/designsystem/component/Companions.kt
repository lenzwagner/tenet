package app.tenet.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin

/** The companions to choose from (Einstellungen → Begleiter). */
enum class CompanionKind(val label: String, val description: String) {
    TENNY("Tenny", "Kleiner Wolken-Roboter"),
    MIO("Mio", "Verschmuster Kater"),
    FOXI("Foxi", "Flinker Fuchs"),
    PINGU("Pingu", "Gemütlicher Pinguin"),
    GLIBBER("Glibber", "Wackelnder Schleim"),
    ;

    companion object {
        fun of(id: String?): CompanionKind = entries.firstOrNull { it.name == id } ?: TENNY
    }
}

/**
 * A companion, drawn on a canvas and animated: idle bobbing, blinking,
 * stepping feet while [walking], "…" while [thinking]; mirrored to face left.
 */
@Composable
fun CompanionCreature(
    kind: CompanionKind,
    walking: Boolean,
    thinking: Boolean,
    facingLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = rememberInfiniteTransition(label = "companion")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (walking) 380 else 2200, easing = LinearEasing)), label = "bob")
    val blink by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "blink")
    Canvas(modifier) {
        val f = Frame(
            wave = sin(bob * 2 * Math.PI).toFloat(),
            walking = walking,
            thinking = thinking,
            blink = blink > 0.94f,
            dots = (bob * 3).toInt() % 3,
        )
        // Soft floating shadow: a blurred ellipse under the feet that shrinks and fades
        // a little as the body rises (bob / hop), so the creature seems to hover.
        val rise = (f.wave + 1f) / 2f
        val sw = size.width * (0.62f - rise * 0.08f)
        val sh = size.height * 0.09f
        val center = Offset(size.width / 2, size.height * 0.95f)
        drawOval(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.20f - rise * 0.06f),
                0.6f to Color.Black.copy(alpha = 0.08f),
                1f to Color.Transparent,
                center = center,
                radius = sw / 2,
            ),
            topLeft = Offset(center.x - sw / 2, center.y - sh / 2),
            size = Size(sw, sh),
        )
        scale(if (facingLeft) -1f else 1f, 1f, pivot = Offset(size.width / 2, size.height / 2)) {
            when (kind) {
                CompanionKind.TENNY -> tenny(f)
                CompanionKind.MIO -> cat(f)
                CompanionKind.FOXI -> fox(f)
                CompanionKind.PINGU -> penguin(f)
                CompanionKind.GLIBBER -> slime(f)
            }
        }
    }
}

private class Frame(val wave: Float, val walking: Boolean, val thinking: Boolean, val blink: Boolean, val dots: Int)

private val Ink = Color(0xFF1E1E28)

/** Two eyes (or thinking dots) centred at [cx], [cy]. */
private fun DrawScope.eyes(f: Frame, cx: Float, cy: Float, gap: Float, r: Float, color: Color = Ink) {
    if (f.thinking) {
        for (i in -1..1) drawCircle(color.copy(alpha = if (i + 1 == f.dots) 1f else 0.3f), r * 0.8f, Offset(cx + i * gap * 0.6f, cy))
        return
    }
    if (f.blink) {
        drawLine(color, Offset(cx - gap / 2 - r, cy), Offset(cx - gap / 2 + r, cy), r * 0.6f, StrokeCap.Round)
        drawLine(color, Offset(cx + gap / 2 - r, cy), Offset(cx + gap / 2 + r, cy), r * 0.6f, StrokeCap.Round)
    } else {
        drawCircle(color, r, Offset(cx - gap / 2, cy))
        drawCircle(color, r, Offset(cx + gap / 2, cy))
        drawCircle(Color.White, r * 0.35f, Offset(cx - gap / 2 + r * 0.3f, cy - r * 0.35f))
        drawCircle(Color.White, r * 0.35f, Offset(cx + gap / 2 + r * 0.3f, cy - r * 0.35f))
    }
}

private fun DrawScope.feet(f: Frame, color: Color, y: Float, xs: Pair<Float, Float>, fw: Float, fh: Float) {
    val step = if (f.walking) f.wave * size.height * 0.05f else 0f
    drawOval(color, Offset(xs.first, y - step), Size(fw, fh))
    drawOval(color, Offset(xs.second, y + step), Size(fw, fh))
}

private fun DrawScope.lift(f: Frame) = (if (f.walking) 0.03f else 0.02f) * f.wave * size.height

// ---- Tenny: cloud robot ---------------------------------------------------------

private fun DrawScope.tenny(f: Frame) {
    val w = size.width
    val h = size.height
    val body = Color(0xFF6F7CF7)
    val dark = Color(0xFF4A55D6)
    feet(f, dark, h * 0.86f, w * 0.30f to w * 0.54f, w * 0.16f, h * 0.12f)
    translate(top = lift(f)) {
        drawCircle(body, w * 0.22f, Offset(w * 0.32f, h * 0.38f))
        drawCircle(body, w * 0.24f, Offset(w * 0.56f, h * 0.32f))
        drawCircle(body, w * 0.20f, Offset(w * 0.74f, h * 0.46f))
        drawCircle(body, w * 0.20f, Offset(w * 0.26f, h * 0.56f))
        drawRoundRect(body, Offset(w * 0.18f, h * 0.36f), Size(w * 0.66f, h * 0.44f), CornerRadius(w * 0.2f))
        drawOval(dark, Offset(w * 0.08f, h * 0.58f), Size(w * 0.12f, h * 0.16f))
        drawOval(dark, Offset(w * 0.80f, h * 0.58f), Size(w * 0.12f, h * 0.16f))
        drawRoundRect(Color(0xFF1E2246), Offset(w * 0.30f, h * 0.36f), Size(w * 0.44f, h * 0.24f), CornerRadius(w * 0.08f))
        val eye = Color(0xFF8FF3FF)
        if (f.thinking) {
            for (i in 0..2) drawCircle(eye.copy(alpha = if (i == f.dots) 1f else 0.35f), w * 0.03f, Offset(w * (0.42f + i * 0.08f), h * 0.48f))
        } else {
            val eh = if (f.blink) h * 0.012f else h * 0.07f
            drawRoundRect(eye, Offset(w * 0.39f, h * 0.48f - eh / 2), Size(w * 0.07f, eh), CornerRadius(w * 0.03f))
            drawRoundRect(eye, Offset(w * 0.56f, h * 0.48f - eh / 2), Size(w * 0.07f, eh), CornerRadius(w * 0.03f))
        }
    }
}

// ---- Mio: cat --------------------------------------------------------------------

private fun DrawScope.cat(f: Frame) {
    val w = size.width
    val h = size.height
    val fur = Color(0xFFF2A65A)
    val dark = Color(0xFFD9822B)
    val pink = Color(0xFFF7A1B5)
    // Tail sways.
    rotate(degrees = f.wave * 12f, pivot = Offset(w * 0.22f, h * 0.72f)) {
        drawLine(dark, Offset(w * 0.22f, h * 0.72f), Offset(w * 0.06f, h * 0.44f), w * 0.07f, StrokeCap.Round)
    }
    feet(f, dark, h * 0.86f, w * 0.30f to w * 0.56f, w * 0.15f, h * 0.10f)
    translate(top = lift(f)) {
        drawOval(fur, Offset(w * 0.24f, h * 0.52f), Size(w * 0.52f, h * 0.38f))
        // Head with ears.
        val ear = Path().apply {
            moveTo(w * 0.28f, h * 0.30f); lineTo(w * 0.30f, h * 0.08f); lineTo(w * 0.46f, h * 0.22f); close()
            moveTo(w * 0.72f, h * 0.30f); lineTo(w * 0.70f, h * 0.08f); lineTo(w * 0.54f, h * 0.22f); close()
        }
        drawPath(ear, fur)
        drawCircle(fur, w * 0.25f, Offset(w * 0.5f, h * 0.38f))
        drawCircle(dark.copy(alpha = 0.5f), w * 0.06f, Offset(w * 0.5f, h * 0.20f))
        eyes(f, w * 0.5f, h * 0.36f, w * 0.19f, w * 0.058f)
        drawCircle(pink, w * 0.028f, Offset(w * 0.5f, h * 0.45f))
        for (s in listOf(-1f, 1f)) {
            drawLine(Ink.copy(alpha = 0.5f), Offset(w * (0.5f + s * 0.08f), h * 0.46f), Offset(w * (0.5f + s * 0.26f), h * 0.43f), w * 0.012f)
            drawLine(Ink.copy(alpha = 0.5f), Offset(w * (0.5f + s * 0.08f), h * 0.48f), Offset(w * (0.5f + s * 0.26f), h * 0.50f), w * 0.012f)
        }
    }
}

// ---- Foxi: fox ---------------------------------------------------------------------

private fun DrawScope.fox(f: Frame) {
    val w = size.width
    val h = size.height
    val fur = Color(0xFFE8682E)
    val dark = Color(0xFF8A3A16)
    val cream = Color(0xFFFFF1E0)
    rotate(degrees = f.wave * 10f, pivot = Offset(w * 0.26f, h * 0.70f)) {
        drawOval(fur, Offset(w * 0.0f, h * 0.48f), Size(w * 0.34f, h * 0.22f))
        drawOval(cream, Offset(w * 0.0f, h * 0.52f), Size(w * 0.12f, h * 0.14f))
    }
    feet(f, dark, h * 0.86f, w * 0.32f to w * 0.56f, w * 0.13f, h * 0.10f)
    translate(top = lift(f)) {
        drawOval(fur, Offset(w * 0.28f, h * 0.52f), Size(w * 0.46f, h * 0.36f))
        drawOval(cream, Offset(w * 0.40f, h * 0.60f), Size(w * 0.22f, h * 0.24f))
        val ears = Path().apply {
            moveTo(w * 0.28f, h * 0.30f); lineTo(w * 0.26f, h * 0.04f); lineTo(w * 0.46f, h * 0.20f); close()
            moveTo(w * 0.72f, h * 0.30f); lineTo(w * 0.74f, h * 0.04f); lineTo(w * 0.54f, h * 0.20f); close()
        }
        drawPath(ears, fur)
        // Pointed face: head plus a cream muzzle.
        drawCircle(fur, w * 0.24f, Offset(w * 0.5f, h * 0.36f))
        val muzzle = Path().apply {
            moveTo(w * 0.30f, h * 0.40f); lineTo(w * 0.5f, h * 0.56f); lineTo(w * 0.70f, h * 0.40f)
            quadraticTo(w * 0.5f, h * 0.48f, w * 0.30f, h * 0.40f); close()
        }
        drawPath(muzzle, cream)
        eyes(f, w * 0.5f, h * 0.34f, w * 0.21f, w * 0.055f)
        drawCircle(Ink, w * 0.03f, Offset(w * 0.5f, h * 0.53f))
    }
}

// ---- Pingu: penguin ------------------------------------------------------------------

private fun DrawScope.penguin(f: Frame) {
    val w = size.width
    val h = size.height
    val black = Color(0xFF2A2F45)
    val orange = Color(0xFFFFA62B)
    feet(f, orange, h * 0.88f, w * 0.32f to w * 0.52f, w * 0.16f, h * 0.08f)
    // Penguins waddle: tilt instead of bob.
    rotate(degrees = if (f.walking) f.wave * 8f else f.wave * 2f, pivot = Offset(w / 2, h * 0.9f)) {
        drawOval(black, Offset(w * 0.24f, h * 0.10f), Size(w * 0.52f, h * 0.80f))
        drawOval(Color.White, Offset(w * 0.33f, h * 0.30f), Size(w * 0.34f, h * 0.56f))
        drawOval(black, Offset(w * 0.12f, h * 0.42f), Size(w * 0.16f, h * 0.30f))
        drawOval(black, Offset(w * 0.72f, h * 0.42f), Size(w * 0.16f, h * 0.30f))
        eyes(f, w * 0.5f, h * 0.27f, w * 0.18f, w * 0.04f, if (f.thinking) Color.White else Ink)
        if (!f.thinking) {
            drawCircle(Color.White, w * 0.06f, Offset(w * 0.41f, h * 0.27f), style = Stroke(w * 0.015f))
            drawCircle(Color.White, w * 0.06f, Offset(w * 0.59f, h * 0.27f), style = Stroke(w * 0.015f))
        }
        val beak = Path().apply { moveTo(w * 0.44f, h * 0.34f); lineTo(w * 0.56f, h * 0.34f); lineTo(w * 0.5f, h * 0.42f); close() }
        drawPath(beak, orange)
    }
}

// ---- Glibber: slime -----------------------------------------------------------------

private fun DrawScope.slime(f: Frame) {
    val w = size.width
    val h = size.height
    val green = Color(0xFF6BD68A)
    val dark = Color(0xFF3FAE62)
    // Squash and stretch instead of feet.
    val squash = if (f.walking) f.wave * 0.10f else f.wave * 0.04f
    scale(1f + squash, 1f - squash, pivot = Offset(w / 2, h * 0.92f)) {
        val drop = Path().apply {
            moveTo(w * 0.5f, h * 0.10f)
            cubicTo(w * 0.62f, h * 0.30f, w * 0.92f, h * 0.48f, w * 0.88f, h * 0.74f)
            cubicTo(w * 0.86f, h * 0.92f, w * 0.14f, h * 0.92f, w * 0.12f, h * 0.74f)
            cubicTo(w * 0.08f, h * 0.48f, w * 0.38f, h * 0.30f, w * 0.5f, h * 0.10f)
            close()
        }
        drawPath(drop, green)
        drawOval(dark.copy(alpha = 0.5f), Offset(w * 0.18f, h * 0.80f), Size(w * 0.64f, h * 0.10f))
        drawOval(Color.White.copy(alpha = 0.55f), Offset(w * 0.30f, h * 0.36f), Size(w * 0.10f, h * 0.14f))
        eyes(f, w * 0.5f, h * 0.60f, w * 0.22f, w * 0.06f)
        if (!f.thinking) drawArc(Ink, 20f, 140f, false, Offset(w * 0.42f, h * 0.64f), Size(w * 0.16f, h * 0.10f), style = Stroke(w * 0.02f, cap = StrokeCap.Round))
    }
}
