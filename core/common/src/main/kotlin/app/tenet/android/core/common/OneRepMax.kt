package app.tenet.android.core.common

/**
 * 1RM estimation formulas (App_Konzept.md 5.2.1). Epley is the default;
 * the user can switch to Brzycki in the settings.
 */
enum class OneRepMaxFormula(val label: String, val description: String) {
    EPLEY("Epley", "1RM = Gewicht × (1 + Wdh / 30)"),
    BRZYCKI("Brzycki", "1RM = Gewicht × 36 / (37 − Wdh)"),
}

object OneRepMax {

    fun epley(weight: Float, reps: Int): Float = when {
        !weight.isFinite() || weight <= 0f || reps <= 0 -> 0f
        reps == 1 -> weight
        else -> weight * (1f + reps / 30f)
    }

    /** Brzycki is only defined for reps < 37; beyond that we fall back to 0. */
    fun brzycki(weight: Float, reps: Int): Float = when {
        !weight.isFinite() || weight <= 0f || reps <= 0 -> 0f
        reps == 1 -> weight
        reps >= 37 -> 0f
        else -> weight * 36f / (37f - reps)
    }

    fun oneRepMax(weight: Float, reps: Int, formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY): Float =
        when (formula) {
            OneRepMaxFormula.EPLEY -> epley(weight, reps)
            OneRepMaxFormula.BRZYCKI -> brzycki(weight, reps)
        }

    /** Inverse equation for planning loads; reps include any chosen reserve. */
    fun weightForReps(oneRepMax: Float, reps: Int, formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY): Float {
        if (!oneRepMax.isFinite() || oneRepMax <= 0f || reps <= 0) return 0f
        if (reps == 1) return oneRepMax
        return when (formula) {
            OneRepMaxFormula.EPLEY -> oneRepMax / (1f + reps / 30f)
            OneRepMaxFormula.BRZYCKI -> if (reps < 37) oneRepMax * (37f - reps) / 36f else 0f
        }
    }

    fun best(
        pairs: Sequence<Pair<Float, Int>>,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): Float = pairs.maxOfOrNull { (weight, reps) -> oneRepMax(weight, reps, formula) } ?: 0f
}
