package app.tenet.android.feature.nutrition

import app.tenet.android.core.common.PortionMath
import app.tenet.android.core.common.FoodPhraseParser
import kotlinx.coroutines.withTimeoutOrNull
import app.tenet.android.core.data.ai.AiFiller
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.data.RecipeRepository
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Quick entry ("Schnelleintrag": only kcal and macros). */
data class QuickEntry(
    val name: String = "",
    val amount: String = "100",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val saveAsFood: Boolean = false,
    val error: String? = null,
)

data class AddFoodState(
    val query: String = "",
    val local: List<Food> = emptyList(),
    val online: List<Food> = emptyList(),
    val onlineLoading: Boolean = false,
    val onlineFailed: Boolean = false,
    val barcodeLoading: Boolean = false,
    /** Food whose portion sheet is open. */
    val selected: Food? = null,
    /** Recipe whose portion sheet is open. */
    val selectedRecipe: RecipeDetail? = null,
    val quick: QuickEntry = QuickEntry(),
    /** Spoken meal, parsed by the AI; null = no voice sheet. */
    val voiceItems: List<VoiceItem>? = null,
    val voiceBusy: Boolean = false,
    /** Parsed without the KI (offline rules) – worth a quick check. */
    val voiceOffline: Boolean = false,
)

/** One food recognized from dictation, matched to the database. */
data class VoiceItem(
    val id: Int,
    val spoken: String,
    val food: Food?,
    val grams: Float,
    val meal: MealType,
    val include: Boolean,
    /** Grams taken from the user's usual portion of this food. */
    val fromHistory: Boolean = false,
) {
    val kcal: Float get() = food?.let { it.kcalPer100 * grams / 100f } ?: 0f
}

@OptIn(FlowPreview::class)
@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    settingsRepository: UserSettingsRepository,
    private val aiFiller: AiFiller,
) : ViewModel() {

    val aiAvailable: Boolean get() = aiFiller.enabled

    /**
     * "Zwei Eier und ein Toast mit Butter" → items with grams, matched to
     * own foods first, then Open Food Facts; shown for review before logging.
     */
    fun voiceLog(text: String) {
        _state.value = _state.value.copy(voiceItems = emptyList(), voiceBusy = true, voiceOffline = false)
        viewModelScope.launch {
            // KI first; without it (off, no key, no network) simple sentences are parsed offline.
            val fromAi = if (aiFiller.enabled) aiFiller.foods(text) else null
            val offline = fromAi == null
            val phrases = fromAi ?: FoodPhraseParser.parse(text)
            if (phrases.isEmpty()) {
                _state.value = _state.value.copy(voiceItems = null, voiceBusy = false)
                _messages.send("Nichts erkannt – bitte manuell suchen.")
                return@launch
            }
            val items = phrases.mapIndexed { i, p ->
                val food = foodRepository.searchLocal(p.name).firstOrNull()
                    ?: withTimeoutOrNull(if (offline) 2_500 else 6_000) {
                        runCatching { foodRepository.searchOnline(p.name).firstOrNull() }.getOrNull()
                    }
                // Usual portion from the own history when nothing (or one piece) was said.
                val (grams, fromHistory) = food?.let { PortionMath.adjust(p, foodRepository.recentAmounts(it.id)) }
                    ?: (p.grams to false)
                VoiceItem(
                    id = i,
                    spoken = p.name,
                    food = food,
                    grams = grams,
                    meal = p.meal?.let { runCatching { MealType.valueOf(it) }.getOrNull() } ?: meal,
                    include = food != null,
                    fromHistory = fromHistory,
                )
            }
            _state.value = _state.value.copy(voiceItems = items, voiceBusy = false, voiceOffline = offline)
        }
    }

    fun toggleVoiceItem(id: Int) = updateVoice(id) { it.copy(include = !it.include && it.food != null) }
    fun setVoiceGrams(id: Int, grams: Float) = updateVoice(id) { it.copy(grams = grams.coerceIn(1f, 3000f)) }
    fun dismissVoice() {
        _state.value = _state.value.copy(voiceItems = null, voiceBusy = false)
    }

    private fun updateVoice(id: Int, block: (VoiceItem) -> VoiceItem) {
        val items = _state.value.voiceItems ?: return
        _state.value = _state.value.copy(voiceItems = items.map { if (it.id == id) block(it) else it })
    }

    fun logVoice(onLogged: () -> Unit = {}) {
        val items = _state.value.voiceItems.orEmpty().filter { it.include && it.food != null }
        if (items.isEmpty()) return
        viewModelScope.launch {
            items.forEach { item ->
                foodRepository.logFood(item.food!!, item.grams, item.grams, "g", item.meal, date)
            }
            _state.value = _state.value.copy(voiceItems = null)
            _messages.send("${items.size} ${if (items.size == 1) "Eintrag" else "Einträge"} hinzugefügt")
            onLogged()
        }
    }

    private val _state = MutableStateFlow(AddFoodState())
    val state: StateFlow<AddFoodState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    var meal: MealType = defaultMealType()
        private set
    var date: String = LocalDate.now().toString()
        private set

    val mealNames: StateFlow<Map<String, String>> = settingsRepository.settings.map { it.mealNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val favorites: StateFlow<List<Food>> = foodRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<Food>> = foodRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Only recipes with known nutrients can be logged (Saffron imports have free-text ingredients).
    val recipes: StateFlow<List<RecipeDetail>> = combine(recipeRepository.observeRecipes(), _state) { all, s ->
        val list = all.filter { it.hasNutrition }
        if (s.query.isBlank()) list else list.filter { it.recipe.title.contains(s.query.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    private var initialized = false

    fun init(mealName: String, dateIso: String) {
        if (initialized) return
        initialized = true
        runCatching { MealType.valueOf(mealName) }.getOrNull()?.let { meal = it }
        if (dateIso.isNotBlank()) date = dateIso
        viewModelScope.launch { foodRepository.ensureSeeded() }
    }

    /** Local results immediately, Open Food Facts after a short pause in typing. */
    fun onQuery(value: String) {
        _state.value = _state.value.copy(query = value, onlineFailed = false)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val local = foodRepository.searchLocal(value)
            _state.value = _state.value.copy(local = local, online = emptyList())
            if (value.trim().length < 3) return@launch
            delay(450)
            _state.value = _state.value.copy(onlineLoading = true)
            val online = runCatching { foodRepository.searchOnline(value) }.getOrElse { emptyList() }
            val known = local.map { it.id }.toSet()
            _state.value = _state.value.copy(
                online = online.filter { it.id !in known },
                onlineLoading = false,
                onlineFailed = online.isEmpty(),
            )
        }
    }

    fun select(food: Food?) { _state.value = _state.value.copy(selected = food) }
    fun selectRecipe(recipe: RecipeDetail?) { _state.value = _state.value.copy(selectedRecipe = recipe) }

    fun onBarcode(code: String) {
        _state.value = _state.value.copy(barcodeLoading = true)
        viewModelScope.launch {
            val food = runCatching { foodRepository.lookupBarcode(code) }.getOrNull()
            _state.value = _state.value.copy(barcodeLoading = false, selected = food)
            if (food == null) _messages.send("Kein Produkt zu $code gefunden. Nutze den Schnelleintrag.")
        }
    }

    /** Scanner could not start (e.g. Google Play services module still downloading). */
    fun onScanUnavailable() {
        viewModelScope.launch {
            _messages.send("Scanner nicht verfügbar. Bitte kurz warten oder den Barcode eintippen.")
        }
    }

    fun toggleFavorite(food: Food, favorite: Boolean) {
        viewModelScope.launch { foodRepository.setFavorite(food, favorite) }
    }

    fun log(
        food: Food,
        grams: Float,
        quantity: Float,
        unit: String,
        mealType: MealType,
        onLogged: () -> Unit = {},
    ) {
        meal = mealType
        viewModelScope.launch {
            foodRepository.logFood(food, grams, quantity, unit, mealType, date)
            _state.value = _state.value.copy(selected = null)
            _messages.send("${food.name} hinzugefügt")
            onLogged()
        }
    }

    fun logRecipe(detail: RecipeDetail, portions: Float, mealType: MealType, onLogged: () -> Unit = {}) {
        meal = mealType
        viewModelScope.launch {
            recipeRepository.logPortions(detail, portions, mealType, date)
            _state.value = _state.value.copy(selectedRecipe = null)
            _messages.send("${detail.recipe.title} hinzugefügt")
            onLogged()
        }
    }

    // ---- Quick entry ---------------------------------------------------------

    fun updateQuick(block: QuickEntry.() -> QuickEntry) {
        _state.value = _state.value.copy(quick = _state.value.quick.block().copy(error = null))
    }

    fun saveQuick(mealType: MealType, onLogged: () -> Unit = {}) {
        val q = _state.value.quick
        val name = q.name.trim()
        val kcal = q.kcal.parseAmount()
        if (name.isEmpty()) return setQuickError("Bitte einen Namen eingeben.")
        if (kcal == null) return setQuickError("Bitte Kalorien angeben (z. B. 250).")
        val grams = q.amount.parseAmount()?.takeIf { it > 0f } ?: 100f
        val protein = q.protein.parseAmount() ?: 0f
        val carbs = q.carbs.parseAmount() ?: 0f
        val fat = q.fat.parseAmount() ?: 0f
        // Values refer to the entered amount; the food stores them per 100 g.
        val food = Food(
            id = if (q.saveAsFood) newUuid() else "quick-${newUuid()}",
            name = name,
            kcalPer100 = kcal * 100f / grams,
            proteinPer100 = protein * 100f / grams,
            carbsPer100 = carbs * 100f / grams,
            fatPer100 = fat * 100f / grams,
        )
        meal = mealType
        viewModelScope.launch {
            if (q.saveAsFood) {
                foodRepository.logFood(food, grams, grams, "g", mealType, date)
            } else {
                // One-off entry: log without adding it to the food database.
                foodRepository.logQuick(food, grams, mealType, date)
            }
            _state.value = _state.value.copy(quick = QuickEntry())
            _messages.send("$name hinzugefügt")
            onLogged()
        }
    }

    private fun setQuickError(message: String) {
        _state.value = _state.value.copy(quick = _state.value.quick.copy(error = message))
    }
}
