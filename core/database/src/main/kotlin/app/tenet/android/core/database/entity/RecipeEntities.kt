package app.tenet.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Recipe (App_Konzept.md 5.4 "Rezepte"). [steps] = one step per line. */
@Entity(tableName = "Recipe")
data class Recipe(
    @PrimaryKey val id: String,
    val title: String,
    val photoUri: String? = null,
    val servings: Int = 2,
    val minutes: Int? = null,
    /** Comma-separated tags, e.g. "High Protein,Meal Prep". */
    val tags: String = "",
    val steps: String = "",
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    // ---- v16: recipes imported from Saffron (Firestore users/{uid}/recipes)
    /** null = created in Tenet, "saffron" = imported. */
    val source: String? = null,
    /** Original page (TikTok, Chefkoch …). */
    val sourceUrl: String? = null,
    @ColumnInfo(defaultValue = "")
    val category: String = "",
    /** 0 = not rated, 1–5 stars. */
    @ColumnInfo(defaultValue = "0")
    val rating: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val vegetarian: Boolean = false,
    /** Free-text ingredients, one per line (Saffron has no gram/nutrient data). */
    @ColumnInfo(defaultValue = "")
    val ingredientLines: String = "",
    /** Structured steps as Saffron JSON [{text, timeMinutes, stepIngredients}]; null = use [steps]. */
    val stepsJson: String? = null,
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    /** Extra photos (slideshow), one URL per line. */
    @ColumnInfo(defaultValue = "")
    val images: String = "",
    @ColumnInfo(defaultValue = "0")
    val cooked: Boolean = false,
)

/**
 * Ingredient with a snapshot of the food's nutrients per 100 g, so recipes
 * keep their values even if the food is later edited or removed.
 */
@Entity(
    tableName = "RecipeIngredient",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class RecipeIngredient(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val foodId: String? = null,
    val name: String,
    val grams: Float,
    val kcalPer100: Float,
    val proteinPer100: Float,
    val carbsPer100: Float,
    val fatPer100: Float,
)

/** Water intake per day in ml (optional tracker card). */
@Entity(tableName = "WaterLog")
data class WaterLog(
    @PrimaryKey val date: String,
    val ml: Int,
)
