package app.tenet.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/**
 * M3 Expressive typescale (App_Konzept.md 4.2) on the default [Typography].
 * Display, headline, title and label styles use tabular figures ("tnum"):
 * weights, times, paces and kcal line up in columns and live counters
 * (timer, distance) do not jitter. Body text keeps proportional digits.
 */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val base = Typography()

val TenetTypography = base.copy(
    displayLarge = base.displayLarge.tabular(),
    displayMedium = base.displayMedium.tabular(),
    displaySmall = base.displaySmall.tabular(),
    headlineLarge = base.headlineLarge.tabular(),
    headlineMedium = base.headlineMedium.tabular(),
    headlineSmall = base.headlineSmall.tabular(),
    titleLarge = base.titleLarge.tabular(),
    titleMedium = base.titleMedium.tabular(),
    titleSmall = base.titleSmall.tabular(),
    labelLarge = base.labelLarge.tabular(),
    labelMedium = base.labelMedium.tabular(),
    labelSmall = base.labelSmall.tabular(),
    displayLargeEmphasized = base.displayLargeEmphasized.tabular(),
    displayMediumEmphasized = base.displayMediumEmphasized.tabular(),
    displaySmallEmphasized = base.displaySmallEmphasized.tabular(),
    headlineLargeEmphasized = base.headlineLargeEmphasized.tabular(),
    headlineMediumEmphasized = base.headlineMediumEmphasized.tabular(),
    headlineSmallEmphasized = base.headlineSmallEmphasized.tabular(),
    titleLargeEmphasized = base.titleLargeEmphasized.tabular(),
    titleMediumEmphasized = base.titleMediumEmphasized.tabular(),
    titleSmallEmphasized = base.titleSmallEmphasized.tabular(),
    labelLargeEmphasized = base.labelLargeEmphasized.tabular(),
    labelMediumEmphasized = base.labelMediumEmphasized.tabular(),
    labelSmallEmphasized = base.labelSmallEmphasized.tabular(),
)
