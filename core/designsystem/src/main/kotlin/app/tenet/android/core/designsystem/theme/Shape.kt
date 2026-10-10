package app.tenet.android.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive corner scale. Components pick their role from here
 * (cards = medium, sheets/dialogs = extraLarge, …); never hardcode radii.
 */
val TenetShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/** "Klar": the smallest role (menus, tooltips) rounded like iOS context menus. */
val ClearShapes = TenetShapes.copy(extraSmall = RoundedCornerShape(14.dp))
