package app.tenet.android.core.common

/**
 * Pure black instead of the dark surfaces (App_Konzept.md 4.1): saves power
 * on OLED screens and is easier on the eyes at night. Only in dark mode.
 */
enum class AmoledMode(val label: String, val description: String) {
    OFF("Aus", "Normale dunkle Flächen"),
    DREAMS("Nur Träume", "Traumseite und Traum-Editor in Schwarz, z. B. nachts"),
    APP("Ganze App", "Alle Seiten im dunklen Modus in Schwarz"),
    ;

    companion object {
        fun fromName(name: String?): AmoledMode = entries.firstOrNull { it.name == name } ?: OFF
    }
}
