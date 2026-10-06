package app.tenet.android.core.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import app.tenet.android.core.common.DesignStyle
import app.tenet.android.core.designsystem.R

/** Current [DesignStyle]; components that differ between the looks read it. */
val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.CLEAR }

/** True for the iOS-like "Klar" look. */
val isClearStyle: Boolean
    @Composable @ReadOnlyComposable get() = LocalDesignStyle.current == DesignStyle.CLEAR

/**
 * "Klar" surfaces after Apple's grouped style: a light grey ground
 * (#F2F2F7, black in dark mode) with white cells and cards (#1C1C1E), grey
 * fills for fields and tracks, grey secondary text and hairline separators.
 * The accent colors of the scheme stay; nothing gets a tonal tint.
 */
fun ColorScheme.clear(dark: Boolean): ColorScheme = if (dark) {
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF2C2C2E),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF1C1C1E),
        surfaceContainer = Color(0xFF1C1C1E),
        surfaceContainerHigh = Color(0xFF2C2C2E),
        surfaceContainerHighest = Color(0xFF3A3A3C),
        surfaceVariant = Color(0xFF2C2C2E),
        surfaceTint = Color.Transparent,
        onBackground = Color(0xFFF5F5F7),
        onSurface = Color(0xFFF5F5F7),
        onSurfaceVariant = Color(0xFF98989F),
        outline = Color(0xFF545458),
        outlineVariant = Color(0xFF38383A),
        inverseSurface = Color(0xFFF2F2F7),
        inverseOnSurface = Color(0xFF1C1C1E),
    )
} else {
    copy(
        background = Color(0xFFF2F2F7),
        surface = Color(0xFFF2F2F7),
        surfaceDim = Color(0xFFE5E5EA),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFEBEBF0),
        surfaceContainerHighest = Color(0xFFE3E3E8),
        surfaceVariant = Color(0xFFE3E3E8),
        surfaceTint = Color.Transparent,
        onBackground = Color(0xFF111113),
        onSurface = Color(0xFF111113),
        onSurfaceVariant = Color(0xFF6E6E73),
        outline = Color(0xFFC6C6C8),
        outlineVariant = Color(0xFFDCDCE0),
        inverseSurface = Color(0xFF2C2C2E),
        inverseOnSurface = Color.White,
    )
}

/** Inter (SIL OFL, variable): text optical size for small text… */
@OptIn(ExperimentalTextApi::class)
private fun interFamily(opticalSize: Float) = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            resId = R.font.inter,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

/** …and Inter Display cut for titles, tighter like SF Pro Display. */
private val InterText = interFamily(14f)
private val InterDisplay = interFamily(32f)

/**
 * "Klar" typescale: Inter in place of Roboto, weights and tracking after
 * Apple's: bold large titles with negative tracking, semibold headlines,
 * regular body, slightly tighter everywhere. Sizes stay M3's, so layouts
 * do not shift; tabular figures stay where [TenetTypography] has them.
 */
fun clearTypography(base: Typography): Typography {
    fun TextStyle.inter(weight: Int, tracking: Float, display: Boolean = false) = copy(
        fontFamily = if (display) InterDisplay else InterText,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.em,
    )
    return base.copy(
        displayLarge = base.displayLarge.inter(700, -0.025f, true),
        displayMedium = base.displayMedium.inter(700, -0.025f, true),
        displaySmall = base.displaySmall.inter(700, -0.022f, true),
        headlineLarge = base.headlineLarge.inter(700, -0.022f, true),
        headlineMedium = base.headlineMedium.inter(700, -0.02f, true),
        headlineSmall = base.headlineSmall.inter(700, -0.018f, true),
        titleLarge = base.titleLarge.inter(700, -0.017f, true),
        titleMedium = base.titleMedium.inter(600, -0.012f),
        titleSmall = base.titleSmall.inter(600, -0.008f),
        bodyLarge = base.bodyLarge.inter(400, -0.011f),
        bodyMedium = base.bodyMedium.inter(400, -0.006f),
        bodySmall = base.bodySmall.inter(400, 0f),
        labelLarge = base.labelLarge.inter(600, -0.006f),
        labelMedium = base.labelMedium.inter(500, 0f),
        labelSmall = base.labelSmall.inter(500, 0.005f),
        displayLargeEmphasized = base.displayLargeEmphasized.inter(800, -0.025f, true),
        displayMediumEmphasized = base.displayMediumEmphasized.inter(800, -0.025f, true),
        displaySmallEmphasized = base.displaySmallEmphasized.inter(800, -0.022f, true),
        headlineLargeEmphasized = base.headlineLargeEmphasized.inter(800, -0.022f, true),
        headlineMediumEmphasized = base.headlineMediumEmphasized.inter(800, -0.02f, true),
        headlineSmallEmphasized = base.headlineSmallEmphasized.inter(800, -0.018f, true),
        titleLargeEmphasized = base.titleLargeEmphasized.inter(800, -0.017f, true),
        titleMediumEmphasized = base.titleMediumEmphasized.inter(700, -0.012f),
        titleSmallEmphasized = base.titleSmallEmphasized.inter(700, -0.008f),
        bodyLargeEmphasized = base.bodyLargeEmphasized.inter(600, -0.011f),
        bodyMediumEmphasized = base.bodyMediumEmphasized.inter(600, -0.006f),
        bodySmallEmphasized = base.bodySmallEmphasized.inter(600, 0f),
        labelLargeEmphasized = base.labelLargeEmphasized.inter(700, -0.006f),
        labelMediumEmphasized = base.labelMediumEmphasized.inter(600, 0f),
        labelSmallEmphasized = base.labelSmallEmphasized.inter(600, 0.005f),
    )
}

/** Card colors of the current look: white cells on grey ground in "Klar". */
@Composable
fun tenetCardColors(): CardColors =
    if (isClearStyle) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        CardDefaults.cardColors()
    }

/** [Card] with the look's default colors; use instead of a plain Card. */
@Composable
fun TenetCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = tenetCardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)

/** Clickable [TenetCard]. */
@Composable
fun TenetCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = tenetCardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Card(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    content = content,
)

/**
 * Colors for list rows on the page background. "Klar" keeps checked or
 * selected rows white like iOS (the switch or check mark shows the state);
 * Expressive tints them.
 */
@Composable
fun tenetListColors(): androidx.compose.material3.ListItemColors {
    val c = MaterialTheme.colorScheme
    return if (isClearStyle) {
        androidx.compose.material3.ListItemDefaults.segmentedColors(
            containerColor = c.surfaceContainer,
            selectedContainerColor = c.surfaceContainer,
            selectedContentColor = c.onSurface,
            selectedSupportingContentColor = c.onSurfaceVariant,
            selectedLeadingContentColor = c.onSurfaceVariant,
            selectedTrailingContentColor = c.onSurfaceVariant,
        )
    } else {
        androidx.compose.material3.ListItemDefaults.segmentedColors(containerColor = c.surfaceContainer)
    }
}
