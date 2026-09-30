package app.tenet.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Type of a planned run (App_Konzept.md 5.2.3 weekly structure). */
enum class RunType { EASY, LONG, TEMPO, INTERVAL, RECOVERY }

/** Where a recorded run came from. */
enum class RunSource { GPS, MANUAL, HEALTH_CONNECT, GPX }

/**
 * Discipline-specific detail of a planned workout: what exactly to run
 * (App_Konzept.md 7.3). 1:1 to [PlannedWorkout]; the goal/pace-method live
 * in [RunPlanDetail].
 */
@Entity(tableName = "RunPlanWorkout")
data class RunPlanWorkout(
    @PrimaryKey val plannedWorkoutId: String,
    val runType: RunType,
    val targetDistanceM: Int? = null,
    val targetDurationSec: Int? = null,
    val targetPaceSecPerKm: Int? = null,
    /** Compact interval spec, e.g. `[{"reps":4,"lengthM":800,"restSec":90}]`. */
    val intervalsJson: String? = null,
)

/**
 * Plan-level running detail: goal, weekly run count and the pace anchor
 * (the current form time the target paces were derived from).
 */
@Entity(tableName = "RunPlanDetail")
data class RunPlanDetail(
    @PrimaryKey val planId: String,
    /** [app.tenet.android.core.common.RunPlanMath.RunGoal] name. */
    val goalId: String,
    val runsPerWeek: Int,
    /** Current form as 5-km time in seconds; null = no pace targets. */
    val current5kSec: Int? = null,
    /** [app.tenet.android.core.common.PaceMethod] id used at creation. */
    val paceMethodId: String? = null,
    /** Goal race time [s] the runner aims for (v11); null = only the prognosis. */
    val targetTimeSec: Int? = null,
    /** Last weeks reduce volume before the race (v11). */
    @ColumnInfo(defaultValue = "0")
    val taper: Boolean = false,
)

/** Aggregated run result (App_Konzept.md 7.3); 1:1 to [WorkoutSession]. */
@Entity(tableName = "RunSession")
data class RunSession(
    @PrimaryKey val sessionId: String,
    val distanceM: Float,
    val durationSec: Int,
    val avgPaceSecPerKm: Int,
    val avgHr: Int? = null,
    val elevationGainM: Int? = null,
    val source: RunSource,
)

/**
 * GPS track (App_Konzept.md 7.3 performance hint: written in batches during
 * the run, list screens read only the [RunSession] aggregates).
 */
@Entity(
    tableName = "RunTrackPoint",
    indices = [Index("sessionId")],
)
data class RunTrackPoint(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val hr: Int? = null,
)

/** Splits per kilometer (App_Konzept.md 7.3). */
@Entity(
    tableName = "RunSplit",
    primaryKeys = ["sessionId", "sortOrder"],
)
data class RunSplit(
    val sessionId: String,
    /** Split number; named sortOrder like elsewhere to dodge SQL keywords. */
    val sortOrder: Int,
    val distanceM: Float,
    val durationSec: Int,
)

/** Best times per distance (App_Konzept.md 7.3 "Bestzeiten"). */
@Entity(
    tableName = "PersonalBest",
    indices = [Index("distanceM")],
)
data class PersonalBest(
    @PrimaryKey val id: String,
    val distanceM: Int,
    val durationSec: Int,
    val sessionId: String,
    val achievedAt: Long,
)

/** PlannedWorkout joined with its running detail (week view). */
data class RunPlanWorkoutRow(
    @Embedded val planned: PlannedWorkout,
    @Embedded val run: RunPlanWorkout,
)
