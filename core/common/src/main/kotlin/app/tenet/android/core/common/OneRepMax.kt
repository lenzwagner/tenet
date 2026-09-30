package app.tenet.android.core.common

/**
 * 1RM estimation formulas (App_Konzept.md 5.2.1). Epley is the default;
 * the user can switch to Brzycki in the settings.
 */
enum class OneRepMaxFormula(val label: String, val description: String) {
    EPLEY("Epley", "1RM = Gewicht × (1 + Wdh / 30)"),
    BRZYCKI("Brzycki", "1RM = Gewicht × 36 / (36 − Wdh)"),
}

object OneRepMax {

    fun epley(weight: Float, reps: Int): Float = when {
        weight <= 0f || reps <= 0 -> 0f
        reps == 1 -> weight
        else -> weight * (1f + reps / 30f)
    }

    /** Brzycki is only defined for reps < 36; beyond that we fall back to 0. */
    fun brzycki(weight: Float, reps: Int): Float = when {
        weight <= 0f || reps <= 0 -> 0f
        reps == 1 -> weight
        reps >= 36 -> 0f
        else -> weight * 36f / (36f - reps)
    }

    fun oneRepMax(weight: Float, reps: Int, formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY): Float =
        when (formula) {
            OneRepMaxFormula.EPLEY -> epley(weight, reps)
            OneRepMaxFormula.BRZYCKI -> brzycki(weight, reps)
        }

    fun best(
        pairs: Sequence<Pair<Float, Int>>,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): Float = pairs.maxOfOrNull { (weight, reps) -> oneRepMax(weight, reps, formula) } ?: 0f
}
