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

/**
 * Newsreader (SIL OFL, variable weight/optical size) as an optional reading
 * font for diary and dreams (App_Konzept.md 4.2).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val NewsreaderFamily = androidx.compose.ui.text.font.FontFamily(
    listOf(400, 500, 600, 700).flatMap { weight ->
        listOf(false, true).map { italic ->
            androidx.compose.ui.text.font.Font(
                resId = if (italic) app.tenet.android.core.designsystem.R.font.newsreader_italic
                else app.tenet.android.core.designsystem.R.font.newsreader,
                weight = androidx.compose.ui.text.font.FontWeight(weight),
                style = if (italic) androidx.compose.ui.text.font.FontStyle.Italic
                else androidx.compose.ui.text.font.FontStyle.Normal,
                variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
                    androidx.compose.ui.text.font.FontVariation.weight(weight),
                ),
            )
        }
    },
)

/** Setting "Serifenschrift im Journal": diary and dream text in [NewsreaderFamily]. */
val LocalJournalSerif = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Reading/writing style for long journal text in the serif: a touch larger
 * with more line height, as serifs read smaller than the UI font.
 */
fun serifReadingStyle(base: TextStyle): TextStyle = base.copy(
    fontFamily = NewsreaderFamily,
    fontSize = base.fontSize * 1.08f,
    lineHeight = base.lineHeight * 1.12f,
)

/**
 * Journal text in the serif when [enabled]: body, title and headline styles
 * switch to [NewsreaderFamily] for [content] (Markdown previews, editor
 * fields), labels and everything else stay in the UI font.
 */
@androidx.compose.runtime.Composable
fun JournalReading(enabled: Boolean = LocalJournalSerif.current, content: @androidx.compose.runtime.Composable () -> Unit) {
    if (!enabled) return content()
    val t = androidx.compose.material3.MaterialTheme.typography
    val serif = androidx.compose.runtime.remember(t) {
        fun TextStyle.s() = copy(fontFamily = NewsreaderFamily)
        t.copy(
            bodyLarge = serifReadingStyle(t.bodyLarge),
            bodyMedium = serifReadingStyle(t.bodyMedium),
            bodySmall = serifReadingStyle(t.bodySmall),
            titleLarge = t.titleLarge.s(),
            titleMedium = t.titleMedium.s(),
            titleSmall = t.titleSmall.s(),
            headlineSmall = t.headlineSmall.s(),
            headlineMedium = t.headlineMedium.s(),
        )
    }
    androidx.compose.material3.MaterialExpressiveTheme(
        colorScheme = androidx.compose.material3.MaterialTheme.colorScheme,
        motionScheme = androidx.compose.material3.MaterialTheme.motionScheme,
        shapes = androidx.compose.material3.MaterialTheme.shapes,
        typography = serif,
        content = content,
    )
}
