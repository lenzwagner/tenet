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
) {
    /** Where the app should go, when the user asked to open something. */
    enum class Destination { TODAY, SPORT, JOURNAL, NUTRITION, SETTINGS }

    data class Reply(val text: String, val navigate: Destination? = null)

    val aiEnabled: Boolean get() = ai.config.value.usable

    suspend fun handle(text: String, name: String = "Tenny"): Reply {
        if (text.isBlank()) return Reply("Sag mir, was ich tun soll.")
        if (!aiEnabled) return offline(text)
        val answer = ai.json(SYSTEM.replace("\"Tenny\"", "\"$name\"") + "\n\nKONTEXT\n" + context(), text, maxTokens = 900)
            ?: return offline(text).let { it.copy(text = "KI gerade nicht erreichbar. " + it.text) }
        val done = mutableListOf<String>()
        var navigate: Destination? = null
        val actions = answer.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            runCatching {
                when (a.optString("type")) {
                    "log_food" -> done += logFood(a)
                    "add_water" -> done += addWater(a.optInt("ml"))
                    "create_note" -> done += createNote(a.optString("title"), a.optString("body"))
                    "append_note" -> done += appendNote(a.optString("title"), a.optString("text"))
                    "log_weight" -> done += logWeight(a.optDouble("kg", Double.NaN))
                    "navigate" -> navigate = runCatching { Destination.valueOf(a.optString("to").uppercase()) }.getOrNull()
                }
            }.onFailure { done += "Das hat nicht geklappt: ${a.optString("type")}" }
        }
        val reply = answer.optString("reply").takeIf { it.isNotBlank() && it != "null" }
        return Reply(listOfNotNull(reply, done.filter { it.isNotBlank() }.joinToString("\n").ifBlank { null }).joinToString("\n"), navigate)
    }

    /** Without AI: simple food sentences still work ("100 g Haferflocken"). */
    private suspend fun offline(text: String): Reply {
        val phrases = FoodPhraseParser.parse(text)
        if (phrases.isEmpty()) {
            return Reply("Ohne KI verstehe ich nur Essen wie „100 g Haferflocken“. Den NVIDIA-Schlüssel trägst du unter Einstellungen → KI ein.")
        }
        val lines = phrases.map { logPhrase(it, defaultMeal()) }
        return Reply(lines.joinToString("\n"))
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

    private suspend fun createNote(title: String, body: String): String {
        if (title.isBlank() && body.isBlank()) return ""
        val now = System.currentTimeMillis()
        entries.save(
            Entry(
                id = newUuid(), type = EntryType.NOTE, title = title.trim(), body = body.trim(),
                createdAt = now, updatedAt = now, entryDate = LocalDate.now().toString(),
            ),
            diaryMeta = null,
            dreamMeta = null,
        )
        return "✓ Notiz „${title.ifBlank { "Ohne Titel" }}“ angelegt"
    }

    /** Adds a line to a note found by title; checklists get a new item. */
    private suspend fun appendNote(title: String, text: String): String {
        if (text.isBlank()) return ""
        val note = entries.findByTitle(title)
            ?: entries.search(title).firstOrNull { it.type == EntryType.NOTE }
            ?: return createNote(title, "- [ ] ${text.trim()}")
        val body = note.body.trimEnd()
        val isList = body.lines().any { it.trimStart().startsWith("- [") }
        val line = if (isList) "- [ ] ${text.trim()}" else text.trim()
        entries.updateBody(note.id, if (body.isEmpty()) line else "$body\n$line")
        return "✓ In „${note.title.ifBlank { "Notiz" }}“ ergänzt: ${text.trim()}"
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
        val notes = entries.observeByType(EntryType.NOTE).first().take(15)
            .joinToString("\n") { "- „${it.title.ifBlank { "Ohne Titel" }}“: ${it.body.replace('\n', ' ').take(160)}" }.ifBlank { "keine" }
        return """
            Heute: ${today.format(day)}, ${LocalTime.now().withSecond(0).withNano(0)} Uhr
            Ernährung heute: ${totals.kcal.roundToInt()} von ${goal.kcal.roundToInt()} kcal, Eiweiß ${totals.protein.roundToInt()}/${goal.protein.roundToInt()} g, Wasser $water ml
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
        private val SYSTEM = """
            Du bist "Tenny", der freundliche Begleiter in der Fitness- und Journal-App Tenet. Antworte immer auf Deutsch, kurz (1–3 Sätze), du duzt.
            Du kannst Fragen zu Training, Ernährung, Gesundheit und Notizen mit dem KONTEXT beantworten und Aktionen ausführen.
            Antworte NUR mit JSON: {"reply": Text für den Nutzer, "actions": [ ... ]}
            Aktionen (nur wenn ausdrücklich gewünscht, sonst leere Liste):
            {"type":"log_food","items":[{"name": einfacher deutscher Lebensmittelname für die Suche, "grams": Zahl, "explicit": true wenn Menge genannt, "meal": "BREAKFAST"|"LUNCH"|"DINNER"|"SNACK"|null}]}
              Mengen ohne Angabe realistisch schätzen (1 Ei = 60 g, 1 Banane = 120 g, Portion Haferflocken = 50 g).
            {"type":"add_water","ml": Zahl}
            {"type":"create_note","title": Text, "body": Text (Listen als Zeilen "- [ ] Punkt")}
            {"type":"append_note","title": Titel einer vorhandenen Notiz, "text": neuer Punkt}  z. B. "schreib Milch auf die Einkaufsliste"
            {"type":"log_weight","kg": Zahl}
            {"type":"navigate","to":"TODAY"|"SPORT"|"JOURNAL"|"NUTRITION"|"SETTINGS"}  nur wenn der Nutzer etwas öffnen will
            Bei Aktionen: reply nur kurz bestätigen (z. B. "Erledigt!"), KEINE Kalorien oder Nährwerte nennen – die echten Werte ergänzt die App. Bei Fragen: konkret auf die Daten im KONTEXT eingehen.
        """.trimIndent()
    }
}
