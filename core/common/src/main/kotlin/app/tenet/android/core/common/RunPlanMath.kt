package app.tenet.android.core.common

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure plan-structure math (App_Konzept.md 5.2.3): goal templates, weekly
 * run-type structure, moderate progression with regular unload weeks and
 * the generated units per week. Kept free of Room so it is unit testable.
 */
object RunPlanMath {

    /** Plan goal (stored as name in RunPlanDetail.goalId). */
    enum class RunGoal(val label: String) {
        FIVE_K("5 km"),
        TEN_K("10 km"),
        HALF("Halbmarathon"),
        MARATHON("Marathon"),
        GENERAL("Fit bleiben"),
        ;

        companion object {
            fun fromName(name: String?): RunGoal =
                entries.firstOrNull { it.name == name } ?: GENERAL
        }
    }

    /** Race distance of a goal [m]; null for "Fit bleiben". */
    fun raceDistanceM(goal: RunGoal): Int? = when (goal) {
        RunGoal.FIVE_K -> 5_000
        RunGoal.TEN_K -> 10_000
        RunGoal.HALF -> 21_097
        RunGoal.MARATHON -> 42_195
        RunGoal.GENERAL -> null
    }

    /** Taper length before the race [weeks]: longer races need more recovery. */
    fun taperWeeks(goal: RunGoal): Int = when (goal) {
        RunGoal.MARATHON -> 3
        RunGoal.HALF -> 2
        RunGoal.FIVE_K, RunGoal.TEN_K -> 1
        RunGoal.GENERAL -> 0
    }

    /**
     * Volume factor of a taper week relative to the peak, by weeks left to
     * the race (0 = race week): marathon 80 → 65 → 45 %, half 70 → 50 %,
     * short races 60 % – intensity stays, volume drops.
     */
    fun taperFactor(goal: RunGoal, weeksToRace: Int): Float {
        val steps = when (taperWeeks(goal)) {
            3 -> listOf(0.45f, 0.65f, 0.8f)
            2 -> listOf(0.5f, 0.7f)
            1 -> listOf(0.6f)
            else -> emptyList()
        }
        return steps.getOrNull(weeksToRace) ?: 1f
    }

    /** True when [weekIndex] of a [totalWeeks] plan is a taper week. */
    fun isTaperWeek(goal: RunGoal, weekIndex: Int, totalWeeks: Int, taper: Boolean): Boolean =
        taper && totalWeeks - 1 - weekIndex in 0 until taperWeeks(goal)

    /** One planned unit of a week (dayIndex: Monday = 0 ... Sunday = 6). */
    data class PlanUnit(
        val dayIndex: Int,
        val zone: RunZone,
        val targetDurationSec: Int?,
        val targetDistanceM: Int?,
        /** Compact interval spec, e.g. `[{"reps":4,"lengthM":800,"restSec":90}]`. */
        val intervalsJson: String?,
        val title: String,
    )

    /**
     * Weekly template per runs-per-week: quality mid-week, long run on the
     * weekend. Inputs are clamped to 2..6 runs.
     */
    /**
     * Moves the template's units onto the user's [days] (0 = Mo … 6 = So).
     * The long run goes to the last chosen weekend day (else the last day);
     * the other units are spread so that hard sessions (tempo, intervals,
     * long run) sit on consecutive days as rarely as possible. Units stay
     * in template order so that callers can index them.
     */
    fun assignDays(template: List<Pair<Int, RunZone>>, days: List<Int>?): List<Pair<Int, RunZone>> {
        val chosen = days?.distinct()?.sorted()?.filter { it in 0..6 }
        if (chosen == null || chosen.size != template.size) return template
        val longIndex = template.indexOfFirst { it.second == RunZone.LONG }
        val longDay = if (longIndex < 0) null else chosen.lastOrNull { it >= 5 } ?: chosen.last()
        val restDays = chosen.filter { it != longDay }
        val restUnits = template.indices.filter { it != longIndex }
        // Try every order of the remaining units (at most 5! = 120) and keep the
        // one with the fewest back-to-back hard days; ties keep template order.
        var best: List<Int> = restUnits
        var bestScore = Int.MAX_VALUE
        permutations(restUnits).forEach { order ->
            val dayOf = HashMap<Int, Int>()
            order.forEachIndexed { i, unit -> dayOf[unit] = restDays[i] }
            if (longIndex >= 0 && longDay != null) dayOf[longIndex] = longDay
            val hardDays = template.indices.filter { isHard(template[it].second) }.map { dayOf.getValue(it) }.toSet()
            val score = hardDays.count { (it + 1) % 7 in hardDays }
            if (score < bestScore) {
                bestScore = score
                best = order
            }
        }
        val dayOf = HashMap<Int, Int>()
        best.forEachIndexed { i, unit -> dayOf[unit] = restDays[i] }
        if (longIndex >= 0 && longDay != null) dayOf[longIndex] = longDay
        return template.mapIndexed { i, (_, zone) -> dayOf.getValue(i) to zone }
    }

    private fun isHard(zone: RunZone) = zone == RunZone.TEMPO || zone == RunZone.INTERVAL || zone == RunZone.LONG

    private fun permutations(items: List<Int>): Sequence<List<Int>> = sequence {
        if (items.size <= 1) {
            yield(items)
        } else {
            for (i in items.indices) {
                val rest = items.take(i) + items.drop(i + 1)
                for (p in permutations(rest)) yield(listOf(items[i]) + p)
            }
        }
    }

    fun weeklyTemplate(runsPerWeek: Int): List<Pair<Int, RunZone>> =
        when (runsPerWeek.coerceIn(2, 6)) {
            2 -> listOf(0 to RunZone.EASY, 5 to RunZone.LONG)
            3 -> listOf(0 to RunZone.EASY, 2 to RunZone.INTERVAL, 5 to RunZone.LONG)
            4 -> listOf(
                0 to RunZone.EASY,
                1 to RunZone.TEMPO,
                3 to RunZone.INTERVAL,
                5 to RunZone.LONG,
            )
            5 -> listOf(
                0 to RunZone.EASY,
                1 to RunZone.TEMPO,
                3 to RunZone.INTERVAL,
                5 to RunZone.LONG,
                6 to RunZone.RECOVERY,
            )
            else -> listOf(
                0 to RunZone.EASY,
                1 to RunZone.TEMPO,
                2 to RunZone.RECOVERY,
                3 to RunZone.INTERVAL,
                5 to RunZone.LONG,
                6 to RunZone.EASY,
            )
        }

    /**
     * Number of plan weeks from [planStart] to [goalDate] inclusive,
     * clamped to a sane 4..24 week block.
     */
    fun weekCount(planStart: LocalDate, goalDate: LocalDate): Int {
        val weeks = ChronoUnit.WEEKS.between(
            WeekMath.weekStart(planStart),
            WeekMath.weekStart(goalDate),
        ).toInt() + 1
        return weeks.coerceIn(4, 24)
    }

    /**
     * Load factor of a week (0-based): +8 % per four-week build cycle,
     * every fourth week is an unload week at 70 % of the current level
     * ("Umfangssteigerung moderat halten, Entlastungswochen einplanen").
     */
    fun loadFactor(weekIndex: Int): Float {
        val week = weekIndex.coerceAtLeast(0)
        val base = 1f + 0.08f * (week / 4)
        return if ((week + 1) % 4 == 0) base * 0.7f else base
    }

    /** Interval length per goal, meters. */
    private fun intervalLength(goal: RunGoal): Int = when (goal) {
        RunGoal.FIVE_K, RunGoal.TEN_K, RunGoal.GENERAL -> 800
        RunGoal.HALF, RunGoal.MARATHON -> 1000
    }

    /** Interval reps: 3 + one per build cycle, capped at 6 (minus one on unload weeks). */
    fun intervalReps(weekIndex: Int): Int {
        val week = weekIndex.coerceAtLeast(0)
        val reps = (3 + week / 4).coerceAtMost(6)
        return if ((week + 1) % 4 == 0) (reps - 1).coerceAtLeast(3) else reps
    }

    /** Base duration [s] per goal and zone (the long run is capped below). */
    private fun baseDuration(goal: RunGoal, zone: RunZone): Int = when (goal) {
        RunGoal.FIVE_K -> when (zone) {
            RunZone.EASY -> 30 * 60
            RunZone.LONG -> 40 * 60
            RunZone.TEMPO -> 20 * 60
            RunZone.RECOVERY -> 25 * 60
            else -> 0
        }
        RunGoal.TEN_K -> when (zone) {
            RunZone.EASY -> 35 * 60
            RunZone.LONG -> 50 * 60
            RunZone.TEMPO -> 25 * 60
            RunZone.RECOVERY -> 30 * 60
            else -> 0
        }
        RunGoal.GENERAL -> when (zone) {
            RunZone.EASY -> 35 * 60
            RunZone.LONG -> 45 * 60
            RunZone.TEMPO -> 20 * 60
            RunZone.RECOVERY -> 30 * 60
            else -> 0
        }
        RunGoal.HALF -> when (zone) {
            RunZone.EASY -> 40 * 60
            RunZone.LONG -> 75 * 60
            RunZone.TEMPO -> 30 * 60
            RunZone.RECOVERY -> 30 * 60
            else -> 0
        }
        RunGoal.MARATHON -> when (zone) {
            RunZone.EASY -> 45 * 60
            RunZone.LONG -> 100 * 60
            RunZone.TEMPO -> 40 * 60
            RunZone.RECOVERY -> 35 * 60
            else -> 0
        }
    }

    /** Upper bound for the long run per goal [minutes]. */
    private fun longRunCapSec(goal: RunGoal): Int = when (goal) {
        RunGoal.FIVE_K, RunGoal.TEN_K, RunGoal.GENERAL -> 90 * 60
        RunGoal.HALF -> 150 * 60
        RunGoal.MARATHON -> 180 * 60
    }

    fun intervalSpecJson(reps: Int, lengthM: Int, restSec: Int): String =
        """[{"reps":$reps,"lengthM":$lengthM,"restSec":$restSec}]"""

    fun zoneTitle(zone: RunZone, reps: Int = 0, lengthM: Int = 0): String = when (zone) {
        RunZone.EASY -> "Lockerlauf"
        RunZone.LONG -> "Langlauf"
        RunZone.TEMPO -> "Tempolauf"
        RunZone.INTERVAL -> "$reps × $lengthM m"
        RunZone.RECOVERY -> "Regeneration"
    }

    /**
     * All units of week [weekIndex] (0-based). With [taper] and a known
     * [totalWeeks], the last weeks before the race keep their structure but
     * scale volume down from the peak level ([taperFactor]); intervals keep
     * their pace with fewer reps.
     */
    fun planWeek(
        goal: RunGoal,
        weekIndex: Int,
        runsPerWeek: Int,
        totalWeeks: Int? = null,
        taper: Boolean = false,
        /** Chosen weekdays 0 (Mo) … 6 (So); null = default template days. */
        days: List<Int>? = null,
        /** Volume scale for the runner's level (0.8 beginner … 1.15 ambitious). */
        volume: Float = 1f,
    ): List<PlanUnit> = assignDays(weeklyTemplate(runsPerWeek), days).map { (dayIndex, zone) ->
        val tapering = totalWeeks != null && isTaperWeek(goal, weekIndex, totalWeeks, taper)
        val weeksToRace = if (totalWeeks != null) totalWeeks - 1 - weekIndex else Int.MAX_VALUE
        // Taper weeks scale from the last build week's level.
        val peakWeek = if (tapering && totalWeeks != null) (totalWeeks - 1 - taperWeeks(goal)).coerceAtLeast(0) else weekIndex
        val unload = (weekIndex + 1) % 4 == 0
        val build = !tapering && !unload
        if (zone == RunZone.INTERVAL) {
            val length = intervalLength(goal)
            val reps = if (tapering) {
                (intervalReps(peakWeek) - (taperWeeks(goal) - weeksToRace)).coerceAtLeast(2)
            } else {
                intervalReps(weekIndex)
            }
            val rest = if (length >= 1000) 120 else 90
            // Build weeks rotate classic → progressive → pyramid; unload and taper stay classic.
            val (blocks, title) = when {
                build && weekIndex % 3 == 1 -> progressiveReps(reps, length, rest) to "$reps × $length m progressiv"
                build && weekIndex % 3 == 2 -> pyramid(goal, reps) to pyramidTitle(goal, reps)
                else -> listOf(RunWorkoutStructure.Block(reps, length, null, rest)) to zoneTitle(zone, reps, length)
            }
            PlanUnit(
                dayIndex = dayIndex,
                zone = zone,
                targetDurationSec = null,
                targetDistanceM = blocks.sumOf { (it.lengthM ?: 0) * it.reps },
                intervalsJson = RunWorkoutStructure.blocksJson(blocks),
                title = title,
            )
        } else {
            val factor = if (tapering) peakLoad(peakWeek) * taperFactor(goal, weeksToRace) else loadFactor(weekIndex)
            val duration = (baseDuration(goal, zone) * factor * volume).toInt()
                .let { if (zone == RunZone.LONG) it.coerceAtMost(longRunCapSec(goal)) else it }
            when {
                // Every second build week: threshold as blocks instead of one piece (cruise intervals).
                zone == RunZone.TEMPO && build && weekIndex % 2 == 1 && duration >= 16 * 60 -> {
                    val reps = if (duration >= 27 * 60) 3 else 2
                    val work = ((duration / reps) / 60) * 60
                    PlanUnit(
                        dayIndex = dayIndex,
                        zone = zone,
                        targetDurationSec = work * reps,
                        targetDistanceM = null,
                        intervalsJson = RunWorkoutStructure.blocksJson(listOf(RunWorkoutStructure.Block(reps, null, work, 90))),
                        title = "$reps × ${work / 60} min Schwelle",
                    )
                }
                // Race goals: long runs with a race-pace finish from week 3, every second build week.
                zone == RunZone.LONG && build && weekIndex >= 2 && weekIndex % 2 == 0 && finishBase(goal) > 0 -> {
                    val finish = (finishBase(goal) + 5 * 60 * ((weekIndex - 2) / 4)).coerceAtMost(finishBase(goal) * 2)
                    PlanUnit(
                        dayIndex = dayIndex,
                        zone = zone,
                        targetDurationSec = duration,
                        targetDistanceM = null,
                        intervalsJson = RunWorkoutStructure.finishJson(finish),
                        title = "Langlauf · ${finish / 60} min Renntempo",
                    )
                }
                else -> PlanUnit(
                    dayIndex = dayIndex,
                    zone = zone,
                    targetDurationSec = duration,
                    targetDistanceM = null,
                    intervalsJson = null,
                    title = zoneTitle(zone),
                )
            }
        }
    }

    /** Minutes of the race-pace finish of a long run (0 = none for this goal). */
    private fun finishBase(goal: RunGoal): Int = when (goal) {
        RunGoal.TEN_K -> 10 * 60
        RunGoal.HALF -> 15 * 60
        RunGoal.MARATHON -> 20 * 60
        else -> 0
    }

    /** Same reps, each a little faster: from +4 s/km to −4 s/km around the target pace. */
    fun progressiveReps(reps: Int, lengthM: Int, restSec: Int): List<RunWorkoutStructure.Block> =
        (0 until reps).map { i ->
            val delta = if (reps <= 1) 0 else (4 - 8.0 * i / (reps - 1)).let { kotlin.math.round(it).toInt() }
            RunWorkoutStructure.Block(1, lengthM, null, restSec, delta)
        }

    /** Up and down the ladder; longer with more reps in later weeks. */
    fun pyramid(goal: RunGoal, reps: Int): List<RunWorkoutStructure.Block> {
        val steps = when (goal) {
            RunGoal.HALF, RunGoal.MARATHON -> if (reps >= 5) listOf(600, 1000, 1600, 2000, 1600, 1000, 600) else listOf(600, 1000, 1600, 1000, 600)
            else -> if (reps >= 5) listOf(400, 800, 1200, 1600, 1200, 800, 400) else listOf(400, 800, 1200, 800, 400)
        }
        return steps.map { RunWorkoutStructure.Block(1, it, null, if (it >= 1200) 120 else 90) }
    }

    private fun pyramidTitle(goal: RunGoal, reps: Int): String {
        val steps = pyramid(goal, reps).map { it.lengthM ?: 0 }
        return "Pyramide ${steps.first()}–${steps.max()} m"
    }

    /** Load of a build week ignoring its own unload dip (taper starts from the true peak). */
    private fun peakLoad(weekIndex: Int): Float = 1f + 0.08f * (weekIndex.coerceAtLeast(0) / 4)

    // ---- Pace progression ---------------------------------------------------

    /** Expected improvement without a goal time: 1.5 % per 4 build weeks, at most 6 %. */
    private const val DEFAULT_GAIN_PER_CYCLE = 0.015
    private const val MAX_DEFAULT_GAIN = 0.06

    /** A goal time needing more than this over the plan is not trusted fully. */
    private const val MAX_GAIN = 0.10

    /**
     * 5 km form the plan works toward [s]: the goal time converted to 5 km
     * (Riegel), else a realistic gain over the build weeks. Never slower
     * than the current form, never more than [MAX_GAIN] faster.
     */
    fun goal5kSec(current5kSec: Int, goal: RunGoal, targetTimeSec: Int?, totalWeeks: Int, taper: Boolean): Int {
        val buildWeeks = (totalWeeks - if (taper) taperWeeks(goal) else 0).coerceAtLeast(1)
        val distance = raceDistanceM(goal)
        val wanted = if (targetTimeSec != null && distance != null) {
            RacePrediction.riegel(distance, targetTimeSec, 5_000)
        } else {
            val gain = (DEFAULT_GAIN_PER_CYCLE * buildWeeks / 4.0).coerceAtMost(MAX_DEFAULT_GAIN)
            (current5kSec * (1 - gain)).toInt()
        }
        val fastest = (current5kSec * (1 - MAX_GAIN)).toInt()
        return wanted.coerceIn(fastest, current5kSec)
    }

    /**
     * 5 km anchor for the paces of week [weekIndex]: moves step by step from
     * the current form to [goal5kSec] over the build weeks, so tempo and
     * interval paces get a little faster as fitness grows; unload weeks keep
     * the previous week's paces, taper weeks (and the race) use the goal form.
     */
    fun anchor5kForWeek(current5kSec: Int, goal5kSec: Int, weekIndex: Int, totalWeeks: Int, goal: RunGoal, taper: Boolean): Int {
        val buildWeeks = (totalWeeks - if (taper) taperWeeks(goal) else 0).coerceAtLeast(1)
        if (weekIndex >= buildWeeks - 1) return goal5kSec
        // An unload week (every fourth) does not add speed.
        val effective = if ((weekIndex + 1) % 4 == 0) weekIndex - 1 else weekIndex
        val fraction = effective.coerceAtLeast(0).toDouble() / (buildWeeks - 1).coerceAtLeast(1)
        return (current5kSec + (goal5kSec - current5kSec) * fraction).toInt()
    }

    // ---- Goal time check ------------------------------------------------------

    enum class GoalRealism { REALISTIC, AMBITIOUS, UNREALISTIC }

    data class GoalCheck(
        val realism: GoalRealism,
        /** Race time with today's form (Riegel from the 5 km form). */
        val predictedNowSec: Int,
        /** Expected race time on race day after the remaining training. */
        val expectedRaceDaySec: Int,
        /** Fastest still plausible race time (strong training response). */
        val stretchSec: Int,
    )

    /** Typical gain over [buildWeeks] of training: 1.5 % per 4 weeks, at most 6 %. */
    fun expectedGain(buildWeeks: Int): Double = (DEFAULT_GAIN_PER_CYCLE * buildWeeks.coerceAtLeast(0) / 4.0).coerceAtMost(MAX_DEFAULT_GAIN)

    /**
     * Race time to expect on race day from today's prediction and the build
     * weeks still ahead ([weeksLeft] incl. the taper weeks).
     */
    fun expectedRaceDaySec(predictedNowSec: Int, goal: RunGoal, weeksLeft: Int, taper: Boolean): Int {
        val build = (weeksLeft - if (taper) taperWeeks(goal) else 0).coerceAtLeast(0)
        return (predictedNowSec * (1 - expectedGain(build))).toInt()
    }

    /**
     * Is [targetSec] for [goal] realistic in [totalWeeks] from a 5 km form of
     * [current5kSec]? Realistic = at or above the expected race-day time;
     * ambitious = up to [MAX_GAIN] faster than today; unrealistic = beyond.
     */
    fun checkGoal(current5kSec: Int, goal: RunGoal, targetSec: Int, totalWeeks: Int, taper: Boolean): GoalCheck? {
        val distance = raceDistanceM(goal) ?: return null
        val now = RacePrediction.riegel(5_000, current5kSec, distance)
        val expected = expectedRaceDaySec(now, goal, totalWeeks, taper)
        val stretch = (now * (1 - MAX_GAIN)).toInt()
        val realism = when {
            targetSec >= expected -> GoalRealism.REALISTIC
            targetSec >= stretch -> GoalRealism.AMBITIOUS
            else -> GoalRealism.UNREALISTIC
        }
        return GoalCheck(realism, now, expected, stretch)
    }
}
