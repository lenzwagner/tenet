package app.tenet.android.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.BodyProfile
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.data.NutritionTotals
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.data.RecipeRepository
import app.tenet.android.core.database.dao.DayTotals
import app.tenet.android.core.database.entity.DailyGoal
import app.tenet.android.core.database.entity.FoodLog
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NutritionUiState(
    val date: LocalDate = LocalDate.now(),
    val logs: List<FoodLog> = emptyList(),
    val totals: NutritionTotals = NutritionTotals(),
    val goal: DailyGoal = FoodRepository.defaultGoal,
    val waterMl: Int = 0,
    val waterGoalMl: Int = 2500,
    val mealNames: Map<String, String> = emptyMap(),
    /** Last 7 days up to [date] (days without logs are missing). */
    val week: List<DayTotals> = emptyList(),
    val profile: BodyProfile? = null,
    val loading: Boolean = true,
) {
    val isToday: Boolean get() = date == LocalDate.now()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    private val settingsRepository: UserSettingsRepository,
    private val cloudSync: app.tenet.android.core.data.sync.CloudSync,
    accounts: app.tenet.android.core.data.sync.AccountRepository,
) : ViewModel() {

    /** Signed in with Google: recipes from Saffron come along. */
    val signedIn: StateFlow<Boolean> = accounts.account.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), accounts.account.value != null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Pull to refresh on the recipes page: sync incl. the Saffron import. */
    fun refreshRecipes() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            cloudSync.sync()
                .onSuccess { _messages.send("Rezepte synchronisiert") }
                .onFailure { _messages.send(it.message ?: "Aktualisieren fehlgeschlagen") }
            _refreshing.value = false
        }
    }

    private val date = MutableStateFlow(LocalDate.now())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val day = date.flatMapLatest { d ->
        val iso = d.toString()
        combine(
            foodRepository.observeLogs(iso),
            foodRepository.totals(iso),
            foodRepository.goal(iso),
            foodRepository.observeWater(iso),
            // Last 30 days: the evaluation card shows 7 or 30 of them.
            foodRepository.observeDailyTotals(d.minusDays(29).toString(), iso),
        ) { logs, totals, goal, water, week -> DayData(d, logs, totals, goal, water, week) }
    }

    private data class DayData(
        val date: LocalDate,
        val logs: List<FoodLog>,
        val totals: NutritionTotals,
        val goal: DailyGoal,
        val water: Int,
        val week: List<DayTotals>,
    )

    val uiState: StateFlow<NutritionUiState> = combine(day, settingsRepository.settings) { d, settings ->
        NutritionUiState(
            date = d.date,
            logs = d.logs,
            totals = d.totals,
            goal = d.goal,
            waterMl = d.water,
            waterGoalMl = settings.waterGoalMl,
            mealNames = settings.mealNames,
            week = d.week,
            profile = settings.profile,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState())

    val recipes: StateFlow<List<RecipeDetail>> = recipeRepository.observeRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { foodRepository.ensureSeeded() }
    }

    /** Multi-select on the recipes page (own recipes only; the browser leaves Saffron ones out). */
    fun deleteRecipes(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { recipeRepository.delete(it) }
            _messages.send(if (ids.size == 1) "Rezept gelöscht" else "${ids.size} Rezepte gelöscht")
        }
    }

    /** Day the tracker last treated as "today" (to follow midnight rollovers). */
    private var knownToday = LocalDate.now()

    /** Called on resume: if the user was looking at "today", move along past midnight. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (now != knownToday) {
            if (date.value == knownToday) date.value = now
            knownToday = now
        }
    }

    fun previousDay() { date.value = date.value.minusDays(1) }
    fun nextDay() { if (date.value < LocalDate.now()) date.value = date.value.plusDays(1) }
    fun goToday() { date.value = LocalDate.now() }
    fun goTo(day: LocalDate) { date.value = minOf(day, LocalDate.now()) }

    fun deleteLog(id: String) {
        viewModelScope.launch { foodRepository.deleteLog(id) }
    }

    /** Undo: puts a deleted log back. */
    fun restoreLog(log: FoodLog) {
        viewModelScope.launch { foodRepository.addLog(log) }
    }

    /** "Gestern kopieren": the previous day's meals onto the shown day. */
    fun copyPreviousDay() {
        val target = date.value
        viewModelScope.launch {
            val count = foodRepository.copyDay(target.minusDays(1).toString(), target.toString())
            _messages.send(if (count == 0) "Am Vortag wurde nichts geloggt." else "$count Einträge vom Vortag übernommen.")
        }
    }

    fun addWater(deltaMl: Int) {
        val s = uiState.value
        viewModelScope.launch { foodRepository.setWater(s.date.toString(), s.waterMl + deltaMl) }
    }

    fun setWaterGoal(ml: Int) {
        viewModelScope.launch { settingsRepository.setWaterGoal(ml) }
    }

    fun renameMeal(meal: String, name: String) {
        viewModelScope.launch { settingsRepository.setMealName(meal, name) }
    }

    fun saveProfile(profile: BodyProfile) {
        viewModelScope.launch { settingsRepository.setProfile(profile) }
    }

    fun setGoal(kcal: Float, protein: Float, carbs: Float, fat: Float) {
        viewModelScope.launch {
            foodRepository.setGoal(
                DailyGoal(
                    dateFrom = LocalDate.now().toString(),
                    kcal = kcal,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                ),
            )
        }
    }
}
