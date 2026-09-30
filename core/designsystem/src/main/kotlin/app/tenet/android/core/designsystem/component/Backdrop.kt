package app.tenet.android.core.designsystem.component

import android.os.Build
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * How strongly the app below an overlay sheet is blurred (0 = sharp,
 * 1 = full blur). Written by sheets that float over the whole app (the new
 * entry sheet), read by the app root. Null outside the app root.
 */
val LocalBackdropBlur = staticCompositionLocalOf<MutableFloatState?> { null }

/** Blurs the content by [progress] (0…1) up to [maxRadiusDp]; no-op below Android 12. */
fun Modifier.backdropBlur(progress: () -> Float, maxRadiusDp: Float = 14f): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) this else graphicsLayer {
        val p = progress().coerceIn(0f, 1f)
        val r = maxRadiusDp.dp.toPx() * p
        renderEffect = if (r >= 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }
