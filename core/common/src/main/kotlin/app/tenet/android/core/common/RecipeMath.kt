package app.tenet.android.core.common

object RecipeMath {
    private val TimeRegex = Regex("""(\d+)\s*(Minuten|Minute|Min\.?|min|Sekunden|Sek\.?|s)(?![a-zäöü])""", RegexOption.IGNORE_CASE)

    /** First time span in a recipe step ("10 Min", "30 Sek") in seconds, or null (Kochmodus-Timer). */
    fun stepTimerSeconds(step: String): Int? {
        val m = TimeRegex.find(step) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        return if (m.groupValues[2].startsWith("s", ignoreCase = true)) n else n * 60
    }

    /** Ingredient amount for [servings] when the recipe is written for [baseServings]. */
    fun scale(grams: Float, baseServings: Int, servings: Int): Float =
        grams * servings / baseServings.coerceAtLeast(1)
}
