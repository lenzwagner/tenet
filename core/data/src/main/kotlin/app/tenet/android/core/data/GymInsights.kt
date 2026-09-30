package app.tenet.android.core.data

import app.tenet.android.core.common.OneRepMax
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.SetType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A working set that counts (ticked off, or logged in a session without ticks). */
data class DoneSet(val weightKg: Float, val reps: Int, val durationSec: Int?)

/** One exercise in one finished session. */
data class ExerciseSessionStat(
    val sessionId: String,
    val date: LocalDate,
    val sets: List<DoneSet>,
) {
    val volumeKg: Float get() = sets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    val top: DoneSet? get() = sets.maxWithOrNull(compareBy<DoneSet> { it.weightKg }.thenBy { it.reps })
    val e1rm: Float get() = sets.maxOfOrNull { OneRepMax.oneRepMax(it.weightKg, it.reps) } ?: 0f
    val bestReps: Int get() = sets.maxOfOrNull { it.reps } ?: 0
    val longestHold: Int get() = sets.filter { it.reps == 0 }.maxOfOrNull { it.durationSec ?: 0 } ?: 0
    val totalReps: Int get() = sets.sumOf { it.reps }
    /** Bodyweight exercise: no set with extra weight. */
    val bodyweight: Boolean get() = sets.all { it.weightKg <= 0f }
}

/** Exercise detail like in Hevy: records and every session with it. */
data class ExerciseDetail(
    val exercise: Exercise,
    val sessions: List<ExerciseSessionStat>,
    /** Newest note on this exercise (Hevy-style), null if none. */
    val lastNote: String? = null,
) {
    val bestE1rm: Float get() = sessions.maxOfOrNull { it.e1rm } ?: 0f
    val heaviest: Float get() = sessions.maxOfOrNull { s -> s.sets.maxOfOrNull { it.weightKg } ?: 0f } ?: 0f
    val bestVolume: Float get() = sessions.maxOfOrNull { it.volumeKg } ?: 0f
    val mostReps: Int get() = sessions.maxOfOrNull { it.bestReps } ?: 0
    val totalSets: Int get() = sessions.sumOf { it.sets.size }
    val longestHold: Int get() = sessions.maxOfOrNull { it.longestHold } ?: 0
    val bestTotalReps: Int get() = sessions.maxOfOrNull { it.totalReps } ?: 0
    val bodyweight: Boolean get() = sessions.all { it.bodyweight }
}

data class ExerciseSummary(
    val exercise: Exercise,
    val current: ExerciseSessionStat,
    val previous: ExerciseSessionStat?,
    /** New best estimated 1RM compared to all earlier sessions. */
    val e1rmPr: Boolean,
    /** Heaviest weight ever lifted on this exercise. */
    val weightPr: Boolean,
    /** Bodyweight: most reps in one set ever. */
    val repsPr: Boolean = false,
    /** Bodyweight: longest hold ever. */
    val holdPr: Boolean = false,
    val note: String = "",
) {
    val anyPr: Boolean get() = e1rmPr || weightPr || repsPr || holdPr
}

/** "Workout abgeschlossen" / session detail. */
data class WorkoutSummary(
    val sessionId: String,
    val title: String,
    val startedAt: Long,
    val durationSec: Long,
    val exercises: List<ExerciseSummary>,
    /** Working sets per main muscle group. */
    val muscles: List<Pair<String, Int>>,
) {
    val volumeKg: Float get() = exercises.sumOf { it.current.volumeKg.toDouble() }.toFloat()
    val setCount: Int get() = exercises.sumOf { it.current.sets.size }
    val prCount: Int get() = exercises.count { it.anyPr }
    val totalReps: Int get() = exercises.sumOf { it.current.totalReps }
    val holdSec: Int get() = exercises.sumOf { e -> e.current.sets.filter { it.reps == 0 }.sumOf { it.durationSec ?: 0 } }
    /** Volume of the same exercises last time (for the comparison). */
    val previousVolumeKg: Float? get() = exercises.mapNotNull { it.previous?.volumeKg }.takeIf { it.isNotEmpty() }?.sum()
}

@Singleton
class GymInsights @Inject constructor(private val dao: SportDao) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Sets of one exercise per finished session, oldest first. */
    private suspend fun history(exerciseId: String): List<Pair<Long, ExerciseSessionStat>> =
        dao.exerciseHistory(exerciseId)
            .groupBy { it.sessionId }
            .values
            .map { rows ->
                val sets = rows
                    .filter { it.type != SetType.WARMUP.name && (it.reps > 0 || (it.durationSec ?: 0) > 0) }
                    .filter { if (it.sessionHasCompleted) it.completed else (it.weight > 0f || it.reps > 0) }
                    .map { DoneSet(it.weight, it.reps, it.durationSec) }
                rows.first().startedAt to ExerciseSessionStat(
                    sessionId = rows.first().sessionId,
                    date = Instant.ofEpochMilli(rows.first().startedAt).atZone(zone).toLocalDate(),
                    sets = sets,
                )
            }
            .filter { it.second.sets.isNotEmpty() }
            .sortedBy { it.first }

    suspend fun exerciseDetail(exerciseId: String): ExerciseDetail? {
        val exercise = dao.exercisesByIds(listOf(exerciseId)).firstOrNull() ?: return null
        return ExerciseDetail(exercise, history(exerciseId).map { it.second }, dao.lastExerciseNote(exerciseId, ""))
    }

    suspend fun workoutSummary(sessionId: String, title: String?): WorkoutSummary? {
        val session = dao.sessionOnce(sessionId) ?: return null
        val sessionExercises = dao.sessionExercisesOnce(sessionId)
        val exercises = dao.exercisesByIds(sessionExercises.map { it.exerciseId }).associateBy { it.id }
        val summaries = sessionExercises.mapNotNull { se ->
            val exercise = exercises[se.exerciseId] ?: return@mapNotNull null
            val all = history(se.exerciseId)
            val current = all.firstOrNull { it.second.sessionId == sessionId }?.second ?: return@mapNotNull null
            val earlier = all.filter { it.first < session.startedAt }.map { it.second }
            ExerciseSummary(
                exercise = exercise,
                current = current,
                previous = earlier.lastOrNull(),
                e1rmPr = earlier.isNotEmpty() && current.e1rm > (earlier.maxOfOrNull { it.e1rm } ?: 0f) + 0.01f,
                weightPr = earlier.isNotEmpty() &&
                    (current.sets.maxOfOrNull { it.weightKg } ?: 0f) > (earlier.maxOfOrNull { s -> s.sets.maxOfOrNull { it.weightKg } ?: 0f } ?: 0f),
                repsPr = earlier.isNotEmpty() && current.bodyweight && current.bestReps > 0 &&
                    current.bestReps > (earlier.maxOfOrNull { it.bestReps } ?: 0),
                note = se.notes,
                holdPr = earlier.isNotEmpty() && current.longestHold > 0 &&
                    current.longestHold > (earlier.maxOfOrNull { it.longestHold } ?: 0),
            )
        }
        val muscles = summaries
            .groupBy { it.exercise.primaryMuscles.split(',').first().trim().ifEmpty { "Sonstiges" } }
            .map { (m, list) -> m to list.sumOf { it.current.sets.size } }
            .sortedByDescending { it.second }
        return WorkoutSummary(
            sessionId = sessionId,
            title = title ?: if (session.discipline.name == "CALISTHENICS") "Calisthenics" else "Workout",
            startedAt = session.startedAt,
            durationSec = ((session.endedAt ?: System.currentTimeMillis()) - session.startedAt) / 1000,
            exercises = summaries,
            muscles = muscles,
        )
    }

    /** Working sets per main muscle group since [from] (weekly volume check). */
    suspend fun muscleSetsSince(from: Long): List<Pair<String, Int>> =
        dao.progressionSetsOnce(from)
            .filter { it.type != SetType.WARMUP.name && it.reps > 0 && (it.completed || !it.sessionHasCompleted) && (it.weight > 0f || it.completed) }
            .groupBy { it.primaryMuscles.split(',').first().trim() }
            .map { (m, sets) -> m to sets.size }
            .sortedByDescending { it.second }
}
