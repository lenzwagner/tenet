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

    /**
     * Built-in basic foods that make sense as a portion of their own (no
     * cooking fats, sweeteners or drinks); candidates for the Makro-Optimierer.
     */
    suspend fun basicFoods(): List<Food> {
        ensureSeeded()
        val skip = setOf("Butter", "Olivenöl", "Honig", "Cola", "Bier", "Orangensaft", "Gouda")
        return SeedFoods.filter { it.name !in skip }
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

    /**
     * Changes a logged entry afterwards: other amount (energy and macros scale with
     * it, they were stored at log time) and/or other meal.
     */
    suspend fun editLog(log: FoodLog, grams: Float, meal: MealType) {
        val factor = if (log.amountG > 0f && grams > 0f) grams / log.amountG else 1f
        foodDao.updateLog(
            id = log.id,
            meal = meal,
            amountG = if (grams > 0f) grams else log.amountG,
            kcal = log.kcal * factor,
            protein = log.protein * factor,
            carbs = log.carbs * factor,
            fat = log.fat * factor,
            quantity = log.quantity?.let { it * factor },
        )
    }

    // ---- Meal templates ("Frühstück wie immer") ------------------------------------

    fun observeTemplates(): Flow<List<app.tenet.android.core.database.entity.MealTemplate>> = foodDao.observeTemplates()

    /** Saves [logs] (a meal as eaten today) under [name]. */
    suspend fun saveTemplate(name: String, logs: List<FoodLog>) {
        if (logs.isEmpty()) return
        val items = org.json.JSONArray()
        logs.forEach { l ->
            items.put(
                org.json.JSONObject()
                    .put("label", l.label).put("grams", l.amountG.toDouble()).put("kcal", l.kcal.toDouble())
                    .put("protein", l.protein.toDouble()).put("carbs", l.carbs.toDouble()).put("fat", l.fat.toDouble())
                    .put("foodId", l.foodId).put("recipeId", l.recipeId)
                    .put("quantity", l.quantity?.toDouble()).put("unit", l.unit),
            )
        }
        val now = System.currentTimeMillis()
        foodDao.upsertTemplate(app.tenet.android.core.database.entity.MealTemplate(newUuid(), name.trim().ifBlank { "Vorlage" }, items.toString(), now, now))
    }

    /** Logs every item of [template] into [meal] on [date]; the template moves to the top. */
    suspend fun logTemplate(template: app.tenet.android.core.database.entity.MealTemplate, meal: MealType, date: String) {
        val now = System.currentTimeMillis()
        templateItems(template).forEach { it ->
            foodDao.insertLog(it.copy(id = newUuid(), date = date, mealType = meal, createdAt = now))
            it.foodId?.let { id -> if (foodDao.foodById(id) != null) foodDao.touchFood(id, now) }
        }
        foodDao.upsertTemplate(template.copy(updatedAt = now))
    }

    suspend fun deleteTemplate(id: String) = foodDao.deleteTemplate(id)

    /** The template's entries as logs (id, date and meal still to set). */
    fun templateItems(template: app.tenet.android.core.database.entity.MealTemplate): List<FoodLog> = runCatching {
        val arr = org.json.JSONArray(template.itemsJson)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { o ->
            fun num(k: String) = o.optDouble(k, 0.0).toFloat()
            fun str(k: String) = o.optString(k).takeIf { o.has(k) && !o.isNull(k) && it.isNotBlank() }
            FoodLog(
                id = "", date = "", mealType = MealType.SNACK, label = o.optString("label"),
                amountG = num("grams"), kcal = num("kcal"), protein = num("protein"), carbs = num("carbs"), fat = num("fat"),
                createdAt = 0L, foodId = str("foodId"), recipeId = str("recipeId"),
                quantity = if (o.isNull("quantity")) null else num("quantity"), unit = str("unit"),
            )
        }
    }.getOrDefault(emptyList())

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
