package app.tenet.android.core.data

import app.tenet.android.core.common.CookStep
import app.tenet.android.core.common.EnergyMath
import app.tenet.android.core.common.IngredientScaler
import app.tenet.android.core.common.SaffronFormat
import app.tenet.android.core.data.sync.SaffronRecipes
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.database.dao.FoodDao
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.database.entity.FoodLog
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.database.entity.Recipe
import app.tenet.android.core.database.entity.RecipeIngredient
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

/** Recipe with its ingredients and computed nutrition (App_Konzept.md 5.4). */
data class RecipeDetail(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredient>,
) {
    val total: NutritionTotals = NutritionTotals(
        kcal = ingredients.sumOf { EnergyMath.per100(it.kcalPer100, it.grams).toDouble() }.toFloat(),
        protein = ingredients.sumOf { EnergyMath.per100(it.proteinPer100, it.grams).toDouble() }.toFloat(),
        carbs = ingredients.sumOf { EnergyMath.per100(it.carbsPer100, it.grams).toDouble() }.toFloat(),
        fat = ingredients.sumOf { EnergyMath.per100(it.fatPer100, it.grams).toDouble() }.toFloat(),
    )
    private val servings = recipe.servings.coerceAtLeast(1)
    val perServing: NutritionTotals = NutritionTotals(
        kcal = total.kcal / servings,
        protein = total.protein / servings,
        carbs = total.carbs / servings,
        fat = total.fat / servings,
    )
    val gramsPerServing: Float = ingredients.sumOf { it.grams.toDouble() }.toFloat() / servings
    val tagList: List<String> = recipe.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val stepList: List<String> = recipe.steps.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Imported from the Saffron app (free-text ingredients, no nutrients). */
    val fromSaffron: Boolean get() = recipe.source == SaffronRecipes.SOURCE

    /** Nutrients known (ingredients from the food database with grams). */
    val hasNutrition: Boolean get() = ingredients.isNotEmpty()

    /** Free-text ingredients and steps (Saffron, link/text import) rather than foods with nutrients. */
    val isTextRecipe: Boolean
        get() = !hasNutrition && (recipe.source != null || recipe.ingredientLines.isNotBlank() || recipe.stepsJson != null)

    /** Free-text ingredient lines (Saffron). */
    val ingredientLines: List<String> = recipe.ingredientLines.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Steps with timers and their ingredients; own recipes: one per line, timer read from the text. */
    val cookSteps: List<CookStep> = recipe.stepsJson?.let { SaffronFormat.steps(it) }?.takeIf { it.isNotEmpty() }
        ?: stepList.map { CookStep(it, 0) }

    /** Cover photo first, then slideshow images. */
    val images: List<String> = (listOfNotNull(recipe.photoUri) + recipe.images.lines().map { it.trim() })
        .filter { it.isNotEmpty() }.distinct()
}

@Singleton
class RecipeRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val entryRepository: EntryRepository,
    private val saffron: SaffronRecipes,
) {
    fun observeRecipes(): Flow<List<RecipeDetail>> =
        combine(foodDao.observeRecipes(), foodDao.observeAllIngredients()) { recipes, ingredients ->
            val byRecipe = ingredients.groupBy { it.recipeId }
            recipes.map { RecipeDetail(it, byRecipe[it.id].orEmpty()) }
        }

    fun observeRecipe(id: String): Flow<RecipeDetail?> =
        combine(foodDao.observeRecipe(id), foodDao.observeIngredients(id)) { recipe, ingredients ->
            recipe?.let { RecipeDetail(it, ingredients) }
        }

    suspend fun recipe(id: String): RecipeDetail? =
        foodDao.recipeOnce(id)?.let { RecipeDetail(it, foodDao.ingredientsOnce(id)) }

    suspend fun search(query: String): List<Recipe> =
        if (query.isBlank()) emptyList() else foodDao.searchRecipes("%${query.trim()}%")

    suspend fun save(recipe: Recipe, ingredients: List<RecipeIngredient>) {
        foodDao.saveRecipe(
            recipe.copy(updatedAt = System.currentTimeMillis()),
            ingredients.mapIndexed { i, item -> item.copy(recipeId = recipe.id, position = i) },
        )
        // Using a food in a recipe counts as "zuletzt verwendet".
        val now = System.currentTimeMillis()
        ingredients.mapNotNull { it.foodId }.distinct().forEach { foodDao.touchFood(it, now) }
    }

    suspend fun delete(id: String) = foodDao.deleteRecipe(id)

    suspend fun setFavorite(recipe: Recipe, favorite: Boolean) {
        val updated = recipe.copy(favorite = favorite)
        foodDao.upsertRecipe(updated)
        saffron.writeBack(updated)
    }

    /** 0 = not rated, 1–5 stars; Saffron recipes get it back in Saffron too. */
    suspend fun setRating(recipe: Recipe, rating: Int) {
        val updated = recipe.copy(rating = rating.coerceIn(0, 5))
        foodDao.upsertRecipe(updated)
        saffron.writeBack(updated)
    }

    /**
     * Saves edited title, free-text ingredients and steps of a text recipe
     * (Saffron, imported). Steps keep timer and step ingredients. Returns
     * true when the change also reached Saffron in Firebase.
     */
    suspend fun updateContent(recipe: Recipe, title: String, ingredients: List<String>, steps: List<CookStep>): Boolean {
        val cleanSteps = steps.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
        val updated = recipe.copy(
            title = title.trim(),
            ingredientLines = ingredients.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
            stepsJson = SaffronFormat.stepsJson(cleanSteps),
            steps = cleanSteps.joinToString("\n") { it.text.replace('\n', ' ') },
            updatedAt = System.currentTimeMillis(),
        )
        foodDao.upsertRecipe(updated)
        return saffron.writeContent(updated)
    }

    suspend fun setCooked(recipe: Recipe, cooked: Boolean) = foodDao.upsertRecipe(recipe.copy(cooked = cooked))

    /** "Als Mahlzeit loggen": [portions] servings of [detail] into [meal] on [date]. */
    suspend fun logPortions(detail: RecipeDetail, portions: Float, meal: MealType, date: String) {
        val per = detail.perServing
        foodDao.insertLog(
            FoodLog(
                id = newUuid(),
                date = date,
                mealType = meal,
                label = detail.recipe.title,
                amountG = detail.gramsPerServing * portions,
                kcal = per.kcal * portions,
                protein = per.protein * portions,
                carbs = per.carbs * portions,
                fat = per.fat * portions,
                createdAt = System.currentTimeMillis(),
                recipeId = detail.recipe.id,
                quantity = portions,
                unit = "Portion",
            ),
        )
    }

    /**
     * Shopping list for [servings] servings, stored as a checklist note in the
     * journal (App_Konzept.md 5.4). Returns the new note's id.
     */
    suspend fun createShoppingList(detail: RecipeDetail, servings: Int): String {
        val factor = servings.toFloat() / detail.recipe.servings.coerceAtLeast(1)
        // Only checklist lines in the body, so the note opens as a checklist.
        val body = buildString {
            detail.ingredients.forEach { item ->
                appendLine("- [ ] ${(item.grams * factor).roundToInt()} g ${item.name}")
            }
            detail.ingredientLines.forEach { line ->
                appendLine("- [ ] ${IngredientScaler.scale(line, factor.toDouble())}")
            }
        }.trimEnd()
        val now = System.currentTimeMillis()
        val id = newUuid()
        entryRepository.save(
            Entry(
                id = id,
                type = EntryType.NOTE,
                title = "Einkaufsliste: ${detail.recipe.title} ($servings Portionen)",
                body = body,
                createdAt = now,
                updatedAt = now,
                entryDate = LocalDate.now().toString(),
            ),
            diaryMeta = null,
            dreamMeta = null,
        )
        entryRepository.setTags(id, listOf("Einkauf"), emptyList())
        return id
    }
}
