package app.tenet.android.feature.nutrition.recipe

import kotlinx.coroutines.withTimeoutOrNull
import app.tenet.android.core.common.RecipeTextParser
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.EnergyMath
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.data.RecipeRepository
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.Recipe
import app.tenet.android.core.database.entity.RecipeIngredient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class RecipeEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val photoUri: String? = null,
    val servings: Int = 2,
    val minutes: String = "",
    val tags: String = "",
    val steps: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val error: String? = null,
    // Ingredient picker
    val pickerQuery: String = "",
    val pickerResults: List<Food> = emptyList(),
    val pickerLoading: Boolean = false,
    /** Text import running (matching ingredients to foods). */
    val importing: Boolean = false,
    /** Result line of the last import, e.g. "7 Zutaten, 2 ohne Nährwerte". */
    val importInfo: String? = null,
) {
    val kcalPerServing: Int
        get() = (ingredients.sumOf { EnergyMath.per100(it.kcalPer100, it.grams).toDouble() } / servings.coerceAtLeast(1)).toInt()
    val proteinPerServing: Int
        get() = (ingredients.sumOf { EnergyMath.per100(it.proteinPer100, it.grams).toDouble() } / servings.coerceAtLeast(1)).toInt()
}

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeEditorState())
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()

    private val _saved = Channel<String>(Channel.BUFFERED)
    /** Emits the saved recipe id. */
    val saved = _saved.receiveAsFlow()

    private var recipeId: String = newUuid()
    private var createdAt: Long = System.currentTimeMillis()
    private var favorite = false
    private var loaded = false
    private var searchJob: Job? = null

    fun load(id: String?) {
        if (loaded) return
        loaded = true
        if (id.isNullOrBlank()) {
            _state.value = RecipeEditorState(loading = false)
            return
        }
        viewModelScope.launch {
            val detail = recipeRepository.recipe(id)
            if (detail == null) {
                _state.value = RecipeEditorState(loading = false)
                return@launch
            }
            val r = detail.recipe
            recipeId = r.id
            createdAt = r.createdAt
            favorite = r.favorite
            _state.value = RecipeEditorState(
                loading = false,
                isNew = false,
                title = r.title,
                photoUri = r.photoUri,
                servings = r.servings,
                minutes = r.minutes?.toString().orEmpty(),
                tags = r.tags.replace(",", ", "),
                steps = r.steps,
                ingredients = detail.ingredients,
            )
        }
    }

    /** Something was changed since loading: leaving stores it. */
    private var dirty = false

    private fun update(block: RecipeEditorState.() -> RecipeEditorState) {
        _state.value = _state.value.block().copy(error = null)
        dirty = true
    }

    /** Back: store the changes without leaving through the "saved" path; an untitled new recipe is dropped. */
    fun saveOnLeave(onDone: () -> Unit) {
        val s = _state.value
        if (!dirty || s.title.isBlank()) return onDone()
        viewModelScope.launch {
            store()
            onDone()
        }
    }

    fun onTitle(v: String) = update { copy(title = v) }
    fun onPhoto(uri: String?) = update { copy(photoUri = uri) }
    fun onServings(v: Int) = update { copy(servings = v.coerceIn(1, 24)) }
    fun onMinutes(v: String) = update { copy(minutes = v.filter { it.isDigit() }.take(4)) }
    fun onTags(v: String) = update { copy(tags = v) }
    fun onSteps(v: String) = update { copy(steps = v) }

    fun removeIngredient(item: RecipeIngredient) = update { copy(ingredients = ingredients - item) }
    fun setGrams(item: RecipeIngredient, grams: Float) = update {
        copy(ingredients = ingredients.map { if (it.id == item.id) it.copy(grams = grams) else it })
    }

    fun addIngredient(food: Food, grams: Float) {
        viewModelScope.launch {
            // Online results become local foods once used.
            if (foodRepository.food(food.id) == null) foodRepository.saveFood(food)
        }
        update {
            copy(
                ingredients = ingredients + RecipeIngredient(
                    id = newUuid(),
                    recipeId = recipeId,
                    position = ingredients.size,
                    foodId = food.id,
                    name = food.brand?.let { "${food.name} ($it)" } ?: food.name,
                    grams = grams,
                    kcalPer100 = food.kcalPer100,
                    proteinPer100 = food.proteinPer100,
                    carbsPer100 = food.carbsPer100,
                    fatPer100 = food.fatPer100,
                ),
                pickerQuery = "",
                pickerResults = emptyList(),
            )
        }
    }

    /** Replaces an (unmatched) ingredient with a picked food, keeping its position. */
    fun replaceIngredient(old: RecipeIngredient, food: Food, grams: Float) {
        viewModelScope.launch { if (foodRepository.food(food.id) == null) foodRepository.saveFood(food) }
        update {
            copy(
                ingredients = ingredients.map {
                    if (it.id != old.id) it else it.copy(
                        foodId = food.id,
                        name = food.brand?.let { b -> "${food.name} ($b)" } ?: food.name,
                        grams = grams,
                        kcalPer100 = food.kcalPer100,
                        proteinPer100 = food.proteinPer100,
                        carbsPer100 = food.carbsPer100,
                        fatPer100 = food.fatPer100,
                    )
                },
                pickerQuery = "",
                pickerResults = emptyList(),
            )
        }
    }

    /**
     * Imports a pasted recipe: title/servings/time/steps from the text,
     * ingredients matched to foods (local first, then Open Food Facts).
     * Unmatched ones are kept without nutrients so they can be assigned.
     */
    fun importText(text: String) {
        val parsed = RecipeTextParser.parse(text)
        _state.value = _state.value.copy(importing = true, importInfo = null)
        viewModelScope.launch {
            val start = _state.value.ingredients.size
            var unmatched = 0
            val imported = parsed.ingredients.mapIndexed { i, ing ->
                val food = foodRepository.searchLocal(ing.name).firstOrNull()
                    ?: runCatching { withTimeoutOrNull(6_000) { foodRepository.searchOnline(ing.name).firstOrNull() } }.getOrNull()
                if (food != null && foodRepository.food(food.id) == null) foodRepository.saveFood(food)
                if (food == null) unmatched++
                RecipeIngredient(
                    id = newUuid(),
                    recipeId = recipeId,
                    position = start + i,
                    foodId = food?.id,
                    name = food?.let { f -> f.brand?.let { "${f.name} ($it)" } ?: f.name }?.let { "${ing.name}$PRODUCT_SEPARATOR$it" } ?: ing.name,
                    grams = ing.grams ?: 0f,
                    kcalPer100 = food?.kcalPer100 ?: 0f,
                    proteinPer100 = food?.proteinPer100 ?: 0f,
                    carbsPer100 = food?.carbsPer100 ?: 0f,
                    fatPer100 = food?.fatPer100 ?: 0f,
                )
            }
            val s = _state.value
            _state.value = s.copy(
                title = s.title.ifBlank { parsed.title },
                servings = parsed.servings ?: s.servings,
                minutes = s.minutes.ifBlank { parsed.minutes?.toString().orEmpty() },
                steps = listOf(s.steps.trim(), parsed.steps.joinToString("\n")).filter { it.isNotEmpty() }.joinToString("\n"),
                ingredients = s.ingredients + imported,
                importing = false,
                importInfo = buildString {
                    append("${imported.size} Zutaten, ${parsed.steps.size} Schritte übernommen.")
                    if (unmatched > 0) append(" $unmatched ohne Nährwerte – antippen zum Zuordnen.")
                    if (parsed.ingredients.any { it.estimated }) append(" Mengen wie EL/Stück sind geschätzt, bitte prüfen.")
                },
            )
        }
    }

    fun onPickerQuery(value: String) {
        _state.value = _state.value.copy(pickerQuery = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val local = foodRepository.searchLocal(value)
            _state.value = _state.value.copy(pickerResults = local)
            if (value.trim().length < 3) return@launch
            delay(450)
            _state.value = _state.value.copy(pickerLoading = true)
            val online = runCatching { foodRepository.searchOnline(value) }.getOrElse { emptyList() }
            val known = local.map { it.id }.toSet()
            _state.value = _state.value.copy(
                pickerResults = local + online.filter { it.id !in known },
                pickerLoading = false,
            )
        }
    }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.value = s.copy(error = "Bitte einen Titel eingeben.")
            return
        }
        val now = System.currentTimeMillis()
        val recipe = Recipe(
            id = recipeId,
            title = s.title.trim(),
            photoUri = s.photoUri,
            servings = s.servings,
            minutes = s.minutes.toIntOrNull(),
            tags = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
            steps = s.steps.trim(),
            favorite = favorite,
            createdAt = createdAt,
            updatedAt = now,
        )
        viewModelScope.launch {
            recipeRepository.save(recipe, s.ingredients)
            dirty = false
            _saved.send(recipeId)
        }
    }

    private suspend fun store() {
        val s = _state.value
        recipeRepository.save(
            Recipe(
                id = recipeId,
                title = s.title.trim(),
                photoUri = s.photoUri,
                servings = s.servings,
                minutes = s.minutes.toIntOrNull(),
                tags = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                steps = s.steps.trim(),
                favorite = favorite,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
            ),
            s.ingredients,
        )
        dirty = false
    }
}
