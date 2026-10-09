package app.tenet.android.core.data.sync

import android.util.Base64
import app.tenet.android.core.common.CaptionRecipe
import app.tenet.android.core.common.DietDetector
import app.tenet.android.core.common.SaffronFormat
import app.tenet.android.core.data.ai.AiAssistant
import app.tenet.android.core.database.TenetDatabase
import app.tenet.android.core.database.entity.Recipe
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A recipe read from a link or text, before it is saved. */
data class ImportedRecipe(
    val url: String?,
    val title: String,
    val thumbnailUrl: String,
    val notes: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val category: String,
    val tags: List<String>,
    val vegetarian: Boolean,
    val servings: Int,
    val minutes: Int,
    val slideImages: List<String>,
)

/**
 * Recipe import like in Saffron: TikTok (oEmbed caption, photo slideshows
 * via the vision model), Instagram (Saffron's cloud function), any web page
 * (embedded schema.org recipe or page text) or pasted text → NVIDIA NIM with
 * Saffron's prompt → title, ingredients, steps, category, tags.
 * Signed in, the recipe is stored exactly like Saffron stores it
 * (users/{uid}/recipes, photo in Storage), so it shows up in both apps.
 */
@Singleton
class RecipeImporter @Inject constructor(
    private val ai: AiAssistant,
    private val accounts: AccountRepository,
    private val saffron: SaffronRecipes,
    private val database: TenetDatabase,
) {
    // ---- Fetch + extract -----------------------------------------------------------

    suspend fun fromUrl(rawUrl: String, onStep: (String) -> Unit = {}): Result<ImportedRecipe> = withContext(Dispatchers.IO) {
        runCatching {
            val url = rawUrl.trim()
            require(url.startsWith("http://") || url.startsWith("https://")) { "Das ist kein gültiger Link." }
            onStep("Rezept wird geladen …")
            val recipe = when {
                url.contains("instagram.com") || url.contains("instagr.am") -> instagram(url, onStep)
                url.contains("tiktok.com") -> tiktok(url, onStep)
                else -> webPage(url, onStep)
            }
            require(recipe.ingredients.isNotEmpty() || recipe.steps.isNotEmpty()) { "Auf dieser Seite wurde kein Rezept gefunden." }
            recipe.copy(url = url)
        }
    }

    suspend fun fromText(text: String, onStep: (String) -> Unit = {}): Result<ImportedRecipe> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.isNotBlank()) { "Kein Text zum Importieren." }
            val recipe = extractText(cleanSocialCaption(text), thumbnail = "", onStep)
            require(recipe.ingredients.isNotEmpty() || recipe.steps.isNotEmpty()) { "Im Text wurde kein Rezept gefunden." }
            recipe
        }
    }

    private suspend fun tiktok(url: String, onStep: (String) -> Unit): ImportedRecipe {
        val resolved = resolveRedirect(url)
        val oembed = getText("https://www.tiktok.com/oembed?url=" + URLEncoder.encode(resolved, "UTF-8"))
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val caption = oembed?.optString("title").orEmpty()
        val thumbnail = oembed?.optString("thumbnail_url").orEmpty()
        val html = getText(resolved).orEmpty()
        val slides = if (html.isNotBlank()) tiktokSlides(html) else emptyList()
        val htmlCaption = if (html.isNotBlank()) tiktokDescription(html) else ""
        val text = if (caption.length >= htmlCaption.length) caption else htmlCaption
        if (slides.size > 1) {
            runCatching { extractVision(slides, text, thumbnail, onStep) }.getOrNull()
                ?.takeIf { it.ingredients.isNotEmpty() }
                ?.let { return it.copy(slideImages = slides) }
        }
        if (text.isNotBlank()) {
            val r = extractText(cleanSocialCaption(text), thumbnail, onStep)
            if (r.ingredients.isNotEmpty() || r.steps.isNotEmpty()) return r.copy(slideImages = slides)
        }
        return webPage(resolved, onStep)
    }

    private suspend fun instagram(url: String, onStep: (String) -> Unit): ImportedRecipe {
        val shortcode = Regex("""instagram\.com/(?:p|reels?|tv)/([A-Za-z0-9_\-]+)""").find(url)?.groupValues?.get(1)
            ?: error("Instagram-Link nicht erkannt.")
        onStep("Instagram-Post wird geladen …")
        val json = listOf(INSTA_FN, INSTA_FN_ALT).firstNotNullOfOrNull { endpoint ->
            runCatching { post(endpoint, JSONObject().put("shortcode", shortcode).toString(), 90_000) }.getOrNull()
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.takeIf { it.optBoolean("success") && it.optString("description").isNotBlank() }
        } ?: error("Instagram liefert den Post gerade nicht (privat oder blockiert). Versuch es später nochmal.")
        return extractText(cleanSocialCaption(json.optString("description")), json.optString("thumbnail_url"), onStep)
    }

    private suspend fun webPage(url: String, onStep: (String) -> Unit): ImportedRecipe {
        val html = getText(url) ?: error("Seite konnte nicht geladen werden.")
        val image = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: ""
        // Recipe sites embed schema.org/Recipe: exact and much shorter than the page text.
        val schema = Regex("""<script[^>]+application/ld\+json[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
            .findAll(html).mapNotNull { schemaRecipe(it.groupValues[1]) }.firstOrNull()
        if (schema != null && schema.ingredients.isNotEmpty()) return fromSchema(schema, htmlDecode(image), onStep)
        return extractText(pageText(html).take(5000), htmlDecode(image), onStep)
    }

    private suspend fun extractText(text: String, thumbnail: String, onStep: (String) -> Unit): ImportedRecipe {
        // Captions with "Ingredients … Method": read directly (all steps, exact
        // amounts), the AI only classifies and translates – like schema.org pages.
        CaptionRecipe.parse(text)?.let { c ->
            return fromSchema(
                SchemaRecipe(c.title, c.ingredients, c.steps.ifEmpty { listOf(VIDEO_HINT) }, c.servings, c.minutes, thumbnail, ""),
                thumbnail,
                onStep,
            ).copy(notes = text.take(500))
        }
        onStep("KI liest das Rezept …")
        // Nemotron 3 Super with the recipe prompt (temperature 0.2, no thinking – set in
        // AiAssistant.chat); gpt-oss as fallback when it is busy or too slow.
        val answer = ai.chat(
            TEXT_PROMPT + text,
            listOf(EXTRACT_MODEL, TEXT_MODEL),
            maxTokens = 3000,
            timeoutMs = 90_000,
            configuredFirst = false,
            accept = { AiAssistant.extractJson(it) != null },
        )
            ?: error(ai.lastError ?: "KI hat nicht geantwortet.")
        return germanize(parse(answer, text, thumbnail), onStep)
    }

    private suspend fun extractVision(images: List<String>, caption: String, thumbnail: String, onStep: (String) -> Unit): ImportedRecipe {
        onStep("KI liest die Bilder …")
        val dataUris = images.take(5).mapNotNull { url ->
            runCatching {
                val conn = open(url)
                val mime = conn.contentType?.substringBefore(';') ?: "image/jpeg"
                val bytes = conn.inputStream.use { it.readBytes() }
                "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.getOrNull()
        }
        require(dataUris.isNotEmpty()) { "Bilder konnten nicht geladen werden." }
        val answer = ai.chat(VISION_PROMPT, listOf(VISION_MODEL, "meta/llama-3.2-11b-vision-instruct"), images = dataUris)
            ?: error(ai.lastError ?: "KI hat nicht geantwortet.")
        return germanize(parse(answer, caption, thumbnail), onStep)
    }

    /** The fallback model sometimes keeps English ingredients: one short translation pass then. */
    private suspend fun germanize(r: ImportedRecipe, onStep: (String) -> Unit): ImportedRecipe {
        val words = (r.ingredients + r.steps).joinToString(" ").lowercase().split(Regex("[^a-zäöüß]+")).toSet()
        if (words.intersect(ENGLISH).size < 2) return r
        onStep("Wird übersetzt …")
        val payload = JSONObject().put("ingredients", JSONArray(r.ingredients)).put("steps", JSONArray(r.steps))
        val prompt = "Übersetze alle Einträge dieses JSON ins Deutsche (Mengen und Einheiten auf deutsch, z. B. \"2 EL\", \"1 Tasse\"). " +
            "Schritte in der Du-Form (\"Brate …\", nicht \"Braten Sie …\"), übliche deutsche Küchenbegriffe " +
            "(seasoning = Gewürzmischung, double/heavy cream = Sahne, fry = anbraten, thighs = Hähnchenschenkel). " +
            "Gleiche Struktur, gleiche Reihenfolge, antworte NUR mit dem JSON.\n" + payload
        // Each model on its own: an answer with missing entries is as bad as none.
        for (model in listOf(EXTRACT_MODEL, TEXT_MODEL)) {
            val json = ai.chat(prompt, listOf(model), maxTokens = 3000, timeoutMs = 60_000, configuredFirst = false)?.let { AiAssistant.extractJson(it) }
            fun list(key: String, size: Int) =
                json?.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it).trim() } }?.takeIf { it.size == size && it.all(String::isNotEmpty) }
            val ingredients = list("ingredients", r.ingredients.size)
            val steps = list("steps", r.steps.size)
            if (ingredients != null && steps != null) return r.copy(ingredients = ingredients, steps = steps)
            android.util.Log.w("RecipeImporter", "Übersetzung mit $model unbrauchbar: ${ai.lastError ?: "falsche Länge"}")
        }
        return r
    }

    /** Only Saffron's categories ("Pasta & Nudel" → "Pasta"). */
    private fun normalizeCategory(raw: String): String =
        CATEGORIES.firstOrNull { raw.equals(it, ignoreCase = true) }
            ?: CATEGORIES.firstOrNull { raw.contains(it, ignoreCase = true) }
            ?: "Andere"

    private fun parse(answer: String, original: String, thumbnail: String): ImportedRecipe {
        val json = AiAssistant.extractJson(answer) ?: error("KI-Antwort war kein Rezept.")
        if (json.optString("error") == "no_recipe") error("Im Text wurde kein Rezept gefunden.")
        fun list(key: String) = json.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf(String::isNotEmpty) } }.orEmpty()
        val ingredients = list("ingredients")
        val tags = DietDetector.correct(
            (list("tags") + listOfNotNull(json.optString("course").takeIf { it.isNotBlank() })).map(CaptionRecipe::cleanTag).distinct(),
            ingredients,
        )
        val title = CaptionRecipe.cleanTitle(json.optString("title")).ifBlank { "Rezept" }
        return ImportedRecipe(
            url = null,
            title = title,
            thumbnailUrl = thumbnail,
            notes = original.take(500),
            ingredients = ingredients,
            steps = list("steps"),
            category = normalizeCategory(json.optString("category")),
            tags = tags,
            vegetarian = tags.firstOrNull() in setOf("Vegetarisch", "Vegan"),
            // Stated in the text beats the AI (it falls back to 2 when unsure).
            servings = CaptionRecipe.servings(original) ?: json.optInt("servings", 0).takeIf { it in 1..24 } ?: 2,
            minutes = json.optInt("cookingTimeMinutes", json.optInt("minutes", 0)).coerceIn(0, 1440),
            slideImages = emptyList(),
        )
    }

    // ---- Save (Saffron format) -----------------------------------------------------

    /**
     * Stores the recipe. Signed in: photo to Storage + document in
     * users/{uid}/recipes (Saffron sees it too), then imported locally.
     * Guest: only local. Returns the Tenet recipe id.
     */
    suspend fun save(r: ImportedRecipe): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = accounts.account.value?.uid
            val key = r.url ?: "tenet:text:" + r.title + ":" + System.currentTimeMillis()
            val docId = java.util.Base64.getUrlEncoder().encodeToString(key.toByteArray())
            val steps = JSONArray().also { a ->
                r.steps.forEach { a.put(JSONObject().put("text", it).put("timeMinutes", 0).put("stepIngredients", JSONArray())) }
            }.toString()
            if (uid != null) {
                val photo = mirrorPhoto(uid, docId, r.thumbnailUrl)
                FirebaseFirestore.getInstance().collection("users").document(uid).collection("recipes").document(docId).set(
                    mapOf(
                        "title" to r.title,
                        "url" to key,
                        "thumbnailUrl" to photo,
                        "category" to r.category,
                        "ingredients" to JSONArray(r.ingredients).toString(),
                        "tags" to JSONArray(r.tags).toString(),
                        "isVegetarian" to r.vegetarian,
                        "servings" to r.servings,
                        "cookingTimeMinutes" to r.minutes,
                        "notes" to r.notes,
                        "steps" to steps,
                        "rating" to 0,
                        "createdAt" to System.currentTimeMillis(),
                        "slideImages" to JSONArray(r.slideImages).toString(),
                        "ownerId" to uid,
                        "isFavorite" to false,
                    ),
                    SetOptions.merge(),
                ).await()
                saffron.importAll()
                SaffronFormat.recipeId(docId)
            } else {
                // Guest: an ordinary Tenet recipe with the imported texts.
                val now = System.currentTimeMillis()
                val id = "import-" + docId.take(40)
                database.foodDao().upsertRecipe(
                    Recipe(
                        id = id,
                        title = r.title,
                        photoUri = r.thumbnailUrl.takeIf { it.startsWith("http") },
                        servings = r.servings,
                        minutes = r.minutes.takeIf { it > 0 },
                        tags = r.tags.joinToString(","),
                        steps = r.steps.joinToString("\n"),
                        createdAt = now,
                        updatedAt = now,
                        sourceUrl = r.url,
                        category = r.category,
                        vegetarian = r.vegetarian,
                        ingredientLines = r.ingredients.joinToString("\n"),
                        stepsJson = steps,
                        notes = r.notes,
                        images = r.slideImages.joinToString("\n"),
                    ),
                )
                id
            }
        }
    }

    /** Social thumbnails expire: keep a copy in Storage like Saffron does. */
    private suspend fun mirrorPhoto(uid: String, docId: String, url: String): String {
        if (!url.startsWith("http")) return url
        return runCatching {
            val bytes = open(url).inputStream.use { it.readBytes() }
            val ref = FirebaseStorage.getInstance().reference.child("users/$uid/recipes/$docId.jpg")
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        }.getOrDefault(url)
    }

    // ---- HTTP + HTML helpers -------------------------------------------------------

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", BROWSER_UA)
        setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
    }

    private fun getText(url: String): String? = runCatching {
        val conn = open(url)
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun post(url: String, body: String, timeoutMs: Int): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        return (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
    }

    private fun resolveRedirect(url: String): String = runCatching {
        var current = url
        repeat(5) {
            val conn = open(current).apply { instanceFollowRedirects = false; requestMethod = "HEAD" }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && location != null) current = URL(URL(current), location).toString() else return current
        }
        current
    }.getOrDefault(url)

    private fun tiktokSlides(html: String): List<String> {
        if (!html.contains("\"imagePost\"")) return emptyList()
        return Regex(""""imageURL"\s*:\s*\{\s*"urlList"\s*:\s*\[\s*"([^"]+)"""").findAll(html)
            .map { it.groupValues[1].replace("\\u002F", "/") }.filter { it.startsWith("https://") }.distinct().toList()
    }

    private fun tiktokDescription(html: String): String =
        Regex("""<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']*)""").find(html)?.groupValues?.get(1)
            ?.let(::htmlDecode)?.takeIf { it.isNotBlank() && !it.contains("TikTok video") }
            ?: Regex(""""desc"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)?.replace("\\n", "\n")
            ?: ""

    private data class SchemaRecipe(
        val name: String,
        val ingredients: List<String>,
        val steps: List<String>,
        val servings: Int?,
        val minutes: Int?,
        val image: String,
        val category: String,
    )

    /** schema.org/Recipe from a JSON-LD block (also inside @graph); null if none. */
    private fun schemaRecipe(raw: String): SchemaRecipe? = runCatching {
        fun find(node: Any?): JSONObject? = when (node) {
            is JSONObject -> {
                val type = node.opt("@type")
                val isRecipe = type == "Recipe" || (type is JSONArray && (0 until type.length()).any { type.optString(it) == "Recipe" })
                if (isRecipe) node else find(node.opt("@graph"))
            }
            is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { find(node.opt(it)) }
            else -> null
        }
        val trimmed = raw.trim()
        val recipe = find(if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)) ?: return null
        // Strings, HowToStep/HowToSection objects and nested lists alike.
        fun strings(v: Any?): List<String> = when (v) {
            is JSONArray -> (0 until v.length()).flatMap { strings(v.opt(it)) }
            is JSONObject -> strings(v.opt("itemListElement")).ifEmpty { listOfNotNull(v.optString("text").takeIf { it.isNotBlank() }) }
            is String -> listOf(v)
            else -> emptyList()
        }
        val image = when (val img = recipe.opt("image")) {
            is String -> img
            is JSONArray -> strings(img).firstOrNull() ?: (img.opt(0) as? JSONObject)?.optString("url").orEmpty()
            is JSONObject -> img.optString("url")
            else -> ""
        }
        fun clean(t: String) = htmlDecode(t.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim()
        SchemaRecipe(
            name = clean(recipe.optString("name")),
            ingredients = strings(recipe.opt("recipeIngredient")).map(::clean).filter { it.isNotEmpty() },
            steps = strings(recipe.opt("recipeInstructions")).flatMap { step ->
                // Some sites put all steps into one string, one per line.
                step.split(Regex("\n+")).map(::clean).filter { it.isNotEmpty() }
            },
            servings = strings(recipe.opt("recipeYield")).firstNotNullOfOrNull { Regex("\\d+").find(it)?.value?.toIntOrNull() },
            minutes = isoMinutes(recipe.optString("totalTime")) ?: isoMinutes(recipe.optString("cookTime")),
            image = image,
            category = strings(recipe.opt("recipeCategory")).firstOrNull().orEmpty(),
        )
    }.getOrNull()

    /** "PT1H20M" → 80. */
    private fun isoMinutes(iso: String): Int? {
        val m = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?""").matchEntire(iso.trim()) ?: return null
        val (d, h, min) = m.destructured
        return ((d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)).takeIf { it > 0 }
    }

    /**
     * Recipe sites: ingredients and steps straight from the page (exact, with
     * amounts); the AI only classifies (category, course, tags) like Saffron.
     */
    private suspend fun fromSchema(r: SchemaRecipe, pageImage: String, onStep: (String) -> Unit): ImportedRecipe {
        onStep("KI ordnet das Rezept ein …")
        val prompt = CLASSIFY_PROMPT + "Titel: ${r.name}\nKategorie der Seite: ${r.category}\nZutaten:\n" +
            r.ingredients.joinToString("\n") { "- $it" }
        val json = ai.chat(
            prompt, listOf(EXTRACT_MODEL, TEXT_MODEL), maxTokens = 600, timeoutMs = 40_000, configuredFirst = false,
            accept = { AiAssistant.extractJson(it) != null },
        )?.let { AiAssistant.extractJson(it) }
        val aiTags = json?.optJSONArray("tags")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf(String::isNotEmpty) } }.orEmpty()
            .plus(listOfNotNull(json?.optString("course")?.takeIf { it.isNotBlank() })).distinct()
        val tags = DietDetector.correct(aiTags.map(CaptionRecipe::cleanTag).filter { it !in PLACEHOLDER_TAGS }.distinct(), r.ingredients)
        val imported = ImportedRecipe(
            url = null,
            title = r.name.ifBlank { "Rezept" },
            thumbnailUrl = r.image.ifBlank { pageImage },
            notes = "",
            ingredients = r.ingredients,
            steps = r.steps,
            category = normalizeCategory(json?.optString("category").orEmpty().ifBlank { r.category }),
            tags = tags,
            vegetarian = tags.firstOrNull() in setOf("Vegetarisch", "Vegan"),
            servings = r.servings?.takeIf { it in 1..24 } ?: 2,
            minutes = r.minutes?.coerceIn(0, 1440) ?: 0,
            slideImages = emptyList(),
        )
        return germanize(imported, onStep)
    }

    private fun pageText(html: String): String = htmlDecode(
        html.replace(Regex("""<(script|style|noscript|svg)[\s\S]*?</\1>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<br\s*/?>|</p>|</li>|</h\d>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), " "),
    ).replace(Regex("""[ \t]+"""), " ").replace(Regex("""\n\s*\n+"""), "\n").trim()

    private fun htmlDecode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    /** Same clean-up as Saffron: links, hashtags and mentions out; emoji bullets become "•". */
    private fun cleanSocialCaption(text: String): String {
        val cleaned = text.replace(Regex("""https?://\S+"""), "").replace(Regex("""[#@]\S+"""), "")
        return cleaned.lines().joinToString("\n") { line ->
            val t = line.trim()
            var i = 0
            var emojis = 0
            while (i < t.length && emojis < 3) {
                val cp = t.codePointAt(i)
                if (cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27FF) { emojis++; i += Character.charCount(cp) } else break
            }
            if (emojis in 1..2 && i < t.length) "• " + t.substring(i).trim() else line
        }.replace(Regex("""[\p{So}\p{Sm}\p{Sk}]{3,}"""), " ").trim()
    }

    private companion object {
        /** Fast and good at German on NIM (low reasoning effort, see AiAssistant). */
        const val TEXT_MODEL = "openai/gpt-oss-20b"
        /** Recipe extraction, classification and translation (gpt-oss is the fallback). */
        const val EXTRACT_MODEL = "nvidia/nemotron-3-super-120b-a12b"
        val PLACEHOLDER_TAGS = setOf("Küche", "Hauptkomponente", "Hauptzutat", "Fleischart", "Eigenschaften", "Gang", "Temperatur")
        const val VIDEO_HINT = "Detaillierte Zubereitung siehe Video / Link in Bio"

        val CATEGORIES = listOf("Hähnchen", "Pute", "Rind", "Fisch", "Pasta", "Reis", "Kartoffeln", "Mexikanisch", "Asiatisch", "Vegetarisch", "Andere")

        /** Common English recipe words: two or more → translate. */
        val ENGLISH = setOf(
            "chicken", "beef", "pork", "oil", "olive", "salt", "pepper", "garlic", "onion", "lemon", "lime", "butter", "cheese",
            "cup", "cups", "tbsp", "tsp", "tablespoon", "teaspoon", "sugar", "flour", "cream", "sauce", "breast", "thighs",
            "rice", "water", "minced", "chopped", "fresh", "and", "with", "the", "add", "cook", "until", "minutes", "heat",
        )
        const val VISION_MODEL = "nvidia/llama-3.2-11b-vision-instruct"
        const val INSTA_FN = "https://us-central1-saffron-498311.cloudfunctions.net/get_insta_recipe"
        const val INSTA_FN_ALT = "https://get-insta-recipe-498311-uc.a.run.app"
        const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** Recipe extraction prompt (caption or page text follows after "Text: "). */
        val TEXT_PROMPT = """
            Du bist ein Koch-Assistent. Der folgende Text stammt aus einer Social-Media-Caption oder Webseite und kann unstrukturiert sein.
            Aufgabe: Extrahiere alle erkennbaren Rezeptinformationen. Antworte NUR mit validem JSON, kein Markdown.
            Nur wenn der Text nichts mit Kochen/Essen zu tun hat, gib zurück: {"error":"no_recipe"}

            JSON-Struktur:
            {"title":"...","ingredients":["..."],"steps":["..."],"category":"...","course":"...","weight":"...","tags":["..."],"servings":0,"cookingTimeMinutes":0}

            TITEL ("title"):
            - In der Originalsprache lassen (englischer Titel bleibt englisch), NICHT übersetzen.
            - KEINE Emojis, KEINE Hashtags, KEINE Sonderzeichen-Deko.
            - NIEMALS reine Großbuchstaben (KEIN ALL CAPS).
            - Jedes Wort beginnt mit einem Großbuchstaben (z. B. "Crispy Honey Garlic Chicken").
            - "with", "and", "mit", "und" werden durch "&" ersetzt (z. B. "Chicken & Rice & Broccoli").
            - Kurz und beschreibend, ohne Zusätze wie "so easy", "best ever", "recipe".

            ZUTATEN ("ingredients"):
            - Format: "Menge Einheit Zutat", z. B. "500 g Hähnchenschenkel", "3 EL Honig".
            - Jede Zutat zwingend ins Deutsche übersetzen (z. B. "Pineapple" → "Ananas", "Red Pepper" → "Rote Paprika", "Shrimp" → "Garnelen", "Egg" → "Ei", "Flour" → "Mehl", "Panko Breadcrumbs" → "Panko-Paniermehl"). KEINE Zutat auf Englisch!
            - Alle Mengen ins metrische System umrechnen und sinnvoll runden:
              * cup → ml (Flüssigkeiten, 1 cup = 240 ml) bzw. g (feste Zutaten, z. B. 1 cup Mehl = 125 g, 1 cup Zucker = 200 g, 1 cup Reis = 185 g)
              * oz → g (1 oz = 28 g), fl oz → ml (1 fl oz = 30 ml), lb → g (1 lb = 450 g), pint → ml (1 pint = 470 ml)
              * tbsp → EL, tsp → TL, inch → cm (1 inch = 2,5 cm), °F → °C
            - Erlaubte Einheiten: g, kg, ml, l, EL, TL, Prise, Bund, Stück, Zehe(n), Dose(n), Scheibe(n).
            - Falls keine Zutaten erkennbar: [].

            SCHRITTE ("steps"):
            - Jeden Zubereitungsschritt als eigenen String, auf Deutsch, im Imperativ.
            - Mengen, Temperaturen und Maße in Schritten ebenfalls metrisch (°C, cm, g, ml).
            - Wenn KEINE Zubereitungsschritte im Text stehen (z. B. nur Zutaten + Verweis auf Video/Bio-Link): genau einen Schritt "Detaillierte Zubereitung siehe Video / Link in Bio".
            - Stehen Anweisungen im Text, extrahiere sie zwingend vollständig.

            Versuche immer, mindestens Titel und Zutaten zu erfassen, auch wenn das Rezept unvollständig ist.

            FELDER:
            - "category": genau eines von: Hähnchen, Pute, Rind, Fisch, Pasta, Reis, Kartoffeln, Mexikanisch, Asiatisch, Vegetarisch, Andere
            - "course": PFLICHT, genau eines von: Vorspeise, Hauptgang, Dessert, Getränk
              * "Getränk": NUR für flüssige Getränke (Smoothies, Cocktails, Kaffee, Tee, Säfte). NIEMALS für Speisen.
              * "Dessert": Süßspeisen, Kuchen, Eis, Nachtisch.
              * "Vorspeise": Salate, Suppen, kleine Snacks vorab.
              * "Hauptgang": Standard für alle sättigenden Hauptmahlzeiten.
            - "weight": PFLICHT, genau eines von: Leicht, Deftig (Leicht = Salate/Gemüse/gesund unter ~500 kcal; Deftig = viel Fett/Käse/Fleisch über ~600 kcal)
            - "servings": ganze Zahl (0 wenn unbekannt). "cookingTimeMinutes": ganze Zahl (0 wenn unbekannt).
            - "tags" (alle auf Deutsch, mit Großbuchstaben beginnend):
              * Index 0 MUSS genau eines sein: "Vegan", "Vegetarisch" oder "Nicht-Vegetarisch".
                - "Nicht-Vegetarisch", wenn Fleisch, Fisch, Garnelen oder andere tierische Fleisch-/Fischprodukte enthalten sind. Wenn unsicher: "Nicht-Vegetarisch".
                - "Vegetarisch" nur, wenn komplett fleisch- und fischfrei.
                - "Vegan" nur, wenn komplett frei von tierischen Produkten (kein Fleisch, Fisch, Ei, Milch, Honig usw.).
              * Temperatur: "Warm" oder "Kalt" (MUSS "Warm" sein, wenn gebraten, gekocht, gebacken oder frittiert).
              * Gang: "Getränk", "Vorspeise", "Hauptgang", "Dessert" oder "Sonstiges".
              * Küche (wenn erkennbar): z. B. "Mediterran", "Asiatisch", "Deutsch", "Italienisch", "Mexikanisch".
              * Sei bei Komponenten-Tags EXTREM WÄHLERISCH: nur wenn ABSOLUTE Hauptkomponente bzw. im Titel.
                - Kohlenhydrate: z. B. "Nudeln", "Reis", "Kartoffeln", "Salat", "Brot" ("Brot" nur bei Sandwich, Burger, Toast oder brotbasiert).
                - Fleisch/Fisch: z. B. "Fisch", "Huhn", "Ente", "Rind", "Schwein", "Lamm", "Garnelen".
                - "Getränk" NIEMALS als Tag für Speisen.
              * Weitere Eigenschaften: z. B. "Schnell", "Gesund", "Scharf", "Süß", "Herzhaft".

            Text:
        """.trimIndent() + " "

        /** Classification only (recipe sites deliver ingredients and steps themselves). */
        val CLASSIFY_PROMPT = """
            Ordne dieses Rezept ein. Antworte NUR mit validem JSON: {"category":"...","course":"...","tags":["..."]}
            - "category": genau eines von: Hähnchen, Pute, Rind, Fisch, Pasta, Reis, Kartoffeln, Mexikanisch, Asiatisch, Vegetarisch, Andere
            - "course": genau eines von: Vorspeise, Hauptgang, Dessert, Getränk
            - "tags": das ERSTE Element ist "Vegan", "Vegetarisch" oder "Nicht-Vegetarisch" (bei Fleisch/Fisch oder Unsicherheit "Nicht-Vegetarisch"),
              danach 2–4 passende, großgeschriebene Tags, z. B. "Warm"/"Kalt", eine Küche ("Italienisch", "Asiatisch" …), die Hauptzutat ("Nudeln", "Reis" …),
              die Fleischart ("Huhn", "Rind" … nur wenn enthalten) oder "Schnell"/"Gesund"/"Scharf". Nur echte Wörter, keine Oberbegriffe wie "Küche".

        """.trimIndent() + "\n"

        val VISION_PROMPT = """
            Du bist ein Koch-Assistent. Analysiere diese Bilder einer TikTok-Slideshow und extrahiere das Rezept.
            Antworte NUR mit validem JSON.
            JSON-Struktur: {"title":"...","ingredients":["..."],"steps":["..."],"category":"...","course":"...","weight":"...","tags":["..."],"servings":2,"minutes":0}
            Kategorien: Hähnchen, Pute, Rind, Fisch, Pasta, Reis, Kartoffeln, Mexikanisch, Asiatisch, Vegetarisch, Andere
            Course: Vorspeise, Hauptgang, Dessert, Getränk
            Weight: Leicht, Deftig
            Regeln: Übersetze alle Felder ins Deutsche, Mengen nur wenn auf den Bildern zu sehen (nichts erfinden), Titel nicht in Großbuchstaben.
            Das erste Tag ist "Vegan", "Vegetarisch" oder "Nicht-Vegetarisch"; weitere Tags großgeschrieben (Warm/Kalt, Gang, Küche, Hauptkomponente, Fleischart, Eigenschaften).
        """.trimIndent()
    }
}
