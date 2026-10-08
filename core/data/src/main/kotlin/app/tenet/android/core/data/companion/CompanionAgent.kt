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
) {
    /** Where the app should go, when the user asked to open something. */
    enum class Destination { TODAY, SPORT, JOURNAL, NUTRITION, SETTINGS }

    /** [acted]: something was stored – the chat closes and a short note confirms it. */
    data class Reply(val text: String, val navigate: Destination? = null, val acted: Boolean = false)

    val aiEnabled: Boolean get() = ai.config.value.usable

    suspend fun handle(text: String, name: String = "Tenny"): Reply {
        if (text.isBlank()) return Reply("Sag mir, was ich tun soll.")
        if (!aiEnabled) return offline(text)
        // chat(): retries when NIM is busy, turns thinking off and falls back to a second
        // model – the plain json() call gave up after 30 s on slower models.
        val prompt = SYSTEM.replace("\"Tenny\"", "\"$name\"") + "\n\nKONTEXT\n" + context() + "\n\nNACHRICHT DES NUTZERS (nur darauf reagieren): " + text
        val answer = ai.chat(
            prompt, fastModels(), maxTokens = 700, timeoutMs = 30_000, configuredFirst = false,
            accept = { AiAssistant.extractJson(it) != null },
        )

            ?.let { AiAssistant.extractJson(it) }
            // No guessing from the sentence here: that once logged "Federweißer" for a question.
            ?: return Reply("Ich erreiche die KI gerade nicht (${ai.lastError ?: "keine Antwort"}). Versuch es gleich nochmal.")
        val done = mutableListOf<String>()
        var navigate: Destination? = null
        val actions = answer.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            runCatching {
                when (a.optString("type")) {
                    "log_food" -> done += logFood(a)
                    "add_water" -> done += addWater(a.optInt("ml"))
                    "create_note" -> done += if (isList(a)) {
                        createNote(a.optString("title"), noteItems(a, "body"))
                    } else {
                        createTextNote(a.optString("title"), a.optString("body"))
                    }
                    "append_note" -> done += appendNote(a.optString("title"), noteItems(a, "text"))
                    "log_weight" -> done += logWeight(a.optDouble("kg", Double.NaN))
                    "navigate" -> navigate = runCatching { Destination.valueOf(a.optString("to").uppercase()) }.getOrNull()
                }
            }.onFailure { done += "Das hat nicht geklappt: ${a.optString("type")}" }
        }
        val reply = answer.optString("reply").takeIf { it.isNotBlank() && it != "null" }
        val stored = done.filter { it.isNotBlank() }
        return Reply(
            // After an action the app's own confirmation is enough; the model's text is for answers.
            if (stored.isNotEmpty()) stored.joinToString("\n") else reply ?: "Hm, da weiß ich gerade nichts zu.",
            navigate,
            acted = stored.any { it.startsWith("✓") },
        )
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

    /** Adds items to a note found by title (new note if there is none); checklists get unchecked items. */
    private suspend fun appendNote(title: String, items: List<String>): String {
        if (items.isEmpty()) return ""
        val note = entries.findByTitle(title)
            ?: entries.search(title).firstOrNull { it.type == EntryType.NOTE }
            ?: return createNote(title, items)
        val body = note.body.trimEnd()
        val isList = body.isEmpty() || body.lines().any { it.trimStart().startsWith("- [") }
        val lines = items.joinToString("\n") { if (isList) "- [ ] $it" else it }
        entries.updateBody(note.id, if (body.isEmpty()) lines else "$body\n$lines")
        return "✓ In „${note.title.ifBlank { "Notiz" }}“ ergänzt: ${items.joinToString(", ")}"
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
            "mistralai/mistral-7b-instruct-v0.3",
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
            {"type":"append_note","title": Titel einer vorhandenen Notiz, "items": ["Punkt", …]}  z. B. "schreib Milch auf die Einkaufsliste"
              Punkte NUR aus der Nachricht des Nutzers, Wort für Wort, jeden einzeln – auch ähnliche (Toilettenpapier UND Klopapier).
              NIE Punkte aus dem KONTEXT oder aus Beispielen übernehmen, nichts zusammenfassen, nichts abhaken.
            {"type":"log_weight","kg": Zahl}
            {"type":"navigate","to":"TODAY"|"SPORT"|"JOURNAL"|"NUTRITION"|"SETTINGS"}  nur wenn der Nutzer etwas öffnen will
            Bei Aktionen: reply nur kurz bestätigen (z. B. "Erledigt!"), KEINE Kalorien oder Nährwerte nennen – die echten Werte ergänzt die App. Bei Fragen: konkret auf die Daten im KONTEXT eingehen.
        """.trimIndent()
    }
}
