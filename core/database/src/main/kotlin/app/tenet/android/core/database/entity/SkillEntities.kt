package app.tenet.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Coarse movement area a skill trains (App_Konzept.md 5.2.2), used to group
 * the skill tree and the weekly volume breakdown.
 */
enum class SkillCategory { PULL, PUSH, LEGS, CORE, BALANCE }

/** What a skill step's promotion criterion is measured in. */
enum class CriterionType { HOLD, REPS }

/**
 * Quality marking of a single set (App_Konzept.md 5.2.2: "sauber / mit
 * Fehlern"). Stored as the enum name via [Converters]. Sloppy sets never
 * count towards a promotion criterion.
 */
enum class FormQuality { CLEAN, SLOPPY }

/**
 * A skill of the calisthenics tree, e.g. "Front Lever"
 * (App_Konzept.md 7.3, vereinfachtes Schema).
 */
@Entity(tableName = "Skill")
data class Skill(
    @PrimaryKey val id: String,
    val name: String,
    val category: SkillCategory,
    val sortOrder: Int,
)

/**
 * One progression step inside a [skill][skillId], e.g. "Advanced Tuck".
 * Promotion is suggested (not automatic) once [criterionSets] x
 * [criterionValue] has been met in two separate sessions.
 */
@Entity(
    tableName = "SkillStep",
    indices = [Index("skillId"), Index("exerciseId")],
)
data class SkillStep(
    @PrimaryKey val id: String,
    val skillId: String,
    /** Display label of the step, e.g. "Tuck" or "Straddle". */
    val label: String,
    val sortOrder: Int,
    /** The exercise this step is trained with (one step = one exercise). */
    val exerciseId: String,
    val criterionType: CriterionType,
    /** How many sets must meet the criterion, e.g. 3 in "3 x 10 s". */
    val criterionSets: Int,
    /** Seconds for [CriterionType.HOLD], reps for [CriterionType.REPS]. */
    val criterionValue: Int,
)

/**
 * Where a skill currently stands. One row per skill; missing rows mean the
 * first step (handled in the repository).
 */
@Entity(tableName = "SkillProgress")
data class SkillProgress(
    @PrimaryKey val skillId: String,
    val currentStepId: String,
    val updatedAt: Long,
)

/**
 * A session in which the current step's criterion was fully met
 * (App_Konzept.md 5.2.2: "in zwei Sessions erfuellt"). Two achievements for
 * the same step unlock the promotion suggestion.
 */
@Entity(
    tableName = "SkillStepAchievement",
    primaryKeys = ["stepId", "sessionId"],
    indices = [Index("stepId"), Index("sessionId")],
)
data class SkillStepAchievement(
    val stepId: String,
    val sessionId: String,
    val achievedAt: Long,
)

/**
 * Short form video of one attempt (App_Konzept.md 5.2.2 "Formvideos"),
 * stored only on this device (app files, not synced) and attached to the
 * session, so the technique of a skill can be compared over weeks.
 */
@Entity(
    tableName = "FormVideo",
    indices = [Index("skillId"), Index("sessionId")],
)
data class FormVideo(
    @PrimaryKey val id: String,
    val skillId: String,
    /** Step trained when recorded, e.g. "Advanced Tuck". */
    val stepId: String,
    val sessionId: String,
    /** The set the attempt belongs to, if recorded from a set row. */
    val setId: String? = null,
    /** file:// URI inside the app's files dir. */
    val uri: String,
    val createdAt: Long,
    val durationMs: Long? = null,
)
