package app.tenet.android.feature.nutrition

import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import app.tenet.android.core.designsystem.component.rememberDictation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.header.PageTabs
import app.tenet.android.core.designsystem.header.PageTab
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.ExpandedFullScreenSearchBar
import app.tenet.android.core.designsystem.component.rememberSheetState
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.data.RecipeDetail
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.FoodSource
import app.tenet.android.core.database.entity.MealType
import app.tenet.android.core.designsystem.component.EmptyState
import app.tenet.android.core.designsystem.component.SectionHeader
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TenetSwitch
import app.tenet.android.core.designsystem.component.TooltipIconButton
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.math.roundToInt

private val Tabs = listOf("Favoriten", "Zuletzt", "Rezepte", "Schnell")

/**
 * "Lebensmittel hinzufügen" (App_Konzept.md 5.4): search (local first, then
 * Open Food Facts), barcode scan, favorites, recently used, recipes as portions
 * and a quick entry with only kcal and macros.
 */
@Composable
fun AddFoodScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit = {},
    initialMeal: String = "",
    date: String = "",
    /** Shown as a floating sheet over the nutrition page (no status bar inset, sheet surface). */
    sheet: Boolean = false,
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    // Synchronously, so meal and date are right on the very first frame.
    remember(initialMeal, date) { viewModel.init(initialMeal, date) }
    val state by viewModel.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    // ^ immediate: text fields must see their own edits in the same frame,
    // otherwise fast typing can drop characters.
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val mealNames by viewModel.mealNames.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val speech = rememberDictation("Was hast du gegessen? z. B. „Zwei Eier und ein Brot mit Käse“", viewModel::voiceLog)

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        runCatching {
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let(viewModel::onBarcode) }
                .addOnFailureListener { viewModel.onScanUnavailable() }
        }.onFailure { viewModel.onScanUnavailable() }
    }

    val text = rememberTextFieldState(state.query)
    val searchState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.drop(1).collect(viewModel::onQuery)
    }
    // Picking a result closes the full-screen search, the portion sheet follows.
    val selectFromSearch: (Food) -> Unit = { food ->
        scope.launch { searchState.animateToCollapsed() }
        viewModel.select(food)
    }
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = searchState,
            onSearch = {},
            placeholder = { Text("Lebensmittel suchen …") },
            leadingIcon = {
                if (searchState.currentValue == SearchBarValue.Expanded) {
                    TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Suche schließen", {
                        scope.launch { searchState.animateToCollapsed() }
                    })
                } else {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (text.text.isNotEmpty()) {
                    TooltipIconButton(Icons.Outlined.Close, "Suche leeren", { text.clearText() })
                } else {
                    TooltipIconButton(Icons.Outlined.QrCodeScanner, "Barcode scannen", ::scan)
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { app.tenet.android.core.designsystem.component.TenetSnackbarHost(snackbar) },
        containerColor = if (sheet) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
        contentWindowInsets = if (sheet) {
            WindowInsets.navigationBars.union(WindowInsets.ime)
        } else {
            androidx.compose.material3.ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            TopAppBar(
                windowInsets = if (sheet) WindowInsets(0, 0, 0, 0) else androidx.compose.material3.TopAppBarDefaults.windowInsets,
                colors = if (sheet) {
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                } else {
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors()
                },
                navigationIcon = {
                    // In the sheet: close (down) instead of back.
                    if (sheet) TooltipIconButton(Icons.Outlined.Close, "Schließen", onBack)
                    else TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack)
                },
                title = { Text("Hinzufügen") },
                subtitle = { Text(viewModel.meal.label(mealNames) + " · " + formatDay(java.time.LocalDate.parse(viewModel.date))) },
                actions = {
                    // Works without KI too: simple sentences are parsed offline.
                    TooltipIconButton(Icons.Outlined.Mic, "Mahlzeit per Sprache", { speech.launch() })
                    TooltipIconButton(Icons.Outlined.QrCodeScanner, "Barcode scannen", ::scan)
                    TooltipIconButton(Icons.Outlined.Check, "Fertig", onBack)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // M3 search: collapsed bar here, tapping opens full-screen results.
            SearchBar(
                state = searchState,
                inputField = inputField,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            PageTabs(
                tabs = Tabs.map { PageTab(it) },
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            if (state.barcodeLoading) TenetLoading()
            when (tab) {
                0 -> FoodList(favorites, viewModel::select, Icons.Outlined.FavoriteBorder, "Noch keine Favoriten", "Markiere Lebensmittel im Portions-Dialog mit dem Herz.")
                1 -> FoodList(recent, viewModel::select, Icons.Outlined.History, "Noch nichts verwendet", "Hier landen deine zuletzt geloggten Lebensmittel.")
                2 -> RecipeList(recipes, viewModel::selectRecipe)
                else -> QuickEntryTab(state.quick, viewModel.meal, mealNames, viewModel::updateQuick, { meal -> viewModel.saveQuick(meal, onLogged) })
            }
        }
    }

    ExpandedFullScreenSearchBar(state = searchState, inputField = inputField) {
        SearchTab(state, selectFromSearch)
    }

    state.voiceItems?.let { items ->
        VoiceMealSheet(
            items = items,
            busy = state.voiceBusy,
            offline = state.voiceOffline,
            mealNames = mealNames,
            onToggle = viewModel::toggleVoiceItem,
            onGrams = viewModel::setVoiceGrams,
            onDismiss = viewModel::dismissVoice,
            onSave = { viewModel.logVoice(onLogged) },
        )
    }

    state.selected?.let { food ->
        PortionSheet(
            food = food,
            initialMeal = viewModel.meal,
            mealNames = mealNames,
            onDismiss = { viewModel.select(null) },
            onToggleFavorite = { viewModel.toggleFavorite(food, it) },
            onAdd = { grams, quantity, unit, meal -> viewModel.log(food, grams, quantity, unit, meal, onLogged) },
        )
    }
    state.selectedRecipe?.let { detail ->
        RecipePortionSheet(
            detail = detail,
            initialMeal = viewModel.meal,
            mealNames = mealNames,
            onDismiss = { viewModel.selectRecipe(null) },
            onAdd = { portions, meal -> viewModel.logRecipe(detail, portions, meal, onLogged) },
        )
    }
}

private val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

@Composable
private fun SearchTab(state: AddFoodState, onSelect: (Food) -> Unit) {
    if (state.query.isBlank()) {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = "Suchen oder scannen",
            body = "Erst deine Lebensmittel, dann Open Food Facts. Barcode oben rechts.",
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
    ) {
        foodGroup("local", "Meine Lebensmittel", state.local, onSelect)
        item(key = "off-h") { SectionHeader("Open Food Facts", Modifier.padding(top = 12.dp)) }
        when {
            state.onlineLoading -> item(key = "off-l") { TenetLoading() }
            state.online.isNotEmpty() -> foodGroup("online", null, state.online, onSelect)
            state.query.trim().length < 3 -> item(key = "off-s") {
                Text("Ab 3 Zeichen wird online gesucht.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.onlineFailed -> item(key = "off-e") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Keine Online-Treffer (oder offline).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun LazyListScope.foodGroup(key: String, title: String?, foods: List<Food>, onSelect: (Food) -> Unit) {
    if (foods.isEmpty()) return
    if (title != null) item(key = "$key-h") { SectionHeader(title) }
    itemsIndexed(foods, key = { _, f -> "$key-${f.id}" }) { index, food ->
        FoodRow(food, app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, foods.size)) { onSelect(food) }
    }
}

@Composable
private fun FoodRow(food: Food, shapes: androidx.compose.material3.ListItemShapes, onClick: () -> Unit) {
    SegmentedListItem(
        colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
        onClick = onClick,
        shapes = shapes,
        leadingContent = {
            ShapeIcon(
                if (food.source == FoodSource.OFF) Icons.Outlined.Public else Icons.Outlined.Kitchen,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    food.brand,
                    "${food.kcalPer100.roundToInt()} kcal/100 g",
                    "E ${food.proteinPer100.fmt()} · K ${food.carbsPer100.fmt()} · F ${food.fatPer100.fmt()}",
                ).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (food.favorite) {
            { Icon(Icons.Filled.Favorite, contentDescription = "Favorit", tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    ) { Text(food.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun FoodList(
    foods: List<Food>,
    onSelect: (Food) -> Unit,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptyBody: String,
) {
    if (foods.isEmpty()) {
        EmptyState(icon = emptyIcon, title = emptyTitle, body = emptyBody)
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
    ) { foodGroup("list", null, foods, onSelect) }
}

@Composable
private fun RecipeList(recipes: List<RecipeDetail>, onSelect: (RecipeDetail) -> Unit) {
    if (recipes.isEmpty()) {
        EmptyState(icon = Icons.AutoMirrored.Outlined.MenuBook, title = "Keine Rezepte", body = "Lege Rezepte im Tab „Rezepte“ an.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
    ) {
        itemsIndexed(recipes, key = { _, r -> r.recipe.id }) { index, detail ->
            SegmentedListItem(
                colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                onClick = { onSelect(detail) },
                shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, recipes.size),
                leadingContent = { ShapeIcon(Icons.Outlined.Restaurant) },
                supportingContent = {
                    Text("${detail.perServing.kcal.roundToInt()} kcal · E ${detail.perServing.protein.roundToInt()} g pro Portion")
                },
            ) { Text(detail.recipe.title) }
        }
    }
}

@Composable
private fun QuickEntryTab(
    quick: QuickEntry,
    initialMeal: MealType,
    mealNames: Map<String, String>,
    onUpdate: (QuickEntry.() -> QuickEntry) -> Unit,
    onSave: (MealType) -> Unit,
) {
    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Nur Kalorien und Makros – schnell erfasst.", style = MaterialTheme.typography.bodyMedium)
        }
        MealPicker(selected = meal, names = mealNames, onSelect = { meal = it })
        OutlinedTextField(
            value = quick.name,
            onValueChange = { v -> onUpdate { copy(name = v) } },
            label = { Text("Was hast du gegessen?") },
            leadingIcon = { Icon(Icons.Outlined.Restaurant, contentDescription = null) },
            isError = quick.error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickField("Menge", quick.amount, "g", Modifier.weight(1f)) { v -> onUpdate { copy(amount = v) } }
            QuickField("Kalorien", quick.kcal, "kcal", Modifier.weight(1f)) { v -> onUpdate { copy(kcal = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickField("Eiweiß", quick.protein, "g", Modifier.weight(1f)) { v -> onUpdate { copy(protein = v) } }
            QuickField("Kohlenh.", quick.carbs, "g", Modifier.weight(1f)) { v -> onUpdate { copy(carbs = v) } }
            QuickField("Fett", quick.fat, "g", Modifier.weight(1f)) { v -> onUpdate { copy(fat = v) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Als Lebensmittel speichern", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TenetSwitch(checked = quick.saveAsFood, onCheckedChange = { v -> onUpdate { copy(saveAsFood = v) } })
        }
        quick.error?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = { onSave(meal) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Text("Hinzufügen")
        }
    }
}

@Composable
private fun QuickField(label: String, value: String, suffix: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** "Rezept als Portion loggen": portions stepper, live nutrients, meal. */
@Composable
internal fun RecipePortionSheet(
    detail: RecipeDetail,
    initialMeal: MealType,
    mealNames: Map<String, String>,
    onDismiss: () -> Unit,
    onAdd: (portions: Float, meal: MealType) -> Unit,
) {
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    var portions by rememberSaveable { mutableFloatStateOf(1f) }
    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    val per = detail.perServing
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(detail.recipe.title, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Portionen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { portions = (portions - 0.5f).coerceAtLeast(0.5f) },
                    shapes = IconButtonDefaults.shapes(),
                ) { Icon(Icons.Outlined.Remove, contentDescription = "Weniger") }
                Text(portions.fmt(), style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(horizontal = 16.dp))
                FilledTonalIconButton(
                    onClick = { portions += 0.5f },
                    shapes = IconButtonDefaults.shapes(),
                ) { Icon(Icons.Outlined.Add, contentDescription = "Mehr") }
            }
            TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Nutrient("${(per.kcal * portions).roundToInt()}", "kcal")
                    Nutrient((per.protein * portions).fmt(), "Eiweiß g")
                    Nutrient((per.carbs * portions).fmt(), "Kohlenh. g")
                    Nutrient((per.fat * portions).fmt(), "Fett g")
                }
            }
            MealPicker(selected = meal, names = mealNames, onSelect = { meal = it })
            Button(onClick = { onAdd(portions, meal) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text("Als Mahlzeit loggen")
            }
        }
    }
}

/** Review of a spoken meal before logging (AI result, editable grams). */
@Composable
private fun VoiceMealSheet(
    items: List<VoiceItem>,
    busy: Boolean,
    offline: Boolean,
    mealNames: Map<String, String>,
    onToggle: (Int) -> Unit,
    onGrams: (Int, Float) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Erkannte Mahlzeit", style = MaterialTheme.typography.titleLarge)
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ordne Lebensmittel zu …", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }
            if (items.isEmpty()) {
                Text("Nichts erkannt. Versuch es mit „zwei Eier und ein Toast“.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            if (offline) {
                Text(
                    "Ohne KI erkannt – Mengen sind Richtwerte, bitte kurz prüfen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                items.forEachIndexed { index, item ->
                    SegmentedListItem(
                        onClick = { onToggle(item.id) },
                        enabled = item.food != null,
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, items.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        leadingContent = { Checkbox(checked = item.include, onCheckedChange = { onToggle(item.id) }, enabled = item.food != null) },
                        supportingContent = {
                            Text(
                                item.food?.let { f ->
                                    listOfNotNull(f.name, f.brand).joinToString(" · ") + " · ${item.kcal.roundToInt()} kcal · " +
                                        item.meal.label(mealNames) +
                                        if (item.fromHistory) " · deine übliche Menge" else ""
                                } ?: "Nicht gefunden – bitte manuell suchen",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            OutlinedTextField(
                                value = item.grams.roundToInt().toString(),
                                onValueChange = { v -> v.filter(Char::isDigit).toFloatOrNull()?.let { onGrams(item.id, it) } },
                                suffix = { Text("g") },
                                singleLine = true,
                                enabled = item.food != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(92.dp),
                            )
                        },
                    ) { Text(item.spoken.replaceFirstChar { it.uppercase() }) }
                }
            }
            val count = items.count { it.include }
            Button(
                onClick = onSave,
                enabled = count > 0,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (count == 0) "Nichts ausgewählt"
                    else "$count ${if (count == 1) "Eintrag" else "Einträge"} speichern · ${items.filter { it.include }.sumOf { it.kcal.toDouble() }.roundToInt()} kcal",
                )
            }
        }
    }
}
