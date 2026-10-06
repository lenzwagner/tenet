package app.tenet.android.core.common

import kotlin.math.roundToInt

/**
 * Progressive overload by double progression (App_Konzept.md 5.2.1
 * "Progressive Overload auf einen Blick"):
 * - all working sets of the last session reached the target reps → more weight,
 * - missed the target twice in a row at the same weight → deload 10 %,
 * - otherwise keep the weight and aim for more reps.
 * Lower-body lifts progress in bigger steps than upper-body ones.
 */
object OverloadMath {

    enum class Decision { INCREASE, KEEP, DELOAD, FIRST_TIME }

    /** Per-exercise rule; nulls fall back to the defaults. */
    data class Rule(
        /** Top of the rep range: add weight only when every top set reaches it. */
        val repMax: Int? = null,
        val stepKg: Float? = null,
        val deloadPercent: Int? = null,
    )

    /** One completed working set. */
    data class WorkSet(val weightKg: Float, val reps: Int)

    data class Suggestion(
        val weightKg: Float,
        val decision: Decision,
        /** Heaviest weight of the last session (null on the first session). */
        val lastWeightKg: Float?,
        val step: Float,
        /** Provenance of a cross-exercise estimate, never a performed result. */
        val estimatedFrom: String? = null,
    ) {
        val deltaKg: Float get() = lastWeightKg?.let { weightKg - it } ?: 0f
    }

    private val LOWER_BODY = listOf("bein", "quad", "gesäß", "glute", "hamstring", "wade", "hüft", "po")

    /** Weight step: 5 kg for lower body, 2.5 kg for the rest, 1 kg for light loads. */
    fun step(primaryMuscles: String, lastWeightKg: Float?): Float = when {
        lastWeightKg != null && lastWeightKg < 20f -> 1f
        LOWER_BODY.any { primaryMuscles.contains(it, ignoreCase = true) } -> 5f
        else -> 2.5f
    }

    /**
     * [history]: completed working sets per past session, newest session
     * first. Bodyweight exercises (weight 0) are never loaded up.
     */
    fun suggest(
        history: List<List<WorkSet>>,
        targetReps: Int,
        primaryMuscles: String,
        rule: Rule = Rule(),
    ): Suggestion {
        val last = history.firstOrNull { it.isNotEmpty() }
            ?: return Suggestion(0f, Decision.FIRST_TIME, null, rule.stepKg ?: step(primaryMuscles, null))
        val lastWeight = last.maxOf { it.weightKg }
        val step = rule.stepKg?.takeIf { it > 0f } ?: step(primaryMuscles, lastWeight)
        if (lastWeight <= 0f) return Suggestion(0f, Decision.KEEP, lastWeight, step)

        val topSets = last.filter { it.weightKg == lastWeight }
        val goal = rule.repMax?.takeIf { it >= targetReps } ?: targetReps
        val hit = topSets.all { it.reps >= goal }
        // Round on the plate grid, not on the step: 62.5 + 5 must stay 67.5 (not jump to 70).
        val grid = minOf(step, 2.5f)
        if (hit) return Suggestion(round(lastWeight + step, grid), Decision.INCREASE, lastWeight, step)

        // Missed twice in a row at the same weight → deload.
        val previous = history.drop(history.indexOf(last) + 1).firstOrNull { it.isNotEmpty() }
        val missedBefore = previous != null &&
            previous.maxOf { it.weightKg } == lastWeight &&
            previous.filter { it.weightKg == lastWeight }.any { it.reps < targetReps }
        val deload = 1f - (rule.deloadPercent ?: 10).coerceIn(0, 50) / 100f
        if (missedBefore) return Suggestion(round(lastWeight * deload, grid), Decision.DELOAD, lastWeight, step)
        return Suggestion(lastWeight, Decision.KEEP, lastWeight, step)
    }

    /** Rounds to the nearest plate step (never below one step). */
    fun round(weight: Float, step: Float): Float =
        ((weight / step).roundToInt() * step).coerceAtLeast(step).let { (it * 100).roundToInt() / 100f }

    fun label(s: Suggestion): String = when (s.decision) {
        Decision.FIRST_TIME ->
            s.estimatedFrom?.let { "$it · etwa 2 Wdh Reserve" }
                ?: if (s.weightKg > 0f) "Startwert aus der Einrichtung · etwa 2 Wdh Reserve" else "Erstes Mal – Startgewicht wählen"
        Decision.INCREASE -> "+${fmt(s.deltaKg)} kg · Ziel beim letzten Mal geschafft"
        Decision.KEEP -> if ((s.lastWeightKg ?: 0f) <= 0f) "Eigengewicht · mehr Wiederholungen" else "Gewicht halten · Wiederholungen steigern"
        Decision.DELOAD -> "Deload −${deloadPct(s)} % · zweimal knapp verpasst"
    }

    private fun deloadPct(s: Suggestion): Int =
        s.lastWeightKg?.takeIf { it > 0f }?.let { ((1f - s.weightKg / it) * 100).roundToInt() } ?: 10

    private fun fmt(v: Float) = if (v % 1f == 0f) v.toInt().toString() else v.toString().replace('.', ',')
}
