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
import com.materialkolor.ktx.harmonize

/**
 * Each area of the app owns one M3 accent role, so its color travels with
 * it (card on "Heute", cards in its tab): Sport = primary, Ernährung =
 * secondary, Journal = tertiary. All roles come from the (dynamic) scheme.
 */
enum class AppArea { SPORT, NUTRITION, JOURNAL }

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

/** Food = green, as an M3 custom color: harmonized to the theme and expanded into a tonal palette. */
private val NutritionSeed = Color(0xFF3FA34D)
private val customCache = HashMap<Pair<Int, Boolean>, ColorScheme>()

private fun nutritionScheme(primary: Color, dark: Boolean): ColorScheme =
    customCache.getOrPut(primary.toArgb() to dark) {
        previewColorScheme(NutritionSeed.harmonize(primary, matchSaturation = false), null, dark, ColorStyle.TONAL)
    }

@Composable
@ReadOnlyComposable
fun areaColors(area: AppArea): AreaColors {
    val c = MaterialTheme.colorScheme
    val (accent, onAccent, container, onContainer) = when (area) {
        AppArea.SPORT -> listOf(c.primary, c.onPrimary, c.primaryContainer, c.onPrimaryContainer)
        AppArea.NUTRITION -> nutritionScheme(c.primary, c.surface.luminance() < 0.5f).let {
            listOf(it.primary, it.onPrimary, it.primaryContainer, it.onPrimaryContainer)
        }
        AppArea.JOURNAL -> listOf(c.tertiary, c.onTertiary, c.tertiaryContainer, c.onTertiaryContainer)
    }
    return AreaColors(
        accent = accent,
        onAccent = onAccent,
        container = container,
        onContainer = onContainer,
        // Dark containers are deep and saturated: a smaller share keeps cards calm.
        card = lerp(c.surfaceContainerLow, container, if (c.surface.luminance() < 0.5f) 0.30f else 0.42f),
    )
}

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
    val base = MaterialTheme.colorScheme
    val colors = areaColors(area)
    val tint = colors.container
    val scheme = androidx.compose.runtime.remember(base, area) {
        fun mix(c: Color, k: Float) = lerp(c, tint, k)
        base.copy(
            // The area's accent leads here (buttons, segments, switches, FABs).
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.container,
            onPrimaryContainer = colors.onContainer,
            surfaceTint = colors.accent,
            // Tonal chips and filled-tonal buttons follow the area too (Sport keeps its secondary).
            secondaryContainer = if (area == AppArea.SPORT) base.secondaryContainer else lerp(colors.container, base.surfaceContainerHigh, 0.35f),
            onSecondaryContainer = if (area == AppArea.SPORT) base.onSecondaryContainer else colors.onContainer,
            surfaceContainerLowest = mix(base.surfaceContainerLowest, 0.10f),
            surfaceContainerLow = mix(base.surfaceContainerLow, 0.22f),
            surfaceContainer = mix(base.surfaceContainer, 0.30f),
            surfaceContainerHigh = mix(base.surfaceContainerHigh, 0.36f),
            surfaceContainerHighest = mix(base.surfaceContainerHighest, 0.42f),
            surfaceVariant = mix(base.surfaceVariant, 0.30f),
        )
    }
    androidx.compose.material3.MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}
