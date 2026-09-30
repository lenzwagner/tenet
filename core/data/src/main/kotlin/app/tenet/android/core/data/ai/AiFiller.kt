package app.tenet.android.core.data.ai

import app.tenet.android.core.common.FoodPhrase
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dictation → form fields, one function per screen. Every result is
 * clamped/filtered to valid values, so a sloppy model answer can never
 * put nonsense into the database. All functions return null when the AI
 * is off or the answer is unusable.
 */
@Singleton
class AiFiller @Inject constructor(private val ai: AiAssistant) {

    val enabled: Boolean get() = ai.config.value.usable

    // ---- Journal -----------------------------------------------------------

    data class DreamFill(
        val title: String?,
        val clarity: Int?,
        val lucid: Boolean?,
        val nightmare: Boolean?,
        val recurring: Boolean?,
        val emotions: List<String>,
        val symbols: List<String>,
    )

    suspend fun dream(text: String, allowedEmotions: List<String>): DreamFill? {
        val j = ai.json(
            SYSTEM + """
            Aufgabe: Traumbericht auswerten. JSON:
            {"title": kurzer Titel max 5 Wörter,
             "clarity": 1-5 (wie klar erinnert),
             "lucid": true nur wenn der Träumer wusste, dass er träumt,
             "nightmare": true nur bei überwiegend Angst/Bedrohung,
             "recurring": true nur wenn als wiederkehrend beschrieben,
             "emotions": Liste NUR aus [${allowedEmotions.joinToString()}],
             "symbols": wichtige Personen, Orte, Objekte (max 6, je 1-2 Wörter, Großschreibung)}
            """.trimIndent(),
            text,
        ) ?: return null
        return DreamFill(
            title = j.str("title"),
            clarity = j.int("clarity")?.coerceIn(1, 5),
            lucid = j.bool("lucid"),
            nightmare = j.bool("nightmare"),
            recurring = j.bool("recurring"),
            emotions = j.strings("emotions").filter { e -> allowedEmotions.any { it.equals(e, true) } }
                .map { e -> allowedEmotions.first { it.equals(e, true) } }.distinct(),
            symbols = j.strings("symbols").take(6),
        )
    }

    data class DiaryFill(
        val title: String?,
        val mood: Int?,
        val energy: Int?,
        val sleep: Int?,
        val tags: List<String>,
    )

    suspend fun diary(text: String): DiaryFill? {
        val j = ai.json(
            SYSTEM + """
            Aufgabe: Tagebucheintrag auswerten. JSON:
            {"title": kurzer Titel max 5 Wörter,
             "mood": 1-5 Stimmung (1 sehr schlecht, 5 sehr gut),
             "energy": 1-5 oder null wenn nicht erwähnt,
             "sleep": 1-5 Schlafqualität oder null wenn nicht erwähnt,
             "tags": max 4 kurze Themen-Tags (ein Wort, klein)}
            """.trimIndent(),
            text,
        ) ?: return null
        return DiaryFill(
            title = j.str("title"),
            mood = j.int("mood")?.coerceIn(1, 5),
            energy = j.int("energy")?.coerceIn(1, 5),
            sleep = j.int("sleep")?.coerceIn(1, 5),
            tags = j.strings("tags").map { it.removePrefix("#").lowercase() }.take(4),
        )
    }

    data class NoteFill(val title: String?, val tags: List<String>)

    suspend fun note(text: String): NoteFill? {
        val j = ai.json(
            SYSTEM + """
            Aufgabe: Notiz auswerten. JSON:
            {"title": prägnanter Titel max 5 Wörter, "tags": max 3 Themen-Tags (ein Wort, klein)}
            """.trimIndent(),
            text,
        ) ?: return null
        return NoteFill(j.str("title"), j.strings("tags").map { it.removePrefix("#").lowercase() }.take(3))
    }

    // ---- Nutrition -----------------------------------------------------------

    /**
     * Meal items with grams, the counted pieces ("2 Eier" = 2) and whether the
     * amount was said in g/ml (then it is never replaced by the history).
     */
    suspend fun foods(text: String): List<FoodPhrase>? {
        val j = ai.json(
            SYSTEM + """
            Aufgabe: Gegessenes erfassen. Schätze realistische Mengen in Gramm (Getränke in ml = g).
            Beispiele: 1 Ei = 60, 1 Scheibe Brot = 45, 1 Toast = 30, 1 EL Butter = 10, 1 Apfel = 150, 1 Banane = 120, 1 Glas Milch = 250, 1 Tasse Kaffee = 200, Schuss Milch = 30.
            Mengen gelten pro genannter Stückzahl (2 Eier = 120).
            JSON: {"items":[{"name": einfacher deutscher Lebensmittelname für eine Datenbanksuche (z. B. "Vollkornbrot", "Ei", "Butter"),
                   "grams": Zahl, "count": genannte Stückzahl/Portionen oder null wenn keine Anzahl genannt,
                   "explicit": true nur wenn die Menge in g, kg, ml oder l gesagt wurde,
                   "meal": "BREAKFAST"|"LUNCH"|"DINNER"|"SNACK"|null}]}
            """.trimIndent(),
            text,
            maxTokens = 700,
        ) ?: return null
        val items = j.optJSONArray("items") ?: return null
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            // 0 g (e.g. "Kaffee" without amount) carries no information – skip.
            val grams = o.optDouble("grams", Double.NaN).takeIf { !it.isNaN() && it >= 1.0 }?.toFloat()?.coerceAtMost(3000f)
                ?: return@mapNotNull null
            FoodPhrase(
                name = name,
                grams = grams,
                count = o.optDouble("count", Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toFloat(),
                explicit = o.optBoolean("explicit", false),
                meal = o.str("meal")?.uppercase()?.takeIf { it in MEALS },
            )
        }.take(12)
    }

    // ---- Sport -----------------------------------------------------------------

    data class RunFill(
        val distanceKm: Float?,
        val durationSec: Int?,
        val avgHr: Int?,
        val date: LocalDate?,
        val time: LocalTime?,
    )

    suspend fun run(text: String, today: LocalDate = LocalDate.now()): RunFill? {
        val j = ai.json(
            SYSTEM + """
            Heute ist ${today} (${today.dayOfWeek}).
            Aufgabe: Laufeinheit erfassen. JSON:
            {"distance_km": Zahl oder null, "duration_sec": Gesamtdauer in Sekunden oder null,
             "avg_hr": Durchschnittspuls oder null, "date": "YYYY-MM-DD" oder null, "time": "HH:MM" Startzeit oder null}
            Uhrzeiten: "halb sieben" = 06:30, abends 18:30; "viertel nach acht" = 08:15.
            """.trimIndent(),
            text,
        ) ?: return null
        return RunFill(
            distanceKm = j.optDouble("distance_km", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.takeIf { it in 0.1f..200f },
            durationSec = j.int("duration_sec")?.takeIf { it in 60..86_400 },
            avgHr = j.int("avg_hr")?.takeIf { it in 40..230 },
            date = j.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isAfter(today) },
            time = j.str("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        )
    }

    data class GymSet(val weightKg: Float?, val reps: Int)
    data class GymFill(val exercise: String, val sets: List<GymSet>)

    /** [exercises]: names in the current session; answers are matched to them. */
    suspend fun gym(text: String, exercises: List<String>): List<GymFill>? {
        val j = ai.json(
            SYSTEM + """
            Aufgabe: Absolvierte Kraftsätze erfassen. Übungen der Session: [${exercises.joinToString()}].
            Jeder Satz ist ein eigenes Objekt: "3 Sätze à 8 mit 80 kg" = [{"kg":80,"reps":8},{"kg":80,"reps":8},{"kg":80,"reps":8}]. "10 und 8 Wiederholungen" = zwei Sätze. Jede Übung nur einmal. Gewicht in kg (Körpergewicht = 0).
            JSON: {"entries":[{"exercise": exakt ein Name aus der Liste, "sets":[{"kg": Zahl, "reps": Zahl}]}]}
            """.trimIndent(),
            text,
            maxTokens = 600,
        ) ?: return null
        val entries = j.optJSONArray("entries") ?: return null
        val parsed = (0 until entries.length()).mapNotNull { i ->
            val o = entries.optJSONObject(i) ?: return@mapNotNull null
            val name = o.str("exercise") ?: return@mapNotNull null
            val match = exercises.firstOrNull { it.equals(name, true) }
                ?: exercises.firstOrNull { it.contains(name, true) || name.contains(it, true) }
                ?: return@mapNotNull null
            val sets = o.optJSONArray("sets") ?: return@mapNotNull null
            GymFill(
                exercise = match,
                sets = (0 until sets.length()).mapNotNull { k ->
                    val s = sets.optJSONObject(k) ?: return@mapNotNull null
                    val reps = s.int("reps")?.takeIf { it in 1..200 } ?: return@mapNotNull null
                    GymSet(s.optDouble("kg", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0f, 500f), reps)
                }.take(12),
            ).takeIf { it.sets.isNotEmpty() }
        }
        // The model sometimes repeats an exercise per set: merge them.
        return parsed.groupBy { it.exercise }.map { (name, list) -> GymFill(name, list.flatMap { it.sets }) }
    }

    private companion object {
        const val SYSTEM = "Du bist ein Extraktor für eine deutsche Tagebuch- und Fitness-App. " +
            "Der Text ist oft ein unsauberes Sprachdiktat. Antworte ausschließlich mit einem JSON-Objekt, " +
            "ohne Erklärung. Erfinde nichts, was nicht im Text steht.\n"
        val MEALS = setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")
    }
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }

private fun JSONObject.int(key: String): Int? =
    if (isNull(key)) null else optDouble(key, Double.NaN).takeIf { !it.isNaN() }?.toInt()

private fun JSONObject.bool(key: String): Boolean? = if (!has(key) || isNull(key)) null else optBoolean(key)

private fun JSONObject.strings(key: String): List<String> {
    val a: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() } }.distinct()
}
