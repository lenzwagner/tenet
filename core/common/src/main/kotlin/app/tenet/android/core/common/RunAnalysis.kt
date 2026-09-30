package app.tenet.android.core.common

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Post-run analysis of a GPS track (App_Konzept.md 5.2.3 "Lauf-Detail":
 * Splits pro Kilometer, Pace- und Höhenprofil, Herzfrequenzzonen;
 * "Bestzeiten … automatisch aus den schnellsten Teilstücken erkannt").
 *
 * Pauses: a gap of more than [PAUSE_GAP_MS] between two points starts a
 * new segment; neither time nor distance across the gap counts. Steps
 * faster than [MAX_SPEED_MPS] are GPS jumps and add no distance. The live
 * tracker records no points while paused, and watches behave the same.
 */
object RunAnalysis {

    const val PAUSE_GAP_MS = 20_000L

    /** Faster than this between two fixes is a GPS jump, not running (same rule as live). */
    const val MAX_SPEED_MPS = 12.0

    data class Point(
        val time: Long,
        val lat: Double,
        val lon: Double,
        val altitude: Double? = null,
        val hr: Int? = null,
    )

    data class Split(
        /** 1-based kilometer. */
        val index: Int,
        /** 1000 m, the last one may be shorter. */
        val distanceM: Float,
        val durationSec: Int,
        val elevationDeltaM: Int?,
        val avgHr: Int?,
    ) {
        val paceSecPerKm: Int get() = if (distanceM <= 0f) 0 else (durationSec / (distanceM / 1000f)).roundToInt()
    }

    /** One sample of the profiles along the distance axis. */
    data class ProfilePoint(val distanceM: Float, val paceSecPerKm: Int?, val altitude: Float?, val hr: Int?)

    data class Summary(
        val distanceM: Float,
        /** Moving time (pauses excluded). */
        val durationSec: Int,
        val elevationGainM: Int?,
        val avgHr: Int?,
        val maxHr: Int?,
    ) {
        val avgPaceSecPerKm: Int get() = if (distanceM <= 0f) 0 else (durationSec / (distanceM / 1000f)).roundToInt()
    }

    /** Standard distances for personal bests. */
    val BEST_DISTANCES = listOf(1_000, 5_000, 10_000, 21_097, 42_195)

    fun distanceM(a: Point, b: Point): Double = haversine(a.lat, a.lon, b.lat, b.lon)

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * Cumulative moving distance (m) and moving time (ms) per point,
     * with pause gaps removed. Both arrays have the size of [points].
     */
    fun cumulative(points: List<Point>): Pair<DoubleArray, LongArray> {
        val dist = DoubleArray(points.size)
        val time = LongArray(points.size)
        for (i in 1 until points.size) {
            val dt = points[i].time - points[i - 1].time
            if (dt in 1..PAUSE_GAP_MS) {
                val step = distanceM(points[i - 1], points[i])
                dist[i] = dist[i - 1] + if (step / (dt / 1000.0) <= MAX_SPEED_MPS) step else 0.0
                time[i] = time[i - 1] + dt
            } else {
                dist[i] = dist[i - 1]
                time[i] = time[i - 1]
            }
        }
        return dist to time
    }

    fun summary(points: List<Point>): Summary {
        val (dist, time) = cumulative(points)
        val hrs = points.mapNotNull { it.hr }.filter { it > 0 }
        return Summary(
            distanceM = dist.lastOrNull()?.toFloat() ?: 0f,
            durationSec = ((time.lastOrNull() ?: 0L) / 1000).toInt(),
            elevationGainM = elevationGain(points.mapNotNull { it.altitude }),
            avgHr = hrs.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            maxHr = hrs.maxOrNull(),
        )
    }

    /** Kilometer splits, time interpolated at each full kilometer. */
    fun splits(points: List<Point>): List<Split> {
        if (points.size < 2) return emptyList()
        val (dist, time) = cumulative(points)
        val total = dist.last()
        if (total < 50) return emptyList()
        val out = mutableListOf<Split>()
        var startDist = 0.0
        var startTime = 0.0
        var startIdx = 0
        var km = 1
        while (startDist < total - 1) {
            val targetDist = minOf(km * 1000.0, total)
            val endTime = interpolate(dist, time, targetDist)
            val endIdx = dist.indexOfFirst { it >= targetDist }.let { if (it < 0) points.lastIndex else it }
            val slice = points.subList(startIdx, (endIdx + 1).coerceAtMost(points.size))
            val alts = slice.mapNotNull { it.altitude }
            val hrs = slice.mapNotNull { it.hr }.filter { it > 0 }
            out += Split(
                index = km,
                distanceM = (targetDist - startDist).toFloat(),
                durationSec = ((endTime - startTime) / 1000).roundToInt(),
                elevationDeltaM = if (alts.size >= 2) (alts.last() - alts.first()).roundToInt() else null,
                avgHr = hrs.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            )
            startDist = targetDist
            startTime = endTime
            startIdx = endIdx
            km++
        }
        // Drop a tiny last fragment (< 50 m) – it only produces absurd paces.
        return if (out.size > 1 && out.last().distanceM < 50f) out.dropLast(1) else out
    }

    /**
     * Fastest time (s) for each standard distance anywhere within the run
     * (sliding window over the moving-time track). Only distances the run
     * actually covers are returned.
     */
    fun bestEfforts(points: List<Point>, distances: List<Int> = BEST_DISTANCES): Map<Int, Int> {
        if (points.size < 2) return emptyMap()
        val (dist, time) = cumulative(points)
        val total = dist.last()
        val result = mutableMapOf<Int, Int>()
        for (target in distances) {
            if (total < target) continue
            var best = Double.MAX_VALUE
            var j = 0
            for (i in dist.indices) {
                // End of the window: first point where dist >= dist[i] + target.
                val goal = dist[i] + target
                if (goal > total) break
                if (j < i) j = i
                while (j < dist.size && dist[j] < goal) j++
                if (j >= dist.size) break
                val endTime = interpolate(dist, time, goal, hint = j)
                val duration = endTime - time[i]
                if (duration in 1.0..<best) best = duration
            }
            if (best < Double.MAX_VALUE) result[target] = (best / 1000).roundToInt()
        }
        return result
    }

    /**
     * Efforts from a run without a track (manual entry, watch without GPS):
     * only standard distances the run hits within +2 % count, scaled to
     * the exact distance.
     */
    fun wholeRunEfforts(distanceM: Float, durationSec: Int, distances: List<Int> = BEST_DISTANCES): Map<Int, Int> =
        distances.filter { distanceM >= it && distanceM <= it * 1.02f }
            .associateWith { (durationSec * it / distanceM).roundToInt() }

    /** Elevation gain with a 3 m hysteresis against GPS altitude noise. */
    fun elevationGain(altitudes: List<Double>, threshold: Double = 3.0): Int? {
        if (altitudes.size < 2) return null
        var gain = 0.0
        var ref = altitudes.first()
        for (a in altitudes.drop(1)) {
            if (a - ref >= threshold) {
                gain += a - ref
                ref = a
            } else if (ref - a >= threshold) {
                ref = a
            }
        }
        return gain.roundToInt()
    }

    /**
     * Pace, altitude and heart rate every [stepM] meters; pace is averaged
     * over a trailing [windowM] window so GPS jitter does not dominate.
     */
    fun profile(points: List<Point>, stepM: Double = 100.0, windowM: Double = 300.0): List<ProfilePoint> {
        if (points.size < 2) return emptyList()
        val (dist, time) = cumulative(points)
        val total = dist.last()
        if (total < stepM) return emptyList()
        val out = mutableListOf<ProfilePoint>()
        var d = stepM
        var idx = 0
        while (d <= total) {
            while (idx < dist.lastIndex && dist[idx] < d) idx++
            val from = (d - windowM).coerceAtLeast(0.0)
            val dt = interpolate(dist, time, d) - interpolate(dist, time, from)
            val pace = if (d - from > 0 && dt > 0) (dt / 1000 / ((d - from) / 1000)).roundToInt() else null
            out += ProfilePoint(
                distanceM = d.toFloat(),
                paceSecPerKm = pace,
                altitude = points[idx].altitude?.toFloat(),
                hr = points[idx].hr?.takeIf { it > 0 },
            )
            d += stepM
        }
        return out
    }

    /** Heart-rate zones as share of max HR: Z1 50–60 % … Z5 90–100 %. */
    fun hrZoneSeconds(points: List<Point>, maxHr: Int): IntArray {
        val zones = IntArray(5)
        if (maxHr <= 0) return zones
        for (i in 1 until points.size) {
            val hr = points[i].hr ?: continue
            val dt = points[i].time - points[i - 1].time
            if (dt !in 1..PAUSE_GAP_MS) continue
            val pct = hr.toDouble() / maxHr
            val zone = when {
                pct < 0.6 -> 0
                pct < 0.7 -> 1
                pct < 0.8 -> 2
                pct < 0.9 -> 3
                else -> 4
            }
            zones[zone] += (dt / 1000).toInt()
        }
        return zones
    }

    /** Tanaka estimate of max heart rate. */
    fun estimatedMaxHr(age: Int): Int = (208 - 0.7 * age).roundToInt()

    private fun interpolate(dist: DoubleArray, time: LongArray, target: Double, hint: Int = -1): Double {
        var j = if (hint >= 0) hint else dist.indexOfFirst { it >= target }
        if (j < 0) return time.last().toDouble()
        if (j == 0) return time[0].toDouble()
        // Skip zero-length steps (pause gaps) backwards.
        val d0 = dist[j - 1]
        val d1 = dist[j]
        if (d1 <= d0) return time[j].toDouble()
        val f = (target - d0) / (d1 - d0)
        return time[j - 1] + f * (time[j] - time[j - 1])
    }
}
