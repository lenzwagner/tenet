package app.tenet.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.tenet.android.core.database.dao.EntryDao
import app.tenet.android.core.database.dao.FoodDao
import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.dao.SkillDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Attachment
import app.tenet.android.core.database.entity.Converters
import app.tenet.android.core.database.entity.EntryTag
import app.tenet.android.core.database.entity.Recipe
import app.tenet.android.core.database.entity.RecipeIngredient
import app.tenet.android.core.database.entity.Tag
import app.tenet.android.core.database.entity.WaterLog
import app.tenet.android.core.database.entity.BodyMetric
import app.tenet.android.core.database.entity.DailyGoal
import app.tenet.android.core.database.entity.DiaryMeta
import app.tenet.android.core.database.entity.DreamMeta
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.FoodLog
import app.tenet.android.core.database.entity.Skill
import app.tenet.android.core.database.entity.SkillProgress
import app.tenet.android.core.database.entity.SkillStep
import app.tenet.android.core.database.entity.SkillStepAchievement
import app.tenet.android.core.database.entity.PersonalBest
import app.tenet.android.core.database.entity.RunPlanDetail
import app.tenet.android.core.database.entity.RunPlanWorkout
import app.tenet.android.core.database.entity.RunSession
import app.tenet.android.core.database.entity.RunSplit
import app.tenet.android.core.database.entity.RunTrackPoint

@Database(
    entities = [
        Entry::class,
        DiaryMeta::class,
        DreamMeta::class,
        Food::class,
        FoodLog::class,
        DailyGoal::class,
        // Sport (v2)
        app.tenet.android.core.database.entity.TrainingPlan::class,
        app.tenet.android.core.database.entity.PlannedWorkout::class,
        app.tenet.android.core.database.entity.WorkoutSession::class,
        app.tenet.android.core.database.entity.Exercise::class,
        app.tenet.android.core.database.entity.RoutineExercise::class,
        app.tenet.android.core.database.entity.SessionExercise::class,
        app.tenet.android.core.database.entity.SetEntry::class,
        BodyMetric::class,
        // Calisthenics skill tree (v4)
        Skill::class,
        SkillStep::class,
        SkillProgress::class,
        SkillStepAchievement::class,
        // Running (v6)
        RunPlanWorkout::class,
        RunPlanDetail::class,
        RunSession::class,
        RunTrackPoint::class,
        RunSplit::class,
        PersonalBest::class,
        // Journal extras (v7)
        Tag::class,
        EntryTag::class,
        Attachment::class,
        // Nutrition completion (v8)
        Recipe::class,
        RecipeIngredient::class,
        WaterLog::class,
        // Note folders + full-text search (v10)
        app.tenet.android.core.database.entity.EntryFts::class,
    ],
    version = 18,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TenetDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun foodDao(): FoodDao
    abstract fun sportDao(): SportDao
    abstract fun skillDao(): SkillDao
    abstract fun runDao(): RunDao
}
