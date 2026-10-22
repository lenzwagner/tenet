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
import app.tenet.android.core.database.entity.Food
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
    private val memory: CompanionMemory,
) {
    /** Where the app should go, when the user asked to open something. */
    enum class Destination { TODAY, SPORT, JOURNAL, NUTRITION, SETTINGS }

    /** What an [Open] leads to. */
    enum class OpenKind { ENTRY, RUN, WORKOUT, RECIPE, NUTRITION, BODY }

    /** Something just stored that can be opened: [id] of the entry/run/session/recipe, [extra] = entry type. */
    data class Open(val label: String, val kind: OpenKind, val id: String = "", val extra: String = "")

    /** [acted]: something was stored; [opens]: what of it can be opened from the chat. */
    data class Reply(val text: String, val navigate: Destination? = null, val acted: Boolean = false, val opens: List<Open> = emptyList())

    val aiEnabled: Boolean get() = ai.config.value.usable

    /** [onStep] gets what is being done right now ("trägt 300 ml Wasser ein"), for the working view. */
    /** One earlier exchange of this chat, so follow-up answers ("Stimmung 4") complete a request. */
    data class Turn(val user: String, val assistant: String)

    suspend fun handle(
        text: String,
        name: String = "Tenny",
        history: List<Turn> = emptyList(),
        /** Photos as data URIs ("trag diesen Riegel ein"). */
        images: List<String> = emptyList(),
        onStep: (String) -> Unit = {},
    ): Reply {
        if (text.isBlank() && images.isEmpty()) return Reply("Sag mir, was ich tun soll.")
        if (!aiEnabled) return if (images.isEmpty()) offline(text) else Reply("Für Fotos brauche ich die KI (Einstellungen → KI).")
        val reply = answer(text.ifBlank { "Was ist auf dem Foto? Wenn es ein Lebensmittel ist, trag es ein." }, name, history, images, onStep)
        // Remembered for a day: later messages may refer to it ("ergänze das").
        memory.add(text.ifBlank { "(Foto)" } + if (images.isNotEmpty() && text.isNotBlank()) " (mit Foto)" else "", reply.text)
        return reply
    }

    private suspend fun answer(text: String, name: String, history: List<Turn>, images: List<String>, onStep: (String) -> Unit): Reply {
        // chat(): retries when NIM is busy, turns thinking off and falls back to a second
        // model – the plain json() call gave up after 30 s on slower models.
        val talk = history.takeLast(6).joinToString("\n") { "Nutzer: ${it.user}\n$name: ${it.assistant}" }
        // A photo is read first by a model that sees; the request itself then runs as usual on what was found.
        val photo = if (images.isEmpty()) null else {
            onStep("liest das Foto")
            ai.chat(PHOTO_READ, VISION_MODELS, images = images, maxTokens = 500, timeoutMs = 60_000, configuredFirst = false, accept = { AiAssistant.extractJson(it) != null })
                ?.let { AiAssistant.extractJson(it) }
                ?: return Reply("Ich konnte das Foto nicht lesen (${ai.lastError ?: "keine Antwort"}). Versuch es nochmal oder sag mir, was es ist.")
        }
        val now = System.currentTimeMillis()
        val earlier = memory.turns(now).takeLast(12).joinToString("\n") { t ->
            val min = (now - t.at) / 60_000
            val ago = if (min < 60) "vor $min min" else "vor ${min / 60} h"
            "- $ago · Nutzer: ${t.user.replace('\n', ' ').take(200)} → ${t.reply.replace('\n', ' ').take(200)}"
        }
        // No expiry: a list written weeks ago is still "the list" until another one is worked on.
        val lastNote = memory.lastNote
        val prompt = SYSTEM.replace("\"Tenny\"", "\"$name\"") + "\n\nKONTEXT\n" + context() +
            (if (earlier.isNotBlank()) "\n\nVERLAUF DER LETZTEN 24 STUNDEN (nur um Bezüge wie „das“, „die Liste“, „dort“ zu verstehen; nichts daraus erneut eintragen)\n$earlier" else "") +
            (lastNote?.let { "\n\nZULETZT BEARBEITETE NOTIZ (${java.time.Instant.ofEpochMilli(it.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}): „${it.title.ifBlank { "Ohne Titel" }}“ – „ergänze das“, „füg noch … hinzu“ ohne Namen meint diese Notiz (append_note mit genau diesem Titel)." } ?: "") +
            (photo?.let { "\n\nFOTO (vom Nutzer angehängt, bereits ausgelesen – „das“, „dieser“, „dieses“ in der Nachricht meint es): $it\n" + PHOTO } ?: "") +
            (if (talk.isNotBlank()) "\n\nBISHERIGES GESPRÄCH (deine Rückfragen und seine Antworten gehören zusammen)\n$talk" else "") +
            "\n\nNACHRICHT DES NUTZERS (nur darauf reagieren): " + text
        val chosen = ai.config.value.companionModel
        // The chosen model first, then the fast ones.
        val models = listOfNotNull(chosen.takeIf { it.isNotBlank() }) + fastModels()
        suspend fun ask(p: String) = ai.chat(p, models, maxTokens = 900, timeoutMs = 30_000, configuredFirst = false, accept = { AiAssistant.extractJson(it) != null })
            ?.let { AiAssistant.extractJson(it) }
        // No guessing from the sentence here: that once logged "Federweißer" for a question.
        val unreachable = { Reply("Ich erreiche die KI gerade nicht (${ai.lastError ?: "keine Antwort"}). Versuch es gleich nochmal.") }
        var answer = ask(prompt) ?: return unreachable()
        // It wants to read entries first: fetch them in full and let it answer with them.
        val wanted = (answer.optJSONArray("actions") ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.firstOrNull { it.optString("type") == "lookup" }?.optString("query")
        }?.takeIf { it.isNotBlank() && it != "null" }
        if (wanted != null) {
            onStep("liest in deinen Einträgen nach „$wanted“")
            val found = lookup(wanted)
            answer = ask(
                prompt + "\n\nGEFUNDENE EINTRÄGE zu „$wanted“ (vollständig gelesen):\n" + found.ifBlank { "nichts gefunden" } +
                    "\nJetzt die Nachricht des Nutzers damit beantworten bzw. ausführen – kein weiteres lookup.",
            ) ?: return unreachable()
        }
        val done = mutableListOf<String>()
        val opens = mutableListOf<Open>()
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
            // The label's values belong to the one product in the photo.
            if (photo != null && a.optString("type") == "log_food") {
                a.optJSONArray("items")?.takeIf { it.length() == 1 }?.optJSONObject(0)?.let { item ->
                    listOf("kcal100", "protein100", "carbs100", "fat100", "brand").forEach { k -> if (!item.has(k) && photo.has(k) && !photo.isNull(k)) item.put(k, photo.get(k)) }
                }
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
                    "log_food" -> done += logFood(a, opens)
                    "add_water" -> done += addWater(a.optInt("ml"))
                    "create_note" -> {
                        val title = a.optString("title")
                        val named = namesNote(title, text)
                        // "Ergänze das" after a list was written – also yesterday's: the model sometimes starts a new note.
                        val followUp = FOLLOW_UP.containsMatchIn(text) && !NEW_NOTE.containsMatchIn(text)
                        val target = when {
                            NEW_NOTE.containsMatchIn(text) -> null
                            followUp && !named -> findNote(title) ?: lastNote?.let { entries.get(it.id)?.entry } ?: recentList()
                            // A note of that name exists already: extend it instead of a second one.
                            followUp || isList(a) -> findNote(title)
                            else -> null
                        }
                        done += when {
                            target != null && followUp && !said(noteItems(a, "body"), text) -> "Was soll ich in „${target.title.ifBlank { "Notiz" }}“ ergänzen?"
                            target != null -> appendTo(target, noteItems(a, "body"), opens)
                            isList(a) -> createNote(title, noteItems(a, "body"), opens)
                            else -> createTextNote(title, a.optString("body"), opens)
                        }
                    }
                    "append_note" -> {
                        val title = a.optString("title")
                        val named = namesNote(title, text)
                        val all = (history.map { it.user } + text).joinToString(" ")
                        // The note the user named; else the one the model picked from the list of all notes
                        // (however old – "meine Traumziele"); without a name the one last worked on, else the newest checklist.
                        val target = (if (named) findNote(noteTitle(title, all)) else null)
                            ?: findNote(title)
                            ?: if (named) null else lastNote?.let { entries.get(it.id)?.entry } ?: recentList()
                        done += if (!said(noteItems(a, "text"), (history.map { it.user } + text).joinToString(" "))) {
                            // Nothing of it was said: the model made the points up (e.g. from an example).
                            "Was soll ich ${target?.let { "in „${it.title.ifBlank { "Notiz" }}“ " }.orEmpty()}ergänzen?"
                        } else if (target != null) appendTo(target, noteItems(a, "text"), opens)
                        else appendNote(noteTitle(title, (history.map { it.user } + text).joinToString(" ")), noteItems(a, "text"), opens)
                    }
                    "edit_note" -> done += editNote(a, text, lastNote, opens)
                    "log_weight" -> done += logWeight(a.optDouble("kg", Double.NaN)).also { if (it.isNotBlank()) opens += Open("Gewichtsverlauf", OpenKind.BODY) }
                    "diary" -> done += diary(a, opens)
                    "dream" -> done += dream(a, opens)
                    "import_recipe" -> done += importRecipe(a.optString("url"), onStep, opens)
                    "log_gym" -> done += logStrength(Discipline.GYM, a, opens)
                    "log_cali" -> done += logStrength(Discipline.CALISTHENICS, a, opens)
                    "log_run" -> done += logRun(a, opens)
                    "navigate" -> navigate = runCatching { Destination.valueOf(a.optString("to").uppercase()) }.getOrNull()
                }
            }.onFailure { done += "Das hat nicht geklappt: ${a.optString("type")}" }
        }
        val reply = answer.optString("reply").takeIf { it.isNotBlank() && it != "null" }
        val stored = done.filter { it.isNotBlank() }
        if (asks.isNotEmpty()) {
            return Reply((stored + asks).joinToString("\n"), navigate, acted = false, opens = opens.distinct())
        }
        return Reply(
            // After an action the app's own confirmation is enough; the model's text is for answers.
            if (stored.isNotEmpty()) stored.joinToString("\n") else reply ?: "Hm, da weiß ich gerade nichts zu.",
            navigate,
            acted = stored.any { it.startsWith("✓") },
            opens = opens.distinct(),
        )
    }

    /**
     * The note of that name, however it is written: same title, or one containing
     * the other ("Einkauf" finds "Einkaufsliste"); the one changed last wins.
     */
    private suspend fun findNote(title: String): Entry? {
        val wanted = norm(title).trim()
        if (wanted.length < 3 || wanted in GENERIC_NOTE_WORDS) return null
        val notes = entries.observeByType(EntryType.NOTE).first().sortedByDescending { it.updatedAt }
        return notes.firstOrNull { norm(it.title).trim() == wanted }
            ?: notes.firstOrNull { n -> norm(n.title).trim().let { t -> t.length >= 4 && (t.contains(wanted) || wanted.contains(t)) } }
            // A slip of the tongue or the keyboard: "Einkaufliste" is the "Einkaufsliste".
            ?: notes.firstOrNull { n -> norm(n.title).trim().let { t -> t.length >= 6 && wanted.length >= 6 && distance(t, wanted) <= 2 } }
    }

    /** Edit distance between two short strings (letters added, dropped or swapped). */
    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /** The checklist changed last: what "ergänze das" means when nothing else is known. */
    private suspend fun recentList(): Entry? =
        entries.observeByType(EntryType.NOTE).first()
            .filter { it.body.lines().any { l -> l.trimStart().startsWith("- [") } }
            .maxByOrNull { it.updatedAt }

    /** Ticks, unticks or removes points of a note ("streich Japan von den Traumzielen", "hak Milch ab"). */
    private suspend fun editNote(a: JSONObject, text: String, lastNote: CompanionMemory.Note?, opens: MutableList<Open>): String {
        val title = a.optString("title")
        val note = findNote(noteTitle(title, text)) ?: findNote(title)
            ?: (if (namesNote(title, text)) null else lastNote?.let { entries.get(it.id)?.entry } ?: recentList())
            ?: return "Die Notiz „${title.ifBlank { "?" }}“ finde ich nicht."
        fun list(key: String) = a.optJSONArray(key)?.let { arr -> (0 until arr.length()).map { norm(arr.optString(it)).trim() }.filter { it.length >= 2 } }.orEmpty()
        val remove = list("remove")
        val check = list("check")
        val uncheck = list("uncheck")
        val changed = mutableListOf<String>()
        fun hits(line: String, wanted: List<String>) = norm(line.replace(Regex("""^\s*([-*•]\s*)?(\[[ xX]?]\s*)?"""), "")).trim().let { l -> l.isNotEmpty() && wanted.any { l == it || l.contains(it) || it.contains(l) } }
        val lines = note.body.lines().mapNotNull { line ->
            val name = line.replace(Regex("""^\s*([-*•]\s*)?(\[[ xX]?]\s*)?"""), "").trim()
            when {
                hits(line, remove) -> null.also { changed += "$name entfernt" }
                hits(line, check) && line.trimStart().startsWith("- [ ]") -> line.replaceFirst("- [ ]", "- [x]").also { changed += "$name abgehakt" }
                hits(line, uncheck) && line.trimStart().lowercase(Locale.GERMAN).startsWith("- [x]") -> line.replaceFirst(Regex("- \\[[xX]]"), "- [ ]").also { changed += "$name wieder offen" }
                else -> line
            }
        }
        if (changed.isEmpty()) return "In „${note.title.ifBlank { "Notiz" }}“ habe ich das nicht gefunden."
        entries.updateBody(note.id, lines.joinToString("\n"))
        noted(note.id, note.title, opens)
        return "✓ „${note.title.ifBlank { "Notiz" }}“: ${changed.joinToString(", ")}"
    }

    /** Entries of every kind matching [query], with their whole text (for "was steht in …?"). */
    private suspend fun lookup(query: String): String {
        val words = listOf(query) + query.split(' ').filter { it.length >= 4 }
        val found = words.flatMap { entries.search(it) }.distinctBy { it.id }.sortedByDescending { it.updatedAt }.take(5)
        return found.joinToString("\n---\n") { e ->
            val kind = when (e.type) {
                EntryType.NOTE -> "Notiz"
                EntryType.DIARY -> "Tagebuch"
                EntryType.DREAM -> "Traum"
                else -> "Eintrag"
            }
            "$kind „${e.title.ifBlank { "Ohne Titel" }}“ (${e.entryDate}):\n${e.body.take(1500)}"
        }
    }

    /** At least one of the [items] occurs in what the user [said] (word start is enough: "Badehosen"). */
    private fun said(items: List<String>, said: String): Boolean {
        val s = norm(said)
        // The request's own words do not count ("Ergänze" is no point "Ergänzung").
        return items.any { item -> norm(item).split(' ').any { w -> w.length >= 3 && COMMAND_STEMS.none { w.startsWith(it) } && s.contains(w.take(5)) } }
    }

    /** The user named a note in this message: the model's title as said, or "auf die/in meine … ". */
    private fun namesNote(modelTitle: String, said: String): Boolean {
        val m = modelTitle.trim()
        return (m.length > 2 && said.contains(m, ignoreCase = true)) || said.contains("einkauf", ignoreCase = true) ||
            // "zu der Liste" names none: any list could be meant.
            NOTE_TARGET.findAll(said).any { it.groupValues[1].lowercase(Locale.GERMAN) !in GENERIC_NOTE_WORDS }
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
        "edit_note" -> "ändert „${a.optString("title").ifBlank { "die Notiz" }}“"
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
        return Reply(lines.joinToString("\n"), opens = if (lines.any { it.startsWith("✓") }) listOf(Open("Ernährung heute", OpenKind.NUTRITION)) else emptyList())
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

    private suspend fun logFood(a: JSONObject, opens: MutableList<Open>): List<String> {
        val items = a.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val grams = o.optDouble("grams", Double.NaN).takeIf { !it.isNaN() && it >= 1 }?.toFloat()?.coerceAtMost(3000f)
                ?: return@mapNotNull null
            val meal = runCatching { MealType.valueOf(o.optString("meal").uppercase()) }.getOrNull() ?: defaultMeal()
            logPhrase(FoodPhrase(name = name, grams = grams, count = null, explicit = o.optBoolean("explicit", true), meal = meal.name), meal, label(o))
        }.also { lines -> if (lines.any { it.startsWith("✓") }) opens += Open("Ernährung heute", OpenKind.NUTRITION) }
    }

    /** Nutrition values read off a photographed label (per 100 g), for products the search does not know. */
    private fun label(o: JSONObject): Food? {
        val kcal = o.optDouble("kcal100", Double.NaN).takeIf { !it.isNaN() && it in 1.0..950.0 } ?: return null
        fun g(k: String) = o.optDouble(k, 0.0).takeIf { !it.isNaN() && it in 0.0..100.0 }?.toFloat() ?: 0f
        return Food(
            id = newUuid(), name = cap(o.optString("name")), brand = o.optString("brand").takeIf { it.isNotBlank() && it != "null" },
            kcalPer100 = kcal.toFloat(), proteinPer100 = g("protein100"), carbsPer100 = g("carbs100"), fatPer100 = g("fat100"),
        )
    }

    private suspend fun logPhrase(p: FoodPhrase, meal: MealType, fromLabel: Food? = null): String {
        val match = food.searchLocal(p.name).firstOrNull()
            // The label in the photo beats a guess from the online search.
            ?: fromLabel?.also { food.saveFood(it) }
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

    /** The note is remembered as "the one just worked on" and offered to open. */
    private fun noted(id: String, title: String, opens: MutableList<Open>) {
        memory.lastNote = CompanionMemory.Note(id, title, System.currentTimeMillis())
        opens += Open("Notiz „${title.ifBlank { "Ohne Titel" }}“", OpenKind.ENTRY, id, EntryType.NOTE.name)
    }

    private suspend fun createTextNote(title: String, body: String, opens: MutableList<Open>): String {
        if (title.isBlank() && body.isBlank()) return ""
        val now = System.currentTimeMillis()
        val id = newUuid()
        entries.save(
            Entry(
                id = id, type = EntryType.NOTE, title = cap(title), body = cap(body.takeIf { it != "null" }.orEmpty()),
                createdAt = now, updatedAt = now, entryDate = LocalDate.now().toString(),
            ),
            diaryMeta = null,
            dreamMeta = null,
        )
        noted(id, cap(title), opens)
        return "✓ Notiz „${cap(title).ifBlank { "Ohne Titel" }}“ angelegt"
    }

    private fun cap(text: String) = text.trim().replaceFirstChar { it.titlecase(Locale.GERMAN) }

    /** New note; items become an unchecked checklist, each starting with a capital letter. */
    private suspend fun createNote(title: String, items: List<String>, opens: MutableList<Open>): String {
        if (title.isBlank() && items.isEmpty()) return ""
        val now = System.currentTimeMillis()
        val id = newUuid()
        entries.save(
            Entry(
                id = id, type = EntryType.NOTE, title = cap(title), body = items.joinToString("\n") { "- [ ] $it" },
                createdAt = now, updatedAt = now, entryDate = LocalDate.now().toString(),
            ),
            diaryMeta = null,
            dreamMeta = null,
        )
        noted(id, cap(title), opens)
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
    private suspend fun appendNote(title: String, items: List<String>, opens: MutableList<Open>): String {
        if (items.isEmpty()) return ""
        val note = entries.findByTitle(title)
            // Only a note whose title really is (or contains) the name, never one that merely mentions it.
            ?: findNote(title)
            ?: return createNote(title, items, opens)
        return appendTo(note, items, opens)
    }

    /** Adds [items] to [note]; what is already on it is not added twice. */
    private suspend fun appendTo(note: Entry, items: List<String>, opens: MutableList<Open>): String {
        val body = note.body.trimEnd()
        val have = body.lines().map { it.replace(Regex("""^\s*([-*•]\s*)?(\[[ xX]?]\s*)?"""), "").trim().lowercase(Locale.GERMAN) }.toSet()
        @Suppress("NAME_SHADOWING")
        val items = items.filter { it.lowercase(Locale.GERMAN) !in have }
        if (items.isEmpty()) {
            noted(note.id, note.title, opens)
            return "Das steht schon in „${note.title.ifBlank { "Notiz" }}“."
        }
        val isList = body.isEmpty() || body.lines().any { it.trimStart().startsWith("- [") }
        val lines = items.joinToString("\n") { if (isList) "- [ ] $it" else it }
        entries.updateBody(note.id, if (body.isEmpty()) lines else "$body\n$lines")
        noted(note.id, note.title, opens)
        return "✓ In „${note.title.ifBlank { "Notiz" }}“ ergänzt: ${items.joinToString(", ")}"
    }

    private fun scale(a: JSONObject, key: String): Int? =
        a.optInt(key, 0).takeIf { it in 1..5 }

    private fun day(a: JSONObject): LocalDate =
        a.optString("date").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()

    /** Diary entry with mood, energy, sleep quality (1–5); today's entry is completed, not doubled. */
    private suspend fun diary(a: JSONObject, opens: MutableList<Open>): String {
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
        opens += Open("Tagebucheintrag", OpenKind.ENTRY, entry.id, EntryType.DIARY.name)
        val parts = listOfNotNull("Stimmung $mood/5", scale(a, "energy")?.let { "Energie $it/5" }, scale(a, "sleep")?.let { "Schlaf $it/5" })
        return "✓ Tagebuch · " + parts.joinToString(", ")
    }

    /** Dream: "had": false stores the no-dream day like the button on "Heute". */
    private suspend fun dream(a: JSONObject, opens: MutableList<Open>): String {
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
        opens += Open("Traum „$title“", OpenKind.ENTRY, id, EntryType.DREAM.name)
        return "✓ Traum „$title“ gespeichert"
    }

    private suspend fun importRecipe(url: String, onStep: (String) -> Unit, opens: MutableList<Open>): String {
        if (!url.startsWith("http")) return "Das ist kein gültiger Rezept-Link."
        val recipe = recipes.fromUrl(url) { onStep(it.trimEnd('.', '…', ' ').replaceFirstChar { c -> c.lowercase() }) }
            .getOrElse { return "Rezept-Import fehlgeschlagen: ${it.message}" }
        val id = recipes.save(recipe).getOrElse { return "Rezept „${recipe.title}“ gefunden, aber nicht gespeichert: ${it.message}" }
        opens += Open("Rezept „${recipe.title}“", OpenKind.RECIPE, id)
        return "✓ Rezept „${recipe.title}“ importiert · ${recipe.ingredients.size} Zutaten"
    }

    /** Gym or calisthenics session told afterwards. */
    private suspend fun logStrength(discipline: Discipline, a: JSONObject, opens: MutableList<Open>): String {
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
        val label = if (discipline == Discipline.GYM) "Gym" else "Calisthenics"
        val (found, unknown) = sport.logPastSession(discipline, exercises, a.optInt("minutes", 0).takeIf { it > 0 }, end) {
            opens += Open("$label-Training", OpenKind.WORKOUT, it)
        }
        if (found.isEmpty()) return "Übungen nicht gefunden: ${unknown.joinToString(", ")} – bitte im Katalog anlegen."
        return "✓ $label-Training · ${found.joinToString(", ")}" + if (unknown.isNotEmpty()) " (unbekannt: ${unknown.joinToString(", ")})" else ""
    }

    private suspend fun logRun(a: JSONObject, opens: MutableList<Open>): String {
        val km = a.optDouble("km", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return ""
        val minutes = a.optDouble("minutes", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return ""
        val sec = (minutes * 60).roundToInt()
        val start = day(a).let { d ->
            if (d == LocalDate.now()) java.time.LocalDateTime.now().minusSeconds(sec.toLong()) else d.atTime(18, 0)
        }
        opens += Open("Lauf", OpenKind.RUN, running.addManualRun(start, (km * 1000).toFloat(), sec, a.optInt("avg_hr", 0).takeIf { it > 0 }, a.optString("notes").takeIf { it != "null" }.orEmpty()))
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
        // Every note by name (newest first), so "die Einkaufsliste von gestern" can be found; the newest with more text.
        val notes = entries.observeByType(EntryType.NOTE).first().sortedByDescending { it.updatedAt }.take(150)
            .mapIndexed { i, it ->
                val changed = java.time.Instant.ofEpochMilli(it.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val list = it.body.lines().count { l -> l.trimStart().startsWith("- [") }
                "- „${it.title.ifBlank { "Ohne Titel" }}“ (geändert $changed" + (if (list > 0) ", Checkliste mit $list Punkten" else "") + "): " +
                    it.body.replace("- [ ] ", "").replace("- [x] ", "✓").replace('\n', ',').take(if (i < 8) 220 else if (i < 40) 60 else 0)
            }.joinToString("\n").ifBlank { "keine" }
        val journal = (entries.observeByType(EntryType.DIARY).first() + entries.observeByType(EntryType.DREAM).first())
            .sortedByDescending { it.entryDate }.take(8)
            .joinToString("\n") { "- ${it.entryDate} ${if (it.type == EntryType.DREAM) "Traum" else "Tagebuch"} „${it.title.ifBlank { "Ohne Titel" }}“: ${it.body.replace('\n', ' ').take(120)}" }
            .ifBlank { "keine" }
        return """
            Heute: ${today.format(day)}, ${LocalTime.now().withSecond(0).withNano(0)} Uhr
            Ernährung heute: ${totals.kcal.roundToInt()} von ${goal.kcal.roundToInt()} kcal, Eiweiß ${totals.protein.roundToInt()}/${goal.protein.roundToInt()} g, Wasser $water ml
            Herz: $heart
            Gesundheit: Schritte ${snap?.steps ?: "?"}, Tenet-Form (eigener Erholungs-Score, nicht Google Health) ${snap?.form ?: "?"}/100, Schlaf ${snap?.sleepText ?: "?"}, Woche aktiv ${snap?.weekActiveMin ?: 0}/150 min
            Trainings der letzten 14 Tage:
            $sessions
            Geplant (7 Tage):
            $planned
            Notizen (alle, neueste zuerst):
            $notes
            Letzte Tagebuch- und Traumeinträge:
            $journal
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

        /** Models that understand images. */
        private val VISION_MODELS = listOf("meta/llama-3.2-11b-vision-instruct", "nvidia/llama-3.2-11b-vision-instruct")

        private val FOLLOW_UP = Regex("ergänz|ergaenz|füg|fueg|hinzu|dazu|außerdem|ausserdem|\\bnoch\\b|\\bauch\\b", RegexOption.IGNORE_CASE)
        private val NEW_NOTE = Regex("neue[nrs]?\\s+(\\p{L}+\\s+)?(liste|notiz|checkliste)|\\banlegen\\b|\\bleg\\b.*\\ban\\b|\\berstell", RegexOption.IGNORE_CASE)
        private val NOTE_TARGET = Regex("(?:auf|in|zu)\\s+(?:die|der|den|das|meine|meiner|meinen|mein)\\s+([\\p{L}-]{3,})", RegexOption.IGNORE_CASE)
        private val COMMAND_STEMS = listOf("ergae", "fueg", "schre", "hinzu", "dazu", "notiz", "liste", "punkt", "eintr")
        private val GENERIC_NOTE_WORDS = setOf("liste", "notiz", "checkliste", "notizen", "aufzählung")

        private val PHOTO_READ = """
            Lies dieses Foto aus. Antworte NUR mit JSON, auf Deutsch:
            {"what": was zu sehen ist (1 Satz), "product": Produkt- oder Gerichtname wie auf der Packung oder null, "brand": Marke oder null,
             "grams": Gewicht eines Stücks bzw. der Packung in Gramm laut Aufdruck oder null,
             "kcal100": kcal pro 100 g laut Nährwerttabelle oder null, "protein100": g, "carbs100": g, "fat100": g (jeweils pro 100 g, nur ablesen, nie raten),
             "text": sonstiger gut lesbarer Text im Bild, kurz}
        """.trimIndent()

        private val PHOTO = """
            Soll es eingetragen werden (z. B. "trag diesen Riegel ein"): log_food mit "name" = product aus dem FOTO, "grams" = grams aus dem FOTO (fehlt es: realistisch schätzen), "explicit": true.
            Bei einer Frage zum Foto: in reply mit dem FOTO-Inhalt beantworten. Ist darauf nichts Brauchbares: das in reply sagen, keine Aktion.
        """.trimIndent()

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
              "Ergänze das", "füg noch X hinzu", "auch noch Y" ohne Namen → append_note auf die ZULETZT BEARBEITETE NOTIZ; gibt es keine, auf die passende Notiz aus der Liste im KONTEXT (Titel exakt von dort, z. B. die Einkaufsliste von gestern). NIE eine neue Notiz, wenn eine passende existiert.
              Passt die Nachricht inhaltlich zu einer vorhandenen Notiz (z. B. "füg Japan zu meinen Reisezielen hinzu" und es gibt „Traumziele“), deren Titel exakt aus der Liste nehmen – egal wie alt sie ist.
            {"type":"edit_note","title": Titel aus der Liste, "remove": ["Punkt", …], "check": ["Punkt", …], "uncheck": ["Punkt", …]}  Punkte streichen, abhaken oder wieder öffnen ("streich Milch von der Einkaufsliste", "hak Brot ab").
            {"type":"lookup","query": Suchwort}  wenn du für die Antwort den ganzen Text einer Notiz, eines Tagebuch- oder Traumeintrags brauchst, der im KONTEXT nur angerissen ist. Dann KEINE andere Aktion; die App liefert die Einträge und fragt dich erneut.
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
