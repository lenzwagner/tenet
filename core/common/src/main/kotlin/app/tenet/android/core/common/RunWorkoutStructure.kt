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
    data class Paces(
        val easy: Int?,
        val recovery: Int?,
        /** Goal race pace, for race-pace finishes of long runs. */
        val race: Int? = null,
    )

    const val WARMUP_SEC = 10 * 60
    const val COOLDOWN_SEC = 10 * 60
    private const val DEFAULT_EASY_PACE = 6 * 60 + 30

    data class IntervalSpec(val reps: Int, val lengthM: Int, val restSec: Int)

    /**
     * One block of a structured session: [reps] × ([lengthM] or [workSec])
     * with [restSec] jog between reps, run [deltaSec] s/km off the session's
     * target pace (negative = faster; progressive reps use one block each).
     */
    data class Block(val reps: Int, val lengthM: Int?, val workSec: Int?, val restSec: Int, val deltaSec: Int = 0)

    private fun objects(json: String?): List<String> =
        if (json.isNullOrBlank()) emptyList() else Regex("\\{[^}]*\\}").findAll(json).map { it.value }.toList()

    private fun num(obj: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(obj)?.groupValues?.get(1)?.toIntOrNull()

    /** All work blocks of `[{"reps":…,"lengthM"|"workSec":…,"restSec":…,"deltaSec":…}, …]`. */
    fun parseBlocks(json: String?): List<Block> = objects(json).mapNotNull { o ->
        val reps = num(o, "reps") ?: return@mapNotNull null
        val length = num(o, "lengthM")
        val work = num(o, "workSec")
        if (length == null && work == null) return@mapNotNull null
        Block(reps, length, work, num(o, "restSec") ?: 0, num(o, "deltaSec") ?: 0)
    }

    /** Race-pace finish of a long run: `[{"finishSec":1200}]` → 1200. */
    fun finishSec(json: String?): Int? = objects(json).firstNotNullOfOrNull { num(it, "finishSec") }

    fun blocksJson(blocks: List<Block>): String = blocks.joinToString(",", "[", "]") { b ->
        buildList {
            add("\"reps\":${b.reps}")
            b.lengthM?.let { add("\"lengthM\":$it") }
            b.workSec?.let { add("\"workSec\":$it") }
            add("\"restSec\":${b.restSec}")
            if (b.deltaSec != 0) add("\"deltaSec\":${b.deltaSec}")
        }.joinToString(",", "{", "}")
    }

    fun finishJson(finishSec: Int): String = "[{\"finishSec\":$finishSec}]"

    /** First distance block as a simple spec (reminder text). */
    fun parseIntervals(json: String?): IntervalSpec? =
        parseBlocks(json).firstOrNull { it.lengthM != null }?.let { IntervalSpec(it.reps, it.lengthM!!, it.restSec) }

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
                zone == RunZone.INTERVAL || (zone == RunZone.TEMPO && parseBlocks(intervalsJson).isNotEmpty()) -> {
                    val blocks = parseBlocks(intervalsJson).ifEmpty { listOf(Block(4, 800, null, 90)) }
                    val total = blocks.sumOf { it.reps }
                    var rep = 0
                    add(Segment(Kind.WARMUP, WARMUP_SEC, null, easy))
                    blocks.forEach { b ->
                        repeat(b.reps) {
                            rep++
                            val pace = targetPaceSecPerKm?.let { it + b.deltaSec }
                            add(Segment(Kind.WORK, b.workSec, b.lengthM, pace, rep))
                            if (rep < total && b.restSec > 0) add(Segment(Kind.RECOVERY, b.restSec, null, paces.recovery, rep))
                        }
                    }
                    add(Segment(Kind.COOLDOWN, COOLDOWN_SEC, null, easy))
                }
                (zone == RunZone.LONG || zone == RunZone.EASY) && finishSec(intervalsJson) != null && targetDurationSec != null -> {
                    // Long run with a race-pace finish: easy first, the last minutes at goal pace.
                    val finish = finishSec(intervalsJson)!!.coerceAtMost(targetDurationSec / 2)
                    add(Segment(Kind.STEADY, targetDurationSec - finish, null, targetPaceSecPerKm))
                    add(Segment(Kind.WORK, finish, null, paces.race ?: targetPaceSecPerKm))
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
            purpose = purpose(zone, race, intervalsJson),
            feel = feel(zone, race),
        )
    }

    /** Planned pace per kilometre over the whole session (warm-up, reps, jogs, cool-down). */
    data class Split(val km: Int, val distanceM: Int, val paceSecPerKm: Int, val kind: Kind)

    /**
     * Splits the session into kilometres: each km gets the distance-weighted
     * pace of the segments it covers, and the kind that dominates it. The
     * last split may be shorter than 1 km.
     */
    fun kmSplits(workout: Workout, fallbackPace: Int = DEFAULT_EASY_PACE): List<Split> {
        data class Piece(val m: Int, val pace: Int, val kind: Kind)
        val pieces = workout.segments.map { Piece(it.estM(fallbackPace), it.paceSecPerKm ?: fallbackPace, it.kind) }.filter { it.m > 0 }
        val splits = mutableListOf<Split>()
        var km = 1
        var filled = 0
        var timeSec = 0.0
        val kindM = HashMap<Kind, Int>()
        fun close(dist: Int) {
            if (dist <= 0) return
            val kind = kindM.maxByOrNull { it.value }?.key ?: Kind.STEADY
            splits += Split(km, dist, (timeSec / dist * 1000).roundToInt(), kind)
            km++; filled = 0; timeSec = 0.0; kindM.clear()
        }
        for (p in pieces) {
            var left = p.m
            while (left > 0) {
                val take = minOf(left, 1000 - filled)
                filled += take
                timeSec += take / 1000.0 * p.pace
                kindM[p.kind] = (kindM[p.kind] ?: 0) + take
                left -= take
                if (filled == 1000) close(1000)
            }
        }
        if (filled >= 100) close(filled)
        return splits
    }

    fun purpose(zone: RunZone, race: Boolean = false, intervalsJson: String? = null): String = when {
        finishSec(intervalsJson) != null && !race ->
            "Langer Lauf mit Endbeschleunigung: die letzten Minuten im Renntempo, wenn die Beine schon müde sind – das trainiert genau das Gefühl für das Rennende."
        zone == RunZone.INTERVAL && parseBlocks(intervalsJson).size > 1 && parseBlocks(intervalsJson).any { it.deltaSec != 0 } ->
            "Progressive Intervalle: jede Wiederholung etwas schneller als die vorige. Du lernst, kontrolliert anzugehen und hinten raus zu beschleunigen."
        zone == RunZone.INTERVAL && parseBlocks(intervalsJson).map { it.lengthM }.distinct().size > 2 ->
            "Pyramide: erst länger werdende, dann kürzer werdende Abschnitte. Trainiert VO₂max und Tempogefühl über verschiedene Längen."
        zone == RunZone.TEMPO && parseBlocks(intervalsJson).isNotEmpty() ->
            "Schwellen-Blöcke: mehrere Abschnitte an der Laktatschwelle mit kurzen Trabpausen – mehr Zeit an der Schwelle als am Stück möglich wäre."
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
