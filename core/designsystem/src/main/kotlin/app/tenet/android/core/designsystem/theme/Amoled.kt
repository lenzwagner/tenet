package app.tenet.android.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Dream pages switch to pure black in dark mode (setting "AMOLED-Schwarz: Nur Träume"). */
val LocalAmoledDreams = staticCompositionLocalOf { false }

/**
 * Dark scheme on pure black: background and surface become #000, the
 * container roles keep their order (low < … < highest) but sit much closer
 * to black, so cards still read as raised. Accents stay unchanged.
 */
fun ColorScheme.amoled(): ColorScheme {
    fun dim(c: Color, k: Float) = lerp(Color.Black, c, k)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = dim(surfaceBright, 0.7f),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = dim(surfaceContainerLow, 0.45f),
        surfaceContainer = dim(surfaceContainer, 0.55f),
        surfaceContainerHigh = dim(surfaceContainerHigh, 0.65f),
        surfaceContainerHighest = dim(surfaceContainerHighest, 0.75f),
        surfaceVariant = dim(surfaceVariant, 0.7f),
    )
}

/**
 * Puts [content] on pure black when [enabled] and the current theme is
 * dark; light mode stays untouched. With [paintBackground] it fills its
 * bounds with the background, so a pager page turns black as a whole;
 * screens with their own Scaffold or sheet do not need that.
 */
@Composable
fun AmoledScope(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    paintBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    if (!enabled || base.surface.luminance() >= 0.5f || base.surface == Color.Black) {
        return Box(modifier) { content() }
    }
    val scheme = remember(base) { base.amoled() }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        Box(if (paintBackground) modifier.background(scheme.background) else modifier) { content() }
    }
}

/** Dream pages: [AmoledScope] driven by the app setting. */
@Composable
fun DreamAmoledScope(modifier: Modifier = Modifier, paintBackground: Boolean = false, content: @Composable () -> Unit) =
    AmoledScope(LocalAmoledDreams.current, modifier, paintBackground, content)
