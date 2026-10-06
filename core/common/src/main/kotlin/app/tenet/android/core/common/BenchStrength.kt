package app.tenet.android.core.common

import kotlin.math.floor

/**
 * Rough starting-load transfer, never a personal record or measured flat-bench 1RM.
 * Incline defaults to 30 degrees because the exercise catalog does not store angles.
 * Group means: Rodriguez-Ridao et al. 2020, doi:10.3390/ijerph17197339, Table 1.
 * Dumbbell total/barbell ratio: Saeterbakken et al. 2011, doi:10.1080/02640414.2010.543916.
 * Combining those studies for incline dumbbells is a heuristic, not a validated equation.
 */
object BenchStrength {
    const val INCLINE_30_RATIO = 63.3f / 81.4f
    const val DUMBBELL_TOTAL_RATIO = 0.83f
    const val DUMBBELL_PER_HAND_RATIO = DUMBBELL_TOTAL_RATIO / 2f
    const val INCLINE_DUMBBELL_RATIO = INCLINE_30_RATIO * DUMBBELL_PER_HAND_RATIO

    enum class Variant(val exerciseId: String, val label: String, val perHand: Boolean, val relativeLoad: Float) {
        FLAT_BARBELL("ex-bankdruecken", "Bankdrücken (LH)", false, 1f),
        INCLINE_BARBELL("ex-schraegbank-lh", "Schrägbank (LH, 30°)", false, INCLINE_30_RATIO),
        FLAT_DUMBBELL("ex-kh-bank", "Bankdrücken (KH, je Hand)", true, DUMBBELL_PER_HAND_RATIO),
        INCLINE_DUMBBELL("ex-schraegbank", "Schrägbank (KH, 30°, je Hand)", true, INCLINE_DUMBBELL_RATIO),
    }

    data class Estimate(val source: Variant, val target: Variant, val oneRepMaxKg: Float) {
        val label: String get() = "Grobe Schätzung aus ${source.label}" +
            if (source == Variant.INCLINE_BARBELL || source == Variant.INCLINE_DUMBBELL ||
                target == Variant.INCLINE_BARBELL || target == Variant.INCLINE_DUMBBELL) " · Annahme: 30°" else ""
    }

    fun variant(exerciseId: String): Variant? = Variant.entries.firstOrNull { it.exerciseId == exerciseId }

    /** Conservative eligibility for automatic transfers; high-rep sets are not extrapolated. */
    fun estimate(
        source: Variant,
        target: Variant,
        weightKg: Float,
        reps: Int,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): Estimate? {
        if (reps !in 1..10 || !weightKg.isFinite() || weightKg <= 0f || source == target) return null
        val sourceMax = OneRepMax.oneRepMax(weightKg, reps, formula)
        val targetMax = sourceMax / source.relativeLoad * target.relativeLoad
        return targetMax.takeIf { it.isFinite() && it > 0f }?.let { Estimate(source, target, it) }
    }

    data class Performance(
        val variant: Variant,
        val weightKg: Float,
        val reps: Int,
        val sessionId: String,
        val startedAt: Long,
        val completed: Boolean,
        val warmup: Boolean,
    )

    /** Latest eligible session, then best set; old and unticked data are not evidence. */
    fun fromHistory(
        target: Variant,
        performances: List<Performance>,
        now: Long,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): Estimate? {
        val cutoff = now - 90L * 24 * 60 * 60 * 1000
        val eligible = performances.filter {
            it.completed && !it.warmup && it.startedAt in cutoff..now &&
                estimate(it.variant, target, it.weightKg, it.reps, formula) != null
        }
        val latest = eligible.maxByOrNull { it.startedAt } ?: return null
        return eligible.filter { it.sessionId == latest.sessionId }
            // Prefer matching equipment when the same session includes both LH and KH.
            .groupBy { it.variant.perHand == target.perHand }
            .let { it[true] ?: it[false].orEmpty() }
            .mapNotNull { estimate(it.variant, target, it.weightKg, it.reps, formula) }
            .maxByOrNull { it.oneRepMaxKg }
    }

    fun applyToSuggestion(
        own: OverloadMath.Suggestion,
        estimate: Estimate?,
        targetReps: Int,
        setupWeightKg: Float? = null,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): OverloadMath.Suggestion {
        if (own.decision != OverloadMath.Decision.FIRST_TIME) return own
        val weight = estimate?.let { workingWeight(it, targetReps, formula) } ?: 0f
        return when {
            weight > 0f -> own.copy(weightKg = weight, estimatedFrom = estimate!!.label)
            setupWeightKg != null && setupWeightKg.isFinite() && setupWeightKg > 0f -> own.copy(weightKg = setupWeightKg)
            else -> own
        }
    }

    /** Inverse of the chosen formula, two reps in reserve, rounded down to the load grid. */
    fun workingWeight(estimate: Estimate, reps: Int, formula: OneRepMaxFormula, stepKg: Float = 2.5f): Float {
        if (reps !in 1..10 || !stepKg.isFinite() || stepKg <= 0f) return 0f
        val raw = OneRepMax.weightForReps(estimate.oneRepMaxKg, reps + 2, formula)
        return (floor(raw / stepKg) * stepKg).coerceAtLeast(0f)
    }
}
