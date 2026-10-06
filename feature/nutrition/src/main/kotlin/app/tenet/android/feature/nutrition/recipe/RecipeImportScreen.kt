package app.tenet.android.feature.nutrition.recipe

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.sync.AccountRepository
import app.tenet.android.core.data.sync.ImportedRecipe
import app.tenet.android.core.data.sync.RecipeImporter
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.TooltipIconButton
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable copy of the import result (the preview is the editor). */
data class ImportEdit(
    val title: String,
    val category: String,
    val servings: Int,
    val minutes: String,
    val ingredients: List<EditLine>,
    val steps: List<EditLine>,
    /** Row just added: gets the keyboard focus once. */
    val focusKey: Long? = null,
)

/** One link of a batch import. */
data class BatchItem(
    val url: String,
    val status: Status = Status.WAITING,
    val title: String? = null,
    val error: String? = null,
    val recipeId: String? = null,
) {
    enum class Status { WAITING, RUNNING, DONE, FAILED }
}

data class RecipeImportState(
    val fromText: Boolean = false,
    /** "Mehrere": several links, imported and saved one after another. */
    val batch: Boolean = false,
    val batchItems: List<BatchItem> = emptyList(),
    val edit: ImportEdit? = null,
    val input: String = "",
    /** Progress text while working; null = idle. */
    val step: String? = null,
    val result: ImportedRecipe? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class RecipeImportViewModel @Inject constructor(
    private val importer: RecipeImporter,
    accounts: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipeImportState())
    val state: StateFlow<RecipeImportState> = _state
    val signedIn: Boolean = accounts.account.value != null

    private val _saved = Channel<String>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private var started = false

    /** Link(s) shared from TikTok, Instagram, the browser …: start right away. */
    fun start(url: String) {
        if (started || url.isBlank()) return
        started = true
        val links = linksIn(url)
        if (links.size > 1) {
            _state.update { it.copy(input = links.joinToString("\n"), fromText = false, batch = true) }
            runBatch()
        } else {
            _state.update { it.copy(input = url, fromText = false, batch = false) }
            run()
        }
    }

    fun setMode(text: Boolean) = _state.update { it.copy(fromText = text, batch = false, error = null) }

    /** 0 = Link, 1 = Mehrere, 2 = Text. */
    fun setModeIndex(index: Int) = _state.update { it.copy(fromText = index == 2, batch = index == 1, error = null) }

    // ---- Batch: several links in a row --------------------------------------------

    fun linksIn(text: String): List<String> =
        Regex("https?://\\S+").findAll(text).map { it.value.trimEnd(',', '.', ')', ']') }.distinct().toList()

    private var batchJob: kotlinx.coroutines.Job? = null

    /** Imports every link one after another and saves each recipe right away. */
    fun runBatch() {
        if (batchJob?.isActive == true) return
        val links = linksIn(_state.value.input)
        if (links.isEmpty()) {
            _state.update { it.copy(error = "Keine Links gefunden.") }
            return
        }
        _state.update { s ->
            // Keep finished ones when started again (e.g. after adding links).
            val done = s.batchItems.filter { it.status == BatchItem.Status.DONE }.associateBy { it.url }
            s.copy(error = null, batchItems = links.map { done[it] ?: BatchItem(it) })
        }
        batchJob = viewModelScope.launch { processBatch() }
    }

    fun retry(url: String) {
        _state.update { s -> s.copy(batchItems = s.batchItems.map { if (it.url == url) BatchItem(url) else it }) }
        if (batchJob?.isActive != true) batchJob = viewModelScope.launch { processBatch() }
    }

    private suspend fun processBatch() {
        while (true) {
            val item = _state.value.batchItems.firstOrNull { it.status == BatchItem.Status.WAITING } ?: break
            setItem(item.url) { it.copy(status = BatchItem.Status.RUNNING) }
            val imported = importer.fromUrl(item.url)
            val recipe = imported.getOrNull()
            if (recipe == null) {
                setItem(item.url) { it.copy(status = BatchItem.Status.FAILED, error = imported.exceptionOrNull()?.message ?: "Import fehlgeschlagen") }
                continue
            }
            importer.save(recipe)
                .onSuccess { id -> setItem(item.url) { it.copy(status = BatchItem.Status.DONE, title = recipe.title, recipeId = id) } }
                .onFailure { e -> setItem(item.url) { it.copy(status = BatchItem.Status.FAILED, title = recipe.title, error = e.message ?: "Speichern fehlgeschlagen") } }
        }
    }

    private fun setItem(url: String, change: (BatchItem) -> BatchItem) =
        _state.update { s -> s.copy(batchItems = s.batchItems.map { if (it.url == url) change(it) else it }) }

    val batchRunning: Boolean get() = batchJob?.isActive == true
    fun setInput(value: String) = _state.update { it.copy(input = value, error = null) }

    fun run() {
        val s = _state.value
        if (s.step != null) return
        viewModelScope.launch {
            _state.update { it.copy(step = "Wird geladen …", error = null, result = null) }
            val onStep: (String) -> Unit = { msg -> _state.update { it.copy(step = msg) } }
            val result = if (s.fromText) importer.fromText(s.input, onStep) else importer.fromUrl(s.input, onStep)
            val r = result.getOrNull()
            _state.update {
                it.copy(
                    step = null,
                    result = r,
                    edit = r?.let { rec ->
                        ImportEdit(
                            title = rec.title,
                            category = rec.category,
                            servings = rec.servings,
                            minutes = rec.minutes.takeIf { m -> m > 0 }?.toString().orEmpty(),
                            ingredients = rec.ingredients.map { line -> EditLine(key(), line) },
                            steps = rec.steps.map { line -> EditLine(key(), line) },
                        )
                    },
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private var nextKey = 0L
    private fun key() = nextKey++

    private fun edit(block: ImportEdit.() -> ImportEdit) = _state.update { s -> s.copy(edit = s.edit?.block()) }

    fun onTitle(v: String) = edit { copy(title = v) }
    fun onCategory(v: String) = edit { copy(category = v) }
    fun onServings(delta: Int) = edit { copy(servings = (servings + delta).coerceIn(1, 24)) }
    fun onMinutes(v: String) = edit { copy(minutes = v.filter(Char::isDigit).take(4)) }
    fun onIngredient(k: Long, v: String) = edit { copy(ingredients = ingredients.map { if (it.key == k) it.copy(text = v) else it }) }
    fun addIngredient() = edit { key().let { k -> copy(ingredients = ingredients + EditLine(k, ""), focusKey = k) } }
    fun removeIngredient(k: Long) = edit { copy(ingredients = ingredients.filterNot { it.key == k }) }
    fun onStep(k: Long, v: String) = edit { copy(steps = steps.map { if (it.key == k) it.copy(text = v) else it }) }
    fun addStep() = edit { key().let { k -> copy(steps = steps + EditLine(k, ""), focusKey = k) } }
    fun removeStep(k: Long) = edit { copy(steps = steps.filterNot { it.key == k }) }
    fun moveStep(k: Long, by: Int) = edit {
        val i = steps.indexOfFirst { it.key == k }
        val j = i + by
        if (i < 0 || j !in steps.indices) this else copy(steps = steps.toMutableList().apply { add(j, removeAt(i)) })
    }
    fun focused() = edit { copy(focusKey = null) }

    fun save() {
        val base = _state.value.result ?: return
        val e = _state.value.edit
        // The edited version wins; the diet tag follows the edited ingredients.
        val r = if (e == null) base else {
            val ingredients = e.ingredients.map { it.text.trim() }.filter { it.isNotEmpty() }
            val tags = app.tenet.android.core.common.DietDetector.correct(base.tags, ingredients)
            base.copy(
                title = e.title.trim().ifBlank { base.title },
                category = e.category,
                servings = e.servings,
                minutes = e.minutes.toIntOrNull() ?: 0,
                ingredients = ingredients,
                steps = e.steps.map { it.text.trim() }.filter { it.isNotEmpty() },
                tags = tags,
                vegetarian = tags.firstOrNull() in setOf("Vegetarisch", "Vegan"),
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            importer.save(r)
                .onSuccess { _saved.send(it) }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: "Speichern fehlgeschlagen") } }
        }
    }

    fun discard() = _state.update { RecipeImportState(fromText = it.fromText) }
}

/**
 * "Rezept importieren": link (TikTok, Instagram, any recipe site) or text →
 * AI like in Saffron → preview → save (to Saffron's Firebase when signed in).
 */
@Composable
fun RecipeImportScreen(
    initialUrl: String,
    onBack: () -> Unit,
    onSaved: (recipeId: String) -> Unit,
    /** Batch: open a saved recipe (stays on the list when coming back). */
    onOpenRecipe: (recipeId: String) -> Unit = {},
    viewModel: RecipeImportViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialUrl) { viewModel.start(initialUrl) }
    LaunchedEffect(Unit) { viewModel.saved.collect(onSaved) }
    val s by viewModel.state.collectAsStateWithLifecycle()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Rezept importieren") },
                subtitle = { Text(if (viewModel.signedIn) "Landet auch in Saffron" else "Als Gast nur auf diesem Handy") },
            )
        },
        bottomBar = {
            val r = s.result
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (r != null) {
                        OutlinedButton(onClick = viewModel::discard, enabled = !s.saving, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                            Text("Verwerfen")
                        }
                        Button(onClick = viewModel::save, enabled = !s.saving, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                            if (s.saving) LoadingIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Speichern")
                        }
                    } else if (s.batch) {
                        val running = s.batchItems.any { it.status == BatchItem.Status.RUNNING || it.status == BatchItem.Status.WAITING }
                        val finished = s.batchItems.isNotEmpty() && !running
                        if (finished) {
                            Button(onClick = onBack, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                                Text("Fertig · ${s.batchItems.count { it.status == BatchItem.Status.DONE }} gespeichert")
                            }
                        } else {
                            val count = viewModel.linksIn(s.input).size
                            Button(
                                onClick = viewModel::runBatch,
                                enabled = !running && count > 0,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (running) LoadingIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                else Text(if (count == 1) "1 Rezept importieren" else "$count Rezepte importieren")
                            }
                        }
                    } else {
                        Button(
                            onClick = viewModel::run,
                            enabled = s.step == null && s.input.isNotBlank(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Importieren") }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val r = s.result
            AnimatedContent(targetState = Triple(r != null, s.step != null, s.fromText), label = "import") { (hasResult, busy, _) ->
                when {
                    s.batch -> BatchImport(s, viewModel, clipboard.getText()?.text, onOpenRecipe)
                    hasResult && r != null -> s.edit?.let { e -> ImportPreview(r, e, viewModel, viewModel.signedIn) }
                    busy -> Column(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LoadingIndicator(Modifier.size(72.dp))
                        Text(s.step.orEmpty(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text("Das dauert meist 5–20 Sekunden.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ImportModes(s, viewModel)
                        if (s.fromText) {
                            OutlinedTextField(
                                value = s.input,
                                onValueChange = viewModel::setInput,
                                label = { Text("Rezepttext") },
                                placeholder = { Text("Rezept aus einer Nachricht, Notiz oder Webseite einfügen") },
                                leadingIcon = { Icon(Icons.Outlined.Notes, contentDescription = null) },
                                minLines = 8,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            OutlinedTextField(
                                value = s.input,
                                onValueChange = viewModel::setInput,
                                label = { Text("Link") },
                                placeholder = { Text("TikTok, Instagram oder Rezeptseite") },
                                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                trailingIcon = {
                                    TooltipIconButton(Icons.Outlined.ContentPaste, "Einfügen", {
                                        clipboard.getText()?.text?.let(viewModel::setInput)
                                    })
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Tipp: In TikTok, Instagram oder im Browser auf „Teilen“ → Tenet tippen, dann startet der Import direkt.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            s.error?.let {
                TenetCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ImportPreview(r: ImportedRecipe, e: ImportEdit, vm: RecipeImportViewModel, signedIn: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (r.thumbnailUrl.startsWith("http")) {
            AsyncImage(
                model = r.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).heightIn(max = 320.dp).clip(MaterialTheme.shapes.extraLarge),
            )
        }
        Text(
            "Alles hier kannst du vor dem Speichern anpassen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Title as a large, quiet field.
        androidx.compose.material3.TextField(
            value = e.title,
            onValueChange = vm::onTitle,
            label = { Text("Titel") },
            textStyle = MaterialTheme.typography.titleLarge,
            shape = MaterialTheme.shapes.large,
            colors = softFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        ImportMeta(e, vm)
        if (r.tags.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.tags.forEach { tag -> RecipeTagChip(tag) }
            }
        }
        if (r.ingredients.isEmpty() && e.ingredients.isEmpty()) {
            // Captions like "full recipe on my website (link in bio)".
            TenetCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Im Beitrag stehen keine Zutaten – das ganze Rezept liegt meist auf der Seite des Autors (Link in Bio). " +
                        "Du kannst sie hier selbst eintragen oder später ergänzen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        SectionHeader("Zutaten", e.ingredients.size)
        e.ingredients.forEach { line ->
            key(line.key) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(e.focusKey) { if (e.focusKey == line.key) { focus.requestFocus(); vm.focused() } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextField(
                        value = line.text,
                        onValueChange = { vm.onIngredient(line.key, it) },
                        placeholder = { Text("z. B. 200 g Mehl") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        colors = softFieldColors(),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    TooltipIconButton(Icons.Outlined.Close, "Zutat entfernen", { vm.removeIngredient(line.key) })
                }
            }
        }
        AddButton("Zutat hinzufügen", vm::addIngredient)
        SectionHeader("Schritte", e.steps.size)
        e.steps.forEachIndexed { index, line ->
            key(line.key) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(e.focusKey) { if (e.focusKey == line.key) { focus.requestFocus(); vm.focused() } }
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(top = 14.dp).size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge) }
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.TextField(
                        value = line.text,
                        onValueChange = { vm.onStep(line.key, it) },
                        placeholder = { Text("Was ist zu tun?") },
                        minLines = 2,
                        shape = MaterialTheme.shapes.large,
                        colors = softFieldColors(),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    Column {
                        if (index > 0) TooltipIconButton(Icons.Outlined.KeyboardArrowUp, "Nach oben", { vm.moveStep(line.key, -1) })
                        TooltipIconButton(Icons.Outlined.Close, "Schritt entfernen", { vm.removeStep(line.key) })
                    }
                }
            }
        }
        AddButton("Schritt hinzufügen", vm::addStep)
        if (signedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Wird in deinem Konto gespeichert und erscheint auch in Saffron.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val IMPORT_CATEGORIES = listOf("Hähnchen", "Pute", "Rind", "Fisch", "Pasta", "Reis", "Kartoffeln", "Mexikanisch", "Asiatisch", "Vegetarisch", "Andere")

/** Category (Saffron's list), portions and time in one row of chips. */
@Composable
private fun ImportMeta(e: ImportEdit, vm: RecipeImportViewModel) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var menu by remember { mutableStateOf(false) }
        Box {
            androidx.compose.material3.FilterChip(
                selected = true,
                onClick = { menu = true },
                label = { Text(e.category.ifBlank { "Kategorie" }) },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            )
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                IMPORT_CATEGORIES.forEach { c ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(c) }, onClick = { menu = false; vm.onCategory(c) })
                }
            }
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(Icons.Outlined.Remove, "Weniger Portionen", { vm.onServings(-1) })
                Text("${e.servings} Portionen", style = MaterialTheme.typography.labelLarge)
                TooltipIconButton(Icons.Outlined.Add, "Mehr Portionen", { vm.onServings(1) })
            }
        }
        androidx.compose.material3.TextField(
            value = e.minutes,
            onValueChange = vm::onMinutes,
            placeholder = { Text("Min") },
            suffix = { Text("Min") },
            leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = CircleShape,
            colors = softFieldColors(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.width(130.dp),
        )
    }
}

@Composable
private fun ImportModes(s: RecipeImportState, vm: RecipeImportViewModel) {
    SegmentedSelector(
        segments = listOf(Segment("Link"), Segment("Mehrere"), Segment("Text")),
        selectedIndex = when {
            s.batch -> 1
            s.fromText -> 2
            else -> 0
        },
        onSelect = vm::setModeIndex,
    )
}

/** Several links: paste them, then each is imported and saved; status per link. */
@Composable
private fun BatchImport(s: RecipeImportState, vm: RecipeImportViewModel, clip: String?, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val started = s.batchItems.isNotEmpty()
        if (!started) {
            ImportModes(s, vm)
            OutlinedTextField(
                value = s.input,
                onValueChange = vm::setInput,
                label = { Text("Links") },
                placeholder = { Text("Einen Link pro Zeile – oder einfach einen Text mit Links einfügen") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                trailingIcon = {
                    TooltipIconButton(Icons.Outlined.ContentPaste, "Einfügen", {
                        clip?.let { vm.setInput((s.input.trimEnd() + "\n" + it).trim()) }
                    })
                },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            val n = vm.linksIn(s.input).size
            Text(
                if (n == 0) "Jeder Link wird importiert und direkt gespeichert – danach kannst du jedes Rezept noch bearbeiten."
                else "$n ${if (n == 1) "Link" else "Links"} erkannt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val done = s.batchItems.count { it.status == BatchItem.Status.DONE || it.status == BatchItem.Status.FAILED }
            Text("$done von ${s.batchItems.size} verarbeitet", style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.LinearWavyProgressIndicator(
                progress = { done.toFloat() / s.batchItems.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.ListItemDefaults.SegmentedGap)) {
                s.batchItems.forEachIndexed { i, item ->
                    androidx.compose.material3.SegmentedListItem(
                        onClick = { item.recipeId?.let(onOpen) },
                        shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(i, s.batchItems.size),
                        colors = app.tenet.android.core.designsystem.theme.tenetListColors(),
                        leadingContent = {
                            when (item.status) {
                                BatchItem.Status.RUNNING -> LoadingIndicator(Modifier.size(28.dp))
                                BatchItem.Status.DONE -> Icon(Icons.Outlined.CloudDone, contentDescription = "Gespeichert", tint = MaterialTheme.colorScheme.primary)
                                BatchItem.Status.FAILED -> Icon(Icons.Outlined.Close, contentDescription = "Fehler", tint = MaterialTheme.colorScheme.error)
                                BatchItem.Status.WAITING -> Icon(Icons.Outlined.Link, contentDescription = "Wartet", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        supportingContent = {
                            Text(
                                when (item.status) {
                                    BatchItem.Status.WAITING -> "Wartet"
                                    BatchItem.Status.RUNNING -> "KI liest das Rezept …"
                                    BatchItem.Status.DONE -> item.url.substringAfter("://").take(48)
                                    BatchItem.Status.FAILED -> item.error.orEmpty()
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            if (item.status == BatchItem.Status.FAILED) {
                                androidx.compose.material3.TextButton(onClick = { vm.retry(item.url) }, shapes = ButtonDefaults.shapes()) { Text("Erneut") }
                            }
                        },
                    ) {
                        Text(item.title ?: item.url.substringAfter("://").substringBefore("?").take(40), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
