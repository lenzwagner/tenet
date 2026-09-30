package app.tenet.android.core.common

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Does the running plan still fit the runner? Compares goal time with the
 * prognosis and the pace anchor (5 km form at plan creation) with the
 * current form from recent runs.
 */
object PlanFit {
    enum class Goal { NONE, ON_TRACK, AMBITIOUS, UNREALISTIC }

    data class Assessment(
        val goal: Goal,
        /** Current 5 km form from recent efforts (s); null without runs. */
        val form5kSec: Int?,
        /** Paces are more than [PACE_DRIFT] off the current form. */
        val pacesOutdated: Boolean,
        /** Suggested new goal time (prognosis rounded to the minute). */
        val suggestedTargetSec: Int?,
    ) {
        val suggestAdjust: Boolean get() = goal == Goal.UNREALISTIC || pacesOutdated
    }

    /** Up to 3 % slower than the goal is "ambitious", more is unrealistic. */
    const val AMBITIOUS = 0.03
    const val PACE_DRIFT = 0.03

    fun assess(targetSec: Int?, predictedSec: Int?, anchor5kSec: Int?, form5kSec: Int?): Assessment {
        val goal = when {
            targetSec == null || predictedSec == null -> Goal.NONE
            predictedSec <= targetSec -> Goal.ON_TRACK
            (predictedSec - targetSec).toDouble() / targetSec <= AMBITIOUS -> Goal.AMBITIOUS
            else -> Goal.UNREALISTIC
        }
        val outdated = anchor5kSec != null && form5kSec != null &&
            abs(form5kSec - anchor5kSec).toDouble() / anchor5kSec > PACE_DRIFT
        return Assessment(
            goal = goal,
            form5kSec = form5kSec,
            pacesOutdated = outdated || (anchor5kSec == null && form5kSec != null),
            suggestedTargetSec = if (goal == Goal.UNREALISTIC) predictedSec?.let { (it / 60.0).roundToInt() * 60 } else null,
        )
    }
}
