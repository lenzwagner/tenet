package app.tenet.android.feature.nutrition

import androidx.compose.material.icons.outlined.LocalFireDepartment
import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.header.pageWash
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.designsystem.component.TenetFabMenu
import app.tenet.android.core.designsystem.component.FabMenuAction
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Edit
import app.tenet.android.feature.nutrition.recipe.RecipeBrowser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.runtime.key
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.TenetSlider
import app.tenet.android.core.designsystem.component.rememberGrowIn
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import app.tenet.android.core.designsystem.header.PageTabs
import app.tenet.android.core.designsystem.header.PageTab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.database.entity.FoodLog
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.core.designsystem.header.HeaderImage
import app.tenet.android.core.designsystem.header.PageHeader
import app.tenet.android.core.designsystem.header.rememberHeaderScrollState
import app.tenet.android.core.designsystem.navigation.ReselectEffect
import app.tenet.android.core.designsystem.navigation.rememberReselectListState
import app.tenet.android.core.designsystem.nutrition.GoalEditorSheet
import app.tenet.android.core.designsystem.nutrition.NutritionRings
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val PagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = TenetDimens.bottomFabPadding)

/**
 * Nutrition tab (App_Konzept.md 5.4): tracker and recipes as swipeable pages.
 */
@Composable
fun NutritionScreen(
    onAddFood: (meal: MealType?, date: String) -> Unit,
    onOpenRecipe: (String) -> Unit = {},
    onNewRecipe: () -> Unit = {},
    onImportRecipe: () -> Unit = {},
    onSearch: () -> Unit = {},
    /** Makro-Optimierer for the shown day (ISO date). */
    onOptimize: (String) -> Unit = {},
    viewModel: NutritionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val headerState = rememberHeaderScrollState()
    ReselectEffect { headerState.animateExpand() }
    val fabExpanded by remember { derivedStateOf { headerState.progress < 0.5f } }
    // Messages ("Mit Saffron synchronisiert") in the app-wide snackbar: at the bottom
    // above the tab bar like everywhere, not lifted above the FAB.
    val appSnackbar = LocalAppSnackbar.current
    LaunchedEffect(Unit) { viewModel.messages.collect { appSnackbar?.show(it) } }

    var goalSheetOpen by remember { mutableStateOf(false) }
    if (goalSheetOpen) {
        GoalEditorSheet(
            currentKcal = state.goal.kcal,
            currentProtein = state.goal.protein,
            currentCarbs = state.goal.carbs,
            currentFat = state.goal.fat,
            onDismiss = { goalSheetOpen = false },
            onSave = { kcal, protein, carbs, fat ->
                viewModel.setGoal(kcal = kcal, protein = protein, carbs = carbs, fat = fat)
                goalSheetOpen = false
            },
            profile = state.profile,
            onSaveProfile = viewModel::saveProfile,
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        floatingActionButton = {
            val recipesPage = pagerState.currentPage == 1
            // Recipes: import (like Saffron) or write one yourself.
            if (recipesPage) {
                TenetFabMenu(
                    actions = listOf(
                        FabMenuAction("Selbst anlegen", Icons.Outlined.Edit, onNewRecipe),
                        FabMenuAction("Aus Link oder Text", Icons.Outlined.Link, onImportRecipe),
                    ),
                    contentDescription = "Rezept hinzufügen",
                    modifier = Modifier.padding(bottom = TenetDimens.bottomTabBarPadding),
                )
            } else ExtendedFloatingActionButton(
                shape = app.tenet.android.core.designsystem.component.tenetFabShape,
                onClick = { if (recipesPage) onNewRecipe() else onAddFood(null, state.date.toString()) },
                expanded = fabExpanded,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(if (recipesPage) "Rezept" else "Mahlzeit") },
                modifier = Modifier
                    .padding(bottom = TenetDimens.bottomTabBarPadding)
                    .semantics { contentDescription = if (recipesPage) "Rezept anlegen" else "Mahlzeit hinzufügen" },
            )
        },
    ) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .pageWash(HeaderImage.NUTRITION, { headerState.progress })
                .nestedScroll(headerState.nestedScrollConnection),
        ) {
            PageHeader(
                header = HeaderImage.NUTRITION,
                title = "Ernährung",
                progress = { headerState.progress },
                onSearch = onSearch,
            )
            PageTabs(
                tabs = listOf(PageTab("Tracker"), PageTab("Rezepte")),
                selectedIndex = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
            HorizontalPager(state = pagerState, beyondViewportPageCount = 1, modifier = Modifier.weight(1f)) { page ->
                if (page == 0) {
                    TrackerPage(
                        state = state,
                        viewModel = viewModel,
                        onAddFood = onAddFood,
                        onEditGoal = { goalSheetOpen = true },
                        onDay = viewModel::goTo,
                        onOptimize = { onOptimize(state.date.toString()) },
                    )
                } else {
                    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
                    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
                    RecipeBrowser(
                        recipes = recipes,
                        onOpenRecipe = onOpenRecipe,
                        signedIn = signedIn,
                        refreshing = refreshing,
                        onRefresh = viewModel::refreshRecipes,
                        contentPadding = PagePadding,
                        onImport = onImportRecipe,
                        onDeleteRecipes = viewModel::deleteRecipes,
                    )
                }
            }
        }
    }
}

// ============================================================================
// Tracker
// ============================================================================

@Composable
private fun TrackerPage(
    state: NutritionUiState,
    viewModel: NutritionViewModel,
    onAddFood: (MealType?, String) -> Unit,
    onEditGoal: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onOptimize: () -> Unit,
) {
    val listState = rememberReselectListState()
    val snackbar = LocalAppSnackbar.current
    var renaming by remember { mutableStateOf<MealType?>(null) }
    var waterGoalDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
    ) {
        item(key = "day") {
            DaySwitcher(
                date = state.date,
                isToday = state.isToday,
                onPrevious = viewModel::previousDay,
                onNext = viewModel::nextDay,
                onToday = viewModel::goToday,
            )
        }
        item(key = "rings") {
            TenetCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                CardHeader(
                    Icons.Outlined.LocalFireDepartment,
                    "Kalorien & Makros",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                )
                NutritionRings(
                    kcal = state.totals.kcal,
                    goalKcal = state.goal.kcal,
                    protein = state.totals.protein,
                    goalProtein = state.goal.protein,
                    carbs = state.totals.carbs,
                    goalCarbs = state.goal.carbs,
                    fat = state.totals.fat,
                    goalFat = state.goal.fat,
                    loading = state.loading,
                    onEditGoal = onEditGoal,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        item(key = "water") {
            WaterCard(
                ml = state.waterMl,
                goalMl = state.waterGoalMl,
                onAdd = { viewModel.addWater(250) },
                onRemove = { viewModel.addWater(-250) },
                onEditGoal = { waterGoalDialog = true },
            )
        }
        // Day actions in one row: optimizer, and on an empty day copying the day before.
        item(key = "actions") {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                app.tenet.android.core.designsystem.component.TenetAssistChip(
                    onClick = onOptimize,
                    label = { Text(if (state.isToday) "Was passt noch?" else "Tag planen") },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                )
                if (state.logs.isEmpty() && !state.loading) {
                    app.tenet.android.core.designsystem.component.TenetAssistChip(
                        onClick = viewModel::copyPreviousDay,
                        label = { Text("Vortag kopieren") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    )
                }
            }
        }
        MealType.entries.forEach { meal ->
            val logs = state.logs.filter { it.mealType == meal }
            item(key = "meal-${meal.name}") {
                MealCard(
                    title = meal.label(state.mealNames),
                    icon = meal,
                    logs = logs,
                    onAdd = { onAddFood(meal, state.date.toString()) },
                    onRename = { renaming = meal },
                    onDelete = { log ->
                        viewModel.deleteLog(log.id)
                        snackbar?.showUndo("„${log.label}“ gelöscht", onUndo = { viewModel.restoreLog(log) })
                    },
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .animateItem(),
                )
            }
        }
        item(key = "week") { WeekCard(state, onDay) }
    }

    renaming?.let { meal ->
        var name by remember(meal) { mutableStateOf(meal.label(state.mealNames)) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            title = { Text("Mahlzeit umbenennen") },
            text = {
                app.tenet.android.core.designsystem.component.TenetTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameMeal(meal.name, name)
                        renaming = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.renameMeal(meal.name, "")
                        renaming = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Zurücksetzen") }
            },
        )
    }

    if (waterGoalDialog) {
        var goal by remember { mutableFloatStateOf(state.waterGoalMl.toFloat()) }
        AlertDialog(
            onDismissRequest = { waterGoalDialog = false },
            icon = { Icon(Icons.Outlined.LocalDrink, contentDescription = null) },
            title = { Text("Wasserziel") },
            text = {
                Column {
                    Text("${goal.roundToInt()} ml", style = MaterialTheme.typography.headlineSmallEmphasized)
                    TenetSlider(value = goal, onValueChange = { goal = it }, valueRange = 1000f..5000f, steps = 15)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setWaterGoal((goal / 250).roundToInt() * 250)
                        waterGoalDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Speichern") }
            },
        )
    }
}

@Composable
private fun DaySwitcher(
    date: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TooltipIconButton(Icons.Outlined.ChevronLeft, "Vorheriger Tag", onPrevious)
        Text(
            formatDay(date),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (!isToday) {
            TextButton(onClick = onToday, shapes = ButtonDefaults.shapes()) { Text("Heute") }
        }
        TooltipIconButton(Icons.Outlined.ChevronRight, "Nächster Tag", onNext, enabled = !isToday)
    }
}

@Composable
private fun WaterCard(ml: Int, goalMl: Int, onAdd: () -> Unit, onRemove: () -> Unit, onEditGoal: () -> Unit) {
    TenetCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val water = app.tenet.android.core.designsystem.theme.cardTint(app.tenet.android.core.designsystem.theme.HealthTint.INFO, MaterialTheme.colorScheme.tertiary)
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
            CardHeader(Icons.Outlined.LocalDrink, "Wasser", color = water, meta = "Ziel ${"%.1f".format(goalMl / 1000f).replace('.', ',')} l", action = {
                TooltipIconButton(Icons.Outlined.Edit, "Wasserziel ändern", onEditGoal)
            })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${"%.2f".format(ml / 1000f).replace('.', ',')} l",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    app.tenet.android.core.designsystem.component.TenetProgress(
                        progress = { (ml.toFloat() / goalMl).coerceIn(0f, 1f) },
                        color = water,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onRemove, enabled = ml > 0, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Remove, contentDescription = "250 ml weniger")
                }
                FilledTonalIconButton(onClick = onAdd, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Add, contentDescription = "Glas (250 ml) trinken")
                }
            }
        }
    }
}

/**
 * One meal as an M3 card: header (shape icon, name, kcal, add, menu) and
 * the logged foods as filled, connected list items. Swipe an entry to
 * delete it (with "Rückgängig" in the snackbar).
 */
@Composable
private fun MealCard(
    title: String,
    icon: MealType,
    logs: List<FoodLog>,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDelete: (FoodLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val kcal = logs.sumOf { it.kcal.toDouble() }.roundToInt()
    TenetCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            CardHeader(icon.icon, title, meta = if (logs.isEmpty()) null else "$kcal kcal", action = {
                FilledTonalIconButton(onClick = onAdd, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Add, contentDescription = "Zu $title hinzufügen")
                }
                Box {
                    IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr zu $title")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Umbenennen") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menu = false; onRename() },
                        )
                    }
                }
            })
            if (logs.isEmpty()) {
                Text(
                    "Noch nichts eingetragen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${logs.size} ${if (logs.size == 1) "Eintrag" else "Einträge"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (logs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
                ) {
                    logs.forEachIndexed { index, log ->
                        key(log.id) {
                            FoodLogRow(
                                log = log,
                                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, logs.size),
                                onDelete = { onDelete(log) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodLogRow(log: FoodLog, shapes: ListItemShapes, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val quantity = log.quantity
    val unit = log.unit
    val amount = if (quantity != null && unit != null && unit != "g") {
        "${quantity.fmt()} $unit · ${log.amountG.roundToInt()} g"
    } else {
        "${log.amountG.roundToInt()} g"
    }
    val haptics = LocalHapticFeedback.current
    val dismiss = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            onDelete()
        },
        backgroundContent = {
            if (dismiss.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        SegmentedListItem(
            shapes = shapes,
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            leadingContent = {
                // Initial in a small shape as a food avatar.
                Surface(
                    shape = MaterialShapes.Cookie4Sided.toShape(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(log.label.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
                    }
                }
            },
            supportingContent = {
                Text(
                    "$amount · E ${log.protein.roundToInt()} · K ${log.carbs.roundToInt()} · F ${log.fat.roundToInt()}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Text(
                    "${log.kcal.roundToInt()}",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) { Text(log.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * Weekly / monthly evaluation (App_Konzept.md 5.4 "Wochen- und
 * Monatsauswertung, Durchschnitte, Zielerreichung"): kcal per day vs. goal,
 * averages of logged days, days on target.
 */
@Composable
private fun WeekCard(state: NutritionUiState, onDay: (LocalDate) -> Unit) {
    var month by rememberSaveable { mutableStateOf(false) }
    val span = if (month) 30 else 7
    val days = (span - 1 downTo 0).map { state.date.minusDays(it.toLong()) }
    val first = days.first().toString()
    val inRange = state.week.filter { it.date >= first }
    val byDate = inRange.associateBy { it.date }
    val logged = inRange.filter { it.kcal > 0f }
    val avg = if (logged.isEmpty()) 0 else (logged.sumOf { it.kcal.toDouble() } / logged.size).roundToInt()
    val goal = state.goal.kcal
    val onTarget = logged.count { it.kcal in goal * 0.9f..goal * 1.1f }
    val avgProtein = if (logged.isEmpty()) 0 else (logged.sumOf { it.protein.toDouble() } / logged.size).roundToInt()

    TenetCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(Icons.Outlined.QueryStats, "Auswertung", action = {
                SegmentedSelector(
                    segments = listOf(Segment("7 Tage"), Segment("30 Tage")),
                    selectedIndex = if (month) 1 else 0,
                    onSelect = { month = it == 1 },
                    modifier = Modifier.width(176.dp),
                )
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Nutrient("$avg", "Ø kcal")
                Nutrient("$avgProtein", "Ø Eiweiß g")
                Nutrient("$onTarget/${logged.size}", "im Ziel ±10 %")
            }
            if (month) {
                val avgCarbs = if (logged.isEmpty()) 0 else (logged.sumOf { it.carbs.toDouble() } / logged.size).roundToInt()
                val avgFat = if (logged.isEmpty()) 0 else (logged.sumOf { it.fat.toDouble() } / logged.size).roundToInt()
                val balance = logged.sumOf { (it.kcal - goal).toDouble() }.roundToInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Nutrient("$avgCarbs", "Ø Kohlenh. g")
                    Nutrient("$avgFat", "Ø Fett g")
                    Nutrient("${logged.size}/30", "Tage geloggt")
                }
                Text(
                    "Bilanz ggü. Ziel: ${if (balance > 0) "+" else ""}$balance kcal" +
                        " (≈ ${String.format(Locale.GERMAN, "%.1f", balance / 7700f)} kg Körpermasse)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val bar = MaterialTheme.colorScheme.primary
            val over = MaterialTheme.colorScheme.error
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            val goalLine = MaterialTheme.colorScheme.onSurfaceVariant
            val maxKcal = maxOf(goal * 1.3f, inRange.maxOfOrNull { it.kcal } ?: 0f, 1f)
            val grow = rememberGrowIn(inRange to span)
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val slot = size.width / span
                val w = slot * 0.55f
                days.forEachIndexed { i, day ->
                    val x = i * slot + (slot - w) / 2
                    drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                    val kcal = byDate[day.toString()]?.kcal ?: 0f
                    val h = size.height * (kcal / maxKcal).coerceIn(0f, 1f) * grow
                    if (h > 0f) {
                        drawRoundRect(
                            if (kcal > goal * 1.1f) over else bar,
                            Offset(x, size.height - h),
                            Size(w, h),
                            CornerRadius(w / 2),
                        )
                    }
                }
                val y = size.height * (1f - goal / maxKcal)
                drawLine(goalLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            if (month) {
                // Every 7th day labelled, oldest first.
                Row {
                    days.forEachIndexed { i, day ->
                        Text(
                            if (i % 7 == 0) "${day.dayOfMonth}.${day.monthValue}." else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Row {
                    days.forEach { day ->
                        TextButton(
                            onClick = { onDay(day) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day == state.date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Recipes
// ============================================================================

internal fun formatDay(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMAN))
    }
}
