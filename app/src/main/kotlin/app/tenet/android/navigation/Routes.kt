package app.tenet.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose).
@Serializable data object TodayRoute
@Serializable data object SportRoute
@Serializable data object JournalRoute
@Serializable data object NutritionRoute
@Serializable data object SettingsRoute

@Serializable
data class EntryEditorRoute(
    /** Empty string means "new entry". */
    val entryId: String = "",
    val type: String = "NOTE",
    /** Prefilled title for new entries (e.g. from a `[[link]]`). */
    val title: String = "",
    /** ISO date for new entries (e.g. from the mood calendar); empty = today. */
    val date: String = "",
    /** Start speech input right away ("Traum erzählen" on Heute). */
    val dictate: Boolean = false,
    /** Start action for new entries: list, audio, image or dictate (FAB menu). */
    val start: String = "",
)

/** A new entry: opens as a sheet over the current page (same fields as [EntryEditorRoute]). */
@Serializable
data class NewEntryRoute(
    val type: String = "NOTE",
    val title: String = "",
    val date: String = "",
    val dictate: Boolean = false,
    val start: String = "",
)

@Serializable data object SearchRoute
@Serializable data object HealthRoute

@Serializable data object ExerciseLibraryRoute

@Serializable data object RoutineEditorRoute

@Serializable
data class AddFoodRoute(
    /** MealType name; empty = by time of day. */
    val meal: String = "",
    /** ISO date to log to; empty = today. */
    val date: String = "",
)

@Serializable data class RecipeDetailRoute(val recipeId: String)

@Serializable data class RecipeEditorRoute(val recipeId: String = "")

/** Title, free-text ingredients and steps of an imported/Saffron recipe. */
@Serializable data class RecipeTextEditRoute(val recipeId: String)

/** Recipe import (link or shared text), like in Saffron. */
@Serializable data class RecipeImportRoute(val url: String = "")

@Serializable data class CookingRoute(val recipeId: String, val servings: Int)

/** Makro-Optimierer for [date] (ISO; empty = today). */
@Serializable data class MacroOptimizerRoute(val date: String = "")

@Serializable
data class ActiveSessionRoute(
    val sessionId: String = "",
)

@Serializable
data class SkillSessionRoute(
    val sessionId: String = "",
)

@Serializable data object WeekCalendarRoute

@Serializable
data class CsWorkoutRoute(
    val sessionId: String = "",
)

/** Live GPS run; [plannedWorkoutId] empty = today's planned run, if any. */
@Serializable
data class ActiveRunRoute(
    val plannedWorkoutId: String = "",
)

@Serializable
data class RunPlanDetailRoute(val planId: String = "")

@Serializable
data class GymPlanDetailRoute(val planId: String = "")

@Serializable
data object CaliPlanDetailRoute

/** Workout summary / detail of a finished gym session; [fresh] right after finishing. */
@Serializable
data class WorkoutSummaryRoute(val sessionId: String = "", val fresh: Boolean = false)

/** All finished gym workouts. */
@Serializable data object WorkoutHistoryRoute

/** Records and history of one exercise. */
@Serializable
data class ExerciseDetailRoute(val exerciseId: String = "")

/** One planned run of the running plan (structure, purpose, start). */
@Serializable
data class RunWorkoutRoute(val plannedId: String = "")

/** First-run setup wizard of a discipline: GYM, CALISTHENICS or RUNNING. */
@Serializable
data class SportSetupRoute(
    val discipline: String = "GYM",
    /** Gym only: seed strength values from completed training sets. */
    val useTrainingHistory: Boolean = false,
)

/** Progression slides; [page] 0 = Gym, 1 = Calisthenics, 2 = Laufen. */
@Serializable
data class ProgressionRoute(val page: Int = 0)

@Serializable
data class RunDetailRoute(
    val sessionId: String = "",
)

/**
 * Top-level destinations of the floating tab bar (App_Konzept.md 2.1).
 * Modules can be disabled in the settings; Settings is always present.
 */
sealed class TopLevelTab(
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val route: Any,
    val routePattern: String,
) {
    data object Today : TopLevelTab(
        label = "Heute",
        filledIcon = Icons.Filled.Today,
        outlinedIcon = Icons.Outlined.Today,
        route = TodayRoute,
        routePattern = TodayRoute::class.qualifiedName.orEmpty(),
    )

    data object Sport : TopLevelTab(
        label = "Sport",
        filledIcon = Icons.Filled.FitnessCenter,
        outlinedIcon = Icons.Outlined.FitnessCenter,
        route = SportRoute,
        routePattern = SportRoute::class.qualifiedName.orEmpty(),
    )

    data object Journal : TopLevelTab(
        label = "Journal",
        filledIcon = Icons.Filled.AutoStories,
        outlinedIcon = Icons.Outlined.AutoStories,
        route = JournalRoute,
        routePattern = JournalRoute::class.qualifiedName.orEmpty(),
    )

    data object Nutrition : TopLevelTab(
        label = "Ernährung",
        filledIcon = Icons.Filled.Restaurant,
        outlinedIcon = Icons.Outlined.Restaurant,
        route = NutritionRoute,
        routePattern = NutritionRoute::class.qualifiedName.orEmpty(),
    )

    data object Settings : TopLevelTab(
        label = "Optionen",
        filledIcon = Icons.Filled.Settings,
        outlinedIcon = Icons.Outlined.Settings,
        route = SettingsRoute,
        routePattern = SettingsRoute::class.qualifiedName.orEmpty(),
    )
}

val TopLevelTabs: List<TopLevelTab> = listOf(
    TopLevelTab.Today,
    TopLevelTab.Sport,
    TopLevelTab.Journal,
    TopLevelTab.Nutrition,
    TopLevelTab.Settings,
)
