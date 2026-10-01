package app.tenet.android.core.common

/**
 * How colorful the M3 scheme is built from the seed (wallpaper or picked
 * color), like Android's "Farbpaletten": the same seed, different chroma
 * for accents and surfaces.
 */
enum class ColorStyle(val label: String, val description: String) {
    TONAL("Ruhig", "Dezente Töne, wie das System"),
    VIBRANT("Kräftig", "Satte Akzente, leicht getönte Flächen"),
    EXPRESSIVE("Expressiv", "Verspielte Farbkontraste"),
    FRUIT_SALAD("Bunt", "Drei klar verschiedene Akzentfarben"),
    ;

    companion object {
        val DEFAULT = VIBRANT
        fun fromName(name: String?): ColorStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
