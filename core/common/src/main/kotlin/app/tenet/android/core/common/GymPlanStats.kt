package app.tenet.android.core.common

import java.time.LocalDate

/**
 * Statistics of a gym plan over its sessions: volume per session and per
 * exercise, top set and estimated 1RM progression.
 */
object GymPlanStats {

    /** One completed set of a finished session of the plan. */
    data class SetRow(
        val sessionId: String,
        val date: LocalDate,
        val exercise: String,
        val weightKg: Float,
        val reps: Int,
    )

    data class SessionStat(val sessionId: String, val date: LocalDate, val volumeKg: Float, val sets: Int)

    data class ExercisePoint(
        val date: LocalDate,
        val volumeKg: Float,
        val topWeightKg: Float,
        val topReps: Int,
        val e1rm: Float,
    )

    data class ExerciseStat(
        val name: String,
        /** Oldest first, one point per session. */
        val points: List<ExercisePoint>,
    ) {
        val bestE1rm: Float get() = points.maxOfOrNull { it.e1rm } ?: 0f
        val lastE1rm: Float get() = points.lastOrNull()?.e1rm ?: 0f
        /** e1RM change since the first session [%]. */
        val progressPercent: Int?
            get() {
                val first = points.firstOrNull()?.e1rm ?: return null
                if (first <= 0f || points.size < 2) return null
                return ((lastE1rm / first - 1f) * 100).toInt()
            }
    }

    fun sessions(rows: List<SetRow>): List<SessionStat> =
        rows.groupBy { it.sessionId }.map { (id, sets) ->
            SessionStat(id, sets.first().date, sets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat(), sets.size)
        }.sortedBy { it.date }

    fun exercises(rows: List<SetRow>, formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY): List<ExerciseStat> =
        rows.groupBy { it.exercise }.map { (name, sets) ->
            ExerciseStat(
                name = name,
                points = sets.groupBy { it.sessionId }.values.map { s ->
                    val top = s.maxWith(compareBy<SetRow> { it.weightKg }.thenBy { it.reps })
                    ExercisePoint(
                        date = s.first().date,
                        volumeKg = s.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat(),
                        topWeightKg = top.weightKg,
                        topReps = top.reps,
                        e1rm = s.maxOf { OneRepMax.oneRepMax(it.weightKg, it.reps, formula) },
                    )
                }.sortedBy { it.date },
            )
        }.sortedByDescending { it.points.size }
}
