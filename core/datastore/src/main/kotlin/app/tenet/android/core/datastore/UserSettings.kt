package app.tenet.android.core.datastore

import app.tenet.android.core.common.BodyProfile
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.PaceMethod

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Modules that can be switched off in the settings (App_Konzept.md 1: "Modular"). */
enum class AppModule { TODAY, SPORT, JOURNAL, NUTRITION }

data class UserSettings(
    /** False until DataStore delivered the stored values (avoids flashing the welcome screen). */
    val loaded: Boolean = false,
    /** Welcome screen (Google or guest, permissions) finished. */
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Palette style for the scheme built from wallpaper or picked seed. */
    val colorStyle: app.tenet.android.core.common.ColorStyle = app.tenet.android.core.common.ColorStyle.DEFAULT,
    /** Only primary + secondary: no own color per area, no tertiary accents. */
    val twoTone: Boolean = true,
    /** Glassmorphism tab bar; false = opaque bar (fallback / accessibility). */
    val glassBar: Boolean = true,
    val enabledModules: Set<AppModule> = AppModule.entries.toSet(),
    /** 1RM estimation formula used for progress displays (App_Konzept.md 5.2.1). */
    val oneRepMaxFormula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    /** Target-pace method for running plans (App_Konzept.md 5.2.3). */
    val paceMethod: PaceMethod = PaceMethod.VDOT,
    /**
     * App-wide seed colors as ARGB; null = default petrol theme. Only used
     * while [dynamicColor] is off (wallpaper colors take precedence).
     */
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    /** Morning "Hast du geträumt?" reminder (App_Konzept.md 5.3). */
    val dreamReminder: Boolean = false,
    /** Minute of day for [dreamReminder], default 07:30. */
    val dreamReminderMinute: Int = 7 * 60 + 30,
    /** Daytime reality checks for lucid-dream training. */
    val realityChecks: Boolean = false,
    /** Morning reminder on training days ("Heute: Intervalle 3 × 1000 m"). */
    val trainingReminder: Boolean = false,
    val trainingReminderMinute: Int = 7 * 60,
    /** Custom meal section names keyed by MealType name (App_Konzept.md 5.4 "umbenennbar"). */
    val mealNames: Map<String, String> = emptyMap(),
    /** Daily water goal in ml. */
    val waterGoalMl: Int = 2500,
    /** Journal behind fingerprint / device lock. */
    val journalLock: Boolean = false,
    /** Disciplines whose first-run setup is done or skipped (GYM, CALISTHENICS, RUNNING). */
    val sportSetupDone: Set<String> = emptySet(),
    /** Body data for the calorie calculator; null until entered. */
    val profile: BodyProfile? = null,
    /** Order of the "Heute" dashboard cards (card ids); empty = default order. */
    val todayCardOrder: List<String> = emptyList(),
    /** Card ids hidden on "Heute". */
    val todayHiddenCards: Set<String> = emptySet(),
)
