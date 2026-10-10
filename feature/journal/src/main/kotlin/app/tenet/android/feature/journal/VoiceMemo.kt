package app.tenet.android.feature.journal

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.component.TooltipIconButton
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay

internal const val VOICE_MIME = "audio/mp4"

/**
 * Records voice memos (App_Konzept.md 5.3 "Anhänge: … Sprachmemos") as
 * AAC/M4A into app-private storage (files/voice), so no storage permission
 * is needed and the file goes away with the entry.
 */
internal class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            true
        }.getOrElse {
            r.release()
            out.delete()
            false
        }
    }

    /** Stops and returns the file:// uri, or null when too short / failed. */
    fun stop(): String? {
        val r = recorder ?: return null
        val out = file
        recorder = null
        file = null
        val ok = runCatching { r.stop() }.isSuccess // throws when stopped immediately
        r.release()
        if (!ok || out == null || !out.exists() || out.length() == 0L) {
            out?.delete()
            return null
        }
        return Uri.fromFile(out).toString()
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        file?.delete()
        file = null
    }
}

/** Duration of an audio file in ms (0 when unreadable). */
internal fun audioDurationMs(context: Context, uri: String): Int = runCatching {
    MediaMetadataRetriever().use { mmr ->
        mmr.setDataSource(context, Uri.parse(uri))
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
    }
}.getOrDefault(0)

internal fun formatMs(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** One voice memo: play/pause, wavy progress, time, optional remove. */
@Composable
internal fun VoiceMemoRow(
    uri: String,
    index: Int,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val duration = remember(uri) { audioDurationMs(context, uri) }
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(uri) { mutableStateOf(false) }
    var position by remember(uri) { mutableIntStateOf(0) }
    var progress by remember(uri) { mutableFloatStateOf(0f) }

    DisposableEffect(uri) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player ?: break
            position = runCatching { p.currentPosition }.getOrDefault(position)
            progress = if (duration > 0) position.toFloat() / duration else 0f
            delay(100)
        }
    }

    fun toggle() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(context, Uri.parse(uri))
                setOnCompletionListener {
                    playing = false
                    position = 0
                    progress = 0f
                }
                prepare()
            }
        }.getOrNull()?.also { player = it } ?: return
        if (playing) {
            p.pause()
            playing = false
        } else {
            p.start()
            playing = true
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = ::toggle,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Icon(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (playing) "Sprachmemo $index pausieren" else "Sprachmemo $index abspielen",
                )
            }
            Spacer(Modifier.width(8.dp))
            app.tenet.android.core.designsystem.component.TenetProgress(
                progress = { progress },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (playing || position > 0) "${formatMs(position)} / ${formatMs(duration)}" else formatMs(duration),
                style = MaterialTheme.typography.labelLarge,
            )
            if (onRemove != null) {
                TooltipIconButton(Icons.Outlined.Close, "Sprachmemo $index entfernen", onRemove)
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}
