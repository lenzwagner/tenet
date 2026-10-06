package app.tenet.android.core.common

/** Overall look of the app. */
enum class DesignStyle(val label: String, val description: String) {
    CLEAR("Klar", "Hell und ruhig wie Apps auf dem iPhone: neutrale Flächen, Inter-Schrift"),
    EXPRESSIVE("Expressiv", "Material 3 Expressive: farbig getönte Flächen und Formen"),
    ;

    companion object {
        fun fromName(name: String?): DesignStyle = entries.firstOrNull { it.name == name } ?: CLEAR
    }
}
