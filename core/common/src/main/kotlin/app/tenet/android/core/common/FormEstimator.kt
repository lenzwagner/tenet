package app.tenet.android.core.common

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Current running form from all runs (GPS, logged, Health Connect):
 * a VDOT per run, then race times for 5 km, 10 km, half and full
 * marathon.
 *
 * - Performance: Daniels/Gilbert VDOT of the run as if it were a race –
 *   a lower bound, because easy runs are not all-out.
 * - Heart rate: with an average HR the run's oxygen demand is scaled up
 *   to 100 % via the heart-rate reserve (Swain: %VO2R ≈ %HRR; Karvonen),
 *   so easy runs count too. Max HR after Tanaka (208 − 0.7 × age) unless
 *   a run shows a higher one; resting HR 60 by default.
 * - The best estimates of the last [WINDOW_DAYS] (recent runs weigh more)
 *   are averaged, which damps one-off GPS or strap glitches.
 */
object FormEstimator {

    const val WINDOW_DAYS = 56L

    data class Run(val date: LocalDate, val distanceM: Float, val durationSec: Int, val avgHr: Int?)

    data class RaceTime(val label: String, val distanceM: Int, val timeSec: Int) {
        val paceSecPerKm: Int get() = (timeSec * 1000.0 / distanceM).roundToInt()
    }

    data class Form(
        val vdot: Double,
        val times: List<RaceTime>,
        /** Runs that went into the estimate, and how many of them had heart rate. */
        val runs: Int,
        val withHr: Int,
    ) {
        fun time(distanceM: Int): Int? = times.firstOrNull { it.distanceM == distanceM }?.timeSec
    }

    val DISTANCES = listOf("5 km" to 5_000, "10 km" to 10_000, "Halbmarathon" to 21_097, "Marathon" to 42_195)

    /** Oxygen cost (ml/kg/min) of running at [mPerMin] (Daniels/Gilbert). */
    fun vo2(mPerMin: Double): Double = -4.60 + 0.182258 * mPerMin + 0.000104 * mPerMin * mPerMin

    /** Share of VO2max sustainable for [minutes] (Daniels/Gilbert). */
    fun fraction(minutes: Double): Double = 0.8 + 0.1894393 * exp(-0.012778 * minutes) + 0.2989558 * exp(-0.1932605 * minutes)

    /** VDOT of a performance treated as an all-out race. */
    fun vdot(distanceM: Double, seconds: Double): Double {
        val minutes = seconds / 60.0
        return vo2(distanceM / minutes) / fraction(minutes)
    }

    /** VDOT from heart rate: the run's VO2 scaled to 100 % of the HR reserve. */
    fun vdotFromHr(distanceM: Double, seconds: Double, avgHr: Int, maxHr: Int, restHr: Int): Double? {
        val hrr = (avgHr - restHr).toDouble() / (maxHr - restHr)
        // Outside 50–97 % the relation is unreliable (warm-up, sprints, bad strap).
        if (hrr !in 0.5..0.97) return null
        val v = vo2(distanceM / (seconds / 60.0))
        return (v - 3.5) / hrr + 3.5
    }

    /** Race time for [distanceM] at [vdot] (bisection on Daniels' formula). */
    fun timeFor(vdot: Double, distanceM: Int): Int {
        var lo = 60.0
        var hi = 60.0 * 60 * 10
        repeat(60) {
            val mid = (lo + hi) / 2
            if (vdot(distanceM.toDouble(), mid) > vdot) lo = mid else hi = mid
        }
        return ((lo + hi) / 2).roundToInt()
    }

    fun maxHr(age: Int?, runs: List<Run>): Int {
        val tanaka = age?.let { (208 - 0.7 * it).roundToInt() } ?: 190
        // A run averaging above ~92 % of it means the real max is higher.
        val observed = runs.mapNotNull { it.avgHr }.maxOrNull()?.let { (it / 0.92).roundToInt() } ?: 0
        return maxOf(tanaka, observed)
    }

    fun estimate(runs: List<Run>, asOf: LocalDate = LocalDate.now(), age: Int? = null, restHr: Int = 60): Form? {
        val recent = runs.filter {
            it.distanceM >= 2_000f && it.durationSec >= 8 * 60 &&
                !it.date.isAfter(asOf) && ChronoUnit.DAYS.between(it.date, asOf) <= WINDOW_DAYS
        }
        if (recent.isEmpty()) return null
        val maxHr = maxHr(age, recent)
        var withHr = 0
        val scored = recent.mapNotNull { r ->
            val perf = vdot(r.distanceM.toDouble(), r.durationSec.toDouble())
            val hr = r.avgHr?.let { vdotFromHr(r.distanceM.toDouble(), r.durationSec.toDouble(), it, maxHr, restHr) }
            if (hr != null) withHr++
            // The run proves at least its race-equivalent; heart rate can only add to it (capped against outliers).
            val v = if (hr != null) maxOf(perf, minOf(hr, perf * 1.35)) else perf
            if (v !in 15.0..90.0) return@mapNotNull null
            val age = ChronoUnit.DAYS.between(r.date, asOf).toDouble()
            v to (1.0 - age / (WINDOW_DAYS * 2.0)) // recent runs weigh more
        }
        if (scored.isEmpty()) return null
        // Mean of the best third (at least one), recency-weighted.
        val best = scored.sortedByDescending { it.first }.take(maxOf(1, (scored.size + 2) / 3))
        val vdot = best.sumOf { it.first * it.second } / best.sumOf { it.second }
        return Form(
            vdot = vdot,
            times = DISTANCES.map { (label, d) -> RaceTime(label, d, timeFor(vdot, d)) },
            runs = recent.size,
            withHr = withHr,
        )
    }

    /** VDOT → equivalent 5 km time (the plan's pace anchor). */
    fun fiveK(vdot: Double): Int = timeFor(vdot, 5_000)

    @Suppress("unused")
    private fun Double.sq() = pow(2)
}
