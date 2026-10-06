package app.tenet.android.core.designsystem.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ColorScheme
import app.tenet.android.core.common.ColorStyle
import com.materialkolor.hct.Hct
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Each area of the app has its own accent, all derived from the one theme
 * color so they match: analogous hues (Sport = the theme hue, Journal +50°,
 * Ernährung −50°) at the same chroma and tones, built with the same palette
 * style as the app. Neighbouring hues never clash like complementary ones.
 */
enum class AppArea(val hueShift: Double) { SPORT(0.0), NUTRITION(-50.0), JOURNAL(50.0) }

data class AreaColors(
    /** Full accent (icons on surface, progress, emphasis). */
    val accent: Color,
    val onAccent: Color,
    /** Tonal container (icon badges, chips, hero cards). */
    val container: Color,
    val onContainer: Color,
    /** Lightly tinted card background: surface with a share of the container. */
    val card: Color,
)

/** "Nur zwei Farben": areas use the app's primary (accents) and secondary (surfaces) only. */
val LocalTwoTone = staticCompositionLocalOf { false }

/** Palette style of the app, so the area schemes are built like the main one. */
val LocalColorStyle = staticCompositionLocalOf { ColorStyle.DEFAULT }

private val areaCache = HashMap<Triple<Int, Boolean, String>, ColorScheme>()

private fun areaScheme(primary: Color, area: AppArea, dark: Boolean, style: ColorStyle): ColorScheme =
    areaCache.getOrPut(Triple(primary.toArgb(), dark, "${area.name}/${style.name}")) {
        val hct = Hct.fromInt(primary.toArgb())
        val hue = ((hct.hue + area.hueShift) % 360.0 + 360.0) % 360.0
        // Same colorfulness for every area, a little above the seed's so containers read as color.
        val seed = Color(Hct.from(hue, hct.chroma.coerceAtLeast(36.0), 50.0).toInt())
        previewColorScheme(seed, null, dark, style)
    }

@Composable
@ReadOnlyComposable
fun areaColors(area: AppArea): AreaColors {
    val c = MaterialTheme.colorScheme
    val dark = c.surface.luminance() < 0.5f
    if (LocalDesignStyle.current == app.tenet.android.core.common.DesignStyle.CLEAR) {
        // "Klar": one accent, neutral white cells – no tinted area cards.
        return AreaColors(
            accent = c.primary,
            onAccent = c.onPrimary,
            container = c.secondaryContainer,
            onContainer = c.onSecondaryContainer,
            card = c.surfaceContainerLow,
        )
    }
    if (LocalTwoTone.current) {
        return AreaColors(
            accent = c.primary,
            onAccent = c.onPrimary,
            container = c.secondaryContainer,
            onContainer = c.onSecondaryContainer,
            card = lerp(c.surfaceContainerLow, c.secondaryContainer, if (dark) 0.22f else 0.30f),
        )
    }
    val scheme = areaScheme(LocalAreaSeed.current ?: c.primary, area, dark, LocalColorStyle.current)
    return AreaColors(
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        container = scheme.primaryContainer,
        onContainer = scheme.onPrimaryContainer,
        // Pastel: only a share of the container, less in dark mode (deep, saturated containers).
        card = lerp(c.surfaceContainerLow, scheme.primaryContainer, if (dark) 0.22f else 0.30f),
    )
}

/** The app-wide primary, kept when an area theme replaces primary (so areas derive from the same seed). */
private val LocalAreaSeed = staticCompositionLocalOf<Color?> { null }

/** Card colors tinted in the area's color (text stays on-surface for contrast). */
@Composable
fun areaCardColors(area: AppArea): CardColors =
    CardDefaults.cardColors(containerColor = areaColors(area).card, contentColor = MaterialTheme.colorScheme.onSurface)

/**
 * Gives a whole area (tab and its screens) its color: the surface-container
 * roles are tinted toward the area's container, so every card, list row,
 * sheet and field there picks it up; accents stay the dynamic scheme's.
 * Background/surface stay neutral, so text contrast does not change.
 */
@Composable
fun AreaTheme(area: AppArea, content: @Composable () -> Unit) {
    // Two-tone and "Klar": the app scheme already is what every area uses.
    if (LocalTwoTone.current || LocalDesignStyle.current == app.tenet.android.core.common.DesignStyle.CLEAR) return content()
    val base = MaterialTheme.colorScheme
    val colors = areaColors(area)
    val tint = colors.container
    val seed = LocalAreaSeed.current ?: base.primary
    val own = areaScheme(seed, area, base.surface.luminance() < 0.5f, LocalColorStyle.current)
    val scheme = androidx.compose.runtime.remember(base, area, colors, own) {
        fun mix(c: Color, k: Float) = lerp(c, tint, k * 0.7f)
        base.copy(
            // The area's accent leads here (buttons, segments, switches, FABs).
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.container,
            onPrimaryContainer = colors.onContainer,
            surfaceTint = colors.accent,
            // Tonal chips and filled-tonal buttons follow the area too (Sport keeps its secondary).
            // Secondary and tertiary from the area's own scheme: every accent there shares its hue family.
            secondary = own.secondary,
            onSecondary = own.onSecondary,
            secondaryContainer = lerp(colors.container, base.surfaceContainerHigh, 0.45f),
            onSecondaryContainer = colors.onContainer,
            tertiary = own.tertiary,
            onTertiary = own.onTertiary,
            tertiaryContainer = own.tertiaryContainer,
            onTertiaryContainer = own.onTertiaryContainer,
            surfaceContainerLowest = mix(base.surfaceContainerLowest, 0.10f),
            surfaceContainerLow = mix(base.surfaceContainerLow, 0.22f),
            surfaceContainer = mix(base.surfaceContainer, 0.30f),
            surfaceContainerHigh = mix(base.surfaceContainerHigh, 0.36f),
            surfaceContainerHighest = mix(base.surfaceContainerHighest, 0.42f),
            surfaceVariant = mix(base.surfaceVariant, 0.30f),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAreaSeed provides seed) {
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MaterialTheme.motionScheme,
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
