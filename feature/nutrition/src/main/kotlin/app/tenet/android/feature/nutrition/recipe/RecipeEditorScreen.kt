package app.tenet.android.feature.nutrition.recipe

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.rememberSheetState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import app.tenet.android.core.database.entity.RecipeIngredient
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.ContentPaste
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.designsystem.component.SectionHeader
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.feature.nutrition.fmt
import app.tenet.android.feature.nutrition.parseAmount
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** Recipe editor: ingredients reference the food database, nutrition is computed live. */
@Composable
fun RecipeEditorScreen(
    recipeId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: RecipeEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }
    LaunchedEffect(Unit) { viewModel.saved.collect(onSaved) }
    val state by viewModel.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    // ^ immediate: text fields must see their own edits in the same frame,
    // otherwise fast typing can drop characters.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<RecipeIngredient?>(null) }
    var importDialog by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.onPhoto(uri.toString())
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text(if (state.isNew) "Neues Rezept" else "Rezept bearbeiten") },
                subtitle = { Text("${state.kcalPerServing} kcal · ${state.proteinPerServing} g Eiweiß pro Portion") },
                actions = {
                    TooltipIconButton(Icons.Outlined.ContentPaste, "Aus Text importieren", { importDialog = true })
                    FilledIconButton(onClick = viewModel::save, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Outlined.Check, contentDescription = "Speichern")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            TenetLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.photoUri != null) {
                Box {
                    AsyncImage(
                        model = state.photoUri,
                        contentDescription = "Rezeptfoto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.extraLarge),
                    )
                    FilledIconButton(
                        onClick = { viewModel.onPhoto(null) },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) { Icon(Icons.Outlined.Close, contentDescription = "Foto entfernen") }
                }
            } else {
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Foto hinzufügen")
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Titel") },
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Portionen", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { viewModel.onServings(state.servings - 1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Weniger")
                }
                Text("${state.servings}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                FilledTonalIconButton(onClick = { viewModel.onServings(state.servings + 1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Add, contentDescription = "Mehr")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.minutes,
                    onValueChange = viewModel::onMinutes,
                    label = { Text("Zeit") },
                    suffix = { Text("Min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.tags,
                    onValueChange = viewModel::onTags,
                    label = { Text("Tags (mit Komma)") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
            }

            if (state.importing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Zutaten werden zugeordnet …", style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.importInfo?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp)) }
            }
            SectionHeader("Zutaten · ${state.ingredients.size}")
            Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                state.ingredients.forEachIndexed { index, item ->
                    val unmatched = item.foodId == null && item.kcalPer100 == 0f
                    SegmentedListItem(
                        colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                        onClick = {
                            replacing = item
                            viewModel.onPickerQuery(item.displayName)
                            picker = true
                        },
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, state.ingredients.size),
                        supportingContent = {
                            if (unmatched) {
                                Text("Keine Nährwerte – antippen zum Zuordnen", color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(
                                    listOfNotNull(item.product, "${(item.kcalPer100 * item.grams / 100f).roundToInt()} kcal").joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = item.grams.fmt(),
                                    onValueChange = { v -> v.parseAmount()?.let { viewModel.setGrams(item, it) } },
                                    suffix = { Text("g") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(96.dp),
                                )
                                TooltipIconButton(Icons.Outlined.Delete, "Zutat entfernen", { viewModel.removeIngredient(item) })
                            }
                        },
                    ) { Text(item.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            OutlinedButton(onClick = { picker = true }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Zutat hinzufügen")
            }

            SectionHeader("Zubereitung")
            OutlinedTextField(
                value = state.steps,
                onValueChange = viewModel::onSteps,
                label = { Text("Ein Schritt pro Zeile") },
                supportingText = { Text("Zeitangaben wie „10 Min“ werden im Kochmodus zum Timer.") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (picker) {
        IngredientPickerSheet(
            query = state.pickerQuery,
            results = state.pickerResults,
            loading = state.pickerLoading,
            onQuery = viewModel::onPickerQuery,
            onDismiss = {
                picker = false
                replacing = null
            },
            onPick = { food, grams ->
                val old = replacing
                if (old != null) viewModel.replaceIngredient(old, food, grams) else viewModel.addIngredient(food, grams)
                picker = false
                replacing = null
            },
        )
    }

    if (importDialog) {
        ImportTextDialog(
            onDismiss = { importDialog = false },
            onImport = {
                importDialog = false
                viewModel.importText(it)
            },
        )
    }
}

/** Paste a recipe (website, chat, notes); parsed and matched on import. */
@Composable
private fun ImportTextDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
        title = { Text("Rezept aus Text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Rezept einfügen – Titel, Zutaten mit Mengen und Zubereitung werden erkannt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Pfannkuchen\nZutaten\n250 g Mehl\n…") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                                ?.let { text = it }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Aus Zwischenablage")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(text) }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text("Importieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
    )
}

/** Search the food database (local, then Open Food Facts) and pick an amount. */
@Composable
private fun IngredientPickerSheet(
    query: String,
    results: List<Food>,
    loading: Boolean,
    onQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Food, Float) -> Unit,
) {
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    var chosen by remember { mutableStateOf<Food?>(null) }
    var grams by remember { mutableStateOf("100") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val food = chosen
            if (food == null) {
                Text("Zutat suchen", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text("z. B. Haferflocken") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) TenetLoading()
                LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap),
                ) {
                    itemsIndexed(results, key = { _, f -> f.id }) { index, f ->
                        SegmentedListItem(
                            colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                            onClick = {
                                chosen = f
                                grams = f.servingSizeG?.fmt() ?: "100"
                            },
                            shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, results.size),
                            supportingContent = {
                                Text(listOfNotNull(f.brand, "${f.kcalPer100.roundToInt()} kcal/100 g").joinToString(" · "))
                            },
                        ) { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            } else {
                Text(food.name, style = MaterialTheme.typography.headlineSmall)
                val g = grams.parseAmount() ?: 0f
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = { Text("Menge") },
                    suffix = { Text("g") },
                    supportingText = { Text("${(food.kcalPer100 * g / 100f).roundToInt()} kcal · E ${(food.proteinPer100 * g / 100f).fmt()} g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onPick(food, g) }, enabled = g > 0f, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text("Übernehmen")
                }
            }
        }
    }
}
