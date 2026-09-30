package app.tenet.android.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import app.tenet.android.core.database.entity.PersonalBest
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RunPlanDetail
import app.tenet.android.core.database.entity.RunPlanWorkout
import app.tenet.android.core.database.entity.RunPlanWorkoutRow
import app.tenet.android.core.database.entity.RunSession
import app.tenet.android.core.database.entity.RunSplit
import app.tenet.android.core.database.entity.RunTrackPoint
import app.tenet.android.core.database.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    // ---- Plan structure ---------------------------------------------------

    @Query(
        """
        SELECT * FROM PlannedWorkout
        INNER JOIN RunPlanWorkout ON PlannedWorkout.id = RunPlanWorkout.plannedWorkoutId
        WHERE PlannedWorkout.planId = :planId
        ORDER BY PlannedWorkout.weekIndex ASC, PlannedWorkout.sortOrder ASC
        """,
    )
    fun observePlanWorkouts(planId: String): Flow<List<RunPlanWorkoutRow>>

    @Upsert
    suspend fun upsertRunPlanWorkouts(workouts: List<RunPlanWorkout>)

    @Upsert
    suspend fun upsertPlanDetail(detail: RunPlanDetail)

    @Query("SELECT * FROM RunPlanDetail WHERE planId = :planId")
    fun observePlanDetail(planId: String): Flow<RunPlanDetail?>

    @Query("SELECT * FROM RunPlanWorkout WHERE plannedWorkoutId = :plannedWorkoutId")
    suspend fun runPlanWorkoutOnce(plannedWorkoutId: String): RunPlanWorkout?

    @Query("SELECT * FROM RunPlanDetail WHERE planId = :planId")
    suspend fun planDetailOnce(planId: String): RunPlanDetail?

    // ---- Recorded runs ----------------------------------------------------

    @Upsert
    suspend fun upsertRunSession(run: RunSession)

    @Query("SELECT * FROM RunSession WHERE sessionId = :sessionId")
    suspend fun runSessionOnce(sessionId: String): app.tenet.android.core.database.entity.RunSession?

    @Query("DELETE FROM RunSession WHERE sessionId = :sessionId")
    suspend fun deleteRunSession(sessionId: String)

    @Query("DELETE FROM RunSplit WHERE sessionId = :sessionId")
    suspend fun deleteSplits(sessionId: String)

    /** Aggregated runs with their session dates for list screens. */
    data class RunSessionRow(
        @Embedded val session: WorkoutSession,
        @Embedded val run: RunSession,
    )

    @Query(
        """
        SELECT WorkoutSession.*, RunSession.*
        FROM RunSession
        INNER JOIN WorkoutSession ON RunSession.sessionId = WorkoutSession.id
        WHERE WorkoutSession.endedAt IS NOT NULL
        ORDER BY WorkoutSession.startedAt DESC
        LIMIT 20
        """,
    )
    fun observeRecentRuns(): Flow<List<RunSessionRow>>

    data class RunVolumeRow(val startedAt: Long, val distanceM: Float, val durationSec: Int)

    /** Finished runs since [from] (epoch millis) for weekly/monthly volume. */
    @Query(
        """
        SELECT WorkoutSession.startedAt AS startedAt, RunSession.distanceM AS distanceM,
            RunSession.durationSec AS durationSec
        FROM RunSession
        INNER JOIN WorkoutSession ON RunSession.sessionId = WorkoutSession.id
        WHERE WorkoutSession.endedAt IS NOT NULL AND WorkoutSession.startedAt >= :from
        """,
    )
    fun observeRunVolume(from: Long): Flow<List<RunVolumeRow>>

    // ---- GPS track ---------------------------------------------------------

    /** Track points are written in batches while the run is active. */
    @Insert
    suspend fun insertTrackPoints(points: List<RunTrackPoint>)

    @Query("SELECT * FROM RunTrackPoint WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun trackPointsOnce(sessionId: String): List<RunTrackPoint>

    /** Live route while recording. */
    @Query("SELECT * FROM RunTrackPoint WHERE sessionId = :sessionId ORDER BY timestamp")
    fun observeTrackPoints(sessionId: String): Flow<List<RunTrackPoint>>

    @Query("SELECT COUNT(*) FROM RunTrackPoint WHERE sessionId = :sessionId")
    suspend fun trackPointCount(sessionId: String): Int

    @Query("DELETE FROM RunTrackPoint WHERE sessionId = :sessionId")
    suspend fun deleteTrackPoints(sessionId: String)

    // ---- Splits ------------------------------------------------------------

    @Upsert
    suspend fun upsertSplits(splits: List<RunSplit>)

    @Query("SELECT * FROM RunSplit WHERE sessionId = :sessionId ORDER BY sortOrder")
    fun observeSplits(sessionId: String): Flow<List<RunSplit>>

    // ---- Personal bests -----------------------------------------------------

    @Upsert
    suspend fun upsertPersonalBest(best: PersonalBest)

    @Query("SELECT * FROM PersonalBest ORDER BY distanceM ASC, durationSec ASC")
    fun observePersonalBests(): Flow<List<PersonalBest>>

    /** Best efforts per run: one row per run and distance; the record is the minimum. */
    @Upsert
    suspend fun upsertPersonalBests(bests: List<PersonalBest>)

    @Query("DELETE FROM PersonalBest WHERE sessionId = :sessionId")
    suspend fun deletePersonalBestsOfSession(sessionId: String)

    @Query("SELECT * FROM PersonalBest WHERE sessionId = :sessionId ORDER BY distanceM")
    fun observePersonalBestsOfSession(sessionId: String): Flow<List<PersonalBest>>

    /** The record (fastest effort) per standard distance. */
    @Query(
        """
        SELECT * FROM PersonalBest pb
        WHERE pb.durationSec = (SELECT MIN(p2.durationSec) FROM PersonalBest p2 WHERE p2.distanceM = pb.distanceM)
        GROUP BY pb.distanceM
        ORDER BY pb.distanceM
        """,
    )
    fun observeRecords(): Flow<List<PersonalBest>>

    @Query(
        """
        SELECT WorkoutSession.*, RunSession.*
        FROM RunSession
        INNER JOIN WorkoutSession ON RunSession.sessionId = WorkoutSession.id
        WHERE RunSession.sessionId = :sessionId
        """,
    )
    fun observeRun(sessionId: String): Flow<RunSessionRow?>

    /** All finished runs since [from] (plan detail: weekly volume, completed units). */
    @Query(
        """
        SELECT WorkoutSession.*, RunSession.*
        FROM RunSession
        INNER JOIN WorkoutSession ON RunSession.sessionId = WorkoutSession.id
        WHERE WorkoutSession.endedAt IS NOT NULL AND WorkoutSession.startedAt >= :from
        ORDER BY WorkoutSession.startedAt DESC
        """,
    )
    fun observeRunsSince(from: Long): Flow<List<RunSessionRow>>
}
