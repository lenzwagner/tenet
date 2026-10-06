package app.tenet.android.core.common

/** Overall look of the app. */
enum class DesignStyle(val label: String, val description: String) {
    CLEAR("Klar", "Hell und ruhig wie Apps auf dem iPhone: neutrale Flächen, große Titel, Inter"),
    EXPRESSIVE("Expressiv", "Material 3 Expressive: farbige Flächen und Fotos im Seitenkopf"),
    ;

    companion object {
        fun fromName(name: String?): DesignStyle = entries.firstOrNull { it.name == name } ?: CLEAR
    }
}
