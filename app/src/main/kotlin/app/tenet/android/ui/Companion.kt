package app.tenet.android.ui

import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.companion.CompanionAgent
import app.tenet.android.core.designsystem.component.rememberDictation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CompanionViewModel @Inject constructor(private val agent: CompanionAgent) : ViewModel() {
    init {
        viewModelScope.launch { runCatching { agent.warmUp() } }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _request = MutableStateFlow<String?>(null)
    /** What is being worked on right now (shown instead of the input). */
    val request: StateFlow<String?> = _request.asStateFlow()
    private val _step = MutableStateFlow<String?>(null)
    /** The real step the agent is on (once the model has decided), else null. */
    val step: StateFlow<String?> = _step.asStateFlow()
    private val _reply = MutableStateFlow<String?>(null)
    val reply: StateFlow<String?> = _reply.asStateFlow()
    /** Something was stored: close the chat and confirm briefly. */
    private val _done = Channel<String>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()
    private val _navigate = Channel<CompanionAgent.Destination>(Channel.BUFFERED)
    val navigate = _navigate.receiveAsFlow()

    /** Runs in the background: the page stays where it is. */
    fun send(text: String, name: String) {
        if (text.isBlank() || _busy.value) return
        _busy.value = true
        _request.value = text
        _reply.value = null
        _step.value = null
        viewModelScope.launch {
            val r = runCatching { agent.handle(text, name) { _step.value = it } }.getOrElse { CompanionAgent.Reply("Ups, das ging schief: ${it.message}") }
            if (r.acted) {
                _reply.value = null
                _request.value = null
                _done.send(r.text)
            } else {
                _reply.value = r.text
            }
            _busy.value = false
            _step.value = null
            r.navigate?.let { _navigate.send(it) }
        }
    }

    fun clearReply() { _reply.value = null }
}

/**
 * "Tenny": a small creature walking around the app. Every few minutes it
 * runs to another spot and stays there; it can be dragged anywhere. Tap it
 * to talk (text or voice): it logs food, adds notes, answers questions.
 */
@Composable
fun CompanionOverlay(
    visible: Boolean,
    kind: app.tenet.android.core.designsystem.component.CompanionKind,
    onNavigate: (CompanionAgent.Destination) -> Unit,
    name: String = kind.label,
    viewModel: CompanionViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val reply by viewModel.reply.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    var chatOpen by remember { mutableStateOf(false) }
    // Closed while working: the creature carries on and says so in a small bubble when done.
    var bubble by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bubble) {
        if (bubble != null) {
            delay(if (reply != null) 6_000 else 2_500)
            bubble = null
        }
    }
    LaunchedEffect(Unit) { viewModel.navigate.collect(onNavigate) }
    // A task done (food logged, note extended …): the chat closes, a snackbar confirms.
    val snackbar = app.tenet.android.core.designsystem.component.LocalAppSnackbar.current
    LaunchedEffect(Unit) {
        viewModel.done.collect { text ->
            if (chatOpen) {
                chatOpen = false
                snackbar?.show(text)
            } else {
                bubble = "Fertig ✓"
            }
        }
    }
    // An answer that arrives with the chat closed: the bubble points to it.
    LaunchedEffect(reply) {
        if (reply != null && !chatOpen) bubble = "Antwort ist da"
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sizePx = with(density) { CreatureSize.toPx() }
        val maxX = constraints.maxWidth - sizePx
        val maxY = constraints.maxHeight - sizePx
        // Position as a fraction of the screen, so rotation keeps it on screen.
        val pos = remember { Animatable(Offset(0.80f, 0.60f), Offset.VectorConverter) }
        var walking by remember { mutableStateOf(false) }
        var facingLeft by remember { mutableStateOf(true) }
        var dragging by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // Going home: walk to the house button in the page header, then shrink into it;
        // coming back: pop out of the house and walk back to the old spot.
        val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
        val house = Offset(
            ((constraints.maxWidth - with(density) { 80.dp.toPx() } - sizePx / 2) / maxX).coerceIn(0f, 1f),
            ((statusTop + with(density) { 22.dp.toPx() } - sizePx * 0.75f) / maxY).coerceIn(0f, 1f),
        )
        val presence = remember { Animatable(if (visible) 1f else 0f) }
        var lastSpot by remember { mutableStateOf(Offset(0.80f, 0.60f)) }
        // True from "go home" until it is back on its spot (also when called back halfway).
        var away by remember { mutableStateOf(!visible) }
        LaunchedEffect(visible) {
            if (!visible && presence.value > 0f) {
                chatOpen = false
                if (!away) lastSpot = pos.value
                away = true
                facingLeft = house.x < pos.value.x
                walking = true
                val distance = hypot((house.x - pos.value.x) * maxX, (house.y - pos.value.y) * maxY)
                pos.animateTo(house, tween((distance / 1.4f).roundToInt().coerceIn(500, 1600), easing = FastOutSlowInEasing))
                walking = false
                presence.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            } else if (visible && away) {
                if (presence.value < 1f) {
                    pos.snapTo(house)
                    presence.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f))
                }
                facingLeft = lastSpot.x < pos.value.x
                walking = true
                val distance = hypot((lastSpot.x - pos.value.x) * maxX, (lastSpot.y - pos.value.y) * maxY)
                pos.animateTo(lastSpot, tween((distance / 1.2f).roundToInt().coerceIn(500, 1800), easing = FastOutSlowInEasing))
                walking = false
                away = false
            }
        }

        // Roaming: after a few minutes, run to another spot and stay there a while.
        LaunchedEffect(visible) {
            while (visible) {
                delay(Random.nextLong(90_000, 240_000))
                if (dragging || chatOpen || busy) continue
                val target = Offset(Random.nextFloat() * 0.75f + 0.08f, Random.nextFloat() * 0.50f + 0.22f)
                val from = pos.value
                facingLeft = target.x < from.x
                val distance = hypot((target.x - from.x) * maxX, (target.y - from.y) * maxY)
                walking = true
                pos.animateTo(target, tween((distance / 0.9f).roundToInt().coerceIn(900, 4000), easing = FastOutSlowInEasing))
                walking = false
            }
        }

        if (presence.value > 0f) Box(
            Modifier
                .offset { IntOffset((pos.value.x * maxX).roundToInt(), (pos.value.y * maxY).roundToInt()) }
                .graphicsLayer {
                    val p = presence.value
                    scaleX = p
                    scaleY = p
                    alpha = p.coerceIn(0f, 1f)
                    // Slips "through the door": shrinks towards its feet and slightly up.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.2f)
                },
        ) {
            app.tenet.android.core.designsystem.component.CompanionCreature(
                kind = kind,
                walking = walking || dragging,
                thinking = busy,
                facingLeft = facingLeft,
                modifier = Modifier
                    .size(CreatureSize)
                    .semantics { contentDescription = "${name} – antippen zum Sprechen, ziehen zum Verschieben" }
                    .pointerInput(maxX, maxY) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, drag ->
                            change.consume()
                            if (drag.x != 0f) facingLeft = drag.x < 0
                            scope.launch {
                                pos.snapTo(
                                    Offset(
                                        (pos.value.x + drag.x / maxX).coerceIn(0f, 1f),
                                        (pos.value.y + drag.y / maxY).coerceIn(0.05f, 1f),
                                    ),
                                )
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            chatOpen = !chatOpen
                            bubble = null
                        }
                    },
            )
            // Speech bubble above the creature, kept on screen at the edges.
            androidx.compose.animation.AnimatedVisibility(
                visible = bubble != null,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(
                        when {
                            pos.value.x > 0.7f -> Alignment.TopEnd
                            pos.value.x < 0.3f -> Alignment.TopStart
                            else -> Alignment.TopCenter
                        },
                    )
                    .offset(y = (-34).dp)
                    .wrapContentSize(unbounded = true, align = Alignment.BottomCenter),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                    onClick = {
                        if (reply != null) chatOpen = true
                        bubble = null
                    },
                ) {
                    Text(
                        bubble.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = presence.value == 1f && chatOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding(),
        ) {
            ChatPanel(
                name = name,
                kind = kind,
                request = request,
                step = step,
                busy = busy,
                reply = reply,
                onSend = { viewModel.send(it, name) },
                onClose = {
                    chatOpen = false
                    viewModel.clearReply()
                },
            )
        }
    }
}

private val CreatureSize = 64.dp

@Composable
private fun ChatPanel(
    name: String,
    kind: app.tenet.android.core.designsystem.component.CompanionKind,
    request: String?,
    step: String?,
    busy: Boolean,
    reply: String?,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val dictation = rememberDictation("Was soll $name tun?") { onSend(it) }
    val submit = {
        if (text.isNotBlank()) {
            onSend(text.trim())
            text = ""
        }
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Close, contentDescription = "Schließen")
                }
            }
            // Working: the request and an animated "doing it" view take the input's place.
            if (busy) {
                WorkingView(name, kind, request, step)
                Text(
                    "Du kannst das schließen – $name macht im Hintergrund weiter.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            if (reply != null && request != null) {
                Text("„$request“", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text(
                reply ?: "Hi! Sag z. B. „100 g Haferflocken zum Frühstück“, „schreib Milch auf die Einkaufsliste“ oder frag mich zu deinem Training.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (reply == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Nachricht an $name") },
                    // Longer messages wrap instead of scrolling sideways, so the cursor can reach every word.
                    singleLine = false,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (text.isBlank()) dictation.launch() else submit() }, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
                    if (text.isBlank()) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Sprechen")
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Senden")
                    }
                }
            }
        }
    }
}

/** What a request is about, guessed from its words, for the "working" text. */
private enum class Task(val steps: List<String>) {
    QUESTION(listOf("liest deine Frage", "schaut in deine Daten", "überlegt eine Antwort")),
    FOOD(listOf("sucht das Lebensmittel", "rechnet die Nährwerte", "trägt es ins Ernährungstagebuch ein")),
    WATER(listOf("holt ein Glas", "trägt das Wasser ein")),
    WEIGHT(listOf("stellt sich auf die Waage", "trägt dein Gewicht ein")),
    NOTE(listOf("blättert in deinen Notizen", "schreibt die Punkte auf", "speichert die Notiz")),
    TRAINING(listOf("schaut in deinen Trainingsplan", "prüft deine letzten Einheiten", "passt das Training an")),
    OPEN(listOf("sucht den richtigen Bereich", "öffnet ihn")),
    OTHER(listOf("liest deine Nachricht", "denkt nach")),
    ;

    companion object {
        fun of(text: String?): Task {
            val t = text.orEmpty().lowercase()
            fun has(vararg w: String) = w.any { it in t }
            val question = t.trim().endsWith("?") ||
                Regex("^(wie|was|wann|wo|warum|wieso|welche|wer|hab|habe|bin|ist|sind|kann|soll)\\b").containsMatchIn(t.trim())
            return when {
                has("wasser", "getrunken", "trinken") && !question -> WATER
                has("notiz", "liste", "aufschreib", "einkauf", "merk dir") && !question -> NOTE
                has("wiege", "gewicht", " kg") && !question -> WEIGHT
                question -> QUESTION
                has("öffne", "zeig mir", "geh zu", "wechsel zu") -> OPEN
                has("training", "trainings", "plan", "satz", "sätze", "lauf", "gym", "workout", "übung") -> TRAINING
                Regex("\\d+\\s*(g|gramm|ml|stück)\\b").containsMatchIn(t) ||
                    has("gegessen", "frühstück", "mittag", "abendessen", "snack", "hinzufügen", "füg", "add", "eintragen") -> FOOD
                else -> OTHER
            }
        }
    }
}

/** While the agent works: the creature thinks, a wavy bar runs and the task's steps change. */
@Composable
private fun WorkingView(name: String, kind: app.tenet.android.core.designsystem.component.CompanionKind, request: String?, actual: String?) {
    val steps = remember(request) { Task.of(request).steps }
    var step by remember(request) { mutableStateOf(0) }
    LaunchedEffect(request) {
        while (step < steps.lastIndex) {
            delay(1_400)
            step++
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        app.tenet.android.core.designsystem.component.CompanionCreature(
            kind = kind,
            walking = true,
            thinking = true,
            facingLeft = false,
            modifier = Modifier.size(56.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            request?.let {
                Text("„$it“", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // The real step as soon as the model has decided; until then a neutral guess.
            androidx.compose.animation.AnimatedContent(targetState = actual ?: steps[step], label = "step") { s ->
                Text("$name $s …", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            androidx.compose.material3.LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
