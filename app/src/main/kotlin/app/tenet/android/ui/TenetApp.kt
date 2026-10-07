package app.tenet.android.ui


import app.tenet.android.core.designsystem.header.HeaderImage
import app.tenet.android.core.designsystem.header.SubPageWash
import androidx.compose.foundation.layout.ime
import kotlinx.coroutines.flow.first
import app.tenet.android.navigation.RecipeImportRoute
import app.tenet.android.feature.nutrition.recipe.RecipeImportScreen
import androidx.compose.runtime.collectAsState
import app.tenet.android.core.data.reminder.ReminderScheduler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.mutableFloatStateOf
import app.tenet.android.core.designsystem.component.backdropBlur
import app.tenet.android.core.designsystem.component.LocalBackdropBlur
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.dialog
import app.tenet.android.navigation.NewEntryRoute
import app.tenet.android.feature.sport.plan.GymPlanDetailScreen
import app.tenet.android.feature.sport.plan.RunPlanDetailScreen
import app.tenet.android.navigation.GymPlanDetailRoute
import app.tenet.android.navigation.ProgressionRoute
import app.tenet.android.navigation.SportSetupRoute
import app.tenet.android.navigation.RunWorkoutRoute
import app.tenet.android.navigation.WorkoutSummaryRoute
import app.tenet.android.navigation.ExerciseDetailRoute
import app.tenet.android.navigation.WorkoutHistoryRoute
import app.tenet.android.navigation.CaliPlanDetailRoute
import app.tenet.android.feature.sport.calisthenics.CaliPlanScreen
import app.tenet.android.feature.sport.gym.WorkoutHistoryScreen
import app.tenet.android.feature.sport.gym.WorkoutSummaryScreen
import app.tenet.android.feature.sport.gym.ExerciseDetailScreen
import app.tenet.android.feature.sport.run.RunWorkoutScreen
import app.tenet.android.feature.sport.setup.GymSetupScreen
import app.tenet.android.feature.sport.setup.RunSetupScreen
import app.tenet.android.feature.sport.setup.CaliSetupScreen
import app.tenet.android.feature.sport.progress.ProgressionScreen
import app.tenet.android.navigation.RunPlanDetailRoute
import androidx.navigation.NavDestination
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.dimens.TenetDimens
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import app.tenet.android.core.designsystem.component.rememberAppSnackbar
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import app.tenet.android.feature.sport.run.RunDetailScreen
import app.tenet.android.feature.sport.run.ActiveRunScreen
import app.tenet.android.navigation.RunDetailRoute
import app.tenet.android.navigation.ActiveRunRoute
import androidx.compose.ui.platform.LocalContext
import app.tenet.android.ui.lock.JournalLockGate
import app.tenet.android.ui.lock.JournalLock
import app.tenet.android.navigation.CookingRoute
import app.tenet.android.navigation.MacroOptimizerRoute
import app.tenet.android.navigation.RecipeDetailRoute
import app.tenet.android.navigation.RecipeEditorRoute
import app.tenet.android.navigation.RecipeTextEditRoute
import app.tenet.android.feature.nutrition.recipe.CookingModeScreen
import app.tenet.android.feature.nutrition.recipe.RecipeDetailScreen
import app.tenet.android.feature.nutrition.recipe.RecipeEditorScreen
import app.tenet.android.feature.nutrition.recipe.RecipeTextEditorScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.tenet.android.core.designsystem.navigation.LocalTabReselect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tenet.android.core.designsystem.theme.AppArea
import app.tenet.android.core.designsystem.theme.AreaTheme
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.datastore.AppModule
import app.tenet.android.core.datastore.UserSettings
import app.tenet.android.feature.journal.EntryEditorScreen
import app.tenet.android.feature.journal.JournalScreen
import app.tenet.android.feature.nutrition.AddFoodScreen
import app.tenet.android.feature.nutrition.NutritionScreen
import app.tenet.android.feature.settings.SettingsScreen
import app.tenet.android.feature.sport.ExerciseLibraryScreen
import app.tenet.android.feature.sport.RoutineEditorScreen
import app.tenet.android.feature.sport.SportScreen
import app.tenet.android.feature.sport.calisthenics.SkillSessionScreen
import app.tenet.android.feature.sport.calisthenics.CsWorkoutScreen
import app.tenet.android.feature.sport.WeekCalendarScreen
import app.tenet.android.feature.sport.gym.ActiveSessionScreen
import app.tenet.android.feature.today.TodayScreen
import app.tenet.android.navigation.ActiveSessionRoute
import app.tenet.android.navigation.AddFoodRoute
import app.tenet.android.navigation.EntryEditorRoute
import app.tenet.android.navigation.ExerciseLibraryRoute
import app.tenet.android.navigation.JournalRoute
import app.tenet.android.navigation.NutritionRoute
import app.tenet.android.navigation.RoutineEditorRoute
import app.tenet.android.navigation.SearchRoute
import app.tenet.android.navigation.SettingsRoute
import app.tenet.android.ui.search.SearchScreen
import app.tenet.android.navigation.SkillSessionRoute
import app.tenet.android.navigation.SportRoute
import app.tenet.android.navigation.TodayRoute
import app.tenet.android.navigation.WeekCalendarRoute
import app.tenet.android.navigation.CsWorkoutRoute
import app.tenet.android.navigation.TopLevelTab
import app.tenet.android.navigation.TopLevelTabs
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun TenetApp(
    settings: UserSettings,
    openDreamRequest: Boolean = false,
    openRunRequest: Boolean = false,
    openSportRequest: Boolean = false,
    openShortcutRequest: String? = null,
    onOpenRequestHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val hazeState = rememberHazeState()

    val topEntry by navController.currentBackStackEntryAsState()
    // A new-entry sheet floats above a page: tab bar and tab state follow that page.
    val backStackEntry = if (topEntry?.destination?.let { it.hasRoute(NewEntryRoute::class) || it.hasRoute(AddFoodRoute::class) } == true) {
        navController.previousBackStackEntry
    } else {
        topEntry
    }
    val currentDestination = backStackEntry?.destination

    // Type-safe tab detection: matches on the destination's KClass instead of
    // comparing route strings, so a destination can never be "invisible" to the
    // bar just because its generated route pattern differs from qualifiedName.
    val currentTab = currentDestination?.let { dest ->
        TopLevelTabs.firstOrNull { dest.hasRoute(it.route::class) }
    }
    val onTopLevel = currentTab != null
    val moduleEnabled: (TopLevelTab) -> Boolean = { tab ->
        when (tab) {
            TopLevelTab.Today -> AppModule.TODAY in settings.enabledModules
            TopLevelTab.Sport -> AppModule.SPORT in settings.enabledModules
            TopLevelTab.Journal -> AppModule.JOURNAL in settings.enabledModules
            TopLevelTab.Nutrition -> AppModule.NUTRITION in settings.enabledModules
            TopLevelTab.Settings -> true
        }
    }
    // The active tab always stays visible, even when its module is switched
    // off while the user is on it – otherwise there is no pill to come back to.
    val enabledTabs = TopLevelTabs.filter { it == currentTab || moduleEnabled(it) }
    // NavHost start destination: first tab whose module is enabled (Settings is
    // always enabled, so this is never empty). Starting on a fixed TodayRoute
    // would strand the user on a hidden tab with no way back.
    val startTab = TopLevelTabs.firstOrNull(moduleEnabled) ?: TopLevelTab.Settings

    // Reselect: tapping the active tab scrolls its list to the top.
    val reselect = remember { MutableSharedFlow<TopLevelTab>(extraBufferCapacity = 1) }

    // Scroll-aware bar (App_Konzept.md 3.3): hides while scrolling down, comes
    // back on the way up and whenever the destination changes.
    var barHiddenByScroll by remember { mutableStateOf(false) }
    LaunchedEffect(currentDestination) { barHiddenByScroll = false }
    // Morning dream reminder tapped: jump straight into the dream editor.
    LaunchedEffect(openDreamRequest) {
        if (openDreamRequest) {
            navController.navigate(NewEntryRoute(type = EntryType.DREAM.name))
            onOpenRequestHandled()
        }
    }
    // Run notification tapped: back to the live run.
    LaunchedEffect(openRunRequest) {
        if (openRunRequest) {
            if (navController.currentDestination?.hasRoute(ActiveRunRoute::class) != true) {
                navController.navigate(ActiveRunRoute())
            }
            onOpenRequestHandled()
        }
    }
    val barScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -12f) barHiddenByScroll = true
                if (consumed.y > 12f || available.y > 0f) barHiddenByScroll = false
                return Offset.Zero
            }
        }
    }

    fun openTab(tab: TopLevelTab) {
        // Read the tab at tap time: a captured `currentTab` can be stale in
        // memoized callbacks and would turn every tap into a "reselect".
        val current = navController.currentDestination?.let { dest ->
            TopLevelTabs.firstOrNull { dest.hasRoute(it.route::class) }
        }
        if (current == tab) {
            barHiddenByScroll = false
            reselect.tryEmit(tab)
            return
        }
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Launcher shortcuts: new note sheet or meal logging.
    LaunchedEffect(openShortcutRequest) {
        if (openShortcutRequest == null) return@LaunchedEffect
        // Cold start: wait until the NavHost has its graph, or the start
        // destination would replace the screen we open here.
        androidx.compose.runtime.snapshotFlow { navController.currentBackStackEntry }.first { it != null }
        when (openShortcutRequest) {
            ReminderScheduler.OPEN_NOTE -> navController.navigate(NewEntryRoute(type = EntryType.NOTE.name))
            ReminderScheduler.OPEN_MEAL -> navController.navigate(AddFoodRoute())
            else -> if (openShortcutRequest?.startsWith(SHARED_RECIPE) == true) {
                navController.navigate(RecipeImportRoute(openShortcutRequest.removePrefix(SHARED_RECIPE)))
            } else {
                return@LaunchedEffect
            }
        }
        onOpenRequestHandled()
    }
    // Training reminder tapped: straight to the sport tab.
    LaunchedEffect(openSportRequest) {
        if (openSportRequest) {
            openTab(TopLevelTab.Sport)
            onOpenRequestHandled()
        }
    }

    val appSnackbar = rememberAppSnackbar()
    // Blur of the whole app while a sheet (new entry) floats above it.
    val backdropBlur = remember { mutableFloatStateOf(0f) }
    // Companion: the house button sends it home until the app is opened again.
    var companionHome by remember { mutableStateOf(false) }
    val companionControl = if (settings.companion) {
        app.tenet.android.core.designsystem.header.CompanionControl(visible = !companionHome) { companionHome = !companionHome }
    } else {
        null
    }
    CompositionLocalProvider(
        LocalAppSnackbar provides appSnackbar,
        LocalBackdropBlur provides backdropBlur,
        app.tenet.android.core.designsystem.header.LocalCompanionControl provides companionControl,
    ) {
    Box(Modifier.fillMaxSize().backdropBlur({ backdropBlur.floatValue })) {
            NavHost(
                navController = navController,
                startDestination = startTab.route,
                // Detail screens use Material's shared axis X (like Google Health):
                // the old page drifts a little to the side and fades out, the new
                // one drifts in from the other side and fades in. Back (button or
                // predictive gesture) is the mirror image; the gesture drives it,
                // so the page follows the finger a bit to the right and fades.
                // Switching between tabs stays without animation.
                enterTransition = {
                    if (targetState.destination.isTopLevel()) EnterTransition.None else sharedAxisIn(forward = true)
                },
                exitTransition = {
                    if (targetState.destination.isTopLevel()) ExitTransition.None else sharedAxisOut(forward = true)
                },
                popEnterTransition = {
                    if (initialState.destination.isTopLevel()) EnterTransition.None else sharedAxisIn(forward = false)
                },
                popExitTransition = {
                    if (initialState.destination.isTopLevel()) ExitTransition.None else sharedAxisOut(forward = false)
                },
                // Without these the gesture uses Navigation's default (shrink to the centre).
                // While the finger drags, the page follows up to ~20 % and stays
                // visible; it only fades once released (like Google Health).
                predictivePopEnterTransition = {
                    if (initialState.destination.isTopLevel()) EnterTransition.None else gestureBackIn()
                },
                predictivePopExitTransition = {
                    if (initialState.destination.isTopLevel()) ExitTransition.None else gestureBackOut()
                },
                modifier = Modifier
                    .fillMaxSize()
                    // Every page ends above the keyboard: focused fields scroll into
                    // view and bottom buttons stay reachable (inner imePaddings are
                    // consumed here, so nothing is counted twice).
                    .imePadding()
                    .nestedScroll(barScrollConnection)
                    .hazeSource(hazeState),
            ) {
                composable<TodayRoute> {
                    TabContent(reselect, TopLevelTab.Today) {
                        TodayScreen(
                            onSearch = { navController.navigate(SearchRoute) },
                            onAddFood = { date -> navController.navigate(AddFoodRoute(date = date)) },
                            onOpenJournal = { openTab(TopLevelTab.Journal) },
                            onOpenSport = { openTab(TopLevelTab.Sport) },
                            onNewEntry = { type, date, dictate ->
                                navController.navigate(NewEntryRoute(type = type.name, date = date, dictate = dictate))
                            },
                            onOpenEntry = { entry -> navController.navigate(EntryEditorRoute(entry.id, entry.type.name)) },
                            journalLocked = settings.journalLock && !JournalLock.unlocked,
                            onStartRun = { id -> navController.navigate(ActiveRunRoute(id)) },
                        )
                    }
                }
                composable<SportRoute> {
 AreaTheme(AppArea.SPORT) {
                    TabContent(reselect, TopLevelTab.Sport) {
                        SportScreen(
                            onSearch = { navController.navigate(SearchRoute) },
                            onOpenSession = { sessionId ->
                                navController.navigate(ActiveSessionRoute(sessionId))
                            },
                            onOpenSkillSession = { sessionId ->
                                navController.navigate(SkillSessionRoute(sessionId))
                            },
                            onOpenWorkout = { sessionId ->
                                navController.navigate(CsWorkoutRoute(sessionId))
                            },
                            onOpenWeekCalendar = { navController.navigate(WeekCalendarRoute) },
                            onOpenLibrary = { navController.navigate(ExerciseLibraryRoute) },
                            onOpenRoutineEditor = { navController.navigate(RoutineEditorRoute) },
                            onStartRun = { planned -> navController.navigate(ActiveRunRoute(planned)) },
                            onOpenRun = { id -> navController.navigate(RunDetailRoute(id)) },
                            onOpenRunPlan = { id -> navController.navigate(RunPlanDetailRoute(id)) },
                            onOpenGymPlan = { id -> navController.navigate(GymPlanDetailRoute(id)) },
                            onOpenProgression = { page -> navController.navigate(ProgressionRoute(page)) },
                            onOpenSetup = { discipline, useHistory ->
                                navController.navigate(SportSetupRoute(discipline, useHistory))
                            },
                            onOpenRunWorkout = { id -> navController.navigate(RunWorkoutRoute(id)) },
                            onOpenGymSummary = { id -> navController.navigate(WorkoutSummaryRoute(id)) },
                            onOpenGymHistory = { navController.navigate(WorkoutHistoryRoute) },
                            onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                            onOpenCaliPlan = { navController.navigate(CaliPlanDetailRoute) },
                        )
                    }
                }
}
                composable<SearchRoute> {
 SubPageWash(HeaderImage.TODAY) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenEntry = { entry ->
                            navController.navigate(EntryEditorRoute(entry.id, entry.type.name))
                        },
                        onOpenExercises = { navController.navigate(ExerciseLibraryRoute) },
                        onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                        hideEntries = settings.journalLock && !JournalLock.unlocked,
                        onOpenNutrition = {
                            navController.popBackStack()
                            openTab(TopLevelTab.Nutrition)
                        },
                    )
                }
}
                composable<ExerciseLibraryRoute> {
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    ExerciseLibraryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                    )
                }
}
}
                composable<RoutineEditorRoute> {
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    RoutineEditorScreen(onBack = { navController.popBackStack() })
                }
}
}
                composable<ActiveSessionRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    val route = entry.toRoute<ActiveSessionRoute>()
                    ActiveSessionScreen(
                        sessionId = route.sessionId,
                        onBack = { navController.popBackStack() },
                        onFinished = {
                            // Straight into the summary, the session screen leaves the stack.
                            navController.navigate(WorkoutSummaryRoute(route.sessionId, fresh = true)) {
                                popUpTo<ActiveSessionRoute> { inclusive = true }
                            }
                        },
                        onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                    )
                }
}
}
                composable<WorkoutSummaryRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    val route = entry.toRoute<WorkoutSummaryRoute>()
                    WorkoutSummaryScreen(
                        sessionId = route.sessionId,
                        fresh = route.fresh,
                        onClose = { navController.popBackStack() },
                        onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                    )
                }
}
}
                composable<CaliPlanDetailRoute> {
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    CaliPlanScreen(
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                    )
                }
}
}
                composable<WorkoutHistoryRoute> {
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    WorkoutHistoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSession = { id -> navController.navigate(WorkoutSummaryRoute(id)) },
                    )
                }
}
}
                composable<ExerciseDetailRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    ExerciseDetailScreen(
                        exerciseId = entry.toRoute<ExerciseDetailRoute>().exerciseId,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { id -> navController.navigate(WorkoutSummaryRoute(id)) },
                    )
                }
}
}
                composable<SkillSessionRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    val route = entry.toRoute<SkillSessionRoute>()
                    SkillSessionScreen(
                        sessionId = route.sessionId,
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                        onFinished = {
                            navController.navigate(WorkoutSummaryRoute(route.sessionId, fresh = true)) {
                                popUpTo<SkillSessionRoute> { inclusive = true }
                            }
                        },
                    )
                }
}
}
                composable<WeekCalendarRoute> {
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    WeekCalendarScreen(onBack = { navController.popBackStack() })
                }
}
}
                composable<ActiveRunRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    val route = entry.toRoute<ActiveRunRoute>()
                    ActiveRunScreen(
                        plannedWorkoutId = route.plannedWorkoutId,
                        onBack = { navController.popBackStack() },
                        onFinished = { id ->
                            navController.navigate(RunDetailRoute(id)) {
                                popUpTo<ActiveRunRoute> { inclusive = true }
                            }
                        },
                    )
                }
}
}
                composable<RunPlanDetailRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    RunPlanDetailScreen(
                        planId = entry.toRoute<RunPlanDetailRoute>().planId,
                        onBack = { navController.popBackStack() },
                        onOpenRun = { id -> navController.navigate(RunDetailRoute(id)) },
                        onOpenWorkout = { id -> navController.navigate(RunWorkoutRoute(id)) },
                    )
                }
}
}
                composable<RunWorkoutRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    RunWorkoutScreen(
                        plannedId = entry.toRoute<RunWorkoutRoute>().plannedId,
                        onBack = { navController.popBackStack() },
                        onStart = { id -> navController.navigate(ActiveRunRoute(id)) },
                        onOpenRun = { id -> navController.navigate(RunDetailRoute(id)) },
                    )
                }
}
}
                composable<SportSetupRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    val done = { navController.popBackStack(); Unit }
                    val setupRoute = entry.toRoute<SportSetupRoute>()
                    when (setupRoute.discipline) {
                        "RUNNING" -> RunSetupScreen(onDone = done)
                        "CALISTHENICS" -> CaliSetupScreen(onDone = done)
                        else -> GymSetupScreen(onDone = done, useTrainingHistory = setupRoute.useTrainingHistory)
                    }
                }
}
}
                composable<ProgressionRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    ProgressionScreen(
                        initialPage = entry.toRoute<ProgressionRoute>().page,
                        onBack = { navController.popBackStack() },
                    )
                }
}
}
                composable<GymPlanDetailRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    GymPlanDetailScreen(
                        planId = entry.toRoute<GymPlanDetailRoute>().planId,
                        onBack = { navController.popBackStack() },
                    )
                }
}
}
                composable<RunDetailRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    val route = entry.toRoute<RunDetailRoute>()
                    RunDetailScreen(
                        sessionId = route.sessionId,
                        onBack = { navController.popBackStack() },
                        onOpenPlanned = { id -> navController.navigate(RunWorkoutRoute(id)) },
                    )
                }
}
}
                composable<CsWorkoutRoute> { entry ->
 SubPageWash(HeaderImage.SPORT) {
 AreaTheme(AppArea.SPORT) {
                    val route = entry.toRoute<CsWorkoutRoute>()
                    CsWorkoutScreen(
                        sessionId = route.sessionId,
                        onBack = { navController.popBackStack() },
                        onFinished = {
                            navController.navigate(WorkoutSummaryRoute(route.sessionId, fresh = true)) {
                                popUpTo<CsWorkoutRoute> { inclusive = true }
                            }
                        },
                    )
                }
}
}
                composable<JournalRoute> {
 AreaTheme(AppArea.JOURNAL) {
                    TabContent(reselect, TopLevelTab.Journal) {
                      JournalLockGate(enabled = settings.journalLock) {
                        JournalScreen(
                            onNewDiaryOn = { date ->
                                navController.navigate(NewEntryRoute(type = EntryType.DIARY.name, date = date))
                            },
                            onSearch = { navController.navigate(SearchRoute) },
                            onOpenEditor = { type, entryId ->
                                navController.navigate(if (entryId == null) NewEntryRoute(type.name) else EntryEditorRoute(entryId, type.name))
                            },
                            onNewEntry = { type, start ->
                                navController.navigate(NewEntryRoute(type = type.name, start = start))
                            },
                        )
                      }
                    }
                }
}
                composable<NutritionRoute> {
 AreaTheme(AppArea.NUTRITION) {
                    TabContent(reselect, TopLevelTab.Nutrition) {
                        NutritionScreen(
                            onSearch = { navController.navigate(SearchRoute) },
                            onAddFood = { meal, date -> navController.navigate(AddFoodRoute(meal?.name.orEmpty(), date)) },
                            onOpenRecipe = { navController.navigate(RecipeDetailRoute(it)) },
                            onNewRecipe = { navController.navigate(RecipeEditorRoute()) },
                            onImportRecipe = { navController.navigate(RecipeImportRoute()) },
                            onOptimize = { date -> navController.navigate(MacroOptimizerRoute(date)) },
                        )
                    }
                }
}
                composable<SettingsRoute> {
                    TabContent(reselect, TopLevelTab.Settings) {
                        val context = LocalContext.current
                        SettingsScreen(
                            onSearch = { navController.navigate(SearchRoute) },
                            journalLockAvailable = remember { JournalLock.isAvailable(context) },
                            authenticate = { title, onResult -> JournalLock.authenticate(context, title, onResult) },
                        )
                    }
                }
                composable<EntryEditorRoute> { entry ->
 SubPageWash(HeaderImage.JOURNAL) {
 AreaTheme(AppArea.JOURNAL) {
                    val route = entry.toRoute<EntryEditorRoute>()
                    JournalLockGate(enabled = settings.journalLock) {
                    EntryEditorScreen(
                        entryId = route.entryId.ifEmpty { null },
                        initialType = runCatching { EntryType.valueOf(route.type) }
                            .getOrDefault(EntryType.NOTE),
                        initialTitle = route.title,
                        initialDate = route.date,
                        autoDictate = route.dictate,
                        startAction = route.start,
                        onBack = { navController.popBackStack() },
                        onOpenEntry = { id, type -> navController.navigate(EntryEditorRoute(id, type.name)) },
                        onCreateNote = { title ->
                            navController.navigate(NewEntryRoute(type = EntryType.NOTE.name, title = title))
                        },
                    )
                    }
                }
}
}
                // New entries float as a sheet over the page they came from.
                dialog<NewEntryRoute>(
                    // Back/Escape go through the sheet's own handler, which saves what was written.
                    dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false),
                ) { entry ->
 AreaTheme(AppArea.JOURNAL) {
                    val route = entry.toRoute<NewEntryRoute>()
                    // The sheet is its own window: the app-wide snackbar would sit behind it.
                    val sheetSnackbar = rememberAppSnackbar()
                    Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalAppSnackbar provides sheetSnackbar) {
                    JournalLockGate(enabled = settings.journalLock) {
                        EntryEditorScreen(
                            entryId = null,
                            initialType = runCatching { EntryType.valueOf(route.type) }.getOrDefault(EntryType.NOTE),
                            initialTitle = route.title,
                            initialDate = route.date,
                            autoDictate = route.dictate,
                            startAction = route.start,
                            sheet = true,
                            onBack = { navController.popBackStack() },
                            onOpenEntry = { id, type -> navController.navigate(EntryEditorRoute(id, type.name)) },
                            onCreateNote = { title ->
                                navController.navigate(NewEntryRoute(type = EntryType.NOTE.name, title = title))
                            },
                        )
                    }
                    }
                    SnackbarHost(
                        hostState = sheetSnackbar.host,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = 8.dp),
                    )
                    }
                }
}
                // Food logging floats as a sheet over the page it came from (the
                // rings stay visible behind it and fill up when it closes).
                dialog<AddFoodRoute>(
                    dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) { entry ->
 AreaTheme(AppArea.NUTRITION) {
                    val route = entry.toRoute<AddFoodRoute>()
                    app.tenet.android.core.designsystem.component.FloatingSheet(onClosed = { navController.popBackStack() }) { close ->
                    AddFoodScreen(
                        sheet = true,
                        onBack = close,
                        // Return to the page that opened food logging. Forcing a
                        // new NutritionRoute here can leave two top-level routes
                        // in the stack (Today -> AddFood -> Nutrition) and break
                        // later tab switches back to Today.
                        onLogged = close,
                        initialMeal = route.meal,
                        date = route.date,
                    )
                    }
                }
}
                composable<RecipeDetailRoute> { entry ->
 SubPageWash(HeaderImage.NUTRITION) {
 AreaTheme(AppArea.NUTRITION) {
                    val route = entry.toRoute<RecipeDetailRoute>()
                    val message by entry.savedStateHandle.getStateFlow<String?>(RECIPE_MESSAGE, null).collectAsStateWithLifecycle()
                    RecipeDetailScreen(
                        message = message,
                        onMessageShown = { entry.savedStateHandle[RECIPE_MESSAGE] = null },
                        recipeId = route.recipeId,
                        onBack = { navController.popBackStack() },
                        onEdit = { text ->
                            navController.navigate(if (text) RecipeTextEditRoute(route.recipeId) else RecipeEditorRoute(route.recipeId))
                        },
                        onCook = { servings -> navController.navigate(CookingRoute(route.recipeId, servings)) },
                    )
                }
}
}
                composable<RecipeImportRoute> { entry ->
 SubPageWash(HeaderImage.NUTRITION) {
 AreaTheme(AppArea.NUTRITION) {
                    RecipeImportScreen(
                        initialUrl = entry.toRoute<RecipeImportRoute>().url,
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.navigate(RecipeDetailRoute(id)) { popUpTo<RecipeImportRoute> { inclusive = true } }
                        },
                        onOpenRecipe = { id -> navController.navigate(RecipeDetailRoute(id)) },
                    )
                }
}
}
                composable<RecipeEditorRoute> { entry ->
 SubPageWash(HeaderImage.NUTRITION) {
 AreaTheme(AppArea.NUTRITION) {
                    val route = entry.toRoute<RecipeEditorRoute>()
                    RecipeEditorScreen(
                        recipeId = route.recipeId.ifEmpty { null },
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.popBackStack()
                            // New recipes land on their detail page.
                            if (route.recipeId.isEmpty()) navController.navigate(RecipeDetailRoute(id))
                        },
                    )
                }
}
}
                composable<RecipeTextEditRoute> { entry ->
 SubPageWash(HeaderImage.NUTRITION) {
 AreaTheme(AppArea.NUTRITION) {
                    RecipeTextEditorScreen(
                        recipeId = entry.toRoute<RecipeTextEditRoute>().recipeId,
                        onBack = { navController.popBackStack() },
                        onSaved = { msg ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(RECIPE_MESSAGE, msg)
                            navController.popBackStack()
                        },
                    )
                }
}
}
                composable<MacroOptimizerRoute> { entry ->
 SubPageWash(HeaderImage.NUTRITION) {
 AreaTheme(AppArea.NUTRITION) {
                    app.tenet.android.feature.nutrition.optimizer.MacroOptimizerScreen(
                        date = entry.toRoute<MacroOptimizerRoute>().date,
                        onBack = { navController.popBackStack() },
                    )
                }
}
}
                composable<CookingRoute> { entry ->
 SubPageWash(HeaderImage.NUTRITION) {
 AreaTheme(AppArea.NUTRITION) {
                    val route = entry.toRoute<CookingRoute>()
                    CookingModeScreen(
                        recipeId = route.recipeId,
                        servings = route.servings,
                        onClose = { navController.popBackStack() },
                    )
                }
}
}
            }

            val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
            val liveRun by app.tenet.android.feature.sport.run.RunTrackingService.state.collectAsState()
            val recording = liveRun?.status?.let { it != app.tenet.android.feature.sport.run.RunStatus.FINISHED } == true
            TenetNavigationBar(
                badges = if (recording) mapOf(TopLevelTab.Sport to "Lauf wird aufgezeichnet") else emptyMap(),
                modifier = Modifier.align(Alignment.BottomCenter),
                tabs = enabledTabs,
                selected = currentTab,
                onSelect = ::openTab,
                // Like iOS: the tab bar steps aside while the keyboard is up.
                visible = onTopLevel && !barHiddenByScroll && !imeVisible,
                glass = settings.glassBar,
                hazeState = hazeState,
            )
            // Above the tab bar: its chat panel covers the bar while open.
            if (settings.companion) {
                CompanionOverlay(
                    visible = !companionHome,
                    kind = app.tenet.android.core.designsystem.component.CompanionKind.of(settings.companionKind),
                    onNavigate = { dest ->
                        when (dest) {
                            app.tenet.android.core.data.companion.CompanionAgent.Destination.TODAY -> openTab(TopLevelTab.Today)
                            app.tenet.android.core.data.companion.CompanionAgent.Destination.SPORT -> openTab(TopLevelTab.Sport)
                            app.tenet.android.core.data.companion.CompanionAgent.Destination.JOURNAL -> openTab(TopLevelTab.Journal)
                            app.tenet.android.core.data.companion.CompanionAgent.Destination.NUTRITION -> openTab(TopLevelTab.Nutrition)
                            app.tenet.android.core.data.companion.CompanionAgent.Destination.SETTINGS -> openTab(TopLevelTab.Settings)
                        }
                    },
                )
            }

            // One snackbar host for the whole app, above the floating tab bar.
            SnackbarHost(
                hostState = appSnackbar.host,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (onTopLevel) TenetDimens.bottomTabBarPadding - 24.dp else 8.dp),
            )
    }
    }
}

/** Provides the reselect events of [tab] to the tab's screen. */
@Composable
private fun TabContent(
    reselect: MutableSharedFlow<TopLevelTab>,
    tab: TopLevelTab,
    content: @Composable () -> Unit,
) {
    val events = remember(reselect, tab) { reselect.filter { it == tab }.map { } }
    CompositionLocalProvider(LocalTabReselect provides events, content = content)
}

/** Duration of the back animation (fast, like system back). */
/** Material shared axis X timing: 300 ms, outgoing fades in the first 90 ms. */
private const val AXIS_MS = 250
private const val AXIS_FADE_OUT_MS = 75

/** Incoming page: drifts in by ~8 % of the width and fades in after the old one is gone. */
private fun sharedAxisIn(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(AXIS_MS, easing = EmphasizedDecelerate)) { w -> if (forward) w / 12 else -w / 12 } +
        fadeIn(tween(AXIS_MS - AXIS_FADE_OUT_MS, delayMillis = AXIS_FADE_OUT_MS, easing = LinearOutSlowInEasing))

/** Outgoing page: drifts ~8 % to the side and fades out quickly. */
private fun sharedAxisOut(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(AXIS_MS, easing = EmphasizedDecelerate)) { w -> if (forward) -w / 12 else w / 12 } +
        fadeOut(tween(AXIS_FADE_OUT_MS, easing = FastOutLinearInEasing))

/** Predictive back, page below: shows up late and drifts in from the left. */
private fun gestureBackIn(): EnterTransition =
    slideInHorizontally(tween(AXIS_MS, easing = LinearEasing)) { w -> -w / 12 } +
        fadeIn(tween(AXIS_MS / 3, delayMillis = AXIS_MS * 2 / 3, easing = LinearEasing))

/** Predictive back, current page: follows the finger ~20 % to the right, fades at the end. */
private fun gestureBackOut(): ExitTransition =
    slideOutHorizontally(tween(AXIS_MS, easing = LinearEasing)) { w -> w / 5 } +
        fadeOut(tween(AXIS_MS / 3, delayMillis = AXIS_MS * 2 / 3, easing = LinearEasing))

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private fun NavDestination.isTopLevel(): Boolean = TopLevelTabs.any { hasRoute(it.route::class) }

/** Prefix of the open request for a link shared into Tenet (share sheet → recipe import). */
const val SHARED_RECIPE = "recipe_import|"

/** Snackbar text handed from the recipe editor back to the detail page. */
private const val RECIPE_MESSAGE = "recipe_message"
