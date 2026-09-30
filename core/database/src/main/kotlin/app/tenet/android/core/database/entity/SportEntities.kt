package app.tenet.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Discipline { GYM, CALISTHENICS, RUNNING }

enum class MeasureType { REPS, HOLD, NEGATIVE, DURATION }

enum class SetType { WARMUP, WORKING, DROP, FAILURE }

/**
 * How a (strength) session is driven (App_Konzept.md 5.2.2: "Formate:
 * klassische Sätze, Zirkel, EMOM, AMRAP"). Null on the row means [SETS].
 * AMRAP uses [WorkoutSession.rounds] as its time cap in minutes.
 */
enum class SessionMode { SETS, CIRCUIT, EMOM, AMRAP }

@Entity(tableName = "TrainingPlan")
data class TrainingPlan(
    @PrimaryKey val id: String,
    val discipline: Discipline,
    val name: String,
    val goal: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val active: Boolean = false,
    /**
     * Fixed training weekdays as ISO numbers ("1,3,5" = Mo/Mi/Fr) for undated
     * plans (Gym, Calisthenics); null = no fixed days (every day counts).
     */
    val trainingDays: String? = null,
)

@Entity(
    tableName = "PlannedWorkout",
    indices = [Index("planId")],
)
data class PlannedWorkout(
    @PrimaryKey val id: String,
    val planId: String,
    val discipline: Discipline,
    /** ISO-8601 date, null = recurring template. */
    val date: String? = null,
    val weekIndex: Int? = null,
    val dayIndex: Int? = null,
    val title: String,
    /** Named "sortOrder" instead of "order" to avoid the SQL keyword. */
    val sortOrder: Int,
    /** Skipped on purpose (v14) – not counted as missed. */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val skipped: Boolean = false,
)

@Entity(
    tableName = "WorkoutSession",
    indices = [Index("endedAt")],
)
data class WorkoutSession(
    @PrimaryKey val id: String,
    val discipline: Discipline,
    val plannedWorkoutId: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String = "",
    val perceivedEffort: Int? = null,
    // ---- Interval modes (v5, App_Konzept.md 5.2.2); null = classic sets
    val mode: SessionMode? = null,
    /** Total rounds (circuit) or minutes (EMOM). */
    val rounds: Int? = null,
    /** Work seconds per circuit station. */
    val workSec: Int? = null,
    /** Transition/rest seconds per circuit station. */
    val restSec: Int? = null,
    /** Seconds per EMOM minute (normally 60). */
    val intervalSec: Int? = null,
)

@Entity(
    tableName = "Exercise",
    indices = [Index("discipline")],
)
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val discipline: Discipline,
    /** Muscle groups as comma-separated names (MVP, no extra table). */
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val equipment: String,
    val measureType: MeasureType,
    val notes: String = "",
    val custom: Boolean = false,
    /** MovementPattern name (v13): exercises with the same pattern can replace each other. */
    val pattern: String? = null,
)

@Entity(
    tableName = "RoutineExercise",
    primaryKeys = ["plannedWorkoutId", "exerciseId"],
    indices = [Index("plannedWorkoutId")],
)
data class RoutineExercise(
    val plannedWorkoutId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val restSec: Int,
    val supersetGroup: Int? = null,
    /** Weight for the very first session, from the setup (v12); null = choose yourself. */
    val startWeightKg: Float? = null,
    // ---- Overload rule per exercise (v15); null = default double progression
    /** Upper end of the rep range: weight goes up only once every set reaches it. */
    val repMax: Int? = null,
    /** Weight step in kg (default: 5 lower body, 2.5 otherwise). */
    val stepKg: Float? = null,
    /** Deload in percent after missing twice (default 10). */
    val deloadPercent: Int? = null,
)

@Entity(
    tableName = "SessionExercise",
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExercise(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val sortOrder: Int,
    /** Note for this exercise in this session, like Hevy (v15). */
    @androidx.room.ColumnInfo(defaultValue = "")
    val notes: String = "",
)

@Entity(
    tableName = "SetEntry",
    indices = [Index("sessionExerciseId")],
)
data class SetEntry(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val sortOrder: Int,
    val type: SetType,
    val weight: Float = 0f,
    val reps: Int = 0,
    val rpe: Float? = null,
    val durationSec: Int? = null,
    val completed: Boolean = false,
    /** Added load (vest/belt), kg on top of bodyweight. */
    val addedWeight: Float? = null,
    /** Assistance level (band strength), negative kg equivalent. */
    val assistance: Float? = null,
    /** CLEAN / SLOPPY quality mark for skill sets. */
    val formQuality: FormQuality? = null,
)

/**
 * Body measurements over time (App_Konzept.md 5.2.1: "Körpergewicht und
 * Maße als Zeitreihe"). One entry per day; [date] is the ISO-8601 local date.
 */
@Entity(tableName = "BodyMetric")
data class BodyMetric(
    @PrimaryKey val id: String,
    val date: String,
    val weight: Float,
    /** Body fat in percent, optional. */
    val bodyFat: Float? = null,
    /** Free-form measurements (waist, chest, ...) as JSON, optional. */
    val measurementsJson: String? = null,
)
