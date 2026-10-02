package app.tenet.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v8 -> v9: training weekdays on plans (Ruhetage on "Heute"). */
/** v11: goal time and taper flag of running plans. */
/** v14: planned units can be skipped (running plan, Runna-style). */
/** v19: supersets for a single session. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `SessionExercise` ADD COLUMN `supersetGroup` INTEGER")
    }
}

/**
 * v18: gym rest times become automatic (0 = RestAdvisor: per exercise and
 * reps, e.g. 3–5 min squat, 60–90 s curls) instead of one value per plan goal.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `RoutineExercise` SET `restSec` = 0 WHERE `exerciseId` IN (SELECT `id` FROM `Exercise` WHERE `discipline` = 'GYM')")
    }
}

/** v17: weather of the day on diary entries. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `DiaryMeta` ADD COLUMN `weatherCode` INTEGER")
        db.execSQL("ALTER TABLE `DiaryMeta` ADD COLUMN `tempMaxC` REAL")
        db.execSQL("ALTER TABLE `DiaryMeta` ADD COLUMN `tempMinC` REAL")
    }
}

/** v16: recipe fields for the Saffron import (category, rating, free-text ingredients, timed steps …). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`source` TEXT",
            "`sourceUrl` TEXT",
            "`category` TEXT NOT NULL DEFAULT ''",
            "`rating` INTEGER NOT NULL DEFAULT 0",
            "`vegetarian` INTEGER NOT NULL DEFAULT 0",
            "`ingredientLines` TEXT NOT NULL DEFAULT ''",
            "`stepsJson` TEXT",
            "`notes` TEXT NOT NULL DEFAULT ''",
            "`images` TEXT NOT NULL DEFAULT ''",
            "`cooked` INTEGER NOT NULL DEFAULT 0",
        ).forEach { db.execSQL("ALTER TABLE `Recipe` ADD COLUMN $it") }
    }
}

/** v15: overload rule per routine exercise, notes per session exercise. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `RoutineExercise` ADD COLUMN `repMax` INTEGER")
        db.execSQL("ALTER TABLE `RoutineExercise` ADD COLUMN `stepKg` REAL")
        db.execSQL("ALTER TABLE `RoutineExercise` ADD COLUMN `deloadPercent` INTEGER")
        db.execSQL("ALTER TABLE `SessionExercise` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `PlannedWorkout` ADD COLUMN `skipped` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v13: movement pattern per exercise (catalog, "Übung tauschen"). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Exercise` ADD COLUMN `pattern` TEXT")
    }
}

/** v12: start weight per routine exercise (gym setup). */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `RoutineExercise` ADD COLUMN `startWeightKg` REAL")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `RunPlanDetail` ADD COLUMN `targetTimeSec` INTEGER")
        db.execSQL("ALTER TABLE `RunPlanDetail` ADD COLUMN `taper` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v10: note folders and the full-text index over entries. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Entry` ADD COLUMN `folder` TEXT")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `EntryFts` USING FTS4(`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, tokenize=unicode61, content=`Entry`)",
        )
        // Same sync triggers Room creates on open (external content table).
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_EntryFts_BEFORE_UPDATE BEFORE UPDATE ON `Entry` " +
                "BEGIN DELETE FROM `EntryFts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_EntryFts_BEFORE_DELETE BEFORE DELETE ON `Entry` " +
                "BEGIN DELETE FROM `EntryFts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_EntryFts_AFTER_UPDATE AFTER UPDATE ON `Entry` " +
                "BEGIN INSERT INTO `EntryFts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_EntryFts_AFTER_INSERT AFTER INSERT ON `Entry` " +
                "BEGIN INSERT INTO `EntryFts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
        )
        // Index the existing entries.
        db.execSQL("INSERT INTO `EntryFts`(`EntryFts`) VALUES('rebuild')")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `TrainingPlan` ADD COLUMN `trainingDays` TEXT")
    }
}

/**
 * v7 -> v8: nutrition completion (App_Konzept.md 5.4) – recipes with
 * ingredients, water log, food "last used" and log quantity/unit/source.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Food` ADD COLUMN `lastUsedAt` INTEGER")
        db.execSQL("ALTER TABLE `FoodLog` ADD COLUMN `foodId` TEXT")
        db.execSQL("ALTER TABLE `FoodLog` ADD COLUMN `recipeId` TEXT")
        db.execSQL("ALTER TABLE `FoodLog` ADD COLUMN `quantity` REAL")
        db.execSQL("ALTER TABLE `FoodLog` ADD COLUMN `unit` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Recipe` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`photoUri` TEXT, `servings` INTEGER NOT NULL, `minutes` INTEGER, `tags` TEXT NOT NULL, " +
                "`steps` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `RecipeIngredient` (`id` TEXT NOT NULL, `recipeId` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `foodId` TEXT, `name` TEXT NOT NULL, `grams` REAL NOT NULL, " +
                "`kcalPer100` REAL NOT NULL, `proteinPer100` REAL NOT NULL, `carbsPer100` REAL NOT NULL, " +
                "`fatPer100` REAL NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`recipeId`) REFERENCES `Recipe`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_RecipeIngredient_recipeId` ON `RecipeIngredient` (`recipeId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `WaterLog` (`date` TEXT NOT NULL, `ml` INTEGER NOT NULL, PRIMARY KEY(`date`))",
        )
    }
}

/**
 * v6 -> v7: journal tags / dream symbols, attachments and dream emotions
 * (App_Konzept.md 5.3). Names must match the Room entities exactly.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Tag` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Tag_name_kind` ON `Tag` (`name`, `kind`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `EntryTag` (`entryId` TEXT NOT NULL, `tagId` TEXT NOT NULL, " +
                "PRIMARY KEY(`entryId`, `tagId`), " +
                "FOREIGN KEY(`entryId`) REFERENCES `Entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tagId`) REFERENCES `Tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_EntryTag_tagId` ON `EntryTag` (`tagId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Attachment` (`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, " +
                "`uri` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`entryId`) REFERENCES `Entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Attachment_entryId` ON `Attachment` (`entryId`)")
        db.execSQL("ALTER TABLE `DreamMeta` ADD COLUMN `emotions` TEXT")
    }
}

/**
 * v5 -> v6: running tables (App_Konzept.md 7.3, "Laufen"). Column names,
 * affinities and index names must match the Room entities exactly.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `RunPlanWorkout` (" +
                "`plannedWorkoutId` TEXT NOT NULL, " +
                "`runType` TEXT NOT NULL, " +
                "`targetDistanceM` INTEGER, " +
                "`targetDurationSec` INTEGER, " +
                "`targetPaceSecPerKm` INTEGER, " +
                "`intervalsJson` TEXT, " +
                "PRIMARY KEY(`plannedWorkoutId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `RunPlanDetail` (" +
                "`planId` TEXT NOT NULL, " +
                "`goalId` TEXT NOT NULL, " +
                "`runsPerWeek` INTEGER NOT NULL, " +
                "`current5kSec` INTEGER, " +
                "`paceMethodId` TEXT, " +
                "PRIMARY KEY(`planId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `RunSession` (" +
                "`sessionId` TEXT NOT NULL, " +
                "`distanceM` REAL NOT NULL, " +
                "`durationSec` INTEGER NOT NULL, " +
                "`avgPaceSecPerKm` INTEGER NOT NULL, " +
                "`avgHr` INTEGER, " +
                "`elevationGainM` INTEGER, " +
                "`source` TEXT NOT NULL, " +
                "PRIMARY KEY(`sessionId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `RunTrackPoint` (" +
                "`id` TEXT NOT NULL, " +
                "`sessionId` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`lat` REAL NOT NULL, " +
                "`lon` REAL NOT NULL, " +
                "`altitude` REAL, " +
                "`hr` INTEGER, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_RunTrackPoint_sessionId` " +
                "ON `RunTrackPoint` (`sessionId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `RunSplit` (" +
                "`sessionId` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`distanceM` REAL NOT NULL, " +
                "`durationSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`sessionId`, `sortOrder`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `PersonalBest` (" +
                "`id` TEXT NOT NULL, " +
                "`distanceM` INTEGER NOT NULL, " +
                "`durationSec` INTEGER NOT NULL, " +
                "`sessionId` TEXT NOT NULL, " +
                "`achievedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_PersonalBest_distanceM` " +
                "ON `PersonalBest` (`distanceM`)",
        )
    }
}

/**
 * v4 -> v5: interval modes for sessions (App_Konzept.md 5.2.2: circuits
 * and EMOM). All columns are nullable; NULL means classic sets.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `WorkoutSession` ADD COLUMN `mode` TEXT")
        db.execSQL("ALTER TABLE `WorkoutSession` ADD COLUMN `rounds` INTEGER")
        db.execSQL("ALTER TABLE `WorkoutSession` ADD COLUMN `workSec` INTEGER")
        db.execSQL("ALTER TABLE `WorkoutSession` ADD COLUMN `restSec` INTEGER")
        db.execSQL("ALTER TABLE `WorkoutSession` ADD COLUMN `intervalSec` INTEGER")
    }
}

/**
 * v3 -> v4: calisthenics skill tree (App_Konzept.md 5.2.2) plus the extra
 * per-set fields for skill training (added weight, assistance, quality).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Skill` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SkillStep` (" +
                "`id` TEXT NOT NULL, " +
                "`skillId` TEXT NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`exerciseId` TEXT NOT NULL, " +
                "`criterionType` TEXT NOT NULL, " +
                "`criterionSets` INTEGER NOT NULL, " +
                "`criterionValue` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SkillStep_skillId` " +
                "ON `SkillStep` (`skillId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SkillStep_exerciseId` " +
                "ON `SkillStep` (`exerciseId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SkillProgress` (" +
                "`skillId` TEXT NOT NULL, " +
                "`currentStepId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`skillId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SkillStepAchievement` (" +
                "`stepId` TEXT NOT NULL, " +
                "`sessionId` TEXT NOT NULL, " +
                "`achievedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`stepId`, `sessionId`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SkillStepAchievement_stepId` " +
                "ON `SkillStepAchievement` (`stepId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SkillStepAchievement_sessionId` " +
                "ON `SkillStepAchievement` (`sessionId`)",
        )
        db.execSQL("ALTER TABLE `SetEntry` ADD COLUMN `addedWeight` REAL")
        db.execSQL("ALTER TABLE `SetEntry` ADD COLUMN `assistance` REAL")
        db.execSQL("ALTER TABLE `SetEntry` ADD COLUMN `formQuality` TEXT")
    }
}

/**
 * v2 -> v3: adds the body-metric time series (weight & measurements).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `BodyMetric` (" +
                "`id` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`weight` REAL NOT NULL, " +
                "`bodyFat` REAL, " +
                "`measurementsJson` TEXT, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v1 -> v2: adds the sport tables (App_Konzept.md 7.3, "Sport").
 * Column names, affinities and index names must match the Room entities
 * exactly so that Room's schema validation passes after the migration.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `TrainingPlan` (" +
                "`id` TEXT NOT NULL, " +
                "`discipline` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`goal` TEXT, " +
                "`startDate` TEXT, " +
                "`endDate` TEXT, " +
                "`active` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `PlannedWorkout` (" +
                "`id` TEXT NOT NULL, " +
                "`planId` TEXT NOT NULL, " +
                "`discipline` TEXT NOT NULL, " +
                "`date` TEXT, " +
                "`weekIndex` INTEGER, " +
                "`dayIndex` INTEGER, " +
                "`title` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_PlannedWorkout_planId` " +
                "ON `PlannedWorkout` (`planId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `WorkoutSession` (" +
                "`id` TEXT NOT NULL, " +
                "`discipline` TEXT NOT NULL, " +
                "`plannedWorkoutId` TEXT, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, " +
                "`notes` TEXT NOT NULL, " +
                "`perceivedEffort` INTEGER, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_WorkoutSession_endedAt` " +
                "ON `WorkoutSession` (`endedAt`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Exercise` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`discipline` TEXT NOT NULL, " +
                "`primaryMuscles` TEXT NOT NULL, " +
                "`secondaryMuscles` TEXT NOT NULL, " +
                "`equipment` TEXT NOT NULL, " +
                "`measureType` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "`custom` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_Exercise_discipline` " +
                "ON `Exercise` (`discipline`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `RoutineExercise` (" +
                "`plannedWorkoutId` TEXT NOT NULL, " +
                "`exerciseId` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`targetSets` INTEGER NOT NULL, " +
                "`targetReps` INTEGER NOT NULL, " +
                "`restSec` INTEGER NOT NULL, " +
                "`supersetGroup` INTEGER, " +
                "PRIMARY KEY(`plannedWorkoutId`, `exerciseId`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_RoutineExercise_plannedWorkoutId` " +
                "ON `RoutineExercise` (`plannedWorkoutId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SessionExercise` (" +
                "`id` TEXT NOT NULL, " +
                "`sessionId` TEXT NOT NULL, " +
                "`exerciseId` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SessionExercise_sessionId` " +
                "ON `SessionExercise` (`sessionId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SessionExercise_exerciseId` " +
                "ON `SessionExercise` (`exerciseId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SetEntry` (" +
                "`id` TEXT NOT NULL, " +
                "`sessionExerciseId` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`weight` REAL NOT NULL, " +
                "`reps` INTEGER NOT NULL, " +
                "`rpe` REAL, " +
                "`durationSec` INTEGER, " +
                "`completed` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SetEntry_sessionExerciseId` " +
                "ON `SetEntry` (`sessionExerciseId`)",
        )
    }
}
