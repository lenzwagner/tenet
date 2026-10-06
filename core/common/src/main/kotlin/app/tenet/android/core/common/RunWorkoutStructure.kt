package app.tenet.android.core.common

import kotlin.math.roundToInt

/**
 * Structure of a planned run like in Runna: warm-up, main part (steady,
 * tempo block or interval reps with jog recoveries), cool-down – each with
 * a target pace, plus estimated distance/duration and why the session is
 * in the plan. Pure, unit tested.
 */
object RunWorkoutStructure {

    enum class Kind(val label: String) {
        WARMUP("Einlaufen"),
        WORK("Belastung"),
        RECOVERY("Trabpause"),
        COOLDOWN("Auslaufen"),
        STEADY("Dauerlauf"),
        RACE("Wettkampf"),
    }

    data class Segment(
        val kind: Kind,
        val durationSec: Int?,
        val distanceM: Int?,
        val paceSecPerKm: Int?,
        /** 1-based rep number for interval work/recovery. */
        val rep: Int? = null,
    ) {
        /** Duration if known, else derived from distance and pace. */
        fun estSec(fallbackPace: Int): Int = durationSec ?: distanceM?.let { (it / 1000f * (paceSecPerKm ?: fallbackPace)).roundToInt() } ?: 0

        /** Distance if known, else derived from duration and pace. */
        fun estM(fallbackPace: Int): Int = distanceM ?: durationSec?.let { (it.toFloat() / (paceSecPerKm ?: fallbackPace) * 1000f).roundToInt() } ?: 0
    }

    data class Workout(
        val zone: RunZone,
        val race: Boolean,
        val segments: List<Segment>,
        val estDistanceM: Int,
        val estDurationSec: Int,
        val purpose: String,
        /** How it should feel (RPE / talk test). */
        val feel: String,
    )

    /** Target pace per zone (from the plan's pace anchor); null = unknown. */
    data class Paces(val easy: Int?, val recovery: Int?)

    const val WARMUP_SEC = 10 * 60
    const val COOLDOWN_SEC = 10 * 60
    private const val DEFAULT_EASY_PACE = 6 * 60 + 30

    data class IntervalSpec(val reps: Int, val lengthM: Int, val restSec: Int)

    /** `[{"reps":4,"lengthM":800,"restSec":90}]` → spec (first block). */
    fun parseIntervals(json: String?): IntervalSpec? {
        if (json.isNullOrBlank()) return null
        fun int(key: String) = Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
        val reps = int("reps") ?: return null
        val length = int("lengthM") ?: return null
        return IntervalSpec(reps, length, int("restSec") ?: 90)
    }

    fun build(
        zone: RunZone,
        targetDurationSec: Int?,
        targetDistanceM: Int?,
        targetPaceSecPerKm: Int?,
        intervalsJson: String?,
        paces: Paces = Paces(null, null),
        race: Boolean = false,
    ): Workout {
        val easy = paces.easy
        val segments = buildList {
            when {
                race -> add(Segment(Kind.RACE, null, targetDistanceM, targetPaceSecPerKm))
                zone == RunZone.INTERVAL -> {
                    val spec = parseIntervals(intervalsJson) ?: IntervalSpec(4, 800, 90)
                    add(Segment(Kind.WARMUP, WARMUP_SEC, null, easy))
                    for (rep in 1..spec.reps) {
                        add(Segment(Kind.WORK, null, spec.lengthM, targetPaceSecPerKm, rep))
                        if (rep < spec.reps) add(Segment(Kind.RECOVERY, spec.restSec, null, paces.recovery, rep))
                    }
                    add(Segment(Kind.COOLDOWN, COOLDOWN_SEC, null, easy))
                }
                zone == RunZone.TEMPO -> {
                    add(Segment(Kind.WARMUP, WARMUP_SEC, null, easy))
                    add(Segment(Kind.WORK, targetDurationSec ?: 20 * 60, targetDistanceM.takeIf { targetDurationSec == null }, targetPaceSecPerKm))
                    add(Segment(Kind.COOLDOWN, COOLDOWN_SEC, null, easy))
                }
                else -> add(Segment(Kind.STEADY, targetDurationSec, targetDistanceM, targetPaceSecPerKm))
            }
        }
        val fallback = easy ?: DEFAULT_EASY_PACE
        return Workout(
            zone = zone,
            race = race,
            segments = segments,
            estDistanceM = segments.sumOf { it.estM(fallback) },
            estDurationSec = segments.sumOf { it.estSec(fallback) },
            purpose = purpose(zone, race),
            feel = feel(zone, race),
        )
    }

    fun purpose(zone: RunZone, race: Boolean = false): String = when {
        race -> "Dein Wettkampf. Alles aus den letzten Wochen zahlt sich heute aus – geh kontrolliert an und steigere dich."
        else -> when (zone) {
            RunZone.EASY -> "Grundlagenausdauer. Der größte Teil jedes Plans: stärkt Herz, Kapillaren und Sehnen, ohne dich zu ermüden."
            RunZone.LONG -> "Der lange Lauf – das Herz des Plans. Baut Ausdauer, Fettstoffwechsel und mentale Stärke für lange Strecken auf."
            RunZone.TEMPO -> "Schwellentraining. Ein zügiger, gleichmäßiger Block hebt deine Laktatschwelle – du kannst schneller laufen, bevor es brennt."
            RunZone.INTERVAL -> "Intervalle. Kurze, schnelle Abschnitte mit Trabpausen verbessern VO₂max und Laufökonomie."
            RunZone.RECOVERY -> "Regeneration. Ganz locker laufen fördert die Durchblutung und Erholung, ohne neue Ermüdung."
        }
    }

    fun feel(zone: RunZone, race: Boolean = false): String = when {
        race -> "Anstrengung 9–10 von 10"
        else -> when (zone) {
            RunZone.EASY, RunZone.LONG -> "Anstrengung 3–4 von 10 · du kannst dich unterhalten"
            RunZone.RECOVERY -> "Anstrengung 2–3 von 10 · sehr locker"
            RunZone.TEMPO -> "Anstrengung 7 von 10 · angenehm hart, nur kurze Sätze"
            RunZone.INTERVAL -> "Anstrengung 8–9 von 10 in den Belastungen"
        }
    }
}

/**
 * Which recorded run completes which planned unit: runs started from the
 * plan are linked directly; other runs (manual, Health Connect) count for a
 * still open unit on the same day, else for a missed one up to
 * [CATCH_UP_DAYS] days earlier (run made up the next day), like Runna's
 * auto-matching. Skipped units are never matched automatically.
 */
object RunPlanMatcher {

    const val CATCH_UP_DAYS = 2L

    data class Planned(val id: String, val date: java.time.LocalDate, val skipped: Boolean = false)
    data class Run(val id: String, val date: java.time.LocalDate, val plannedId: String?)

    /** plannedId → runId */
    fun match(planned: List<Planned>, runs: List<Run>): Map<String, String> {
        val result = HashMap<String, String>()
        val used = HashSet<String>()
        val plannedIds = planned.map { it.id }.toSet()
        runs.filter { it.plannedId != null && it.plannedId in plannedIds }.forEach { run ->
            if (run.plannedId!! !in result) {
                result[run.plannedId] = run.id
                used += run.id
            }
        }
        // Unlinked runs, or ones linked to a unit outside this plan.
        fun free(run: Run) = run.id !in used && (run.plannedId == null || run.plannedId !in plannedIds)
        val open = planned.filter { !it.skipped }.sortedBy { it.date }
        open.filter { it.id !in result }.forEach { p ->
            runs.firstOrNull { free(it) && it.date == p.date }?.let { run ->
                result[p.id] = run.id
                used += run.id
            }
        }
        runs.filter { free(it) }.sortedBy { it.date }.forEach { run ->
            open.lastOrNull { p ->
                p.id !in result && p.date.isBefore(run.date) && !p.date.isBefore(run.date.minusDays(CATCH_UP_DAYS))
            }?.let { p ->
                result[p.id] = run.id
                used += run.id
            }
        }
        return result
    }
}
