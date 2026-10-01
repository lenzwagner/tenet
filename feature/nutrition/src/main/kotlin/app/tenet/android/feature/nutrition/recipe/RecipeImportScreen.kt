package app.tenet.android.feature.nutrition.recipe

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

data class RecipeImportState(
    val fromText: Boolean = false,
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

    /** Link shared from TikTok, Instagram, the browser …: start right away. */
    fun start(url: String) {
        if (started || url.isBlank()) return
        started = true
        _state.update { it.copy(input = url, fromText = false) }
        run()
    }

    fun setMode(text: Boolean) = _state.update { it.copy(fromText = text, error = null) }
    fun setInput(value: String) = _state.update { it.copy(input = value, error = null) }

    fun run() {
        val s = _state.value
        if (s.step != null) return
        viewModelScope.launch {
            _state.update { it.copy(step = "Wird geladen …", error = null, result = null) }
            val onStep: (String) -> Unit = { msg -> _state.update { it.copy(step = msg) } }
            val result = if (s.fromText) importer.fromText(s.input, onStep) else importer.fromUrl(s.input, onStep)
            _state.update {
                it.copy(step = null, result = result.getOrNull(), error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun save() {
        val r = _state.value.result ?: return
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
                    hasResult && r != null -> ImportPreview(r, viewModel.signedIn)
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
                        SegmentedSelector(
                            segments = listOf(Segment("Link"), Segment("Text")),
                            selectedIndex = if (s.fromText) 1 else 0,
                            onSelect = { viewModel.setMode(it == 1) },
                        )
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
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ImportPreview(r: ImportedRecipe, signedIn: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (r.thumbnailUrl.startsWith("http")) {
            AsyncImage(
                model = r.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).heightIn(max = 320.dp).clip(MaterialTheme.shapes.extraLarge),
            )
        }
        Text(r.title, style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(
            listOfNotNull(r.category, "${r.servings} Portionen", r.minutes.takeIf { it > 0 }?.let { "$it Min" }, "vegetarisch".takeIf { r.vegetarian })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.tags.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.tags.forEach { tag -> RecipeTagChip(tag) }
            }
        }
        if (r.ingredients.isEmpty()) {
            // Captions like "full recipe on my website (link in bio)".
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Im Beitrag stehen keine Zutaten – das ganze Rezept liegt meist auf der Seite des Autors (Link in Bio). " +
                        "Du kannst es trotzdem speichern und später ergänzen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (r.ingredients.size == 1) "1 Zutat" else "${r.ingredients.size} Zutaten", style = MaterialTheme.typography.titleSmall)
                    r.ingredients.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (r.steps.size == 1) "1 Schritt" else "${r.steps.size} Schritte", style = MaterialTheme.typography.titleSmall)
                r.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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
