package app.tenet.android.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.tenet.android.core.common.ColorStyle

/** Default brand seed; also the primary when only a secondary color is picked. */
val DefaultSeed = Color(0xFF006A6A)

// Fallback seed: deep petrol #006A6A (App_Konzept.md 4.1). Every M3 color
// role is set explicitly – the surface-container roles in particular would
// otherwise fall back to the purple baseline palette.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F0),
    onPrimaryContainer = Color(0xFF004F4F),
    inversePrimary = Color(0xFF80D5D4),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF324B4B),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF334863),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceTint = Color(0xFF006A6A),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F1),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4FBFA),
    surfaceDim = Color(0xFFD5DBDA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F4),
    surfaceContainer = Color(0xFFE9EFEE),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDDE4E3),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D5D4),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFF9CF1F0),
    inversePrimary = Color(0xFF006A6A),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceTint = Color(0xFF80D5D4),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3231),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4948),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A3A),
    surfaceDim = Color(0xFF0E1514),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF303636),
)

/**
 * App theme on M3 Expressive: full color roles, expressive spring motion,
 * the expressive corner scale and the emphasized typescale.
 */
@Composable
fun TenetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    colorStyle: ColorStyle = ColorStyle.DEFAULT,
    /** Only primary and secondary: tertiary roles take the secondary ones, no area colors. */
    twoTone: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> when (colorStyle) {
            // "Ruhig" = exactly the system's own wallpaper scheme.
            ColorStyle.TONAL -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Other styles: same wallpaper seed (system accent), richer palette.
            else -> {
                val seed = Color(context.getColor(android.R.color.system_accent1_500))
                remember(seed, darkTheme, colorStyle) { schemeFor(seed, null, darkTheme, colorStyle) }
            }
        }

        // User-picked seeds: full M3 tonal scheme via the material color
        // utilities (HCT), so every role (containers, surfaces, …) follows.
        primaryColor != null || secondaryColor != null -> remember(primaryColor, secondaryColor, darkTheme, colorStyle) {
            schemeFor(primaryColor ?: DefaultSeed, secondaryColor, darkTheme, colorStyle)
        }

        darkTheme -> remember(colorStyle) { if (colorStyle == ColorStyle.TONAL) DarkColorScheme else schemeFor(DefaultSeed, null, true, colorStyle) }
        else -> remember(colorStyle) { if (colorStyle == ColorStyle.TONAL) LightColorScheme else schemeFor(DefaultSeed, null, false, colorStyle) }
    }

    val finalScheme = if (!twoTone) colorScheme else remember(colorScheme) {
        colorScheme.copy(
            tertiary = colorScheme.secondary,
            onTertiary = colorScheme.onSecondary,
            tertiaryContainer = colorScheme.secondaryContainer,
            onTertiaryContainer = colorScheme.onSecondaryContainer,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalColorStyle provides colorStyle, LocalTwoTone provides twoTone) {
        MaterialExpressiveTheme(
            colorScheme = finalScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = TenetShapes,
            typography = TenetTypography,
            content = content,
        )
    }
}

/** Scheme the app would use for these seeds; for live previews in the color picker. */
fun previewColorScheme(primary: Color?, secondary: Color?, darkTheme: Boolean, style: ColorStyle = ColorStyle.DEFAULT): ColorScheme =
    schemeFor(primary ?: DefaultSeed, secondary, darkTheme, style)

private fun schemeFor(primary: Color, secondary: Color?, darkTheme: Boolean, style: ColorStyle): ColorScheme =
    dynamicColorScheme(
        primary = primary,
        secondary = secondary,
        isDark = darkTheme,
        style = when (style) {
            ColorStyle.TONAL -> PaletteStyle.TonalSpot
            ColorStyle.VIBRANT -> PaletteStyle.Vibrant
            ColorStyle.EXPRESSIVE -> PaletteStyle.Expressive
            ColorStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
        },
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
