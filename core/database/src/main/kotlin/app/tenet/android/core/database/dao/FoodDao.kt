package app.tenet.android.core.database.dao

import androidx.room.Transaction
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.Recipe
import app.tenet.android.core.database.entity.RecipeIngredient
import app.tenet.android.core.database.entity.WaterLog
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import app.tenet.android.core.database.entity.DailyGoal
import app.tenet.android.core.database.entity.FoodLog
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    /** Recent logged amounts of a food (portion history for voice logging). */
    @Query("SELECT amountG FROM FoodLog WHERE foodId = :foodId ORDER BY createdAt DESC LIMIT 20")
    suspend fun recentAmounts(foodId: String): List<Float>


    @Query("SELECT * FROM FoodLog WHERE date = :date ORDER BY createdAt ASC")
    fun observeLogs(date: String): Flow<List<FoodLog>>

    @Insert
    suspend fun insertLog(log: FoodLog)

    @Query("SELECT * FROM FoodLog WHERE label LIKE :pattern ORDER BY createdAt DESC LIMIT 20")
    suspend fun searchLogs(pattern: String): List<FoodLog>

    @Query("DELETE FROM FoodLog WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("SELECT * FROM DailyGoal WHERE dateFrom <= :date ORDER BY dateFrom DESC LIMIT 1")
    suspend fun getGoal(date: String): DailyGoal?

    /** Reactive variant: the goal in force on [date] (latest "valid from" row at or before it). */
    @Query("SELECT * FROM DailyGoal WHERE dateFrom <= :date ORDER BY dateFrom DESC LIMIT 1")
    fun observeGoal(date: String): Flow<DailyGoal?>

    @Upsert
    suspend fun upsertGoal(goal: DailyGoal)

    // ---- Foods -------------------------------------------------------------

    @Query(
        """
        SELECT * FROM Food WHERE name LIKE :pattern OR brand LIKE :pattern
        ORDER BY favorite DESC, (lastUsedAt IS NULL), lastUsedAt DESC, name
        LIMIT 40
        """,
    )
    suspend fun searchFoods(pattern: String): List<Food>

    @Query("SELECT * FROM Food WHERE favorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<Food>>

    @Query("SELECT * FROM Food WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<Food>>

    @Query("SELECT * FROM Food WHERE id = :id")
    suspend fun foodById(id: String): Food?

    @Query("SELECT * FROM Food WHERE barcode = :barcode LIMIT 1")
    suspend fun foodByBarcode(barcode: String): Food?

    @Query("SELECT COUNT(*) FROM Food")
    suspend fun foodCount(): Int

    @Upsert
    suspend fun upsertFood(food: Food)

    @Upsert
    suspend fun upsertFoods(foods: List<Food>)

    @Query("UPDATE Food SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE Food SET lastUsedAt = :now WHERE id = :id")
    suspend fun touchFood(id: String, now: Long)

    // ---- Logs -------------------------------------------------------------

    @Query("SELECT DISTINCT date FROM FoodLog")
    fun observeLogDates(): Flow<List<String>>

    @Query("SELECT * FROM FoodLog WHERE date = :date ORDER BY createdAt ASC")
    suspend fun logsOnce(date: String): List<FoodLog>

    @Insert
    suspend fun insertLogs(logs: List<FoodLog>)

    @Query(
        """
        SELECT date, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat
        FROM FoodLog WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date
        """,
    )
    fun observeDailyTotals(from: String, to: String): Flow<List<DayTotals>>

    // ---- Water -------------------------------------------------------------

    @Query("SELECT * FROM WaterLog WHERE date = :date")
    fun observeWater(date: String): Flow<WaterLog?>

    @Upsert
    suspend fun upsertWater(log: WaterLog)

    // ---- Recipes -----------------------------------------------------------

    @Query("SELECT * FROM Recipe ORDER BY favorite DESC, updatedAt DESC")
    fun observeRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM RecipeIngredient ORDER BY position")
    fun observeAllIngredients(): Flow<List<RecipeIngredient>>

    @Query("SELECT * FROM Recipe WHERE id = :id")
    fun observeRecipe(id: String): Flow<Recipe?>

    @Query("SELECT * FROM Recipe WHERE id = :id")
    suspend fun recipeOnce(id: String): Recipe?

    @Query("SELECT * FROM RecipeIngredient WHERE recipeId = :recipeId ORDER BY position")
    fun observeIngredients(recipeId: String): Flow<List<RecipeIngredient>>

    @Query("SELECT * FROM RecipeIngredient WHERE recipeId = :recipeId ORDER BY position")
    suspend fun ingredientsOnce(recipeId: String): List<RecipeIngredient>

    @Query("SELECT * FROM Recipe WHERE title LIKE :pattern OR tags LIKE :pattern ORDER BY title LIMIT 20")
    suspend fun searchRecipes(pattern: String): List<Recipe>

    @Upsert
    suspend fun upsertRecipe(recipe: Recipe)

    @Query("DELETE FROM RecipeIngredient WHERE recipeId = :recipeId")
    suspend fun clearIngredients(recipeId: String)

    @Upsert
    suspend fun upsertIngredients(items: List<RecipeIngredient>)

    @Transaction
    suspend fun saveRecipe(recipe: Recipe, ingredients: List<RecipeIngredient>) {
        upsertRecipe(recipe)
        clearIngredients(recipe.id)
        upsertIngredients(ingredients)
    }

    @Query("DELETE FROM Recipe WHERE id = :id")
    suspend fun deleteRecipe(id: String)

    @Query("SELECT id FROM Recipe WHERE source = :source")
    suspend fun recipeIdsFromSource(source: String): List<String>

    @Upsert
    suspend fun upsertRecipes(recipes: List<Recipe>)
}

data class DayTotals(val date: String, val kcal: Float, val protein: Float, val carbs: Float, val fat: Float)
