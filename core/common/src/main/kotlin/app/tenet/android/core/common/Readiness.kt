package app.tenet.android.core.common

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * "Tenet-Form": Tenet's own morning recovery score, 0–100, close to Fitbit's /
 * Google Health's Daily Readiness (whose score is not shared via Health Connect):
 *
 * - HRV (RMSSD) against the personal 30-day baseline – the strongest signal,
 * - resting heart rate against its baseline (higher = still strained or ill),
 * - sleep of the last 7 nights, last night counting half (like Fitbit's
 *   sleep history instead of a single night).
 *
 * Every part is optional; the score uses the parts that have data and
 * re-weights them. Without sleep and without heart data there is no score.
 */
object Readiness {
    /** Shown name: Tenet's own score, not to be mistaken for Google Health's. */
    const val NAME = "Tenet-Form"

    /** Inputs for one morning. Baselines are the days before today. */
    data class Input(
        val sleep: SleepNight? = null,
        /** Average asleep minutes of the previous nights (null = too few). */
        val sleepBaselineMin: Int? = null,
        /** The nights before last night, newest first (up to 6), for the sleep history. */
        val previousNights: List<SleepNight> = emptyList(),
        val restingHr: Int? = null,
        val restingHrBaseline: Double? = null,
        val hrvMs: Double? = null,
        val hrvBaselineMs: Double? = null,
    )

    enum class Level(val label: String) {
        HIGH("Hoch"),
        GOOD("Gut"),
        MODERATE("Mäßig"),
        LOW("Niedrig"),
    }

    enum class Kind(val label: String) { HRV("HRV"), RESTING_HR("Ruhepuls"), SLEEP("Schlaf") }

    /** One part of the score: its own 0–100 and a short line ("54 bpm · 3 unter Schnitt"). */
    data class Contributor(val kind: Kind, val score: Int, val value: String, val detail: String)

    data class Result(
        val score: Int,
        val level: Level,
        val headline: String,
        val advice: String,
        val contributors: List<Contributor>,
    )

    /** Share of each part in the score (also shown under the score). */
    val Weights = mapOf(Kind.HRV to 0.40, Kind.SLEEP to 0.35, Kind.RESTING_HR to 0.25)

    /** Nights in the sleep part; last night counts as much as all the others together. */
    const val SLEEP_NIGHTS = 7

    fun compute(input: Input): Result? {
        val parts = listOfNotNull(hrv(input), restingHr(input), sleep(input))
        if (parts.isEmpty()) return null
        val weight = parts.sumOf { Weights.getValue(it.kind) }
        val score = (parts.sumOf { it.score * Weights.getValue(it.kind) } / weight).roundToInt().coerceIn(1, 100)
        val level = when {
            score >= 80 -> Level.HIGH
            score >= 60 -> Level.GOOD
            score >= 40 -> Level.MODERATE
            else -> Level.LOW
        }
        return Result(score, level, headline(level), advice(level, parts), parts.sortedByDescending { Weights.getValue(it.kind) })
    }

    private fun hrv(i: Input): Contributor? {
        val today = i.hrvMs ?: return null
        val base = i.hrvBaselineMs
        if (base == null || base <= 0.0) {
            return Contributor(Kind.HRV, 70, "${today.roundToInt()} ms", "Baseline entsteht noch")
        }
        val ratio = today / base
        val score = (70 + (ratio - 1) * 150).roundToInt().coerceIn(5, 100)
        val pct = ((ratio - 1) * 100).roundToInt()
        val detail = when {
            abs(pct) < 5 -> "wie üblich"
            pct > 0 -> "$pct % über Schnitt"
            else -> "${-pct} % unter Schnitt"
        }
        return Contributor(Kind.HRV, score, "${today.roundToInt()} ms", detail)
    }

    private fun restingHr(i: Input): Contributor? {
        val today = i.restingHr ?: return null
        val base = i.restingHrBaseline
            ?: return Contributor(Kind.RESTING_HR, 72, "$today bpm", "Baseline entsteht noch")
        val diff = today - base
        val score = (75 - diff * 6).roundToInt().coerceIn(5, 100)
        val d = abs(diff).roundToInt()
        val detail = when {
            d == 0 -> "wie üblich"
            diff < 0 -> "$d unter Schnitt"
            else -> "$d über Schnitt"
        }
        return Contributor(Kind.RESTING_HR, score, "$today bpm", detail)
    }

    /** One night 0–100: 8 h = full marks, shorter nights cost more than linearly. */
    private fun nightScore(night: SleepNight): Double {
        val asleep = night.asleepMin
        var score = 100 * (asleep / 480.0).coerceAtMost(1.0).pow(1.6)
        val deep = night.deepMin
        val rem = night.remMin
        if (deep != null && rem != null && asleep > 0) {
            val restorative = (deep + rem).toDouble() / asleep
            if (restorative < 0.25) score -= 4
            if (restorative > 0.40) score += 2
        }
        return score.coerceIn(5.0, 100.0)
    }

    private fun sleep(i: Input): Contributor? {
        val night = i.sleep ?: return null
        val asleep = night.asleepMin
        val before = i.previousNights.take(SLEEP_NIGHTS - 1)
        // Last night half, the nights before the other half: one short night hurts less.
        val score = if (before.isEmpty()) nightScore(night) else nightScore(night) * 0.5 + before.map(::nightScore).average() * 0.5
        val base = i.sleepBaselineMin
        val detail = when {
            base == null -> "letzte Nacht"
            asleep - base >= 30 -> "${SleepMath.duration(asleep - base)} mehr als üblich"
            base - asleep >= 30 -> "${SleepMath.duration(base - asleep)} weniger als üblich"
            else -> "wie üblich"
        }
        return Contributor(Kind.SLEEP, score.roundToInt().coerceIn(5, 100), SleepMath.duration(asleep), detail)
    }

    private fun headline(level: Level) = when (level) {
        Level.HIGH -> "Du bist bereit"
        Level.GOOD -> "Gut erholt"
        Level.MODERATE -> "Teilweise erholt"
        Level.LOW -> "Dein Körper braucht Ruhe"
    }

    private fun advice(level: Level, parts: List<Contributor>): String {
        val weakest = parts.filter { it.score < 50 }.minByOrNull { it.score }
        val reason = when (weakest?.kind) {
            Kind.HRV -> " Deine HRV ist niedriger als sonst."
            Kind.RESTING_HR -> " Dein Ruhepuls ist erhöht."
            Kind.SLEEP -> " Du hast zuletzt wenig geschlafen."
            null -> ""
        }
        return when (level) {
            Level.HIGH -> "Guter Tag für eine harte Einheit oder einen Rekordversuch."
            Level.GOOD -> "Trainiere wie geplant.$reason"
            Level.MODERATE -> "Lieber locker oder kürzer trainieren.$reason"
            Level.LOW -> "Heute Erholung: Spaziergang, Mobility oder ganz frei.$reason"
        }
    }

    /** Notification line: "Tenet-Form 78 · Gut erholt – Trainiere wie geplant." */
    fun notification(r: Result): Pair<String, String> =
        "$NAME ${r.score} · ${r.headline}" to (
            r.contributors.joinToString(" · ") { "${it.kind.label} ${it.value}" } + "\n" + r.advice
            )
}
