package app.tenet.android.core.data

import app.tenet.android.core.common.EnergyMath
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.data.food.OpenFoodFactsClient
import app.tenet.android.core.data.food.SeedFoods
import app.tenet.android.core.database.dao.DayTotals
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.database.entity.WaterLog
import app.tenet.android.core.database.dao.FoodDao
import app.tenet.android.core.database.entity.DailyGoal
import app.tenet.android.core.database.entity.FoodLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NutritionTotals(
    val kcal: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
)

@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val openFoodFacts: OpenFoodFactsClient,
) {
    fun observeLogs(date: String): Flow<List<FoodLog>> = foodDao.observeLogs(date)

    fun totals(date: String): Flow<NutritionTotals> = observeLogs(date).map { logs ->
        NutritionTotals(
            kcal = logs.sumOf { it.kcal.toDouble() }.toFloat(),
            protein = logs.sumOf { it.protein.toDouble() }.toFloat(),
            carbs = logs.sumOf { it.carbs.toDouble() }.toFloat(),
            fat = logs.sumOf { it.fat.toDouble() }.toFloat(),
        )
    }

    /** The goal in force on [date]; falls back to [defaultGoal] until the user sets their own. */
    fun goal(date: String): Flow<DailyGoal> =
        foodDao.observeGoal(date).map { it ?: defaultGoal.copy(dateFrom = date) }

    /** Persists the user's calorie and macro goals starting from the goal's dateFrom. */
    suspend fun setGoal(goal: DailyGoal) = foodDao.upsertGoal(goal)

    suspend fun addLog(log: FoodLog) = foodDao.insertLog(log)

    suspend fun deleteLog(id: String) = foodDao.deleteLog(id)

    // ---- Food database ------------------------------------------------------

    /** Inserts the basic food list once, so search works offline from day one. */
    suspend fun ensureSeeded() {
        if (foodDao.foodCount() == 0) foodDao.upsertFoods(SeedFoods)
    }

    /** Amounts the user logged of this food recently (newest first). */
    suspend fun recentAmounts(foodId: String): List<Float> = foodDao.recentAmounts(foodId)

    suspend fun searchLocal(query: String): List<Food> {
        ensureSeeded()
        return if (query.isBlank()) emptyList() else foodDao.searchFoods("%${query.trim()}%")
    }

    /** Open Food Facts search; results are only stored once they are used. */
    suspend fun searchOnline(query: String): List<Food> =
        if (query.trim().length < 3) emptyList() else openFoodFacts.search(query)

    /** Barcode → food: local database first, then Open Food Facts (cached locally). */
    suspend fun lookupBarcode(barcode: String): Food? {
        foodDao.foodByBarcode(barcode)?.let { return it }
        val remote = openFoodFacts.product(barcode) ?: return null
        foodDao.upsertFood(remote)
        return remote
    }

    fun observeFavorites(): Flow<List<Food>> = foodDao.observeFavorites()

    fun observeRecent(): Flow<List<Food>> = foodDao.observeRecent()

    suspend fun food(id: String): Food? = foodDao.foodById(id)

    suspend fun saveFood(food: Food) = foodDao.upsertFood(food)

    /** Persists [food] if needed (e.g. an online result) and flips its favorite flag. */
    suspend fun setFavorite(food: Food, favorite: Boolean) {
        if (foodDao.foodById(food.id) == null) foodDao.upsertFood(food.copy(favorite = favorite))
        else foodDao.setFavorite(food.id, favorite)
    }

    /** Logs [grams] of [food] (entered as [quantity] [unit]) and marks it as recently used. */
    suspend fun logFood(
        food: Food,
        grams: Float,
        quantity: Float,
        unit: String,
        meal: MealType,
        date: String,
    ) {
        val now = System.currentTimeMillis()
        if (foodDao.foodById(food.id) == null) foodDao.upsertFood(food.copy(lastUsedAt = now))
        else foodDao.touchFood(food.id, now)
        foodDao.insertLog(
            FoodLog(
                id = newUuid(),
                date = date,
                mealType = meal,
                label = food.brand?.let { "${food.name} ($it)" } ?: food.name,
                amountG = grams,
                kcal = EnergyMath.per100(food.kcalPer100, grams),
                protein = EnergyMath.per100(food.proteinPer100, grams),
                carbs = EnergyMath.per100(food.carbsPer100, grams),
                fat = EnergyMath.per100(food.fatPer100, grams),
                createdAt = now,
                foodId = food.id,
                quantity = quantity,
                unit = unit,
            ),
        )
    }

    /** Logs a one-off entry (Schnelleintrag) without storing a food. */
    suspend fun logQuick(food: Food, grams: Float, meal: MealType, date: String) {
        foodDao.insertLog(
            FoodLog(
                id = newUuid(),
                date = date,
                mealType = meal,
                label = food.name,
                amountG = grams,
                kcal = EnergyMath.per100(food.kcalPer100, grams),
                protein = EnergyMath.per100(food.proteinPer100, grams),
                carbs = EnergyMath.per100(food.carbsPer100, grams),
                fat = EnergyMath.per100(food.fatPer100, grams),
                createdAt = System.currentTimeMillis(),
                quantity = grams,
                unit = "g",
            ),
        )
    }

    /** "Gestern kopieren": duplicates all logs of [from] onto [to]. Returns the count. */
    suspend fun copyDay(from: String, to: String): Int {
        val logs = foodDao.logsOnce(from)
        val now = System.currentTimeMillis()
        foodDao.insertLogs(logs.mapIndexed { i, log -> log.copy(id = newUuid(), date = to, createdAt = now + i) })
        return logs.size
    }

    /** Days with at least one logged meal (tracking streak). */
    fun observeLogDates(): Flow<List<String>> = foodDao.observeLogDates()

    fun observeDailyTotals(from: String, to: String): Flow<List<DayTotals>> =
        foodDao.observeDailyTotals(from, to)

    // ---- Water -------------------------------------------------------------

    fun observeWater(date: String): Flow<Int> = foodDao.observeWater(date).map { it?.ml ?: 0 }

    suspend fun setWater(date: String, ml: Int) = foodDao.upsertWater(WaterLog(date, ml.coerceAtLeast(0)))

    companion object {
        /** Used until the user sets their own goals in the settings. */
        val defaultGoal = DailyGoal(
            dateFrom = "",
            kcal = 2000f,
            protein = 150f,
            carbs = 200f,
            fat = 60f,
        )
    }
}
