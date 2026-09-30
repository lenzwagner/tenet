package app.tenet.android.core.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.edit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Offline tile images for imported recipes: downloaded once after the
 * import, scaled to [WIDTH] px and kept in files/ (not the purgeable cache),
 * so every recipe shows its photo without network and without the ~250 KB
 * original. Re-downloaded only when the recipe's photo URL changes.
 */
object RecipeThumbnails {
    private const val DIR = "recipe_thumbs"
    private const val WIDTH = 600
    private const val PREFS = "recipe_thumbs"

    fun file(context: Context, recipeId: String): File = File(File(context.filesDir, DIR), "$recipeId.webp")

    /** Local thumbnail if present, for Coil (File model). */
    fun existing(context: Context, recipeId: String): File? = file(context, recipeId).takeIf { it.length() > 0 }

    /** [photos]: recipe id → photo URL (null = none). Removes thumbnails of recipes not listed. */
    suspend fun sync(context: Context, photos: Map<String, String?>) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dir.listFiles()?.forEach { f ->
            val id = f.name.removeSuffix(".webp")
            if (photos[id] == null) {
                f.delete()
                prefs.edit { remove(id) }
            }
        }
        val todo = photos.filter { (id, url) ->
            url != null && url.startsWith("http") && (prefs.getString(id, null) != url || existing(context, id) == null)
        }
        val gate = Semaphore(4)
        coroutineScope {
            todo.map { (id, url) ->
                async {
                    gate.withPermit {
                        if (download(url!!, file(context, id))) prefs.edit { putString(id, url) }
                    }
                }
            }.awaitAll()
        }
    }

    private fun download(url: String, target: File): Boolean = runCatching {
        val bytes = (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 20_000
            try {
                if (responseCode !in 200..299) return false
                inputStream.use { it.readBytes() }
            } finally {
                disconnect()
            }
        }
        // Decode already subsampled (power of two), then scale to the exact width.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        val scaled = if (decoded.width > WIDTH) {
            Bitmap.createScaledBitmap(decoded, WIDTH, (decoded.height * WIDTH.toFloat() / decoded.width).toInt(), true)
        } else {
            decoded
        }
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { out ->
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            scaled.compress(format, 78, out)
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        tmp.renameTo(target)
    }.getOrDefault(false)
}
