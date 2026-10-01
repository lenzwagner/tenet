package app.tenet.android.feature.settings

import app.tenet.android.core.data.ai.AiAssistant
import app.tenet.android.core.common.BodyProfile
import app.tenet.android.core.data.reminder.ReminderScheduler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.datastore.AppModule
import app.tenet.android.core.datastore.ThemeMode
import app.tenet.android.core.datastore.UserSettings
import app.tenet.android.core.datastore.UserSettingsRepository
import app.tenet.android.core.database.entity.DailyGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Context
import app.tenet.android.core.data.health.HealthConnectRepository
import app.tenet.android.core.data.health.HealthSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Also used by MainActivity to apply theme + dynamic color before the
 * settings screen is ever opened.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserSettingsRepository,
    private val foodRepository: FoodRepository,
    private val reminderScheduler: ReminderScheduler,
    private val healthConnect: HealthConnectRepository,
    private val ai: AiAssistant,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // ---- AI assistant (NVIDIA NIM) ------------------------------------------

    val aiConfig: StateFlow<AiAssistant.Config> = ai.config
    fun setAiEnabled(enabled: Boolean) = ai.setEnabled(enabled)
    fun setAiKey(key: String) = ai.setApiKey(key)
    fun setAiModel(model: String) = ai.setModel(model)

    /** Round trip; returns null on success, else the error text. */
    fun testAi(onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(if (ai.test()) null else (ai.lastError ?: "Keine Antwort")) }
    }

    // ---- Health Connect -------------------------------------------------

    val healthStatus: StateFlow<HealthConnectRepository.Status> = healthConnect.status
    fun healthAvailability(): HealthConnectRepository.Availability = healthConnect.availability()

    /** After the permission dialog: connect when the core permissions were granted. */
    fun onHealthPermissions(granted: Set<String>) {
        if (!granted.containsAll(HealthConnectRepository.REQUIRED)) return
        healthConnect.setEnabled(true)
        HealthSyncWorker.schedule(appContext, true)
        syncHealthNow()
    }

    fun disconnectHealth() {
        healthConnect.setEnabled(false)
        HealthSyncWorker.schedule(appContext, false)
    }

    fun syncHealthNow() {
        viewModelScope.launch { healthConnect.sync() }
    }

    val settings: StateFlow<UserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /** The calorie/macro goal in force today (falls back to the app default). */
    val goal: StateFlow<DailyGoal> = foodRepository.goal(LocalDate.now().toString())
        .stateIn(viewModelScope, SharingStarted.Eagerly, FoodRepository.defaultGoal)

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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setTwoTone(enabled: Boolean) {
        viewModelScope.launch { repository.setTwoTone(enabled) }
    }

    fun setColorStyle(style: app.tenet.android.core.common.ColorStyle) {
        viewModelScope.launch { repository.setColorStyle(style) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setPrimaryColor(argb: Int?) {
        viewModelScope.launch { repository.setThemeColors(argb, settings.value.secondaryColor) }
    }

    fun setSecondaryColor(argb: Int?) {
        viewModelScope.launch { repository.setThemeColors(settings.value.primaryColor, argb) }
    }

    /**
     * Pins "not done" while the setup runs: older installs count any stored
     * preference as finished onboarding, so saving the profile would end it.
     */
    fun startOnboarding() {
        viewModelScope.launch { repository.setOnboardingDone(false) }
    }

    /** Welcome screen finished (signed in or guest, permissions asked). */
    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingDone() }
    }

    fun setTrainingReminder(enabled: Boolean, minuteOfDay: Int = settings.value.trainingReminderMinute) {
        viewModelScope.launch { repository.setTrainingReminder(enabled, minuteOfDay) }
        reminderScheduler.scheduleTrainingReminder(enabled, minuteOfDay)
    }

    fun setDreamReminder(enabled: Boolean, minuteOfDay: Int = settings.value.dreamReminderMinute) {
        viewModelScope.launch { repository.setDreamReminder(enabled, minuteOfDay) }
        reminderScheduler.scheduleDreamReminder(enabled, minuteOfDay)
    }

    fun setJournalLock(enabled: Boolean) {
        viewModelScope.launch { repository.setJournalLock(enabled) }
    }

    fun setRealityChecks(enabled: Boolean) {
        viewModelScope.launch { repository.setRealityChecks(enabled) }
        reminderScheduler.scheduleRealityChecks(enabled)
    }

    fun saveProfile(profile: BodyProfile) {
        viewModelScope.launch { repository.setProfile(profile) }
    }

    fun setWaterGoal(ml: Int) {
        viewModelScope.launch { repository.setWaterGoal(ml) }
    }

    /** Onboarding: profile plus the calorie/macro goal derived from it (2 g protein, 0.9 g fat per kg). */
    fun saveProfileWithGoal(profile: BodyProfile) {
        saveProfile(profile)
        val kcal = app.tenet.android.core.common.EnergyMath.targetKcal(profile)
        val m = app.tenet.android.core.common.EnergyMath.macrosByBodyWeight(kcal, profile.weightKg, 2f, 0.9f)
        setGoal(m.kcal, m.protein, m.carbs, m.fat)
    }

    fun setGlassBar(enabled: Boolean) {
        viewModelScope.launch { repository.setGlassBar(enabled) }
    }

    fun setModuleEnabled(module: AppModule, enabled: Boolean) {
        viewModelScope.launch { repository.setModuleEnabled(module, enabled) }
    }

    fun setOneRepMaxFormula(formula: OneRepMaxFormula) {
        viewModelScope.launch { repository.setOneRepMaxFormula(formula) }
    }

    fun setPaceMethod(method: PaceMethod) {
        viewModelScope.launch { repository.setPaceMethod(method) }
    }
}
