package app.tenet.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.tenet.android.core.database.entity.FormVideo
import app.tenet.android.core.database.entity.SetEntry
import app.tenet.android.core.database.entity.Skill
import app.tenet.android.core.database.entity.SkillProgress
import app.tenet.android.core.database.entity.SkillStep
import app.tenet.android.core.database.entity.SkillStepAchievement
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {

    // ---- Form videos (local only) --------------------------------------

    @Insert
    suspend fun insertFormVideo(video: FormVideo)

    @Query("DELETE FROM FormVideo WHERE id = :id")
    suspend fun deleteFormVideo(id: String)

    @Query("SELECT * FROM FormVideo WHERE skillId = :skillId ORDER BY createdAt DESC")
    fun observeFormVideosOfSkill(skillId: String): Flow<List<FormVideo>>

    @Query("SELECT * FROM FormVideo WHERE sessionId = :sessionId ORDER BY createdAt")
    fun observeFormVideosOfSession(sessionId: String): Flow<List<FormVideo>>

    @Query("SELECT * FROM FormVideo ORDER BY createdAt DESC")
    fun observeAllFormVideos(): Flow<List<FormVideo>>

    // ---- Skill tree -----------------------------------------------------

    @Query("SELECT * FROM Skill ORDER BY sortOrder")
    fun observeSkills(): Flow<List<Skill>>

    @Query("SELECT * FROM SkillStep ORDER BY sortOrder")
    fun observeSteps(): Flow<List<SkillStep>>

    @Query("SELECT * FROM Skill WHERE id = :id")
    suspend fun skillById(id: String): Skill?

    @Query("SELECT * FROM SkillStep WHERE skillId = :skillId ORDER BY sortOrder")
    suspend fun stepsOfSkillOnce(skillId: String): List<SkillStep>

    @Query("SELECT * FROM SkillStep WHERE id = :id")
    suspend fun stepOnce(id: String): SkillStep?

    /** Map exerciseId -> step, used to find the step behind a running session. */
    @Query("SELECT * FROM SkillStep WHERE exerciseId = :exerciseId")
    suspend fun stepByExercise(exerciseId: String): SkillStep?

    @Query("SELECT * FROM SkillProgress")
    fun observeProgress(): Flow<List<SkillProgress>>

    @Query("SELECT * FROM SkillProgress WHERE skillId = :skillId")
    suspend fun progressOnce(skillId: String): SkillProgress?

    @Upsert
    suspend fun upsertProgress(progress: SkillProgress)

    @Upsert
    suspend fun upsertSkill(skill: Skill)

    @Upsert
    suspend fun upsertStep(step: SkillStep)

    // ---- Achievements ---------------------------------------------------

    @Query("SELECT * FROM SkillStepAchievement")
    fun observeAchievements(): Flow<List<SkillStepAchievement>>

    @Query("SELECT * FROM SkillStepAchievement WHERE stepId = :stepId")
    suspend fun achievementsOfStep(stepId: String): List<SkillStepAchievement>

    /** Returns -1 when the achievement already existed (conflict ignored). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: SkillStepAchievement): Long

    @Query("SELECT COUNT(*) FROM Skill")
    suspend fun skillCount(): Int

    // ---- Per-session criterion evaluation -------------------------------

    /** Completed sets of one session's exercises for one exercise. */
    @Query(
        """
        SELECT SetEntry.*
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        WHERE SessionExercise.sessionId = :sessionId
          AND SessionExercise.exerciseId = :exerciseId
        ORDER BY SetEntry.sortOrder
        """,
    )
    suspend fun setsOfSessionExercise(sessionId: String, exerciseId: String): List<SetEntry>

    /** Total logged sets of a session (interval rounds count from this). */
    @Query(
        """
        SELECT COUNT(*) FROM SetEntry
        WHERE sessionExerciseId IN (SELECT id FROM SessionExercise WHERE sessionId = :sessionId)
        """,
    )
    suspend fun countSetsOfSession(sessionId: String): Int

    /** Deletes the [count] most recently logged sets of a session (AMRAP undo). */
    @Query(
        """
        DELETE FROM SetEntry WHERE id IN (
            SELECT id FROM SetEntry
            WHERE sessionExerciseId IN (SELECT id FROM SessionExercise WHERE sessionId = :sessionId)
            ORDER BY sortOrder DESC LIMIT :count
        )
        """,
    )
    suspend fun deleteLastSetsOfSession(sessionId: String, count: Int)

    // ---- Best values per exercise (for the ladder display) --------------

    data class ExerciseBest(
        val exerciseId: String,
        val bestHold: Int?,
        val bestReps: Int?,
    )

    @Query(
        """
        SELECT SessionExercise.exerciseId AS exerciseId,
               MAX(SetEntry.durationSec) AS bestHold,
               MAX(SetEntry.reps) AS bestReps
        FROM SetEntry
        INNER JOIN SessionExercise ON SetEntry.sessionExerciseId = SessionExercise.id
        WHERE SetEntry.completed = 1
        GROUP BY SessionExercise.exerciseId
        """,
    )
    fun bestByExercise(): Flow<List<ExerciseBest>>
}
