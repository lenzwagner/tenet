package app.tenet.android.feature.sport

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Exercise photos (start and end position) bundled in assets/exercises.
 * Source: free-exercise-db by yuhonas, released into the public domain (Unlicense).
 */
object ExerciseImages {
    @Volatile private var available: Set<String>? = null

    private fun ids(context: Context): Set<String> = available ?: runCatching {
        context.assets.list("exercises").orEmpty()
            .filter { it.endsWith("_0.webp") }
            .map { it.removeSuffix("_0.webp") }
            .toSet()
    }.getOrDefault(emptySet()).also { available = it }

    fun has(context: Context, exerciseId: String): Boolean = exerciseId in ids(context)

    fun uri(exerciseId: String, frame: Int) = "file:///android_asset/exercises/${exerciseId}_$frame.webp"

    const val CREDIT = "Übungsfotos: free-exercise-db (gemeinfrei)"
}

/** Small square thumbnail (start position); pattern icon when there is no photo. */
@Composable
fun ExerciseThumb(exerciseId: String, modifier: Modifier = Modifier, size: Dp = 48.dp, shape: Shape = MaterialTheme.shapes.medium) {
    val context = LocalContext.current
    val has = remember(exerciseId) { ExerciseImages.has(context, exerciseId) }
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (has) {
            AsyncImage(
                model = ExerciseImages.uri(exerciseId, 0),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        }
    }
}

/** Large image that alternates between start and end position, like a slow GIF. Nothing without a photo. */
@Composable
fun ExerciseMotion(exerciseId: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val has = remember(exerciseId) { ExerciseImages.has(context, exerciseId) }
    if (!has) return
    var frame by remember(exerciseId) { mutableIntStateOf(0) }
    LaunchedEffect(exerciseId) {
        while (true) {
            delay(1_400)
            frame = 1 - frame
        }
    }
    Box(modifier.aspectRatio(4f / 3f).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Crossfade(frame, animationSpec = tween(450), label = "exercise-motion") { f ->
            AsyncImage(
                model = ExerciseImages.uri(exerciseId, f),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
