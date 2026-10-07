package app.tenet.android.ui

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
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _reply = MutableStateFlow<String?>(null)
    val reply: StateFlow<String?> = _reply.asStateFlow()
    private val _navigate = Channel<CompanionAgent.Destination>(Channel.BUFFERED)
    val navigate = _navigate.receiveAsFlow()

    /** Runs in the background: the page stays where it is. */
    fun send(text: String) {
        if (text.isBlank() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val r = runCatching { agent.handle(text) }.getOrElse { CompanionAgent.Reply("Ups, das ging schief: ${it.message}") }
            _reply.value = r.text
            _busy.value = false
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
    onNavigate: (CompanionAgent.Destination) -> Unit,
    viewModel: CompanionViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val reply by viewModel.reply.collectAsStateWithLifecycle()
    var chatOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.navigate.collect(onNavigate) }
    LaunchedEffect(visible) { if (!visible) chatOpen = false }

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

        // Roaming: after a few minutes, run to another spot and stay there a while.
        LaunchedEffect(visible) {
            while (visible) {
                delay(Random.nextLong(90_000, 240_000))
                if (dragging || chatOpen) continue
                val target = Offset(Random.nextFloat() * 0.75f + 0.08f, Random.nextFloat() * 0.50f + 0.22f)
                val from = pos.value
                facingLeft = target.x < from.x
                val distance = hypot((target.x - from.x) * maxX, (target.y - from.y) * maxY)
                walking = true
                pos.animateTo(target, tween((distance / 0.9f).roundToInt().coerceIn(900, 4000), easing = FastOutSlowInEasing))
                walking = false
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.4f),
            exit = fadeOut() + scaleOut(targetScale = 0.4f),
            modifier = Modifier.offset { IntOffset((pos.value.x * maxX).roundToInt(), (pos.value.y * maxY).roundToInt()) },
        ) {
            Creature(
                walking = walking || dragging,
                thinking = busy,
                facingLeft = facingLeft,
                modifier = Modifier
                    .size(CreatureSize)
                    .semantics { contentDescription = "Tenny – antippen zum Sprechen, ziehen zum Verschieben" }
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
                    .pointerInput(Unit) { detectTapGestures { chatOpen = !chatOpen } },
            )
        }

        AnimatedVisibility(
            visible = visible && chatOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding(),
        ) {
            ChatPanel(
                busy = busy,
                reply = reply,
                onSend = viewModel::send,
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
private fun ChatPanel(busy: Boolean, reply: String?, onSend: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val dictation = rememberDictation("Was soll Tenny tun?") { onSend(it) }
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
                Text("Tenny", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (busy) LoadingIndicator(Modifier.size(32.dp))
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Close, contentDescription = "Schließen")
                }
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
                    placeholder = { Text("Nachricht an Tenny") },
                    singleLine = true,
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

/** The creature: a soft cloud body with a screen face, blinking eyes and little feet. */
@Composable
private fun Creature(walking: Boolean, thinking: Boolean, facingLeft: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "tenny")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (walking) 360 else 2200, easing = LinearEasing)), label = "bob")
    val blink by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "blink")
    val body = Color(0xFF6F7CF7)
    val bodyDark = Color(0xFF4A55D6)
    val screen = Color(0xFF1E2246)
    val eye = Color(0xFF8FF3FF)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val wave = sin(bob * 2 * Math.PI).toFloat()
        val lift = if (walking) wave * h * 0.03f else wave * h * 0.02f
        // Feet: alternate while walking.
        val footY = h * 0.86f
        val stepL = if (walking) wave * h * 0.05f else 0f
        drawOval(bodyDark, Offset(w * 0.30f, footY - stepL), Size(w * 0.16f, h * 0.12f))
        drawOval(bodyDark, Offset(w * 0.54f, footY + stepL), Size(w * 0.16f, h * 0.12f))
        translate(top = lift) {
            scale(if (facingLeft) -1f else 1f, 1f, pivot = Offset(w / 2, h / 2)) {
                // Cloud body: overlapping circles.
                drawCircle(body, w * 0.22f, Offset(w * 0.32f, h * 0.38f))
                drawCircle(body, w * 0.24f, Offset(w * 0.56f, h * 0.32f))
                drawCircle(body, w * 0.20f, Offset(w * 0.74f, h * 0.46f))
                drawCircle(body, w * 0.20f, Offset(w * 0.26f, h * 0.56f))
                drawRoundRect(body, Offset(w * 0.18f, h * 0.36f), Size(w * 0.66f, h * 0.44f), CornerRadius(w * 0.2f))
                // Arms.
                drawOval(bodyDark, Offset(w * 0.08f, h * 0.58f), Size(w * 0.12f, h * 0.16f))
                drawOval(bodyDark, Offset(w * 0.80f, h * 0.58f), Size(w * 0.12f, h * 0.16f))
                // Screen face.
                drawRoundRect(screen, Offset(w * 0.30f, h * 0.36f), Size(w * 0.44f, h * 0.24f), CornerRadius(w * 0.08f))
                if (thinking) {
                    // Three dots, one lit after another.
                    for (i in 0..2) {
                        val on = ((bob * 3).toInt() % 3) == i
                        drawCircle(eye.copy(alpha = if (on) 1f else 0.35f), w * 0.03f, Offset(w * (0.42f + i * 0.08f), h * 0.48f))
                    }
                } else {
                    val closed = blink > 0.94f
                    val eyeH = if (closed) h * 0.012f else h * 0.07f
                    drawRoundRect(eye, Offset(w * 0.39f, h * 0.48f - eyeH / 2), Size(w * 0.07f, eyeH), CornerRadius(w * 0.03f))
                    drawRoundRect(eye, Offset(w * 0.56f, h * 0.48f - eyeH / 2), Size(w * 0.07f, eyeH), CornerRadius(w * 0.03f))
                }
            }
        }
    }
}
