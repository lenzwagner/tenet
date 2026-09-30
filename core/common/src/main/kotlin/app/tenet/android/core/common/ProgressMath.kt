package app.tenet.android.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * Pure progress math for the gym feature (App_Konzept.md 5.2.1
 * "Fortschritt"): 1RM history, weekly volume per muscle group, PR
 * detection. Kept free of Room so it is unit testable.
 */
object ProgressMath {

    /** One completed set, flattened for analysis. */
    data class SetRecord(
        val exercise: String,
        /** Primary muscles as a comma-separated string (Exercise.primaryMuscles). */
        val primaryMuscles: String,
        val weight: Float,
        val reps: Int,
        /** Session start epoch millis. */
        val startedAt: Long,
    )

    data class DatedValue(val date: LocalDate, val value: Float)

    data class WeekVolume(val weekStart: LocalDate, val volumeKg: Float)

    data class PersonalBest(
        val exercise: String,
        val kind: Kind,
        val value: Float,
        /** When the record was set. */
        val at: Long,
    ) {
        enum class Kind { WEIGHT, REPS, ONE_REP_MAX, VOLUME }
    }

    // ---- 1RM history ----------------------------------------------------

    /**
     * Best estimated 1RM of [exercise] per training day, ascending by date.
     * Days without a completed set of that exercise are omitted.
     */
    fun oneRepMaxHistory(
        sets: List<SetRecord>,
        exercise: String,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DatedValue> =
        sets.asSequence()
            .filter { it.exercise == exercise }
            .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .map { (date, daySets) ->
                DatedValue(date, OneRepMax.best(daySets.asSequence().map { it.weight to it.reps }, formula))
            }
            .filter { it.value > 0f }
            .sortedBy { it.date }

    /** Exercises that have at least one completed set (for the chart selector). */
    fun exercisesWithData(sets: List<SetRecord>): List<String> =
        sets.map { it.exercise }.distinct().sorted()

    // ---- Weekly volume --------------------------------------------------

    /**
     * Volume (Σ weight × reps) per ISO week for [weeksBack] weeks ending
     * the week of [reference]. Weeks without sets are included with 0 kg so
     * the chart axis stays contiguous. Only [sets] whose primary muscle
     * contains [muscle] (case-insensitive, null = all) are counted.
     */
    fun weeklyVolume(
        sets: List<SetRecord>,
        muscle: String? = null,
        weeksBack: Int = 8,
        reference: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeekVolume> {
        val weekFields = WeekFields.ISO
        val currentWeek = reference.with(weekFields.dayOfWeek(), 1L)
        val relevant = if (muscle == null) {
            sets
        } else {
            sets.filter { record ->
                record.primaryMuscles.split(',').any { it.trim().equals(muscle, ignoreCase = true) }
            }
        }

        return (weeksBack downTo 0).map { back ->
            val weekStart = currentWeek.minusWeeks(back.toLong())
            val volume = relevant.sumOf { record ->
                val date = Instant.ofEpochMilli(record.startedAt).atZone(zone).toLocalDate()
                val belongs = !date.isBefore(weekStart) && date.isBefore(weekStart.plusWeeks(1))
                (if (belongs) record.weight * record.reps else 0f).toDouble()
            }
            WeekVolume(weekStart, volume.toFloat())
        }
    }

    /** Distinct primary muscle groups present in [sets], sorted. */
    fun muscleGroups(sets: List<SetRecord>): List<String> =
        sets.flatMap { it.primaryMuscles.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    // ---- PR detection ---------------------------------------------------

    /**
     * All-time personal bests per exercise plus a workout-volume record,
     * computed chronologically: a set is a PR when it beats the best value
     * recorded *before* its session. First records of a kind count as PRs
     * (nothing to beat yet). Results are sorted newest first.
     */
    fun personalBests(
        sets: List<SetRecord>,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): List<PersonalBest> {
        val bestWeight = mutableMapOf<String, Float>()
        val bestReps = mutableMapOf<String, Int>()
        val bestOrm = mutableMapOf<String, Float>()
        val events = mutableListOf<PersonalBest>()

        sets.sortedBy { it.startedAt }.forEach { record ->
            val orm = OneRepMax.oneRepMax(record.weight, record.reps, formula)

            if (record.weight > (bestWeight[record.exercise] ?: 0f)) {
                bestWeight[record.exercise] = record.weight
                events += PersonalBest(record.exercise, PersonalBest.Kind.WEIGHT, record.weight, record.startedAt)
            }
            if (record.reps > (bestReps[record.exercise] ?: 0)) {
                bestReps[record.exercise] = record.reps
                events += PersonalBest(record.exercise, PersonalBest.Kind.REPS, record.reps.toFloat(), record.startedAt)
            }
            if (orm > (bestOrm[record.exercise] ?: 0f)) {
                bestOrm[record.exercise] = orm
                events += PersonalBest(record.exercise, PersonalBest.Kind.ONE_REP_MAX, orm, record.startedAt)
            }
        }

        // Volume record: per finished session (grouped by startedAt day would
        // mix sessions; sessions are identified by their start timestamp).
        val bestVolume = mutableMapOf<Long, Float>()
        sets.groupBy { it.startedAt }.toSortedMap().forEach { (sessionStart, sessionSets) ->
            val volume = sessionSets.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
            val previousBest = bestVolume.values.maxOrNull() ?: 0f
            if (volume > previousBest) {
                bestVolume[sessionStart] = volume
                events += PersonalBest(
                    exercise = "Workout",
                    kind = PersonalBest.Kind.VOLUME,
                    value = volume,
                    at = sessionStart,
                )
            }
        }

        return events.sortedByDescending { it.at }
    }
}
