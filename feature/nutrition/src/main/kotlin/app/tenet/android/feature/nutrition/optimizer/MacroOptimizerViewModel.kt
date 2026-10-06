package app.tenet.android.feature.nutrition.optimizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.MacroCandidate
import app.tenet.android.core.common.MacroOptimizer
import app.tenet.android.core.common.MacroPlan
import app.tenet.android.core.common.Macros
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.data.RecipeRepository
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the optimizer may pick from. */
enum class CandidateSource(val label: String) {
    RECIPES("Rezepte"), FAVORITES("Favoriten"), RECENT("Zuletzt gegessen"), BASICS("Grundlebensmittel")
}

/** What the target is: the rest of the day's goal or a whole day from scratch. */
enum class OptimizeScope(val label: String) { REST("Rest von heute"), DAY("Ganzer Tag") }

/** A candidate plus how to log it. */
internal sealed interface Pick {
    val candidate: MacroCandidate

    data class FoodPick(override val candidate: MacroCandidate, val food: Food, val gramsPerUnit: Float) : Pick
    data class RecipePick(override val candidate: MacroCandidate, val detail: RecipeDetail) : Pick
}

data class OptimizerUiState(
    val loading: Boolean = true,
    val scope: OptimizeScope = OptimizeScope.REST,
    val sources: Set<CandidateSource> = CandidateSource.entries.toSet() - CandidateSource.BASICS,
    val maxItems: Int = 5,
    val goal: Macros = Macros(),
    val eaten: Macros = Macros(),
    val plan: MacroPlan? = null,
    /** Removed by the user ("nicht vorschlagen"). */
    val excluded: Set<String> = emptySet(),
    val candidateCount: Int = 0,
    /** Recipes skipped because they have no nutrients (Saffron, text imports). */
    val recipesWithoutNutrition: Int = 0,
    val meal: MealType = MealType.DINNER,
    val mealNames: Map<String, String> = emptyMap(),
) {
    val target: Macros get() = if (scope == OptimizeScope.REST) (goal - eaten).atLeastZero() else goal
}

@HiltViewModel
class MacroOptimizerViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    settings: UserSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OptimizerUiState())
    val state: StateFlow<OptimizerUiState> = _state.asStateFlow()

    val mealNames: StateFlow<Map<String, String>> = settings.settings.map { it.mealNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var date: String = LocalDate.now().toString()
    private var picks: List<Pick> = emptyList()
    /** Items of earlier suggestions, avoided by "Anderer Vorschlag". */
    private var avoid: Set<String> = emptySet()
    private var started = false
    /** Basics are switched on once automatically when your own foods are too few to combine. */
    private var basicsChecked = false

    fun init(date: String, meal: MealType) {
        if (started) return
        started = true
        this.date = date.ifEmpty { LocalDate.now().toString() }
        _state.update { it.copy(meal = meal) }
        viewModelScope.launch {
            combine(foodRepository.goal(this@MacroOptimizerViewModel.date), foodRepository.totals(this@MacroOptimizerViewModel.date)) { g, t ->
                Macros(g.kcal, g.protein, g.carbs, g.fat) to Macros(t.kcal, t.protein, t.carbs, t.fat)
            }.collect { (goal, eaten) ->
                val first = _state.value.loading
                _state.update { it.copy(goal = goal, eaten = eaten) }
                if (first) loadCandidates() else solve()
            }
        }
    }

    private suspend fun loadCandidates() {
        val recipes = recipeRepository.observeRecipes().first()
        val favorites = foodRepository.observeFavorites().first()
        val recent = foodRepository.observeRecent().first()
        var sources = _state.value.sources
        if (!basicsChecked) {
            basicsChecked = true
            val own = recipes.count { it.hasNutrition } + (favorites + recent).distinctBy { it.id }.size
            if (own < 5) {
                sources = sources + CandidateSource.BASICS
                _state.update { it.copy(sources = sources) }
            }
        }
        val list = mutableListOf<Pick>()
        if (CandidateSource.RECIPES in sources) {
            recipes.filter { it.hasNutrition && it.perServing.kcal > 0f }.forEach { detail ->
                list += Pick.RecipePick(
                    MacroCandidate(
                        id = "r:" + detail.recipe.id,
                        label = detail.recipe.title,
                        unitLabel = "Portion",
                        perUnit = detail.perServing.let { Macros(it.kcal, it.protein, it.carbs, it.fat) },
                        maxUnits = 2,
                    ),
                    detail,
                )
            }
        }
        val foods = buildList {
            if (CandidateSource.FAVORITES in sources) addAll(favorites)
            if (CandidateSource.RECENT in sources) addAll(recent)
            if (CandidateSource.BASICS in sources) addAll(foodRepository.basicFoods())
        }.distinctBy { it.id }.filter { it.kcalPer100 > 0f }
        foods.forEach { food -> list += foodPick(food) }
        picks = list
        _state.update {
            it.copy(
                loading = false,
                candidateCount = list.size,
                recipesWithoutNutrition = if (CandidateSource.RECIPES in sources) recipes.count { r -> !r.hasNutrition } else 0,
            )
        }
        solve()
    }

    /**
     * One unit of a food = your usual amount: the median of what you logged
     * recently, else the pack's serving size, else 100 g. Up to three units
     * (small portions like 100 g rice would otherwise never fill a day).
     */
    private suspend fun foodPick(food: Food): Pick {
        val usual = foodRepository.recentAmounts(food.id).sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val grams = (usual ?: food.servingSizeG?.takeIf { it in 5f..800f } ?: 100f).let { (it / 5f).roundToInt() * 5f }.coerceAtLeast(5f)
        val k = grams / 100f
        return Pick.FoodPick(
            MacroCandidate(
                id = "f:" + food.id,
                label = food.brand?.let { "${food.name} ($it)" } ?: food.name,
                unitLabel = "${grams.roundToInt()} g",
                perUnit = Macros(food.kcalPer100 * k, food.proteinPer100 * k, food.carbsPer100 * k, food.fatPer100 * k),
                maxUnits = 3,
            ),
            food,
            grams,
        )
    }

    private fun solve() {
        val s = _state.value
        val candidates = picks.map { it.candidate }.filter { it.id !in s.excluded }
        viewModelScope.launch {
            val plan = withContext(Dispatchers.Default) {
                MacroOptimizer.optimize(candidates, s.target, maxItems = s.maxItems, avoid = avoid)
            }
            _state.update { it.copy(plan = plan) }
        }
    }

    fun setScope(scope: OptimizeScope) {
        avoid = emptySet()
        _state.update { it.copy(scope = scope) }
        solve()
    }

    fun setMaxItems(max: Int) {
        _state.update { it.copy(maxItems = max) }
        solve()
    }

    fun toggleSource(source: CandidateSource) {
        _state.update { s ->
            val next = if (source in s.sources) s.sources - source else s.sources + source
            s.copy(sources = next.ifEmpty { s.sources })
        }
        viewModelScope.launch { loadCandidates() }
    }

    fun exclude(id: String) {
        _state.update { it.copy(excluded = it.excluded + id) }
        solve()
    }

    fun resetExcluded() {
        avoid = emptySet()
        _state.update { it.copy(excluded = emptySet()) }
        solve()
    }

    /** Next suggestion: dishes of the shown plans are avoided where there is an alternative. */
    fun another() {
        avoid = avoid + _state.value.plan?.items.orEmpty().map { it.candidate.id }
        solve()
    }

    fun setMeal(meal: MealType) = _state.update { it.copy(meal = meal) }

    /** Logs every dish of the plan into the chosen meal. */
    fun logPlan(onDone: () -> Unit) {
        val plan = _state.value.plan ?: return
        val meal = _state.value.meal
        viewModelScope.launch {
            plan.items.forEach { item ->
                when (val pick = picks.firstOrNull { it.candidate.id == item.candidate.id }) {
                    is Pick.FoodPick -> {
                        val grams = pick.gramsPerUnit * item.units
                        foodRepository.logFood(pick.food, grams, grams, "g", meal, date)
                    }
                    is Pick.RecipePick -> recipeRepository.logPortions(pick.detail, item.units.toFloat(), meal, date)
                    null -> Unit
                }
            }
            _messages.send("${plan.items.size} ${if (plan.items.size == 1) "Eintrag" else "Einträge"} gespeichert")
            onDone()
        }
    }
}
