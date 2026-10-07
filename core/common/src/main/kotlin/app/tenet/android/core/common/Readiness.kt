package app.tenet.android.core.common

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Morning readiness after Fitbit's "Daily Readiness" / Garmin's "Training
 * Readiness": how recovered the body is today, 0–100, from
 *
 * - HRV (RMSSD) against the personal 30-day baseline – the strongest signal,
 * - resting heart rate against its baseline (higher = still strained or ill),
 * - last night's sleep (duration, deep + REM share),
 * - training load: the last 7 days against the 4-week average (acute:chronic).
 *
 * Every part is optional; the score uses the parts that have data and
 * re-weights them. Without sleep and without heart data there is no score.
 */
object Readiness {

    /** Inputs for one morning. Baselines are the days before today. */
    data class Input(
        val sleep: SleepNight? = null,
        /** Average asleep minutes of the previous nights (null = too few). */
        val sleepBaselineMin: Int? = null,
        val restingHr: Int? = null,
        val restingHrBaseline: Double? = null,
        val hrvMs: Double? = null,
        val hrvBaselineMs: Double? = null,
        /** Training minutes of the last 7 days (today excluded). */
        val acuteLoadMin: Int = 0,
        /** Training minutes of the last 28 days (today excluded). */
        val chronicLoadMin: Int = 0,
        /** No training data at all yet: load is left out instead of reading as "rested". */
        val hasTrainingHistory: Boolean = false,
    )

    enum class Level(val label: String) {
        HIGH("Hoch"),
        GOOD("Gut"),
        MODERATE("Mäßig"),
        LOW("Niedrig"),
    }

    enum class Kind(val label: String) { HRV("HRV"), RESTING_HR("Ruhepuls"), SLEEP("Schlaf"), LOAD("Belastung") }

    /** One part of the score: its own 0–100 and a short line ("54 bpm · 3 unter Schnitt"). */
    data class Contributor(val kind: Kind, val score: Int, val value: String, val detail: String)

    data class Result(
        val score: Int,
        val level: Level,
        val headline: String,
        val advice: String,
        val contributors: List<Contributor>,
    )

    private val Weights = mapOf(Kind.HRV to 0.35, Kind.RESTING_HR to 0.20, Kind.SLEEP to 0.30, Kind.LOAD to 0.15)

    fun compute(input: Input): Result? {
        val parts = listOfNotNull(hrv(input), restingHr(input), sleep(input), load(input))
        // Load alone says nothing about recovery.
        if (parts.none { it.kind != Kind.LOAD }) return null
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

    private fun sleep(i: Input): Contributor? {
        val night = i.sleep ?: return null
        val asleep = night.asleepMin
        // 8 h = full marks; shorter nights cost more than linearly.
        var score = (100 * (asleep / 480.0).coerceAtMost(1.0).pow(1.6))
        val deep = night.deepMin
        val rem = night.remMin
        if (deep != null && rem != null && asleep > 0) {
            val restorative = (deep + rem).toDouble() / asleep
            if (restorative < 0.25) score -= 8
            if (restorative > 0.40) score += 4
        }
        val base = i.sleepBaselineMin
        val detail = when {
            base == null -> "letzte Nacht"
            asleep - base >= 30 -> "${SleepMath.duration(asleep - base)} mehr als üblich"
            base - asleep >= 30 -> "${SleepMath.duration(base - asleep)} weniger als üblich"
            else -> "wie üblich"
        }
        return Contributor(Kind.SLEEP, score.roundToInt().coerceIn(5, 100), SleepMath.duration(asleep), detail)
    }

    private fun load(i: Input): Contributor? {
        if (!i.hasTrainingHistory) return null
        val weeklyAvg = i.chronicLoadMin / 4.0
        if (weeklyAvg < 15) return Contributor(Kind.LOAD, 80, "${i.acuteLoadMin} min", "7 Tage")
        val acwr = i.acuteLoadMin / weeklyAvg
        val score = when {
            acwr < 0.6 -> 90.0
            acwr <= 1.0 -> 85.0
            acwr <= 1.3 -> 85 - (acwr - 1.0) / 0.3 * 15
            else -> 70 - (acwr - 1.3) / 0.7 * 40
        }.roundToInt().coerceIn(20, 100)
        val detail = when {
            acwr < 0.8 -> "weniger als sonst"
            acwr <= 1.2 -> "im üblichen Rahmen"
            acwr <= 1.5 -> "mehr als sonst"
            else -> "deutlich mehr als sonst"
        }
        return Contributor(Kind.LOAD, score, "${i.acuteLoadMin} min / 7 Tage", detail)
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
            Kind.SLEEP -> " Die Nacht war kurz."
            Kind.LOAD -> " Die letzten Tage waren intensiv."
            null -> ""
        }
        return when (level) {
            Level.HIGH -> "Guter Tag für eine harte Einheit oder einen Rekordversuch."
            Level.GOOD -> "Trainiere wie geplant.$reason"
            Level.MODERATE -> "Lieber locker oder kürzer trainieren.$reason"
            Level.LOW -> "Heute Erholung: Spaziergang, Mobility oder ganz frei.$reason"
        }
    }

    /** Notification line: "Bereitschaft 78 · Gut erholt – Trainiere wie geplant." */
    fun notification(r: Result): Pair<String, String> =
        "Bereitschaft ${r.score} · ${r.headline}" to (
            r.contributors.joinToString(" · ") { "${it.kind.label} ${it.value}" } + "\n" + r.advice
            )
}
