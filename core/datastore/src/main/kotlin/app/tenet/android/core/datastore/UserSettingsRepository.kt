package app.tenet.android.core.datastore

import android.content.Context
import app.tenet.android.core.common.ActivityLevel
import app.tenet.android.core.common.BodyProfile
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.Sex
import app.tenet.android.core.common.WeightGoal
import app.tenet.android.core.common.PaceMethod
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tenetDataStore: DataStore<Preferences> by preferencesDataStore(name = "tenet_settings")

@Singleton
class UserSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<UserSettings> = context.tenetDataStore.data.map { prefs ->
        UserSettings(
            loaded = true,
            // Installs from before the welcome screen already have settings: skip it there.
            onboardingDone = prefs[KEY_ONBOARDING] ?: prefs.asMap().isNotEmpty(),
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            colorStyle = app.tenet.android.core.common.ColorStyle.fromName(prefs[KEY_COLOR_STYLE]),
            // Default: the whole app in primary + secondary only.
            twoTone = prefs[KEY_TWO_TONE] ?: true,
            glassBar = prefs[KEY_GLASS_BAR] ?: true,
            washStrength = prefs[KEY_WASH_STRENGTH] ?: 1f,
            companion = prefs[KEY_COMPANION] ?: true,
            calendarRead = prefs[KEY_CALENDAR_READ] ?: false,
            calendarWriteId = prefs[KEY_CALENDAR_WRITE]?.takeIf { it >= 0 },
            companionKind = prefs[KEY_COMPANION_KIND] ?: "TENNY",
            companionNames = prefs.asMap().entries.mapNotNull { (key, value) ->
                if (key.name.startsWith("pet_name_") && value is String && value.isNotBlank())
                    key.name.removePrefix("pet_name_") to value else null
            }.toMap(),
            designStyle = app.tenet.android.core.common.DesignStyle.fromName(prefs[KEY_DESIGN_STYLE]),
            amoledMode = app.tenet.android.core.common.AmoledMode.fromName(prefs[KEY_AMOLED]),
            journalSerif = prefs[KEY_JOURNAL_SERIF] ?: false,
            enabledModules = prefs[KEY_MODULES]
                ?.mapNotNull { runCatching { AppModule.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: AppModule.entries.toSet(),
            sportDisciplines = prefs[KEY_SPORT_DISCIPLINES]?.takeIf { it.isNotEmpty() } ?: setOf("GYM", "CALISTHENICS", "RUNNING"),
            pendingSportSetups = prefs[KEY_PENDING_SPORT_SETUPS].orEmpty().split(',').filter { it.isNotBlank() },
            oneRepMaxFormula = prefs[KEY_ONE_REP_MAX_FORMULA]
                ?.let { runCatching { OneRepMaxFormula.valueOf(it) }.getOrNull() }
                ?: OneRepMaxFormula.EPLEY,
            paceMethod = prefs[KEY_PACE_METHOD]
                ?.let { PaceMethod.fromId(it) }
                ?: PaceMethod.VDOT,
            primaryColor = prefs[KEY_PRIMARY_COLOR],
            secondaryColor = prefs[KEY_SECONDARY_COLOR],
            dreamReminder = prefs[KEY_DREAM_REMINDER] ?: false,
            dreamReminderMinute = prefs[KEY_DREAM_REMINDER_MINUTE] ?: (7 * 60 + 30),
            realityChecks = prefs[KEY_REALITY_CHECKS] ?: false,
            trainingReminder = prefs[KEY_TRAINING_REMINDER] ?: false,
            trainingReminderMinute = prefs[KEY_TRAINING_REMINDER_MINUTE] ?: (7 * 60),
            readinessReport = prefs[KEY_READINESS] ?: false,
            readinessReportMinute = prefs[KEY_READINESS_MINUTE] ?: (7 * 60 + 30),
            readinessDismissedDate = prefs[KEY_READINESS_DISMISSED],
            mealNames = MEAL_KEYS.mapNotNull { (meal, key) -> prefs[key]?.let { meal to it } }.toMap(),
            waterGoalMl = prefs[KEY_WATER_GOAL] ?: 2500,
            journalLock = prefs[KEY_JOURNAL_LOCK] ?: false,
            sportSetupDone = prefs[KEY_SPORT_SETUP] ?: emptySet(),
            profile = prefs[KEY_PROFILE_WEIGHT]?.let { weight ->
                BodyProfile(
                    sex = prefs[KEY_PROFILE_SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: Sex.MALE,
                    age = prefs[KEY_PROFILE_AGE] ?: 30,
                    heightCm = prefs[KEY_PROFILE_HEIGHT] ?: 178f,
                    weightKg = weight,
                    activity = prefs[KEY_PROFILE_ACTIVITY]
                        ?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: ActivityLevel.MODERATE,
                    goal = prefs[KEY_PROFILE_GOAL]
                        ?.let { runCatching { WeightGoal.valueOf(it) }.getOrNull() } ?: WeightGoal.MAINTAIN,
                )
            },
            todayCardOrder = prefs[KEY_TODAY_ORDER]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            todayHiddenCards = prefs[KEY_TODAY_HIDDEN] ?: emptySet(),
            lastWeightPromptDate = prefs[KEY_LAST_WEIGHT_PROMPT_DATE],
        )
    }

    /** Shows or hides one card on "Heute" (Einstellungen → Heute). */
    suspend fun setTodayCardHidden(id: String, hidden: Boolean) {
        context.tenetDataStore.edit {
            val now = it[KEY_TODAY_HIDDEN] ?: emptySet()
            it[KEY_TODAY_HIDDEN] = if (hidden) now + id else now - id
        }
    }

    suspend fun setTodayCards(order: List<String>, hidden: Set<String>) {
        context.tenetDataStore.edit {
            it[KEY_TODAY_ORDER] = order.joinToString(",")
            it[KEY_TODAY_HIDDEN] = hidden
        }
    }

    suspend fun setOneRepMaxFormula(formula: OneRepMaxFormula) {
        context.tenetDataStore.edit { it[KEY_ONE_REP_MAX_FORMULA] = formula.name }
    }

    suspend fun setPaceMethod(method: PaceMethod) {
        context.tenetDataStore.edit { it[KEY_PACE_METHOD] = method.id }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.tenetDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setTwoTone(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_TWO_TONE] = enabled }
    }

    suspend fun setColorStyle(style: app.tenet.android.core.common.ColorStyle) {
        context.tenetDataStore.edit { it[KEY_COLOR_STYLE] = style.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    /**
     * Sets (or with null resets) a seed color. Picking a custom color turns
     * dynamic color off, otherwise the wallpaper would hide the choice.
     */
    suspend fun setThemeColors(primary: Int?, secondary: Int?) {
        context.tenetDataStore.edit { prefs ->
            if (primary != null) prefs[KEY_PRIMARY_COLOR] = primary else prefs.remove(KEY_PRIMARY_COLOR)
            if (secondary != null) prefs[KEY_SECONDARY_COLOR] = secondary else prefs.remove(KEY_SECONDARY_COLOR)
            if (primary != null || secondary != null) prefs[KEY_DYNAMIC_COLOR] = false
        }
    }

    suspend fun setDreamReminder(enabled: Boolean, minuteOfDay: Int) {
        context.tenetDataStore.edit {
            it[KEY_DREAM_REMINDER] = enabled
            it[KEY_DREAM_REMINDER_MINUTE] = minuteOfDay
        }
    }

    /** Marks the first-run setup of a discipline as done (or skipped). */
    suspend fun markSportSetupDone(discipline: String) {
        context.tenetDataStore.edit { it[KEY_SPORT_SETUP] = (it[KEY_SPORT_SETUP] ?: emptySet()) + discipline }
    }

    suspend fun setJournalLock(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_JOURNAL_LOCK] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean = true) {
        context.tenetDataStore.edit { it[KEY_ONBOARDING] = done }
    }

    suspend fun setTrainingReminder(enabled: Boolean, minuteOfDay: Int) {
        context.tenetDataStore.edit {
            it[KEY_TRAINING_REMINDER] = enabled
            it[KEY_TRAINING_REMINDER_MINUTE] = minuteOfDay
        }
    }

    suspend fun setReadinessReport(enabled: Boolean, minuteOfDay: Int) {
        context.tenetDataStore.edit {
            it[KEY_READINESS] = enabled
            it[KEY_READINESS_MINUTE] = minuteOfDay
        }
    }

    suspend fun setReadinessDismissed(date: String) {
        context.tenetDataStore.edit { it[KEY_READINESS_DISMISSED] = date }
    }

    suspend fun setRealityChecks(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_REALITY_CHECKS] = enabled }
    }

    /** Renames a meal section; blank resets it to the default name. */
    suspend fun setMealName(meal: String, name: String) {
        val key = MEAL_KEYS.firstOrNull { it.first == meal }?.second ?: return
        context.tenetDataStore.edit { if (name.isBlank()) it.remove(key) else it[key] = name.trim() }
    }

    suspend fun setWaterGoal(ml: Int) {
        context.tenetDataStore.edit { it[KEY_WATER_GOAL] = ml.coerceIn(500, 6000) }
    }

    suspend fun setProfile(profile: BodyProfile) {
        context.tenetDataStore.edit {
            it[KEY_PROFILE_SEX] = profile.sex.name
            it[KEY_PROFILE_AGE] = profile.age
            it[KEY_PROFILE_HEIGHT] = profile.heightCm
            it[KEY_PROFILE_WEIGHT] = profile.weightKg
            it[KEY_PROFILE_ACTIVITY] = profile.activity.name
            it[KEY_PROFILE_GOAL] = profile.goal.name
        }
    }

    suspend fun markWeightPromptHandled(date: String) {
        context.tenetDataStore.edit { it[KEY_LAST_WEIGHT_PROMPT_DATE] = date }
    }

    suspend fun setDesignStyle(style: app.tenet.android.core.common.DesignStyle) {
        context.tenetDataStore.edit { it[KEY_DESIGN_STYLE] = style.name }
    }

    suspend fun setAmoledMode(mode: app.tenet.android.core.common.AmoledMode) {
        context.tenetDataStore.edit { it[KEY_AMOLED] = mode.name }
    }

    suspend fun setJournalSerif(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_JOURNAL_SERIF] = enabled }
    }

    suspend fun setWashStrength(value: Float) {
        context.tenetDataStore.edit { it[KEY_WASH_STRENGTH] = value.coerceIn(0f, 1.6f) }
    }

    suspend fun setGlassBar(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_GLASS_BAR] = enabled }
    }

    suspend fun setCalendarRead(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_CALENDAR_READ] = enabled }
    }

    suspend fun setCalendarWrite(calendarId: Long?) {
        context.tenetDataStore.edit { it[KEY_CALENDAR_WRITE] = calendarId ?: -1L }
    }

    suspend fun setCompanion(enabled: Boolean) {
        context.tenetDataStore.edit { it[KEY_COMPANION] = enabled }
    }

    suspend fun setCompanionName(kind: String, name: String) {
        context.tenetDataStore.edit { prefs ->
            val key = stringPreferencesKey("pet_name_$kind")
            val cleaned = name.trim().take(40)
            if (cleaned.isEmpty()) prefs.remove(key) else prefs[key] = cleaned
        }
    }

    suspend fun setCompanionKind(kind: String) {
        context.tenetDataStore.edit { it[KEY_COMPANION_KIND] = kind }
    }

    /** At least one sport stays on. */
    suspend fun setSportDiscipline(discipline: String, enabled: Boolean) {
        context.tenetDataStore.edit { prefs ->
            val current = prefs[KEY_SPORT_DISCIPLINES]?.takeIf { it.isNotEmpty() } ?: setOf("GYM", "CALISTHENICS", "RUNNING")
            val next = if (enabled) current + discipline else current - discipline
            if (next.isNotEmpty()) prefs[KEY_SPORT_DISCIPLINES] = next
        }
    }

    suspend fun setPendingSportSetups(disciplines: List<String>) {
        context.tenetDataStore.edit { it[KEY_PENDING_SPORT_SETUPS] = disciplines.joinToString(",") }
    }

    suspend fun sportSetupHandled(discipline: String) {
        context.tenetDataStore.edit { prefs ->
            prefs[KEY_PENDING_SPORT_SETUPS] = prefs[KEY_PENDING_SPORT_SETUPS].orEmpty().split(',').filter { it.isNotBlank() && it != discipline }.joinToString(",")
        }
    }

    suspend fun setModuleEnabled(module: AppModule, enabled: Boolean) {
        context.tenetDataStore.edit { prefs ->
            val current = prefs[KEY_MODULES] ?: AppModule.entries.map { it.name }.toSet()
            prefs[KEY_MODULES] = if (enabled) current + module.name else current - module.name
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_COLOR_STYLE = stringPreferencesKey("color_style")
        val KEY_TWO_TONE = booleanPreferencesKey("two_tone")
        val KEY_GLASS_BAR = booleanPreferencesKey("glass_bar")
        val KEY_WASH_STRENGTH = androidx.datastore.preferences.core.floatPreferencesKey("wash_strength")
        val KEY_COMPANION = booleanPreferencesKey("companion")
        val KEY_CALENDAR_READ = booleanPreferencesKey("calendar_read")
        val KEY_CALENDAR_WRITE = androidx.datastore.preferences.core.longPreferencesKey("calendar_write_id")
        val KEY_COMPANION_KIND = stringPreferencesKey("companion_kind")
        val KEY_SPORT_DISCIPLINES = stringSetPreferencesKey("sport_disciplines")
        val KEY_PENDING_SPORT_SETUPS = stringPreferencesKey("pending_sport_setups")
        val KEY_AMOLED = stringPreferencesKey("amoled_mode")
        val KEY_DESIGN_STYLE = stringPreferencesKey("design_style")
        val KEY_JOURNAL_SERIF = booleanPreferencesKey("journal_serif")
        val KEY_MODULES = stringSetPreferencesKey("enabled_modules")
        val KEY_ONE_REP_MAX_FORMULA = stringPreferencesKey("one_rep_max_formula")
        val KEY_PACE_METHOD = stringPreferencesKey("pace_method")
        val KEY_PRIMARY_COLOR = intPreferencesKey("primary_color")
        val KEY_SECONDARY_COLOR = intPreferencesKey("secondary_color")
        val KEY_DREAM_REMINDER = booleanPreferencesKey("dream_reminder")
        val KEY_DREAM_REMINDER_MINUTE = intPreferencesKey("dream_reminder_minute")
        val KEY_REALITY_CHECKS = booleanPreferencesKey("reality_checks")
        val KEY_TRAINING_REMINDER = booleanPreferencesKey("training_reminder")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        val KEY_TRAINING_REMINDER_MINUTE = intPreferencesKey("training_reminder_minute")
        val KEY_READINESS = booleanPreferencesKey("readiness_report")
        val KEY_READINESS_MINUTE = intPreferencesKey("readiness_report_minute")
        val KEY_READINESS_DISMISSED = stringPreferencesKey("readiness_dismissed")
        val KEY_WATER_GOAL = intPreferencesKey("water_goal_ml")
        val KEY_JOURNAL_LOCK = booleanPreferencesKey("journal_lock")
        val KEY_SPORT_SETUP = stringSetPreferencesKey("sport_setup_done")
        val KEY_TODAY_ORDER = stringPreferencesKey("today_card_order")
        val KEY_TODAY_HIDDEN = stringSetPreferencesKey("today_hidden_cards")
        val KEY_PROFILE_SEX = stringPreferencesKey("profile_sex")
        val KEY_PROFILE_AGE = intPreferencesKey("profile_age")
        val KEY_PROFILE_HEIGHT = floatPreferencesKey("profile_height_cm")
        val KEY_PROFILE_WEIGHT = floatPreferencesKey("profile_weight_kg")
        val KEY_PROFILE_ACTIVITY = stringPreferencesKey("profile_activity")
        val KEY_PROFILE_GOAL = stringPreferencesKey("profile_goal")
        val KEY_LAST_WEIGHT_PROMPT_DATE = stringPreferencesKey("last_weight_prompt_date")
        val MEAL_KEYS = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")
            .map { it to stringPreferencesKey("meal_name_$it") }
    }
}
