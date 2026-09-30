package app.tenet.android.core.data.sync

import app.tenet.android.core.common.CookStep

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import app.tenet.android.core.common.SaffronFormat
import app.tenet.android.core.database.TenetDatabase
import app.tenet.android.core.database.entity.Recipe
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Recipes from the Saffron app, which shares the Firebase project: after the
 * Google sign-in Tenet mirrors users/{uid}/recipes into its own Recipe table
 * (id "saffron-<docId>", source "saffron"). Favorite and rating changed in
 * Tenet are written back, so both apps show the same.
 */
@Singleton
class SaffronRecipes @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TenetDatabase,
    private val accounts: AccountRepository,
) {
    /** Original Storage URL → fresh download URL (Saffron's saved tokens can be revoked). */
    private val imageCache = context.getSharedPreferences("saffron_images", Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val dao get() = database.foodDao()

    /** Imports/updates all Saffron recipes and removes the ones deleted there. Returns the count. */
    suspend fun importAll(): Int {
        val uid = accounts.account.value?.uid ?: return 0
        val docs = firestore.collection("users").document(uid).collection("recipes").get().await().documents
        val now = System.currentTimeMillis()
        val recipes = docs.mapNotNull { doc ->
            val existing = dao.recipeOnce(SaffronFormat.recipeId(doc.id))
            toRecipe(doc, existing, now)?.let { r ->
                r.copy(
                    photoUri = r.photoUri?.let { resolveImage(it) },
                    images = buildList { for (u in r.images.lines().filter { it.isNotBlank() }) add(resolveImage(u)) }.joinToString("\n"),
                )
            }
        }
        val keep = recipes.map { it.id }.toSet()
        // Tiles offline too: small copies of every photo, before the rows land.
        runCatching { RecipeThumbnails.sync(context, recipes.associate { it.id to it.photoUri }) }
        database.withTransaction {
            // Not echoed into Tenet's own sync: every device imports from Saffron itself.
            database.openHelper.writableDatabase.execSQL("UPDATE SyncControl SET applying = 1")
            dao.upsertRecipes(recipes)
            dao.recipeIdsFromSource(SOURCE).filter { it !in keep }.forEach { dao.deleteRecipe(it) }
            database.openHelper.writableDatabase.execSQL("UPDATE SyncControl SET applying = 0")
        }
        return recipes.size
    }

    private fun toRecipe(doc: DocumentSnapshot, existing: Recipe?, now: Long): Recipe? {
        val title = doc.getString("title")?.trim().orEmpty()
        if (title.isEmpty()) return null
        val tags = SaffronFormat.strings(doc.getString("tags"))
        val slides = SaffronFormat.strings(doc.getString("slideImages")).filter { it.startsWith("http") }
        val thumb = doc.getString("thumbnailUrl")?.takeIf { it.startsWith("http") }
        val steps = SaffronFormat.steps(doc.getString("steps"))
        return Recipe(
            id = SaffronFormat.recipeId(doc.id),
            title = title,
            photoUri = thumb ?: slides.firstOrNull(),
            servings = (doc.getLong("servings") ?: 0L).toInt().takeIf { it > 0 } ?: 2,
            minutes = (doc.getLong("cookingTimeMinutes") ?: 0L).toInt().takeIf { it > 0 },
            tags = tags.joinToString(","),
            steps = steps.joinToString("\n") { it.text },
            favorite = doc.getBoolean("isFavorite") ?: false,
            createdAt = doc.getLong("createdAt") ?: existing?.createdAt ?: now,
            updatedAt = existing?.updatedAt ?: now,
            source = SOURCE,
            sourceUrl = doc.getString("url")?.takeIf { it.startsWith("http") },
            category = doc.getString("category").orEmpty(),
            rating = (doc.getLong("rating") ?: 0L).toInt().coerceIn(0, 5),
            vegetarian = doc.getBoolean("isVegetarian") ?: false,
            ingredientLines = SaffronFormat.strings(doc.getString("ingredients")).joinToString("\n"),
            stepsJson = doc.getString("steps"),
            notes = doc.getString("notes").orEmpty(),
            images = slides.joinToString("\n"),
            cooked = existing?.cooked ?: false,
        )
    }

    /**
     * Photos uploaded by Saffron live in Firebase Storage; their stored
     * download token may be revoked (HTTP 403). Signed in, Tenet may read
     * them (storage.rules), so ask Storage for a current URL once and cache it.
     */
    private suspend fun resolveImage(url: String): String {
        if (!url.contains("firebasestorage.googleapis.com") && !url.contains(".firebasestorage.app")) return url
        imageCache.getString(url, null)?.let { return it }
        return runCatching { FirebaseStorage.getInstance().getReferenceFromUrl(url).downloadUrl.await().toString() }
            .onSuccess { fresh -> imageCache.edit { putString(url, fresh) } }
            .getOrDefault(url)
    }

    /** Favorite/rating edited in Tenet → back into the Saffron document. */
    suspend fun writeBack(recipe: Recipe) {
        if (recipe.source != SOURCE) return
        val uid = accounts.account.value?.uid ?: return
        runCatching {
            firestore.collection("users").document(uid).collection("recipes")
                .document(recipe.id.removePrefix("saffron-"))
                .set(mapOf("isFavorite" to recipe.favorite, "rating" to recipe.rating), SetOptions.merge())
                .await()
        }
    }

    /**
     * Edited title, ingredients and steps back to Saffron (same document,
     * Saffron's JSON-string fields). Firestore queues the write offline;
     * false = not signed in or not confirmed within a few seconds.
     */
    suspend fun writeContent(recipe: Recipe): Boolean {
        if (recipe.source != SOURCE) return false
        val uid = accounts.account.value?.uid ?: return false
        val ingredients = recipe.ingredientLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val steps = recipe.stepsJson ?: SaffronFormat.stepsJson(recipe.steps.lines().filter { it.isNotBlank() }.map { CookStep(it.trim()) })
        val write = firestore.collection("users").document(uid).collection("recipes")
            .document(recipe.id.removePrefix("saffron-"))
            .set(
                mapOf("title" to recipe.title, "ingredients" to SaffronFormat.stringsJson(ingredients), "steps" to steps),
                SetOptions.merge(),
            )
        return kotlinx.coroutines.withTimeoutOrNull(8_000) { runCatching { write.await() }.isSuccess } ?: false
    }

    companion object {
        const val SOURCE = "saffron"
    }
}
