package app.tenet.android.core.designsystem.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Starts the in-app dictation sheet; see [rememberDictation]. */
class DictationLauncher internal constructor(private val start: () -> Unit) {
    fun launch() = start()
}

/**
 * In-app dictation in a Material 3 sheet instead of Google's system dialog.
 * Keeps listening across pauses (the recognizer is restarted after every
 * pause) until the user taps "Fertig"; shows the text live while speaking.
 * [onResult] gets the whole spoken text (never blank).
 */
@Composable
fun rememberDictation(prompt: String, onResult: (String) -> Unit): DictationLauncher {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val currentOnResult by rememberUpdatedState(onResult)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) open = true
    }
    if (open) {
        DictationSheet(
            prompt = prompt,
            onCancel = { open = false },
            onDone = { text ->
                open = false
                if (text.isNotBlank()) currentOnResult(text)
            },
        )
    }
    return remember(context) {
        DictationLauncher {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                open = true
            } else {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DictationSheet(prompt: String, onCancel: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val recognizer = remember { ContinuousRecognizer(context.applicationContext) }
    DisposableEffect(recognizer) {
        recognizer.start()
        onDispose { recognizer.destroy() }
    }

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                prompt,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            MicBlob(
                listening = recognizer.listening,
                level = recognizer.level,
                onToggle = { if (recognizer.listening) recognizer.pause() else recognizer.start() },
            )

            Text(
                text = recognizer.error ?: if (recognizer.listening) "Hört zu … Pausen sind okay" else "Pausiert – tippe aufs Mikrofon",
                style = MaterialTheme.typography.labelLarge,
                color = if (recognizer.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Transcript(recognizer.committed, recognizer.partial)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text("Abbrechen")
                }
                Button(
                    onClick = { onDone(recognizer.text()) },
                    enabled = recognizer.text().isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Fertig") }
            }
        }
    }
}

/** Expressive mic: a cookie shape that turns while listening and swells with the voice level. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MicBlob(listening: Boolean, level: Float, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "rot")
    val swell by animateFloatAsState(if (listening) 1f + level * 0.35f else 0.9f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "swell")
    // Louder voice → the cookie bursts out (shape morph follows the level).
    val shape = levelMorphShape(if (listening) level else 0f, MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst, rotation)
    Box(
        Modifier
            .size(132.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(role = Role.Button, onClickLabel = if (listening) "Pausieren" else "Weiter zuhören", onClick = onToggle)
            .semantics { contentDescription = if (listening) "Mikrofon an" else "Mikrofon pausiert" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = swell
                    scaleY = swell
                }
                .background(if (listening) colors.primaryContainer else colors.surfaceContainerHighest, shape),
        )
        Box(
            Modifier
                .size(64.dp)
                .background(if (listening) colors.primary else colors.outline, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (listening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                contentDescription = null,
                tint = if (listening) colors.onPrimary else colors.surface,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun Transcript(committed: String, partial: String) {
    val scroll = rememberScrollState()
    LaunchedEffect(committed, partial) { scroll.animateScrollTo(scroll.maxValue) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 220.dp),
    ) {
        Box(Modifier.verticalScroll(scroll).padding(16.dp)) {
            if (committed.isBlank() && partial.isBlank()) {
                Text(
                    "Sprich einfach los.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    buildAnnotatedString {
                        append(committed)
                        if (partial.isNotBlank()) {
                            if (committed.isNotBlank()) append(' ')
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(partial) }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * Wraps [SpeechRecognizer] so that it keeps going: every final result is
 * appended and listening restarts right away, also after "no speech"
 * timeouts. Must be used on the main thread.
 */
private class ContinuousRecognizer(private val context: Context) : RecognitionListener {
    var committed by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    var listening by mutableStateOf(false)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Hints only; most recognizers still stop after a pause, hence the restart loop.
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)

    fun text(): String = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ").trim()

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "Spracherkennung auf diesem Gerät nicht verfügbar"
            return
        }
        error = null
        active = true
        listening = true
        listen()
    }

    fun pause() {
        active = false
        listening = false
        level = 0f
        recognizer?.stopListening()
    }

    fun destroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!active) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        runCatching { r.startListening(intent) }.onFailure { restartSoon() }
    }

    private fun restartSoon(delayMs: Long = 120) {
        handler.removeCallbacksAndMessages(null)
        if (active) handler.postDelayed({ listen() }, delayMs)
    }

    private fun Bundle?.best(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()

    override fun onResults(results: Bundle?) {
        val text = results.best()
        if (text.isNotEmpty()) committed = if (committed.isBlank()) text else "$committed $text"
        partial = ""
        restartSoon()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults.best()
        if (text.isNotEmpty()) partial = text
    }

    override fun onError(code: Int) {
        // A pending partial result would be lost otherwise.
        if (partial.isNotBlank()) {
            committed = if (committed.isBlank()) partial else "$committed $partial"
            partial = ""
        }
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT -> restartSoon()
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                recognizer?.cancel()
                restartSoon(400)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Kein Zugriff aufs Mikrofon")
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                fail("Keine Verbindung zur Spracherkennung")
            else -> restartSoon(400)
        }
    }

    private fun fail(message: String) {
        error = message
        active = false
        listening = false
        level = 0f
    }

    override fun onRmsChanged(rmsdB: Float) {
        level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        level = 0f
    }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
