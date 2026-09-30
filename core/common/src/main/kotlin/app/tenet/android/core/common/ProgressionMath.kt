package app.tenet.android.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Progression over time for all three disciplines (Sport → Fortschritt):
 * strength as estimated 1RM per exercise, calisthenics as best reps or
 * hold time per exercise, running as 5 km form, average pace and weekly
 * distance. One point per training day (per week for weekly distance).
 */
object ProgressionMath {

    /** NEUTRAL: no direction is better (body weight). */
    enum class Better { HIGHER, LOWER, NEUTRAL }

    enum class Unit { KG, REPS, SECONDS, PACE, KM, TIME, RATIO }

    data class Point(val date: LocalDate, val value: Float)

    data class Series(
        val key: String,
        val label: String,
        val unit: Unit,
        val better: Better,
        /** Oldest first. */
        val points: List<Point>,
    ) {
        val first: Float? get() = points.firstOrNull()?.value
        val last: Float? get() = points.lastOrNull()?.value
        val best: Float?
            get() = when (better) {
                Better.HIGHER -> points.maxOfOrNull { it.value }
                Better.LOWER -> points.minOfOrNull { it.value }
                Better.NEUTRAL -> last
            }

        /** Change since the first point, signed so that positive = better. */
        val improvement: Float?
            get() {
                val f = first ?: return null
                val l = last ?: return null
                if (points.size < 2) return null
                return if (better == Better.LOWER) f - l else l - f
            }

        /** Improvement in percent of the first value (positive = better). */
        val improvementPercent: Int?
            get() {
                val f = first ?: return null
                val gain = improvement ?: return null
                if (f == 0f) return null
                return (gain / f * 100f).toInt()
            }

        fun since(from: LocalDate?): Series =
            if (from == null) this else copy(points = points.filter { !it.date.isBefore(from) })
    }

    /** One logged strength or calisthenics set. */
    data class SetRecord(
        val exercise: String,
        /** Exercise.measureType name: REPS, HOLD, NEGATIVE or DURATION. */
        val measure: String,
        val weight: Float,
        val reps: Int,
        val durationSec: Int?,
        val startedAt: Long,
    )

    /**
     * Gym: best estimated 1RM per day and exercise. Exercises trained
     * without weight fall back to best reps per day.
     */
    fun strength(
        sets: List<SetRecord>,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Series> =
        sets.groupBy { it.exercise }.mapNotNull { (name, exSets) ->
            val weighted = exSets.any { it.weight > 0f }
            val points = daily(exSets, zone) { day ->
                if (weighted) {
                    day.filter { it.weight > 0f && it.reps > 0 }
                        .maxOfOrNull { OneRepMax.oneRepMax(it.weight, it.reps, formula) }
                } else {
                    day.maxOfOrNull { it.reps.toFloat() }
                }
            }
            if (points.isEmpty()) null
            else Series(name, name, if (weighted) Unit.KG else Unit.REPS, Better.HIGHER, points)
        }.sortedByDescending { it.points.size }

    /** Calisthenics: best reps per day, or longest hold for timed exercises. */
    fun calisthenics(sets: List<SetRecord>, zone: ZoneId = ZoneId.systemDefault()): List<Series> =
        sets.groupBy { it.exercise }.mapNotNull { (name, exSets) ->
            val timed = exSets.first().measure == "HOLD" || exSets.first().measure == "DURATION"
            val points = daily(exSets, zone) { day ->
                if (timed) day.mapNotNull { it.durationSec }.filter { it > 0 }.maxOrNull()?.toFloat()
                else day.filter { it.reps > 0 }.maxOfOrNull { it.reps.toFloat() }
            }
            if (points.isEmpty()) null
            else Series(name, name, if (timed) Unit.SECONDS else Unit.REPS, Better.HIGHER, points)
        }.sortedByDescending { it.points.size }

    /** Body weight over time (one point per measured day). */
    fun bodyweight(weights: List<Point>): Series? =
        weights.filter { it.value > 0f }.sortedBy { it.date }.takeIf { it.isNotEmpty() }
            ?.let { Series("bodyweight", "Körpergewicht", Unit.KG, Better.NEUTRAL, it) }

    /**
     * Relative strength: estimated 1RM divided by the body weight of that day
     * (latest weighing on or before it, else the first one). Weighted lifts only.
     */
    fun relative(strength: List<Series>, weights: List<Point>): List<Series> {
        val w = weights.filter { it.value > 0f }.sortedBy { it.date }
        if (w.isEmpty()) return emptyList()
        fun weightOn(date: LocalDate) = (w.lastOrNull { !it.date.isAfter(date) } ?: w.first()).value
        return strength.filter { it.unit == Unit.KG }.map { s ->
            Series(
                key = "rel-${s.key}",
                label = "${s.label} · relativ",
                unit = Unit.RATIO,
                better = Better.HIGHER,
                points = s.points.map { Point(it.date, it.value / weightOn(it.date)) },
            )
        }
    }

    /** Minimum distance for a run to count towards the 5 km form. */
    const val FORM_MIN_DISTANCE_M = 3_000f

    /**
     * Running: 5 km equivalent (Riegel) of every run of at least 3 km, average
     * pace per day and distance per completed week (weeks without runs count as 0 km).
     */
    fun running(runs: List<RunVolumeMath.Run>, today: LocalDate = LocalDate.now()): List<Series> {
        if (runs.isEmpty()) return emptyList()
        val byDay = runs.filter { it.distanceM > 0f && it.durationSec > 0 }.groupBy { it.date }.toSortedMap()
        val form = byDay.mapNotNull { (date, day) ->
            day.filter { it.distanceM >= FORM_MIN_DISTANCE_M }
                .minOfOrNull { RacePrediction.riegel(it.distanceM.toInt(), it.durationSec, 5_000) }
                ?.let { Point(date, it.toFloat()) }
        }
        val pace = byDay.mapNotNull { (date, day) ->
            val dist = day.sumOf { it.distanceM.toDouble() }
            if (dist < 1_000) null
            else Point(date, (day.sumOf { it.durationSec } / (dist / 1000.0)).toFloat())
        }
        val firstWeek = runs.minOf { it.date }.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val kmByWeek = runs.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .mapValues { (_, w) -> w.sumOf { it.distanceM.toDouble() }.toFloat() / 1000f }
        // The running week is not over yet and would always look like a drop.
        val weekly = generateSequence(firstWeek) { it.plusWeeks(1) }
            .takeWhile { it.isBefore(lastWeek) }
            .map { Point(it, kmByWeek[it] ?: 0f) }
            .toList()
        return listOf(
            Series("form", "5-km-Form", Unit.TIME, Better.LOWER, form),
            Series("pace", "Ø Pace", Unit.PACE, Better.LOWER, pace),
            Series("weekly", "Wochen-km", Unit.KM, Better.HIGHER, weekly),
        ).filter { it.points.isNotEmpty() }
    }

    private inline fun daily(
        sets: List<SetRecord>,
        zone: ZoneId,
        value: (List<SetRecord>) -> Float?,
    ): List<Point> =
        sets.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .mapNotNull { (date, day) -> value(day)?.takeIf { it > 0f }?.let { Point(date, it) } }
            .sortedBy { it.date }
}
