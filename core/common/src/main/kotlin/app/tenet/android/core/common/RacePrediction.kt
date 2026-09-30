package app.tenet.android.core.common

import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Race time prediction from recent best efforts (Riegel: T2 = T1 ·
 * (D2/D1)^1.06). Longer source efforts predict long races better, so the
 * effort whose prediction comes from the longest distance wins among
 * similarly recent ones.
 */
object RacePrediction {

    const val RIEGEL_EXPONENT = 1.06

    data class Effort(val date: LocalDate, val distanceM: Int, val durationSec: Int)

    data class Prediction(
        val timeSec: Int,
        /** Effort the prediction is based on. */
        val basis: Effort,
    )

    fun riegel(fromDistanceM: Int, fromSec: Int, toDistanceM: Int): Int =
        (fromSec * (toDistanceM.toDouble() / fromDistanceM).pow(RIEGEL_EXPONENT)).roundToInt()

    /**
     * Prediction for [targetDistanceM] on [asOf] using efforts of the last
     * [windowDays]. Among them the best (fastest) predicted time counts,
     * with efforts ≥ 5 km preferred over shorter ones for races ≥ 21 km
     * (short sprints over-predict marathons).
     */
    fun predict(
        efforts: List<Effort>,
        targetDistanceM: Int,
        asOf: LocalDate = LocalDate.now(),
        windowDays: Long = 56,
    ): Prediction? {
        val recent = efforts.filter { !it.date.isAfter(asOf) && !it.date.isBefore(asOf.minusDays(windowDays)) }
        if (recent.isEmpty()) return null
        val pool = if (targetDistanceM >= 21_000 && recent.any { it.distanceM >= 5_000 }) {
            recent.filter { it.distanceM >= 5_000 }
        } else {
            recent
        }
        return pool
            .map { Prediction(riegel(it.distanceM, it.durationSec, targetDistanceM), it) }
            .minByOrNull { it.timeSec }
    }

    /**
     * Prediction as it would have been on each date an effort happened
     * (for the progression chart), oldest first.
     */
    fun history(efforts: List<Effort>, targetDistanceM: Int, windowDays: Long = 56): List<Pair<LocalDate, Int>> =
        efforts.map { it.date }.distinct().sorted().mapNotNull { day ->
            predict(efforts, targetDistanceM, day, windowDays)?.let { day to it.timeSec }
        }
}
