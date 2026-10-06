package app.tenet.android.feature.sport.calisthenics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.PlaybackParams
import android.net.Uri
import android.provider.MediaStore
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.tenet.android.core.data.SkillRepository
import app.tenet.android.core.database.entity.FormVideo
import app.tenet.android.core.designsystem.component.TooltipIconButton
import coil3.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Longest form clip; enough for a hold or a few reps, keeps files small. */
private const val MAX_SECONDS = 30

/** Poster frame next to the video (same name, .jpg), for lists without decoding video. */
internal fun posterOf(video: FormVideo): File? =
    runCatching { File(java.net.URI(video.uri)).resolveSibling(File(java.net.URI(video.uri)).nameWithoutExtension + ".jpg") }
        .getOrNull()?.takeIf { it.exists() }

/** System camera in video mode, writing into [Uri] (limited to [MAX_SECONDS]). */
private class CaptureFormVideo : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, MAX_SECONDS)
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == android.app.Activity.RESULT_OK
}

/**
 * Returns a function that opens the camera for a short form video. After a
 * successful recording [onRecorded] gets the file:// URI and the length; a
 * poster frame is stored next to it. [onUnavailable] fires when no camera
 * app can record video.
 */
@Composable
internal fun rememberFormVideoRecorder(
    onRecorded: (uri: String, durationMs: Long?) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(CaptureFormVideo()) { ok ->
        val file = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (!ok || !file.exists() || file.length() == 0L) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        val duration = writePoster(file)
        onRecorded(Uri.fromFile(file).toString(), duration)
    }
    return {
        val dir = File(context.filesDir, SkillRepository.FORM_VIDEO_DIR).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pending = file
        try {
            launcher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pending = null
            onUnavailable()
        } catch (_: SecurityException) {
            pending = null
            onUnavailable()
        }
    }
}

/** Saves a small JPEG of the middle frame next to [video]; returns the length in ms. */
private fun writePoster(video: File): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(video.absolutePath)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val frame = retriever.getScaledFrameAtTime(
            (duration ?: 0L) * 500L, // middle, in µs
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            480,
            480,
        )
        frame?.let { bmp ->
            File(video.parentFile, video.nameWithoutExtension + ".jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            bmp.recycle()
        }
        duration
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

internal fun formatVideoDate(video: FormVideo): String =
    Instant.ofEpochMilli(video.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)

/** Poster tile with date and step; [selected] marks it for comparison. */
@Composable
internal fun FormVideoTile(
    video: FormVideo,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val poster = remember(video.uri) { posterOf(video) }
    Column(modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (poster != null) {
                    AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Outlined.Videocam, contentDescription = null)
                }
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Abspielen", modifier = Modifier.padding(6.dp).size(20.dp))
                }
                video.durationMs?.let { ms ->
                    Text(
                        "${(ms / 1000).coerceAtLeast(1)} s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(caption, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Row of form videos (newest first). Tap plays one; "Vergleichen" lets you
 * pick two and plays them side by side. Long press deletes.
 */
@Composable
internal fun FormVideoStrip(
    videos: List<FormVideo>,
    caption: (FormVideo) -> String,
    onDelete: (FormVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var comparing by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<FormVideo>() }
    var playing by remember { mutableStateOf<List<FormVideo>>(emptyList()) }
    var deleting by remember { mutableStateOf<FormVideo?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (videos.size >= 2) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = comparing,
                    onClick = {
                        comparing = !comparing
                        picked.clear()
                    },
                    label = { Text(if (comparing) "Zwei Videos wählen (${picked.size}/2)" else "Vergleichen") },
                    leadingIcon = { Icon(Icons.Outlined.CompareArrows, contentDescription = null) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(videos, key = { it.id }) { video ->
                FormVideoTile(
                    video = video,
                    caption = caption(video),
                    selected = video in picked,
                    onClick = {
                        if (!comparing) {
                            playing = listOf(video)
                        } else {
                            if (video in picked) picked.remove(video) else if (picked.size < 2) picked.add(video)
                            if (picked.size == 2) {
                                // Older attempt on the left, newer on the right.
                                playing = picked.sortedBy { it.createdAt }
                                picked.clear()
                                comparing = false
                            }
                        }
                    },
                    onLongClick = { deleting = video },
                )
            }
        }
    }

    if (playing.isNotEmpty()) {
        FormVideoPlayer(videos = playing, caption = caption, onDismiss = { playing = emptyList() })
    }
    deleting?.let { video ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Video löschen?") },
            text = { Text("Das Video vom ${formatVideoDate(video)} wird von diesem Gerät gelöscht.") },
            confirmButton = {
                TextButton(onClick = { onDelete(video); deleting = null }, shapes = ButtonDefaults.shapes()) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") }
            },
        )
    }
}

/**
 * Full-screen player for one video or two side by side (compare technique
 * over weeks). Both loop; play/pause and half speed act on both at once.
 */
@Composable
internal fun FormVideoPlayer(videos: List<FormVideo>, caption: (FormVideo) -> String, onDismiss: () -> Unit) {
    val views = remember { mutableStateListOf<VideoView>() }
    // Players from onPrepared: VideoView exposes no speed control itself.
    val players = remember { mutableStateListOf<android.media.MediaPlayer>() }
    var playing by remember { mutableStateOf(true) }
    var slow by remember { mutableStateOf(false) }
    fun apply() {
        views.forEach { v ->
            if (playing) v.start() else v.pause()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    TooltipIconButton(Icons.Outlined.Close, "Schließen", onDismiss)
                    Text(
                        if (videos.size == 2) "Vergleich" else "Formvideo",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    videos.forEach { video ->
                        Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(Uri.parse(video.uri))
                                            setOnPreparedListener { mp ->
                                                players += mp
                                                mp.isLooping = true
                                                mp.setVolume(0f, 0f)
                                                runCatching { mp.playbackParams = PlaybackParams().setSpeed(if (slow) 0.5f else 1f) }
                                                if (playing) start()
                                            }
                                            views += this
                                        }
                                    },
                                    onRelease = {
                                        views.remove(it)
                                        players.clear()
                                        it.stopPlayback()
                                    },
                                )
                            }
                            Text(caption(video), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TooltipIconButton(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) "Pause" else "Abspielen",
                        {
                            playing = !playing
                            apply()
                        },
                    )
                    FilterChip(
                        selected = slow,
                        onClick = {
                            slow = !slow
                            // Restart from the beginning so both stay in step.
                            views.forEach { it.seekTo(0) }
                            players.forEach { mp -> runCatching { mp.playbackParams = mp.playbackParams.setSpeed(if (slow) 0.5f else 1f) } }
                            playing = true
                            apply()
                        },
                        label = { Text("½ Tempo") },
                    )
                }
            }
        }
    }
}
