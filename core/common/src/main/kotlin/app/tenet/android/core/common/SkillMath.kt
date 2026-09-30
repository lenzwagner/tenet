package app.tenet.android.core.common

/**
 * Pure skill-tree math (App_Konzept.md 5.2.2): evaluation of promotion
 * criteria, best values for the ladder display and the promotion rule.
 * Kept free of Room so it is unit testable; quality flags arrive as plain
 * booleans instead of the database enum.
 */
object SkillMath {

    /** One attempt (set) as seen by a criterion check. */
    data class Attempt(
        val completed: Boolean,
        /** Seconds held, used for hold criteria. */
        val seconds: Int? = null,
        /** Reps done, used for rep criteria. */
        val reps: Int? = null,
        /** True when the set was marked "mit Fehlern"; never counts. */
        val sloppy: Boolean = false,
    )

    /**
     * Criterion of one step, e.g. "3 x 10 s" -> sets=3, value=10,
     * isHold=true (value in seconds) or sets=3, value=8 (reps).
     */
    data class Criterion(
        val isHold: Boolean,
        val sets: Int,
        val value: Int,
    )

    /** Eligible = completed and not marked sloppy ("sauber gehalten"). */
    private fun eligible(attempts: List<Attempt>): List<Attempt> =
        attempts.filter { it.completed && !it.sloppy }

    private fun Attempt.reaches(criterion: Criterion): Boolean {
        val value = if (criterion.isHold) seconds ?: 0 else reps ?: 0
        return value >= criterion.value
    }

    /** How many eligible sets of this session reach the target. */
    fun qualifyingSets(attempts: List<Attempt>, criterion: Criterion): Int =
        eligible(attempts).count { it.reaches(criterion) }

    /**
     * True when at least [Criterion.sets] eligible sets of this session
     * reach the target — "the criterion is met in this session".
     */
    fun sessionMeets(attempts: List<Attempt>, criterion: Criterion): Boolean =
        qualifyingSets(attempts, criterion) >= criterion.sets

    /**
     * Best value for the ladder display ("8 s / Ziel 3 x 10 s"): longest
     * hold or most reps among eligible sets; null when nothing counts yet.
     */
    fun bestValue(attempts: List<Attempt>, isHold: Boolean): Int? =
        eligible(attempts)
            .mapNotNull { if (isHold) it.seconds else it.reps }
            .maxOrNull()

    /**
     * Promotion rule (App_Konzept.md 5.2.2): the criterion must be met in
     * two different sessions; the promotion itself is confirmed by the user
     * and never happens automatically.
     */
    fun shouldSuggestPromotion(confirmedSessions: Int): Boolean = confirmedSessions >= 2
}
