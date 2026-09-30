package app.tenet.android.core.database.entity

import androidx.room.FtsOptions
import androidx.room.Fts4
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EntryType { NOTE, DIARY, DREAM }

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

enum class FoodSource { USER, OFF }

/**
 * One entity for notes, diary entries and dreams (App_Konzept.md 5.3:
 * "Ein Datenmodell, viele Sichten"). Diary- and dream-specific fields live
 * in the meta tables.
 */
@Entity(tableName = "Entry")
data class Entry(
    @PrimaryKey val id: String,
    val type: EntryType,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** ISO-8601 local date, the day the entry belongs to. */
    val entryDate: String,
    val pinned: Boolean = false,
    val color: Int? = null,
    val archived: Boolean = false,
    /** Notes only: folder name, null = no folder (v10). */
    val folder: String? = null,
)

/**
 * Full-text index over [Entry] title and body (v10, App_Konzept.md 6
 * "Globale Suche … FTS4"). External content: Room keeps it in sync with
 * triggers. unicode61 folds umlauts/accents, so "uber" finds "über".
 */
@Fts4(contentEntity = Entry::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "EntryFts")
data class EntryFts(
    val title: String,
    val body: String,
)

@Entity(
    tableName = "DiaryMeta",
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class DiaryMeta(
    @PrimaryKey val entryId: String,
    /** 1..5 */
    val mood: Int,
    /** 1..5, optional */
    val energy: Int? = null,
    /** 1..5, optional */
    val sleepQuality: Int? = null,
    // ---- v17: weather of the day (Open-Meteo WMO code, °C), filled on save
    val weatherCode: Int? = null,
    val tempMaxC: Float? = null,
    val tempMinC: Float? = null,
)

@Entity(
    tableName = "DreamMeta",
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class DreamMeta(
    @PrimaryKey val entryId: String,
    /** 1..5, how clear the dream was. */
    val clarity: Int,
    val lucid: Boolean = false,
    val nightmare: Boolean = false,
    val recurring: Boolean = false,
    /** Comma-separated emotion labels (App_Konzept.md 5.3 "Emotionen (Chips)"). */
    val emotions: String? = null,
)

@Entity(tableName = "Food")
data class Food(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val kcalPer100: Float,
    val proteinPer100: Float,
    val carbsPer100: Float,
    val fatPer100: Float,
    val servingSizeG: Float? = null,
    val favorite: Boolean = false,
    val source: FoodSource = FoodSource.USER,
    /** Last time this food was logged or used in a recipe ("Zuletzt verwendet"). */
    val lastUsedAt: Long? = null,
)

/**
 * Nutrition values are stored denormalized at log time so historical days
 * stay correct when a food or recipe changes later.
 */
@Entity(
    tableName = "FoodLog",
    indices = [Index("date")],
)
data class FoodLog(
    @PrimaryKey val id: String,
    /** ISO-8601 local date. */
    val date: String,
    val mealType: MealType,
    /** Display name of what was eaten (food or recipe title). */
    val label: String,
    val amountG: Float,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val createdAt: Long,
    /** Source food, if logged from the food database. */
    val foodId: String? = null,
    /** Source recipe, if logged as recipe portions. */
    val recipeId: String? = null,
    /** Amount in [unit] as entered (e.g. 2 Stück); [amountG] holds the grams. */
    val quantity: Float? = null,
    val unit: String? = null,
)

@Entity(tableName = "DailyGoal")
data class DailyGoal(
    /** ISO-8601 local date from which this goal applies. */
    @PrimaryKey val dateFrom: String,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
)
