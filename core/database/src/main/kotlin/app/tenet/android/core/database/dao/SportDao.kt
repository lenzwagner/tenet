package app.tenet.android.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import app.tenet.android.core.database.entity.BodyMetric
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RoutineExercise
import app.tenet.android.core.database.entity.RunPlanWorkout
import app.tenet.android.core.database.entity.SessionExercise
import app.tenet.android.core.database.entity.SetEntry
import app.tenet.android.core.database.entity.TrainingPlan
import app.tenet.android.core.database.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SportDao {

    // ---- Exercises -----------------------------------------------------

    @Query("SELECT * FROM Exercise WHERE discipline = :discipline ORDER BY name")
    fun observeExercises(discipline: Discipline): Flow<List<Exercise>>

    @Query("SELECT * FROM Exercise WHERE id IN (:ids)")
    suspend fun exercisesByIds(ids: List<String>): List<Exercise>

    @Insert
    suspend fun insertExercises(exercises: List<Exercise>)

    /** Adds library exercises that are missing (setup templates), keeps existing ones. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertMissingExercises(exercises: List<Exercise>)

    @Query("DELETE FROM RoutineExercise WHERE plannedWorkoutId = :workoutId")
    suspend fun deleteRoutine(workoutId: String)

    data class ExerciseHistoryRow(
        val sessionId: String,
        val startedAt: Long,
        val weight: Float,
        val reps: Int,
        val durationSec: Int?,
        val completed: Boolean,
        val type: String,
        val sortOrder: Int,
        val sessionHasCompleted: Boolean,
    )

    data class MuscleSetRow(
        val primaryMuscles: String,
        val weight: Float,
        val reps: Int,
        val completed: Boolean,
        val type: String,
        val sessionHasCompleted: Boolean,
    )

    /** Sets of finished gym sessions since [from] with the exercise's main muscles. */
    @Query(
        """
        SELECT Exercise.primaryMuscles AS primaryMuscles, SetEntry.weight AS weight, SetEntry.reps AS reps,
               SetEntry.completed AS completed, SetEntry.type AS type,
               EXISTS(
                   SELECT 1 FROM SetEntry s2
                   INNER JOIN SessionExercise x2 ON s2.sessionExerciseId = x2.id
                   WHERE x2.sessionId = WorkoutSession.id AND s2.completed = 1
               ) AS sessionHasCompleted
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        INNER JOIN Exercise ON SessionExercise.exerciseId = Exercise.id
        INNER JOIN WorkoutSession ON SessionExercise.sessionId = WorkoutSession.id
        WHERE WorkoutSession.endedAt IS NOT NULL AND WorkoutSession.discipline = 'GYM'
          AND WorkoutSession.startedAt >= :from
        """,
    )
    suspend fun progressionSetsOnce(from: Long): List<MuscleSetRow>

    /** Every set of an exercise in finished sessions, oldest session first. */
    @Query(
        """
        SELECT WorkoutSession.id AS sessionId, WorkoutSession.startedAt AS startedAt,
               SetEntry.weight AS weight, SetEntry.reps AS reps, SetEntry.durationSec AS durationSec,
               SetEntry.completed AS completed, SetEntry.type AS type, SetEntry.sortOrder AS sortOrder,
               EXISTS(
                   SELECT 1 FROM SetEntry s2
                   INNER JOIN SessionExercise x2 ON s2.sessionExerciseId = x2.id
                   WHERE x2.sessionId = WorkoutSession.id AND s2.completed = 1
               ) AS sessionHasCompleted
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        INNER JOIN WorkoutSession ON SessionExercise.sessionId = WorkoutSession.id
        WHERE SessionExercise.exerciseId = :exerciseId AND WorkoutSession.endedAt IS NOT NULL
        ORDER BY WorkoutSession.startedAt ASC, SetEntry.sortOrder ASC
        """,
    )
    suspend fun exerciseHistory(exerciseId: String): List<ExerciseHistoryRow>

    /** Moves a planned unit to another weekday of its week (0 = Mo). */
    @Query("UPDATE PlannedWorkout SET dayIndex = :dayIndex WHERE id = :id")
    suspend fun movePlannedDay(id: String, dayIndex: Int)

    @Query("UPDATE PlannedWorkout SET skipped = :skipped WHERE id = :id")
    suspend fun setPlannedSkipped(id: String, skipped: Boolean)

    /** Fills in the catalog pattern of built-in exercises (kept once set). */
    @Query("UPDATE Exercise SET pattern = :pattern WHERE id = :id AND pattern IS NULL")
    suspend fun setPatternIfMissing(id: String, pattern: String)

    @Query("SELECT COUNT(*) FROM Exercise WHERE pattern IS NULL AND custom = 0")
    suspend fun exercisesWithoutPattern(): Int

    // ---- Session editing (swap / add / remove exercises) ----------------

    @Query("SELECT * FROM SessionExercise WHERE id = :id")
    suspend fun sessionExerciseOnce(id: String): SessionExercise?

    @Query("UPDATE SessionExercise SET exerciseId = :exerciseId WHERE id = :id")
    suspend fun setSessionExerciseExercise(id: String, exerciseId: String)

    @Query("UPDATE SessionExercise SET notes = :notes WHERE id = :id")
    suspend fun setSessionExerciseNotes(id: String, notes: String)

    /** Newest non-empty note on this exercise from another session. */
    @Query(
        """
        SELECT SessionExercise.notes FROM SessionExercise
        JOIN WorkoutSession ON WorkoutSession.id = SessionExercise.sessionId
        WHERE SessionExercise.exerciseId = :exerciseId AND SessionExercise.sessionId != :excludeSessionId
          AND SessionExercise.notes != ''
        ORDER BY WorkoutSession.startedAt DESC LIMIT 1
        """,
    )
    suspend fun lastExerciseNote(exerciseId: String, excludeSessionId: String): String?

    @Query("DELETE FROM SessionExercise WHERE id = :id")
    suspend fun deleteSessionExercise(id: String)

    @Query("DELETE FROM SetEntry WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun deleteSetsOfSessionExercise(sessionExerciseId: String)

    @Query("DELETE FROM SetEntry WHERE sessionExerciseId = :sessionExerciseId AND completed = 0")
    suspend fun deleteOpenSetsOfSessionExercise(sessionExerciseId: String)

    /** Makes room after [afterOrder] for an inserted exercise. */
    @Query("UPDATE SessionExercise SET sortOrder = sortOrder + 1 WHERE sessionId = :sessionId AND sortOrder > :afterOrder")
    suspend fun shiftSessionExercises(sessionId: String, afterOrder: Int)

    @Query("DELETE FROM PlannedWorkout WHERE planId = :planId")
    suspend fun deleteWorkoutsOfPlan(planId: String)

    /** Workout of the plan trained last (for rotating splits). */
    @Query(
        """
        SELECT plannedWorkoutId FROM WorkoutSession
        WHERE endedAt IS NOT NULL
          AND plannedWorkoutId IN (SELECT id FROM PlannedWorkout WHERE planId = :planId)
        ORDER BY startedAt DESC LIMIT 1
        """,
    )
    fun observeLastWorkoutId(planId: String): Flow<String?>

    @Query(
        """
        SELECT plannedWorkoutId FROM WorkoutSession
        WHERE endedAt IS NOT NULL
          AND plannedWorkoutId IN (SELECT id FROM PlannedWorkout WHERE planId = :planId)
        ORDER BY startedAt DESC LIMIT 1
        """,
    )
    suspend fun lastWorkoutIdOnce(planId: String): String?

    @Query("SELECT * FROM PlannedWorkout WHERE planId = :planId ORDER BY sortOrder")
    suspend fun workoutsOnce(planId: String): List<PlannedWorkout>

    @Query("SELECT COUNT(*) FROM Exercise WHERE discipline = :discipline")
    suspend fun exerciseCount(discipline: Discipline): Int

    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun exerciseById(id: String): Exercise?

    @Query("SELECT * FROM Exercise WHERE name LIKE :pattern ORDER BY name LIMIT 20")
    suspend fun searchExercises(pattern: String): List<Exercise>

    @Upsert
    suspend fun upsertExercise(exercise: Exercise)

    @Query("DELETE FROM Exercise WHERE id = :id AND custom = 1")
    suspend fun deleteCustomExercise(id: String)

    // ---- Plans ---------------------------------------------------------

    @Query("SELECT * FROM TrainingPlan WHERE active = 1 AND discipline = :discipline LIMIT 1")
    fun observeActivePlan(discipline: Discipline): Flow<TrainingPlan?>

    @Query("SELECT * FROM TrainingPlan WHERE active = 1 AND discipline = :discipline LIMIT 1")
    suspend fun activePlanOnce(discipline: Discipline): TrainingPlan?

    @Upsert
    suspend fun upsertPlan(plan: TrainingPlan)

    /** Deactivates all plans of one discipline (single active plan per discipline). */
    @Query("UPDATE TrainingPlan SET trainingDays = :days WHERE id = :planId")
    suspend fun setTrainingDays(planId: String, days: String?)

    @Query("UPDATE TrainingPlan SET active = 0 WHERE discipline = :discipline")
    suspend fun deactivatePlans(discipline: Discipline)

    /**
     * Active planned workouts of a weekly structure (weekIndex + dayIndex,
     * anchored at the plan's startDate) for the week calendar and the
     * running page. The concrete date is computed in the repository from
     * the anchor Monday; [RecurringPlanned.runWorkout] carries the running
     * targets for active running plans.
     */
    data class RecurringPlanned(
        @Embedded val planned: PlannedWorkout,
        val planStartDate: String?,
        @Embedded val runWorkout: RunPlanWorkout?,
    )

    @Query(
        """
        SELECT PlannedWorkout.*, TrainingPlan.startDate AS planStartDate,
               RunPlanWorkout.*
        FROM PlannedWorkout
        INNER JOIN TrainingPlan ON PlannedWorkout.planId = TrainingPlan.id
        LEFT JOIN RunPlanWorkout
            ON PlannedWorkout.id = RunPlanWorkout.plannedWorkoutId
        WHERE TrainingPlan.active = 1
          AND PlannedWorkout.weekIndex IS NOT NULL
          AND PlannedWorkout.dayIndex IS NOT NULL
        """,
    )
    fun observeRecurringPlanned(): Flow<List<RecurringPlanned>>

    @Query("SELECT * FROM TrainingPlan WHERE id = :id")
    fun observePlan(id: String): Flow<TrainingPlan?>

    /** All units of one plan (active or not) with their running detail. */
    @Query(
        """
        SELECT PlannedWorkout.*, TrainingPlan.startDate AS planStartDate, RunPlanWorkout.*
        FROM PlannedWorkout
        INNER JOIN TrainingPlan ON PlannedWorkout.planId = TrainingPlan.id
        LEFT JOIN RunPlanWorkout ON PlannedWorkout.id = RunPlanWorkout.plannedWorkoutId
        WHERE PlannedWorkout.planId = :planId
        ORDER BY PlannedWorkout.weekIndex, PlannedWorkout.dayIndex
        """,
    )
    fun observePlanUnits(planId: String): Flow<List<RecurringPlanned>>

    data class PlanSetRow(
        val sessionId: String,
        val startedAt: Long,
        val exercise: String,
        val weight: Float,
        val reps: Int,
        val completed: Boolean,
    )

    /** Working sets of all finished sessions of a plan (gym/calisthenics). */
    @Query(
        """
        SELECT WorkoutSession.id AS sessionId, WorkoutSession.startedAt AS startedAt,
               Exercise.name AS exercise, SetEntry.weight AS weight, SetEntry.reps AS reps,
               SetEntry.completed AS completed
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        INNER JOIN Exercise ON SessionExercise.exerciseId = Exercise.id
        INNER JOIN WorkoutSession ON SessionExercise.sessionId = WorkoutSession.id
        WHERE WorkoutSession.endedAt IS NOT NULL
          AND SetEntry.type != 'WARMUP' AND SetEntry.reps > 0
          AND WorkoutSession.plannedWorkoutId IN (SELECT id FROM PlannedWorkout WHERE planId = :planId)
        ORDER BY WorkoutSession.startedAt
        """,
    )
    fun observePlanSets(planId: String): Flow<List<PlanSetRow>>

    @Upsert
    suspend fun upsertWorkout(workout: PlannedWorkout)

    @Query("SELECT * FROM PlannedWorkout WHERE planId = :planId ORDER BY sortOrder")
    fun observeWorkouts(planId: String): Flow<List<PlannedWorkout>>

    @Query("SELECT * FROM PlannedWorkout WHERE planId = :planId ORDER BY sortOrder LIMIT 1")
    suspend fun firstWorkoutOnce(planId: String): PlannedWorkout?

    @Query("SELECT * FROM PlannedWorkout WHERE id = :id")
    suspend fun workoutById(id: String): PlannedWorkout?

    @Upsert
    suspend fun upsertRoutineExercises(exercises: List<RoutineExercise>)

    @Query("SELECT * FROM RoutineExercise WHERE plannedWorkoutId = :workoutId ORDER BY sortOrder")
    fun observeRoutine(workoutId: String): Flow<List<RoutineExercise>>

    @Query("SELECT * FROM RoutineExercise WHERE plannedWorkoutId = :workoutId ORDER BY sortOrder")
    suspend fun routineOnce(workoutId: String): List<RoutineExercise>

    @Query("DELETE FROM RoutineExercise WHERE plannedWorkoutId = :workoutId")
    suspend fun clearRoutine(workoutId: String)

    @Query("DELETE FROM RoutineExercise WHERE plannedWorkoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun deleteRoutineExercise(workoutId: String, exerciseId: String)

    /** True if any routine still references this exercise (blocks deletion). */
    @Query("SELECT COUNT(*) FROM RoutineExercise WHERE exerciseId = :exerciseId")
    suspend fun routineUsageCount(exerciseId: String): Int

    // ---- Sessions ------------------------------------------------------

    @Query(
        "SELECT * FROM WorkoutSession " +
            "WHERE endedAt IS NULL AND discipline = :discipline ORDER BY startedAt DESC LIMIT 1",
    )
    fun observeActiveSession(discipline: Discipline): Flow<WorkoutSession?>

    @Query(
        "SELECT * FROM WorkoutSession " +
            "WHERE endedAt IS NULL AND discipline = :discipline ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun activeSessionOnce(discipline: Discipline): WorkoutSession?

    @Query("SELECT startedAt FROM WorkoutSession WHERE endedAt IS NOT NULL")
    fun observeFinishedSessionStarts(): Flow<List<Long>>

    @Query("SELECT * FROM WorkoutSession WHERE id = :id")
    fun observeSession(id: String): Flow<WorkoutSession?>

    @Query("SELECT * FROM WorkoutSession WHERE id = :id")
    suspend fun sessionOnce(id: String): WorkoutSession?

    @Query(
        "SELECT * FROM WorkoutSession " +
            "WHERE endedAt IS NOT NULL AND discipline = :discipline ORDER BY startedAt DESC LIMIT 20",
    )
    fun observeFinishedSessions(discipline: Discipline): Flow<List<WorkoutSession>>

    @Insert
    suspend fun insertSession(session: WorkoutSession)

    @Upsert
    suspend fun upsertSession(session: WorkoutSession)

    /** Ids of imported sessions (prefix match) started at or after [from]. */
    @Query("SELECT id FROM WorkoutSession WHERE id LIKE :prefix || '%' AND startedAt >= :from")
    suspend fun importedSessionIds(prefix: String, from: Long): List<String>

    @Query("UPDATE WorkoutSession SET endedAt = :endedAt WHERE id = :id")
    suspend fun endSession(id: String, endedAt: Long)

    @Query(
        "UPDATE WorkoutSession SET notes = :notes, perceivedEffort = :effort WHERE id = :id",
    )
    suspend fun updateSessionSummary(id: String, notes: String, effort: Int?)

    @Query(
        """
        DELETE FROM SetEntry
        WHERE sessionExerciseId IN (SELECT id FROM SessionExercise WHERE sessionId = :sessionId)
        """,
    )
    suspend fun deleteSetsOfSession(sessionId: String)

    @Query("DELETE FROM SessionExercise WHERE sessionId = :sessionId")
    suspend fun deleteSessionExercises(sessionId: String)

    @Query("DELETE FROM WorkoutSession WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM SessionExercise WHERE sessionId = :sessionId ORDER BY sortOrder")
    fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>>

    @Insert
    suspend fun insertSessionExercises(exercises: List<SessionExercise>)

    @Query("SELECT * FROM SessionExercise WHERE sessionId = :sessionId ORDER BY sortOrder")
    suspend fun sessionExercisesOnce(sessionId: String): List<SessionExercise>

    @Query("SELECT * FROM SetEntry WHERE sessionExerciseId = :sessionExerciseId ORDER BY sortOrder")
    fun observeSets(sessionExerciseId: String): Flow<List<SetEntry>>

    @Upsert
    suspend fun upsertSet(set: SetEntry)

    @Insert
    suspend fun insertSets(sets: List<SetEntry>)

    @Query("SELECT * FROM SetEntry WHERE sessionExerciseId = :sessionExerciseId ORDER BY sortOrder")
    suspend fun setsOnce(sessionExerciseId: String): List<SetEntry>

    // ---- "Vorher": sets of the most recent earlier session for an exercise

    data class PreviousSetRow(
        @Embedded val set: SetEntry,
        val sessionId: String,
        /** Whether anything at all was ticked off in that session. */
        val sessionHasCompleted: Boolean,
    )

    @Query(
        """
        SELECT SetEntry.*, SessionExercise.sessionId AS sessionId,
               EXISTS(
                   SELECT 1 FROM SetEntry s2
                   INNER JOIN SessionExercise x2 ON s2.sessionExerciseId = x2.id
                   WHERE x2.sessionId = SessionExercise.sessionId AND s2.completed = 1
               ) AS sessionHasCompleted
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        WHERE SessionExercise.exerciseId = :exerciseId
          AND SessionExercise.sessionId <> :currentSessionId
          AND SessionExercise.sessionId IN (SELECT id FROM WorkoutSession WHERE endedAt IS NOT NULL)
        ORDER BY (SELECT startedAt FROM WorkoutSession WHERE id = SessionExercise.sessionId) DESC,
                 SetEntry.sortOrder ASC
        """,
    )
    suspend fun previousSets(
        exerciseId: String,
        currentSessionId: String,
    ): List<PreviousSetRow>

    // ---- Progress ------------------------------------------------------

    data class SessionVolume(val sessionId: String, val volume: Float)

    @Query(
        """
        SELECT SessionExercise.sessionId AS sessionId,
               SUM(SetEntry.weight * SetEntry.reps) AS volume
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        WHERE SetEntry.completed = 1
          AND SessionExercise.sessionId IN
              (SELECT id FROM WorkoutSession WHERE discipline = :discipline)
        GROUP BY SessionExercise.sessionId
        """,
    )
    suspend fun sessionVolumes(discipline: Discipline): List<SessionVolume>

    data class NamedSet(val name: String, val weight: Float, val reps: Int)

    @Query(
        """
        SELECT Exercise.name AS name, SetEntry.weight AS weight, SetEntry.reps AS reps,
               SetEntry.completed AS completed
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        INNER JOIN Exercise ON SessionExercise.exerciseId = Exercise.id
        INNER JOIN WorkoutSession ON SessionExercise.sessionId = WorkoutSession.id
        WHERE SetEntry.completed = 1
          AND WorkoutSession.endedAt IS NOT NULL
          AND WorkoutSession.discipline = :discipline
          AND SetEntry.reps > 0
          AND SetEntry.type != 'WARMUP'
        """,
    )
    suspend fun completedSetsWithExercise(discipline: Discipline): List<NamedSet>

    /**
     * Completed sets with the session start date and muscle groups, ordered
     * chronologically. Powers the 1RM history, weekly volume and PR detection
     * in one query (progress is only read for finished sessions).
     */
    data class ProgressSetRow(
        val name: String,
        val primaryMuscles: String,
        val weight: Float,
        val reps: Int,
        /** Session start epoch millis. */
        val startedAt: Long,
    )

    @Query(
        """
        SELECT Exercise.name AS name,
               Exercise.primaryMuscles AS primaryMuscles,
               SetEntry.weight AS weight,
               SetEntry.reps AS reps,
               WorkoutSession.startedAt AS startedAt
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        INNER JOIN Exercise ON SessionExercise.exerciseId = Exercise.id
        INNER JOIN WorkoutSession ON SessionExercise.sessionId = WorkoutSession.id
        WHERE SetEntry.completed = 1
          AND WorkoutSession.endedAt IS NOT NULL
          AND WorkoutSession.discipline = :discipline
          AND SetEntry.reps > 0
          AND SetEntry.type != 'WARMUP'
        ORDER BY WorkoutSession.startedAt ASC
        """,
    )
    suspend fun progressSets(discipline: Discipline): List<ProgressSetRow>

    data class ProgressionSetRow(
        val name: String,
        val measureType: String,
        val weight: Float,
        val reps: Int,
        val durationSec: Int?,
        val completed: Boolean,
        val sessionId: String,
        val sessionHasCompleted: Boolean,
        val startedAt: Long,
    )

    /** Working sets of all finished sessions of [discipline] (progression slides). */
    @Query(
        """
        SELECT Exercise.name AS name, Exercise.measureType AS measureType,
               SetEntry.weight AS weight, SetEntry.reps AS reps, SetEntry.durationSec AS durationSec,
               SetEntry.completed AS completed, WorkoutSession.id AS sessionId,
               EXISTS(
                   SELECT 1 FROM SetEntry s2
                   INNER JOIN SessionExercise x2 ON s2.sessionExerciseId = x2.id
                   WHERE x2.sessionId = WorkoutSession.id AND s2.completed = 1
               ) AS sessionHasCompleted,
               WorkoutSession.startedAt AS startedAt
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        INNER JOIN Exercise ON SessionExercise.exerciseId = Exercise.id
        INNER JOIN WorkoutSession ON SessionExercise.sessionId = WorkoutSession.id
        WHERE WorkoutSession.endedAt IS NOT NULL
          AND WorkoutSession.discipline = :discipline
          AND SetEntry.type != 'WARMUP'
        ORDER BY WorkoutSession.startedAt ASC
        """,
    )
    fun observeProgressionSets(discipline: Discipline): Flow<List<ProgressionSetRow>>

    // ---- Week calendar (all disciplines) ---------------------------------

    /** A session with the title of its planned workout, if linked. */
    data class SessionWithWorkout(
        @Embedded val session: WorkoutSession,
        val workoutTitle: String?,
    )

    @Query(
        """
        SELECT WorkoutSession.*, PlannedWorkout.title AS workoutTitle
        FROM WorkoutSession
        LEFT JOIN PlannedWorkout ON WorkoutSession.plannedWorkoutId = PlannedWorkout.id
        WHERE WorkoutSession.startedAt >= :from AND WorkoutSession.startedAt < :to
        ORDER BY WorkoutSession.startedAt ASC
        """,
    )
    fun observeSessionsBetween(from: Long, to: Long): Flow<List<SessionWithWorkout>>

    /** Dated planned workouts of the range (templates without date are excluded). */
    @Query(
        """
        SELECT * FROM PlannedWorkout
        WHERE date IS NOT NULL AND date >= :fromDate AND date <= :toDate
        ORDER BY date ASC
        """,
    )
    fun observePlannedBetween(fromDate: String, toDate: String): Flow<List<PlannedWorkout>>

    // ---- Body metrics --------------------------------------------------

    @Query("SELECT * FROM BodyMetric ORDER BY date ASC")
    fun observeBodyMetrics(): Flow<List<BodyMetric>>

    @Query("SELECT * FROM BodyMetric ORDER BY date DESC LIMIT 1")
    suspend fun latestBodyMetric(): BodyMetric?

    @Upsert
    suspend fun upsertBodyMetric(metric: BodyMetric)
}
