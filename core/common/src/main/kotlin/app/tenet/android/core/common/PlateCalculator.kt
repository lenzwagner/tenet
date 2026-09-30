package app.tenet.android.core.common

/**
 * Plate calculator (App_Konzept.md 5.2.1): which plates per side make up
 * [targetKg] on a given barbell. Greedy from the heaviest plate down; the
 * remainder is reported so the UI can show "not exactly loadable".
 */
object PlateCalculator {

    /** Metric plate set, heaviest first (kg). */
    val STANDARD_PLATES: List<Float> = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f, 0.5f, 0.25f)

    const val STANDARD_BAR_KG: Float = 20f

    data class Result(
        /** Plates for ONE side, heaviest first. */
        val plates: List<Float>,
        /** Weight that could not be represented with the available plates (per side). */
        val remainderPerSide: Float,
        val barKg: Float,
        val targetKg: Float,
    ) {
        /** True when the loaded weight (bar + plates) matches the target. */
        val exact: Boolean get() = kotlin.math.abs(achievedKg - targetKg) <= 0.01f

        /** Actual weight on the bar when the remainder is dropped. */
        val achievedKg: Float get() = barKg + 2f * plates.sum()
    }

    /**
     * @param targetKg total weight on the bar
     * @param barKg barbell weight (20 kg standard, 15 kg for EZ/women's bar)
     * @param plates available plate weights per side, heaviest first
     */
    fun calculate(
        targetKg: Float,
        barKg: Float = STANDARD_BAR_KG,
        plates: List<Float> = STANDARD_PLATES,
    ): Result {
        var perSide = (targetKg - barKg) / 2f
        if (perSide < 0f) return Result(emptyList(), perSide.coerceAtLeast(0f), barKg, targetKg)

        val loaded = mutableListOf<Float>()
        for (plate in plates) {
            while (perSide + 0.001f >= plate) {
                loaded += plate
                perSide -= plate
            }
        }
        // Floating point noise (e.g. 0.19999999) counts as exact.
        if (perSide < 0.01f) perSide = 0f
        return Result(loaded, perSide, barKg, targetKg)
    }
}
