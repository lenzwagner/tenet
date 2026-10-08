package app.tenet.android.core.data.companion

import app.tenet.android.core.common.FoodPhrase
import app.tenet.android.core.common.FoodPhraseParser
import app.tenet.android.core.common.PortionMath
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.data.EntryRepository
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.data.WeekCalendarRepository
import app.tenet.android.core.data.ai.AiAssistant
import app.tenet.android.core.data.health.TodayFeedRepository
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.database.entity.MealType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * The companion's brain: a sentence ("füg 100 g Haferflocken hinzu", "wie lief
 * mein Training diese Woche?") goes to NVIDIA NIM together with a short
 * picture of the app's data; the model answers with a reply and actions,
 * which run here in the background – nothing navigates unless asked to.
 */
@Singleton
class CompanionAgent @Inject constructor(
    private val ai: AiAssistant,
    private val food: FoodRepository,
    private val entries: EntryRepository,
    private val sport: SportRepository,
    private val weekCalendar: WeekCalendarRepository,
    private val feed: TodayFeedRepository,
    private val readiness: app.tenet.android.core.data.health.ReadinessRepository,
    private val running: app.tenet.android.core.data.RunningRepository,
    private val recipes: app.tenet.android.core.data.sync.RecipeImporter,
) {
    /** Where the app should go, when the user asked to open something. */
    enum class Destination { TODAY, SPORT, JOURNAL, NUTRITION, SETTINGS }

    /** [acted]: something was stored – the chat closes and a short note confirms it. */
    data class Reply(val text: String, val navigate: Destination? = null, val acted: Boolean = false)

    val aiEnabled: Boolean get() = ai.config.value.usable

    /** [onStep] gets what is being done right now ("trägt 300 ml Wasser ein"), for the working view. */
    /** One earlier exchange of this chat, so follow-up answers ("Stimmung 4") complete a request. */
    data class Turn(val user: String, val assistant: String)

    suspend fun handle(
        text: String,
        name: String = "Tenny",
        history: List<Turn> = emptyList(),
        onStep: (String) -> Unit = {},
    ): Reply {
        if (text.isBlank()) return Reply("Sag mir, was ich tun soll.")
        if (!aiEnabled) return offline(text)
        // chat(): retries when NIM is busy, turns thinking off and falls back to a second
        // model – the plain json() call gave up after 30 s on slower models.
        val talk = history.takeLast(6).joinToString("\n") { "Nutzer: ${it.user}\n$name: ${it.assistant}" }
        val prompt = SYSTEM.replace("\"Tenny\"", "\"$name\"") + "\n\nKONTEXT\n" + context() +
            (if (talk.isNotBlank()) "\n\nBISHERIGES GESPRÄCH (deine Rückfragen und seine Antworten gehören zusammen)\n$talk" else "") +
            "\n\nNACHRICHT DES NUTZERS (nur darauf reagieren): " + text
        val answer = ai.chat(
            prompt, fastModels(), maxTokens = 900, timeoutMs = 30_000, configuredFirst = false,
            accept = { AiAssistant.extractJson(it) != null },
        )

            ?.let { AiAssistant.extractJson(it) }
            // No guessing from the sentence here: that once logged "Federweißer" for a question.
            ?: return Reply("Ich erreiche die KI gerade nicht (${ai.lastError ?: "keine Antwort"}). Versuch es gleich nochmal.")
        val done = mutableListOf<String>()
        var navigate: Destination? = null
        val actions = answer.optJSONArray("actions") ?: JSONArray()
        val asks = mutableListOf<String>()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            // Models name text fields differently; the dream itself is in the user's words anyway.
            if (a.optString("type") == "dream" && a.optBoolean("had", true) && a.optString("text").let { it.isBlank() || it == "null" }) {
                val alt = listOf("body", "description", "content", "dream").map { a.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" }
                val told = (history.map { it.user } + text).filter { Regex("tr(ä|ae)um", RegexOption.IGNORE_CASE).containsMatchIn(it) }.maxByOrNull { it.length }
                (alt ?: told)?.let { a.put("text", it) }
            }
            // Exercises only as the user named them: the model must not invent one from the history.
            if (a.optString("type") == "log_gym" || a.optString("type") == "log_cali") {
                val said = norm((history.map { it.user } + text).joinToString(" "))
                val ex = a.optJSONArray("exercises") ?: JSONArray()
                val kept = JSONArray()
                for (j in 0 until ex.length()) {
                    val o = ex.optJSONObject(j) ?: continue
                    val n = norm(o.optString("name"))
                    if (n.length >= 3 && (said.contains(n) || said.contains(n.take(5)))) kept.put(o)
                }
                a.put("exercises", kept)
            }
            // Something essential missing: ask instead of storing half an entry.
            missing(a)?.let {
                asks += it
                continue
            }
            step(a)?.let {
                onStep(it)
                kotlinx.coroutines.delay(700)
            }
            runCatching {
                when (a.optString("type")) {
                    "log_food" -> done += logFood(a)
                    "add_water" -> done += addWater(a.optInt("ml"))
                    "create_note" -> done += if (isList(a)) {
                        createNote(a.optString("title"), noteItems(a, "body"))
                    } else {
                        createTextNote(a.optString("title"), a.optString("body"))
                    }
                    "append_note" -> done += appendNote(noteTitle(a.optString("title"), (history.map { it.user } + text).joinToString(" ")), noteItems(a, "text"))
                    "log_weight" -> done += logWeight(a.optDouble("kg", Double.NaN))
                    "diary" -> done += diary(a)
                    "dream" -> done += dream(a)
                    "import_recipe" -> done += importRecipe(a.optString("url"), onStep)
                    "log_gym" -> done += logStrength(Discipline.GYM, a)
                    "log_cali" -> done += logStrength(Discipline.CALISTHENICS, a)
                    "log_run" -> done += logRun(a)
                    "navigate" -> navigate = runCatching { Destination.valueOf(a.optString("to").uppercase()) }.getOrNull()
                }
            }.onFailure { done += "Das hat nicht geklappt: ${a.optString("type")}" }
        }
        val reply = answer.optString("reply").takeIf { it.isNotBlank() && it != "null" }
        val stored = done.filter { it.isNotBlank() }
        if (asks.isNotEmpty()) {
            return Reply((stored + asks).joinToString("\n"), navigate, acted = false)
        }
        return Reply(
            // After an action the app's own confirmation is enough; the model's text is for answers.
            if (stored.isNotEmpty()) stored.joinToString("\n") else reply ?: "Hm, da weiß ich gerade nichts zu.",
            navigate,
            acted = stored.any { it.startsWith("✓") },
        )
    }

    /** Lower case, umlauts spelled out, letters only: "Klimmzüge" and "klimmzuege" match. */
    private fun norm(t: String) = t.lowercase(Locale.GERMAN)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9 ]"), "")

    /** The question to ask when an action lacks what it needs, else null. */
    private fun missing(a: JSONObject): String? {
        fun has(k: String) = a.has(k) && !a.isNull(k) && a.optString(k).isNotBlank() && a.optString(k) != "null"
        return when (a.optString("type")) {
            "diary" -> listOfNotNull(
                "Stimmung".takeIf { scale(a, "mood") == null },
                "Energie".takeIf { scale(a, "energy") == null },
                "Schlafqualität".takeIf { scale(a, "sleep") == null },
            ).takeIf { it.isNotEmpty() }?.let { "Wie war deine ${it.joinToString(", ").replace(Regex(", ([^,]+)$"), " und $1")} heute, jeweils von 1 (schlecht) bis 5 (super)?" }
            "dream" -> when {
                !a.optBoolean("had", true) -> null
                !has("text") -> "Erzähl mir deinen Traum – was ist passiert?"
                scale(a, "clarity") == null -> "Wie klar erinnerst du dich, von 1 (kaum) bis 5 (glasklar)? War er luzid oder ein Albtraum?"
                else -> null
            }
            "log_gym" -> {
                val ex = a.optJSONArray("exercises")
                val incomplete = (0 until (ex?.length() ?: 0)).mapNotNull { ex?.optJSONObject(it) }.filter { o ->
                    val sets = o.optJSONArray("sets")
                    sets == null || sets.length() == 0 ||
                        (0 until sets.length()).any { j -> sets.optJSONObject(j)?.let { it.optInt("reps", 0) <= 0 || it.optDouble("kg", 0.0) <= 0 } ?: true }
                }.map { it.optString("name") }
                when {
                    ex == null || ex.length() == 0 -> "Welche Übungen hast du gemacht – mit Sätzen, Wiederholungen und Gewicht?"
                    incomplete.isNotEmpty() -> "Wie viele Sätze, Wiederholungen und wie viel kg bei ${incomplete.joinToString(", ")}?"
                    else -> null
                }
            }
            "log_cali" -> {
                val ex = a.optJSONArray("exercises")
                val incomplete = (0 until (ex?.length() ?: 0)).mapNotNull { ex?.optJSONObject(it) }.filter { o ->
                    val sets = o.optJSONArray("sets")
                    sets == null || sets.length() == 0 ||
                        (0 until sets.length()).any { j -> sets.optJSONObject(j)?.let { it.optInt("reps", 0) <= 0 && it.optInt("seconds", 0) <= 0 } ?: true }
                }.map { it.optString("name") }
                when {
                    ex == null || ex.length() == 0 -> "Welche Übungen hast du gemacht – wie viele Sätze und Wiederholungen?"
                    incomplete.isNotEmpty() -> "Wie viele Sätze und Wiederholungen (oder Sekunden) bei ${incomplete.joinToString(", ")}?"
                    else -> null
                }
            }
            "log_run" -> when {
                a.optDouble("km", 0.0) <= 0 && a.optDouble("minutes", 0.0) <= 0 -> "Wie weit bist du gelaufen und wie lange hat es gedauert?"
                a.optDouble("km", 0.0) <= 0 -> "Wie viele Kilometer waren es?"
                a.optDouble("minutes", 0.0) <= 0 -> "Wie lange warst du unterwegs (Minuten)?"
                else -> null
            }
            "import_recipe" -> if (!a.optString("url").startsWith("http")) "Schick mir den Link zum Rezept." else null
            else -> null
        }
    }

    /** The step an action stands for, from what the model actually decided (not guessed from the words). */
    private fun step(a: JSONObject): String? = when (a.optString("type")) {
        "log_food" -> {
            val items = a.optJSONArray("items")
            val names = (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
            "trägt ${names.joinToString(", ").ifBlank { "das Essen" }} ins Ernährungstagebuch ein"
        }
        "add_water" -> "trägt ${a.optInt("ml")} ml Wasser ein"
        "create_note" -> "legt die Notiz „${cap(a.optString("title")).ifBlank { "Ohne Titel" }}“ an"
        "append_note" -> "ergänzt „${a.optString("title").ifBlank { "die Notiz" }}“"
        "log_weight" -> "trägt dein Gewicht ein"
        "diary" -> "schreibt deinen Tagebucheintrag"
        "dream" -> if (a.optBoolean("had", true)) "schreibt deinen Traum auf" else "notiert „kein Traum“"
        "import_recipe" -> "lädt das Rezept"
        "log_gym" -> "trägt dein Gym-Training ein"
        "log_cali" -> "trägt dein Calisthenics-Training ein"
        "log_run" -> "trägt deinen Lauf ein"
        "navigate" -> "öffnet den Bereich"
        else -> null
    }

    /** Without AI: simple food sentences still work ("100 g Haferflocken"). */
    private suspend fun offline(text: String): Reply {
        // Only clear food sentences with an amount ("100 g …", "2 Eier"), never a question.
        val phrases = if (text.trim().endsWith("?")) emptyList() else FoodPhraseParser.parse(text).filter { it.explicit || it.count != null }
        if (phrases.isEmpty()) {
            return Reply("Ohne KI verstehe ich nur Essen wie „100 g Haferflocken“. Den NVIDIA-Schlüssel trägst du unter Einstellungen → KI ein.")
        }
        val lines = phrases.map { logPhrase(it, defaultMeal()) }
        return Reply(lines.joinToString("\n"))
    }

    /** Small models this key can use, fastest first; looked up once per app start. */
    private var models: List<String>? = null

    /** Looks the models up ahead of the first message (saves ~1.5 s on it). */
    suspend fun warmUp() {
        if (aiEnabled) fastModels()
    }

    private suspend fun fastModels(): List<String> {
        models?.let { return it }
        val available = runCatching { ai.fetchModels().map { it.id }.toSet() }.getOrDefault(emptySet())
        val picked = FAST_MODELS.filter { it in available }.ifEmpty { listOf(AiAssistant.DEFAULT_MODEL) }
        if (available.isNotEmpty()) models = picked
        return picked
    }

    // ---- Actions --------------------------------------------------------------

    private suspend fun logFood(a: JSONObject): List<String> {
        val items = a.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val grams = o.optDouble("grams", Double.NaN).takeIf { !it.isNaN() && it >= 1 }?.toFloat()?.coerceAtMost(3000f)
                ?: return@mapNotNull null
            val meal = runCatching { MealType.valueOf(o.optString("meal").uppercase()) }.getOrNull() ?: defaultMeal()
            logPhrase(FoodPhrase(name = name, grams = grams, count = null, explicit = o.optBoolean("explicit", true), meal = meal.name), meal)
        }
    }

    private suspend fun logPhrase(p: FoodPhrase, meal: MealType): String {
        val match = food.searchLocal(p.name).firstOrNull()
            ?: withTimeoutOrNull(6_000) { runCatching { food.searchOnline(p.name).firstOrNull() }.getOrNull() }
            ?: return "„${p.name}“ habe ich nicht gefunden – bitte selbst suchen."
        val (grams, _) = PortionMath.adjust(p, food.recentAmounts(match.id))
        food.logFood(match, grams, grams, "g", meal, LocalDate.now().toString())
        val kcal = (match.kcalPer100 * grams / 100f).roundToInt()
        return "✓ ${grams.roundToInt()} g ${match.name} · $kcal kcal (${mealLabel(meal)})"
    }

    private suspend fun addWater(ml: Int): String {
        if (ml <= 0) return ""
        val today = LocalDate.now().toString()
        val now = food.observeWater(today).first()
        food.setWater(today, now + ml.coerceAtMost(3000))
        return "✓ $ml ml Wasser"
    }

    /** List items of an action: "items":[…] or lines of [field]; markers and ticks removed. */
    private fun noteItems(a: JSONObject, field: String): List<String> {
        val arr = a.optJSONArray("items")
        val raw = if (arr != null && arr.length() > 0) {
            (0 until arr.length()).map { arr.optString(it) }
        } else {
            a.optString(field).lines()
        }
        return raw.map { it.replace(Regex("""^\s*([-*•]\s*)?(\[[ xX]?]\s*)?"""), "").trim() }
            .filter { it.isNotBlank() && it != "null" }
            .map { it.replaceFirstChar { c -> c.titlecase(Locale.GERMAN) } }
    }

    private fun isList(a: JSONObject) = (a.optJSONArray("items")?.length() ?: 0) > 0 ||
        a.optString("body").lines().any { it.trimStart().startsWith("-") || it.trimStart().startsWith("*") || it.trimStart().startsWith("•") }

    private suspend fun createTextNote(title: String, body: String): String {
        if (title.isBlank() && body.isBlank()) return ""
        val now = System.currentTimeMillis()
        entries.save(
            Entry(
                id = newUuid(), type = EntryType.NOTE, title = cap(title), body = cap(body.takeIf { it != "null" }.orEmpty()),
                createdAt = now, updatedAt = now, entryDate = LocalDate.now().toString(),
            ),
            diaryMeta = null,
            dreamMeta = null,
        )
        return "✓ Notiz „${cap(title).ifBlank { "Ohne Titel" }}“ angelegt"
    }

    private fun cap(text: String) = text.trim().replaceFirstChar { it.titlecase(Locale.GERMAN) }

    /** New note; items become an unchecked checklist, each starting with a capital letter. */
    private suspend fun createNote(title: String, items: List<String>): String {
        if (title.isBlank() && items.isEmpty()) return ""
        val now = System.currentTimeMillis()
        entries.save(
            Entry(
                id = newUuid(), type = EntryType.NOTE, title = cap(title), body = items.joinToString("\n") { "- [ ] $it" },
                createdAt = now, updatedAt = now, entryDate = LocalDate.now().toString(),
            ),
            diaryMeta = null,
            dreamMeta = null,
        )
        return "✓ Notiz „${cap(title).ifBlank { "Ohne Titel" }}“ angelegt" + if (items.isEmpty()) "" else ": " + items.joinToString(", ")
    }

    /**
     * The note the user named: the model's title only if the user said it, else the
     * name from the sentence ("… auf die Einkaufsliste" → Einkaufsliste) – so items
     * never land in an unrelated note just because it is the only one around.
     */
    private fun noteTitle(model: String, said: String): String {
        val m = model.trim()
        if (m.isNotBlank() && said.contains(m, ignoreCase = true)) return m
        Regex("""(?:auf|in|zu)\s+(?:die|der|den|das|meine|meiner|meinen|mein)\s+([\p{L}-]+)""", RegexOption.IGNORE_CASE)
            .findAll(said).map { it.groupValues[1] }.lastOrNull { it.length > 2 }
            ?.let { return cap(it) }
        return if (said.contains("einkauf", ignoreCase = true)) "Einkaufsliste" else m
    }

    /** Adds items to a note found by title (new note if there is none); checklists get unchecked items. */
    private suspend fun appendNote(title: String, items: List<String>): String {
        if (items.isEmpty()) return ""
        val note = entries.findByTitle(title)
            // Only a note whose title really is (or contains) the name, never one that merely mentions it.
            ?: entries.search(title).firstOrNull { it.type == EntryType.NOTE && title.isNotBlank() && it.title.contains(title.trim(), ignoreCase = true) }
            ?: return createNote(title, items)
        val body = note.body.trimEnd()
        val isList = body.isEmpty() || body.lines().any { it.trimStart().startsWith("- [") }
        val lines = items.joinToString("\n") { if (isList) "- [ ] $it" else it }
        entries.updateBody(note.id, if (body.isEmpty()) lines else "$body\n$lines")
        return "✓ In „${note.title.ifBlank { "Notiz" }}“ ergänzt: ${items.joinToString(", ")}"
    }

    private fun scale(a: JSONObject, key: String): Int? =
        a.optInt(key, 0).takeIf { it in 1..5 }

    private fun day(a: JSONObject): LocalDate =
        a.optString("date").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()

    /** Diary entry with mood, energy, sleep quality (1–5); today's entry is completed, not doubled. */
    private suspend fun diary(a: JSONObject): String {
        val mood = scale(a, "mood") ?: return ""
        val date = day(a).toString()
        val text = a.optString("text").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val existing = entries.observeByType(EntryType.DIARY).first().firstOrNull { it.entryDate == date }
        val now = System.currentTimeMillis()
        val entry = existing?.let {
            // Completing today's entry: new text is added once, never twice.
            val body = if (text.isBlank() || it.body.contains(text.trim(), ignoreCase = true)) it.body
            else listOf(it.body.trimEnd(), cap(text)).filter { b -> b.isNotBlank() }.joinToString("\n\n")
            it.copy(body = body, updatedAt = now)
        } ?: Entry(
            id = newUuid(), type = EntryType.DIARY, title = a.optString("title").takeIf { it.isNotBlank() && it != "null" }?.let(::cap).orEmpty(),
            body = cap(text), createdAt = now, updatedAt = now, entryDate = date,
        )
        val old = existing?.let { entries.get(it.id)?.diaryMeta }
        entries.save(
            entry,
            diaryMeta = (old ?: app.tenet.android.core.database.entity.DiaryMeta(entry.id, mood))
                .copy(mood = mood, energy = scale(a, "energy") ?: old?.energy, sleepQuality = scale(a, "sleep") ?: old?.sleepQuality),
            dreamMeta = null,
        )
        val parts = listOfNotNull("Stimmung $mood/5", scale(a, "energy")?.let { "Energie $it/5" }, scale(a, "sleep")?.let { "Schlaf $it/5" })
        return "✓ Tagebuch · " + parts.joinToString(", ")
    }

    /** Dream: "had": false stores the no-dream day like the button on "Heute". */
    private suspend fun dream(a: JSONObject): String {
        val date = day(a).toString()
        val now = System.currentTimeMillis()
        if (!a.optBoolean("had", true)) {
            entries.save(
                Entry(id = newUuid(), type = EntryType.DREAM, title = "Kein Traum", body = "", createdAt = now, updatedAt = now, entryDate = date),
                diaryMeta = null,
                dreamMeta = null,
            )
            return "✓ Kein Traum notiert"
        }
        val text = a.optString("text").takeIf { it.isNotBlank() && it != "null" } ?: return ""
        val title = a.optString("title").takeIf { it.isNotBlank() && it != "null" }?.let(::cap) ?: "Traum"
        val id = newUuid()
        val emotions = a.optJSONArray("emotions")?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() } }
        entries.save(
            Entry(id = id, type = EntryType.DREAM, title = title, body = cap(text), createdAt = now, updatedAt = now, entryDate = date),
            diaryMeta = null,
            dreamMeta = app.tenet.android.core.database.entity.DreamMeta(
                entryId = id,
                clarity = scale(a, "clarity") ?: 3,
                lucid = a.optBoolean("lucid", false),
                nightmare = a.optBoolean("nightmare", false),
                recurring = a.optBoolean("recurring", false),
                emotions = emotions?.takeIf { it.isNotEmpty() }?.joinToString(","),
            ),
        )
        return "✓ Traum „$title“ gespeichert"
    }

    private suspend fun importRecipe(url: String, onStep: (String) -> Unit): String {
        if (!url.startsWith("http")) return "Das ist kein gültiger Rezept-Link."
        val recipe = recipes.fromUrl(url) { onStep(it.trimEnd('.', '…', ' ').replaceFirstChar { c -> c.lowercase() }) }
            .getOrElse { return "Rezept-Import fehlgeschlagen: ${it.message}" }
        recipes.save(recipe).getOrElse { return "Rezept „${recipe.title}“ gefunden, aber nicht gespeichert: ${it.message}" }
        return "✓ Rezept „${recipe.title}“ importiert · ${recipe.ingredients.size} Zutaten"
    }

    /** Gym or calisthenics session told afterwards. */
    private suspend fun logStrength(discipline: Discipline, a: JSONObject): String {
        val arr = a.optJSONArray("exercises") ?: return ""
        val exercises = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sets = o.optJSONArray("sets")?.let { s ->
                (0 until s.length()).mapNotNull { j ->
                    s.optJSONObject(j)?.let { set ->
                        SportRepository.LoggedSet(
                            weightKg = set.optDouble("kg", Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toFloat(),
                            reps = set.optInt("reps", 0).takeIf { it > 0 },
                            seconds = set.optInt("seconds", 0).takeIf { it > 0 },
                        )
                    }
                }
            }.orEmpty()
            name to sets
        }
        if (exercises.isEmpty()) return ""
        val end = day(a).let { d -> if (d == LocalDate.now()) System.currentTimeMillis() else d.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val (found, unknown) = sport.logPastSession(discipline, exercises, a.optInt("minutes", 0).takeIf { it > 0 }, end)
        val label = if (discipline == Discipline.GYM) "Gym" else "Calisthenics"
        if (found.isEmpty()) return "Übungen nicht gefunden: ${unknown.joinToString(", ")} – bitte im Katalog anlegen."
        return "✓ $label-Training · ${found.joinToString(", ")}" + if (unknown.isNotEmpty()) " (unbekannt: ${unknown.joinToString(", ")})" else ""
    }

    private suspend fun logRun(a: JSONObject): String {
        val km = a.optDouble("km", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return ""
        val minutes = a.optDouble("minutes", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return ""
        val sec = (minutes * 60).roundToInt()
        val start = day(a).let { d ->
            if (d == LocalDate.now()) java.time.LocalDateTime.now().minusSeconds(sec.toLong()) else d.atTime(18, 0)
        }
        running.addManualRun(start, (km * 1000).toFloat(), sec, a.optInt("avg_hr", 0).takeIf { it > 0 }, a.optString("notes").takeIf { it != "null" }.orEmpty())
        val pace = sec / km
        return "✓ Lauf ${"%.1f".format(Locale.GERMAN, km)} km · ${minutes.roundToInt()} min (${(pace / 60).toInt()}:${"%02d".format((pace % 60).roundToInt().coerceAtMost(59))} /km)"
    }

    private suspend fun logWeight(kg: Double): String {
        if (kg.isNaN() || kg !in 20.0..400.0) return ""
        sport.saveBodyMetric(LocalDate.now().toString(), kg.toFloat())
        return "✓ Gewicht ${"%.1f".format(Locale.GERMAN, kg)} kg"
    }

    // ---- What the model knows -------------------------------------------------

    private suspend fun context(): String {
        val today = LocalDate.now()
        val day = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
        val iso = today.toString()
        val totals = food.totals(iso).first()
        val goal = food.goal(iso).first()
        val water = food.observeWater(iso).first()
        val snap = runCatching { feed.snapshot() }.getOrNull()
        val ready = runCatching { readiness.today() }.getOrNull()
        val heart = ready?.input?.let { i ->
            listOfNotNull(
                i.restingHr?.let { "Ruhepuls heute $it bpm" + (i.restingHrBaseline?.let { b -> " (Schnitt ${b.roundToInt()})" } ?: "") },
                i.hrvMs?.let { "HRV heute ${it.roundToInt()} ms" + (i.hrvBaselineMs?.let { b -> " (Schnitt ${b.roundToInt()})" } ?: "") },
                i.sleep?.let { s -> "Schlaf Tief ${s.deepMin ?: "?"} min, REM ${s.remMin ?: "?"} min, wach ${s.awakeMin ?: "?"} min" },
            ).joinToString(", ")
        }.orEmpty().ifBlank { "keine Puls-/HRV-Daten (Health Connect-Rechte?)" }
        val sessions = weekCalendar.observeSessionsBetween(today.minusDays(14), today.plusDays(1)).first()
            .filter { it.session.endedAt != null }
            .joinToString("\n") { s ->
                val d = java.time.Instant.ofEpochMilli(s.session.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val min = ((s.session.endedAt!! - s.session.startedAt) / 60_000L)
                "- $d ${s.session.discipline.label()} ${s.workoutTitle.orEmpty()} $min min"
            }.ifBlank { "keine" }
        val planned = weekCalendar.observePlannedBetween(today, today.plusDays(6)).first()
            .filter { !it.skipped }
            .joinToString("\n") { "- ${it.date ?: "?"} ${it.discipline.label()} ${it.title}" }.ifBlank { "nichts" }
        val notes = entries.observeByType(EntryType.NOTE).first().take(8)
            .joinToString("\n") { "- „${it.title.ifBlank { "Ohne Titel" }}“: ${it.body.replace('\n', ' ').take(100)}" }.ifBlank { "keine" }
        return """
            Heute: ${today.format(day)}, ${LocalTime.now().withSecond(0).withNano(0)} Uhr
            Ernährung heute: ${totals.kcal.roundToInt()} von ${goal.kcal.roundToInt()} kcal, Eiweiß ${totals.protein.roundToInt()}/${goal.protein.roundToInt()} g, Wasser $water ml
            Herz: $heart
            Gesundheit: Schritte ${snap?.steps ?: "?"}, Tagesform ${snap?.form ?: "?"}/100, Schlaf ${snap?.sleepText ?: "?"}, Woche aktiv ${snap?.weekActiveMin ?: 0}/150 min
            Trainings der letzten 14 Tage:
            $sessions
            Geplant (7 Tage):
            $planned
            Letzte Notizen:
            $notes
        """.trimIndent()
    }

    private fun Discipline.label() = when (this) {
        Discipline.GYM -> "Gym"
        Discipline.CALISTHENICS -> "Calisthenics"
        Discipline.RUNNING -> "Laufen"
    }

    private fun mealLabel(m: MealType) = when (m) {
        MealType.BREAKFAST -> "Frühstück"
        MealType.LUNCH -> "Mittag"
        MealType.DINNER -> "Abend"
        MealType.SNACK -> "Snack"
    }

    private fun defaultMeal(): MealType = when (LocalTime.now().hour) {
        in 4..10 -> MealType.BREAKFAST
        in 11..14 -> MealType.LUNCH
        in 17..21 -> MealType.DINNER
        else -> MealType.SNACK
    }

    companion object {
        /**
         * Small, fast models first (a short JSON answer needs no big model, ~1–2 s),
         * then the one from the settings, then the app default.
         */
        // Measured on NIM (Oct 2026): ~2.5 s each, Nemotron Lightning answered most naturally.
        private val FAST_MODELS = listOf(
            "nvidia/nemotron-3.5-lightning-30b-a3b",
            AiAssistant.DEFAULT_MODEL,
            "nvidia/nemotron-nano-3-30b-a3b",
            "openai/gpt-oss-20b",
        )

        private val SYSTEM = """
            Du bist "Tenny", der freundliche Begleiter in der Fitness- und Journal-App Tenet. Antworte immer auf Deutsch, kurz (1–3 Sätze), du duzt.
            Du kannst Fragen zu Training, Ernährung, Gesundheit und Notizen mit dem KONTEXT beantworten und Aktionen ausführen.
            Antworte NUR mit JSON: {"reply": Text für den Nutzer, "actions": [ ... ]}
            Aktionen (nur wenn ausdrücklich gewünscht, sonst leere Liste):
            {"type":"log_food","items":[{"name": einfacher deutscher Lebensmittelname für die Suche, "grams": Zahl, "explicit": true wenn Menge genannt, "meal": "BREAKFAST"|"LUNCH"|"DINNER"|"SNACK"|null}]}
              Mengen ohne Angabe realistisch schätzen (1 Ei = 60 g, 1 Banane = 120 g, Portion Haferflocken = 50 g).
            {"type":"add_water","ml": Zahl}
            {"type":"create_note","title": Titel, "items": ["Punkt", …]}  für Listen (Einkaufsliste …), sonst "body": Text
            {"type":"append_note","title": Titel genau wie vom Nutzer genannt, "items": ["Punkt", …]}  z. B. "schreib Milch auf die Einkaufsliste" → title "Einkaufsliste" (gibt es sie nicht, legt die App sie an)
              Punkte NUR aus der Nachricht des Nutzers, Wort für Wort, jeden einzeln – auch ähnliche (Toilettenpapier UND Klopapier).
              NIE Punkte aus dem KONTEXT oder aus Beispielen übernehmen, nichts zusammenfassen, nichts abhaken.
            {"type":"log_weight","kg": Zahl}
            {"type":"diary","mood": 1-5,"energy": 1-5,"sleep": 1-5 (Schlafqualität),"text": Tagebuchtext oder "","date": "YYYY-MM-DD" oder null}
              Skala 1 = sehr schlecht … 5 = sehr gut. Fehlt Stimmung, Energie oder Schlaf: KEINE Aktion, sondern in reply genau danach fragen (z. B. "Wie war deine Energie von 1 bis 5?"). Text ist optional.
            {"type":"dream","had": true|false,"title": kurzer Titel,"text": Traum,"clarity": 1-5,"lucid": bool,"nightmare": bool,"recurring": bool,"emotions": ["Freude", …]}
              "kein Traum"/"nicht geträumt" → {"type":"dream","had": false}. Traum ohne Inhalt: KEINE Aktion, nach dem Traum fragen. Klarheit fehlt → nachfragen.
            {"type":"import_recipe","url": Link}  wenn der Nutzer einen Rezept-Link importieren will
            {"type":"log_gym","exercises":[{"name": deutscher Übungsname (z. B. "Bankdrücken","Kniebeuge","Kreuzheben"),"sets":[{"kg": Zahl,"reps": Zahl}, …]}],"minutes": Zahl oder null,"date": null}
              "3x8 mit 60 kg" = drei Sätze {"kg":60,"reps":8}. Fehlen Gewicht oder Wiederholungen: nachfragen, keine Aktion.
            {"type":"log_cali","exercises":[{"name": z. B. "Klimmzüge","Liegestütze","Dips","Handstand","Planche","L-Sit","sets":[{"reps": Zahl} oder {"seconds": Zahl} (Halten), optional "kg" Zusatzgewicht]}],"minutes": Zahl oder null}
            {"type":"log_run","km": Zahl,"minutes": Zahl,"avg_hr": Zahl oder null,"notes": "","date": null}
              Fehlt Distanz oder Dauer: nachfragen, keine Aktion.
            {"type":"navigate","to":"TODAY"|"SPORT"|"JOURNAL"|"NUTRITION"|"SETTINGS"}  nur wenn der Nutzer etwas öffnen will
            Notizen schreiben/ergänzen siehe create_note/append_note.
            Erzählt der Nutzer von einem gemachten Training (Gym, Calisthenics, Lauf), IMMER sofort die passende log_-Aktion – nicht nach Stimmung oder Energie fragen, das gehört nur zum Tagebuch.
            Calisthenics = Körpergewicht (Klimmzüge, Liegestütze, Dips, L-Sit, Handstand …) → log_cali; Hanteln/Maschinen mit kg → log_gym.
            Nachfragen: nur nach dem, was fehlt, eine kurze Frage. Antwortet der Nutzer darauf, die Aktion mit allen Angaben aus dem BISHERIGEN GESPRÄCH ausführen.
            Bei Aktionen: reply nur kurz bestätigen (z. B. "Erledigt!"), KEINE Kalorien oder Nährwerte nennen – die echten Werte ergänzt die App. Bei Fragen: konkret auf die Daten im KONTEXT eingehen.
        """.trimIndent()
    }
}
