package app.tenet.android.feature.nutrition.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.data.RecipeRepository
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    settingsRepository: UserSettingsRepository,
) : ViewModel() {

    private val id = MutableStateFlow<String?>(null)

    val detail: StateFlow<RecipeDetail?> = id.filterNotNull()
        .flatMapLatest { recipeRepository.observeRecipe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mealNames: StateFlow<Map<String, String>> = settingsRepository.settings.map { it.mealNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted = _deleted.receiveAsFlow()

    fun load(recipeId: String) { id.value = recipeId }

    fun toggleFavorite() {
        val d = detail.value ?: return
        viewModelScope.launch { recipeRepository.setFavorite(d.recipe, !d.recipe.favorite) }
    }

    /** Tap the same star again to clear the rating. */
    fun rate(stars: Int) {
        val d = detail.value ?: return
        viewModelScope.launch { recipeRepository.setRating(d.recipe, if (d.recipe.rating == stars) 0 else stars) }
    }

    fun toggleCooked() {
        val d = detail.value ?: return
        viewModelScope.launch { recipeRepository.setCooked(d.recipe, !d.recipe.cooked) }
    }

    fun log(portions: Float, meal: MealType) {
        val d = detail.value ?: return
        viewModelScope.launch {
            recipeRepository.logPortions(d, portions, meal, LocalDate.now().toString())
            _messages.send("${portions.toString().removeSuffix(".0")} Portion(en) geloggt")
        }
    }

    fun shoppingList(servings: Int) {
        val d = detail.value ?: return
        viewModelScope.launch {
            recipeRepository.createShoppingList(d, servings)
            _messages.send("Einkaufsliste im Journal angelegt")
        }
    }

    fun delete() {
        val d = detail.value ?: return
        viewModelScope.launch {
            recipeRepository.delete(d.recipe.id)
            _deleted.send(Unit)
        }
    }
}
