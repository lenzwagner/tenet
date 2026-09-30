package app.tenet.android.core.common

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Running volume per week/month and the "sudden jump" hint
 * (App_Konzept.md 5.2.3: warn when weekly volume rises abruptly –
 * rule of thumb, no hard limit).
 */
object RunVolumeMath {

    data class Run(val date: LocalDate, val distanceM: Float, val durationSec: Int)

    data class Period(
        /** First day of the week (Monday) or month. */
        val start: LocalDate,
        val distanceM: Float,
        val runs: Int,
        val durationSec: Int,
    )

    data class JumpWarning(
        val increasePercent: Int,
        val currentM: Float,
        /** Average of the previous weeks. */
        val baselineM: Float,
    )

    /** Last [count] weeks incl. the current one, oldest first. */
    fun weekly(runs: List<Run>, today: LocalDate, count: Int = 8): List<Period> {
        val current = WeekMath.weekStart(today)
        val starts = (count - 1 downTo 0).map { current.minusWeeks(it.toLong()) }
        val grouped = runs.groupBy { WeekMath.weekStart(it.date) }
        return starts.map { period(it, grouped[it].orEmpty()) }
    }

    /** Last [count] months incl. the current one, oldest first. */
    fun monthly(runs: List<Run>, today: LocalDate, count: Int = 6): List<Period> {
        val current = today.withDayOfMonth(1)
        val starts = (count - 1 downTo 0).map { current.minusMonths(it.toLong()) }
        val grouped = runs.groupBy { it.date.withDayOfMonth(1) }
        return starts.map { period(it, grouped[it].orEmpty()) }
    }

    /**
     * Warns when the latest week exceeds the average of the [lookback]
     * weeks before it by more than [threshold] (30 %) and at least
     * [minDeltaM] (3 km), so tiny volumes don't trigger it. Needs a
     * baseline: at least two of the previous weeks with running.
     */
    fun jumpWarning(
        weeks: List<Period>,
        lookback: Int = 3,
        threshold: Float = 0.3f,
        minDeltaM: Float = 3_000f,
    ): JumpWarning? {
        if (weeks.size < lookback + 1) return null
        val current = weeks.last()
        val previous = weeks.dropLast(1).takeLast(lookback)
        if (previous.count { it.runs > 0 } < 2) return null
        val baseline = previous.sumOf { it.distanceM.toDouble() }.toFloat() / lookback
        if (baseline <= 0f) return null
        val delta = current.distanceM - baseline
        if (delta < minDeltaM || current.distanceM <= baseline * (1 + threshold)) return null
        return JumpWarning(
            increasePercent = ((current.distanceM / baseline - 1f) * 100).roundToInt(),
            currentM = current.distanceM,
            baselineM = baseline,
        )
    }

    private fun period(start: LocalDate, runs: List<Run>) = Period(
        start = start,
        distanceM = runs.sumOf { it.distanceM.toDouble() }.toFloat(),
        runs = runs.size,
        durationSec = runs.sumOf { it.durationSec },
    )
}
