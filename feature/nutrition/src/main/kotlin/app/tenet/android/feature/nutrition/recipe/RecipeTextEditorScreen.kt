package app.tenet.android.feature.nutrition.recipe

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.CookStep
import app.tenet.android.core.data.RecipeRepository
import app.tenet.android.core.data.sync.AccountRepository
import app.tenet.android.core.database.entity.Recipe
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable row; [key] keeps focus and text stable while rows move. */
data class EditLine(val key: Long, val text: String)

data class EditStep(val key: Long, val text: String, val minutes: String, val ingredients: List<String>)

data class RecipeTextEditState(
    val loading: Boolean = true,
    val title: String = "",
    val ingredients: List<EditLine> = emptyList(),
    val steps: List<EditStep> = emptyList(),
    val saffron: Boolean = false,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    /** Row just added: gets the keyboard focus once. */
    val focusKey: Long? = null,
)

@HiltViewModel
class RecipeTextEditViewModel @Inject constructor(
    private val recipes: RecipeRepository,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipeTextEditState())
    val state: StateFlow<RecipeTextEditState> = _state

    /** Result message after saving; the screen closes then. */
    private val _done = Channel<String>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private var recipe: Recipe? = null
    private var nextKey = 0L
    private fun key() = nextKey++

    fun load(id: String) {
        if (recipe != null) return
        viewModelScope.launch {
            val d = recipes.recipe(id) ?: return@launch
            recipe = d.recipe
            _state.value = RecipeTextEditState(
                loading = false,
                title = d.recipe.title,
                ingredients = d.ingredientLines.map { EditLine(key(), it) },
                steps = d.cookSteps.map { EditStep(key(), it.text, it.minutes.takeIf { m -> m > 0 }?.toString().orEmpty(), it.ingredients) },
                saffron = d.fromSaffron && accounts.account.value != null,
            )
        }
    }

    private fun edit(block: RecipeTextEditState.() -> RecipeTextEditState) = _state.update { it.block().copy(dirty = true) }

    fun onTitle(v: String) = edit { copy(title = v) }

    fun onIngredient(key: Long, v: String) = edit { copy(ingredients = ingredients.map { if (it.key == key) it.copy(text = v) else it }) }
    fun addIngredient() = edit { key().let { k -> copy(ingredients = ingredients + EditLine(k, ""), focusKey = k) } }
    fun focused() = _state.update { it.copy(focusKey = null) }
    fun removeIngredient(key: Long) = edit { copy(ingredients = ingredients.filterNot { it.key == key }) }

    fun onStep(key: Long, v: String) = edit { copy(steps = steps.map { if (it.key == key) it.copy(text = v) else it }) }
    fun onStepMinutes(key: Long, v: String) =
        edit { copy(steps = steps.map { if (it.key == key) it.copy(minutes = v.filter(Char::isDigit).take(3)) else it }) }
    fun addStep() = edit { key().let { k -> copy(steps = steps + EditStep(k, "", "", emptyList()), focusKey = k) } }
    fun removeStep(key: Long) = edit { copy(steps = steps.filterNot { it.key == key }) }
    fun moveStep(key: Long, by: Int) = edit {
        val i = steps.indexOfFirst { it.key == key }
        val j = i + by
        if (i < 0 || j !in steps.indices) this
        else copy(steps = steps.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun save() {
        val r = recipe ?: return
        val s = _state.value
        if (s.title.isBlank() || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val cloud = recipes.updateContent(
                r,
                s.title,
                s.ingredients.map { it.text },
                s.steps.map { CookStep(it.text, it.minutes.toIntOrNull() ?: 0, it.ingredients) },
            )
            _done.send(
                when {
                    !s.saffron -> "Rezept gespeichert"
                    cloud -> "Gespeichert – auch in deinem Konto"
                    else -> "Gespeichert – wird synchronisiert, sobald du online bist"
                },
            )
        }
    }
}

/**
 * Edit title, free-text ingredients and steps (with timer) of an imported
 * or Saffron recipe. Saffron recipes are written back to Firebase in
 * Saffron's format, so Saffron shows the change too.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecipeTextEditorScreen(
    recipeId: String,
    onBack: () -> Unit,
    /** Saved: close and show [message] on the detail page. */
    onSaved: (message: String) -> Unit,
    viewModel: RecipeTextEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }
    val s by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.done.collect(onSaved) }
    // Leaving stores the changes (no "save?" question; also syncs Saffron).
    val leave = { if (s.dirty && s.title.isNotBlank()) viewModel.save() else onBack() }
    BackHandler(enabled = s.dirty && !s.saving) { leave() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { app.tenet.android.core.designsystem.component.TenetSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", leave) },
                title = { Text("Rezept bearbeiten") },
                subtitle = { if (s.saffron) Text("Änderungen werden mit deinem Konto synchronisiert") },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = !s.saving && s.title.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (s.saving) app.tenet.android.core.designsystem.component.TenetSpinner(Modifier.size(20.dp)) else Text("Speichern")
                    }
                },
            )
        },
    ) { padding ->
        if (s.loading) {
            TenetLoading(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "title") {
                app.tenet.android.core.designsystem.component.TenetTextField(
                    value = s.title,
                    onValueChange = viewModel::onTitle,
                    label = { Text("Titel") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    isError = s.title.isBlank(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "ing-h") { SectionHeader("Zutaten", s.ingredients.size) }
            itemsIndexed(s.ingredients, key = { _, it -> "i${it.key}" }) { _, line ->
                val focus = remember { FocusRequester() }
                LaunchedEffect(s.focusKey) {
                    if (s.focusKey == line.key) {
                        focus.requestFocus()
                        viewModel.focused()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = line.text,
                        onValueChange = { viewModel.onIngredient(line.key, it) },
                        placeholder = { Text("z. B. 200 g Mehl") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        colors = softFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    TooltipIconButton(Icons.Outlined.Close, "Zutat entfernen", { viewModel.removeIngredient(line.key) })
                }
            }
            item(key = "ing-add") { AddButton("Zutat hinzufügen", viewModel::addIngredient) }

            item(key = "step-h") { SectionHeader("Schritte", s.steps.size) }
            itemsIndexed(s.steps, key = { _, it -> "s${it.key}" }) { index, step ->
                StepCard(
                    focusNow = s.focusKey == step.key,
                    onFocused = viewModel::focused,
                    number = index + 1,
                    step = step,
                    first = index == 0,
                    last = index == s.steps.lastIndex,
                    onText = { viewModel.onStep(step.key, it) },
                    onMinutes = { viewModel.onStepMinutes(step.key, it) },
                    onMove = { viewModel.moveStep(step.key, it) },
                    onRemove = { viewModel.removeStep(step.key) },
                )
            }
            item(key = "step-add") { AddButton("Schritt hinzufügen", viewModel::addStep) }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Deine Änderungen am Rezept gehen verloren.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; viewModel.save() }) { Text("Speichern") } },
        )
    }
}

@Composable
internal fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AddButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepCard(
    focusNow: Boolean,
    onFocused: () -> Unit,
    number: Int,
    step: EditStep,
    first: Boolean,
    last: Boolean,
    onText: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusNow) {
        if (focusNow) {
            focus.requestFocus()
            onFocused()
        }
    }
    TenetCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialShapes.Cookie6Sided.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("$number", style = MaterialTheme.typography.titleSmall) }
                }
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = step.minutes,
                    onValueChange = onMinutes,
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    placeholder = { Text("Min") },
                    singleLine = true,
                    shape = CircleShape,
                    colors = softFieldColors(MaterialTheme.colorScheme.secondaryContainer),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(112.dp),
                )
                Spacer(Modifier.weight(1f))
                if (!first) TooltipIconButton(Icons.Outlined.KeyboardArrowUp, "Nach oben", { onMove(-1) })
                if (!last) TooltipIconButton(Icons.Outlined.KeyboardArrowDown, "Nach unten", { onMove(1) })
                TooltipIconButton(Icons.Outlined.Close, "Schritt entfernen", onRemove)
            }
            TextField(
                value = step.text,
                onValueChange = onText,
                placeholder = { Text("Was ist zu tun?") },
                shape = MaterialTheme.shapes.large,
                colors = softFieldColors(MaterialTheme.colorScheme.surfaceContainerHighest),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (step.ingredients.isNotEmpty()) {
                Text(
                    step.ingredients.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Filled field without underline: calm tonal rows instead of many outlines. */
@Composable
internal fun softFieldColors(container: Color = MaterialTheme.colorScheme.surfaceContainerHigh) = TextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
