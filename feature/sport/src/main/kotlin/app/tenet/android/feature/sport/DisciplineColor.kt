package app.tenet.android.feature.sport

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.tenet.android.core.database.entity.Discipline

/**
 * Harmonized discipline accent (App_Konzept.md 5.3: "Sport — tertiary als
 * Basis, je Disziplin eine harmonisierte Custom Color … Kalenderpunkte,
 * Slider-Indikator"). Light values sit in the tertiary slate-blue family;
 * dark values are the lifted counterparts.
 */
@Composable
fun disciplineColor(discipline: Discipline): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (discipline) {
        Discipline.GYM ->
            if (isDark) Color(0xFFB3C8E8) else Color(0xFF4B607C)

        Discipline.CALISTHENICS ->
            if (isDark) Color(0xFF9CD4B4) else Color(0xFF3F7A5A)

        Discipline.RUNNING ->
            if (isDark) Color(0xFFF0B48A) else Color(0xFF9A5B33)
    }
}

/** German display name of a discipline. */
fun Discipline.label(): String = when (this) {
    Discipline.GYM -> "Gym"
    Discipline.CALISTHENICS -> "Calisthenics"
    Discipline.RUNNING -> "Laufen"
}
