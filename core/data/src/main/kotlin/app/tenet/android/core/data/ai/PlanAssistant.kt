package app.tenet.android.core.data.ai

import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.common.RunZone
import app.tenet.android.core.data.PlanRepository
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.MeasureType
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * The plan chat: the user says what they want ("4er-Split, 5 Tage, viel
 * Volumen für Brust, kein Kreuzheben"), the model answers with a short reply
 * and a complete plan built from the app's own exercise catalogue. Every
 * further message revises that plan. Nothing is stored here – the caller
 * saves the draft when the user accepts it.
 */
@Singleton
class PlanAssistant @Inject constructor(
    private val ai: AiAssistant,
    private val plans: PlanRepository,
    private val running: RunningRepository,
) {
    data class Turn(val user: String, val assistant: String)

    /** A running plan as the app's own generator needs it. */
    data class RunDraft(
        val goal: RunPlanMath.RunGoal,
        val goalDate: LocalDate,
        val runsPerWeek: Int,
        /** Weekdays 0 (Mo) … 6 (So), one per run; empty = default days. */
        val days: List<Int>,
        val current5kSec: Int?,
        val targetTimeSec: Int?,
        val volume: Float,
        /** Which kind of run on which day (0 = Mo … 6 = So); empty = the standard mix. */
        val sessions: List<Pair<Int, RunZone>> = emptyList(),
        /** Wanted kilometres in the biggest week; null = from [volume]. */
        val weeklyKm: Int? = null,
    ) {
        val weeks: Int get() = RunPlanMath.weekCount(app.tenet.android.core.common.WeekMath.weekStart(LocalDate.now()), goalDate)
    }

    /**
     * What the user set in the form: kilometres per week and how many runs of each
     * kind. These numbers hold; the model only places them on days and explains.
     */
    data class RunWish(val weeklyKm: Int?, val long: Int, val tempo: Int, val interval: Int, val easy: Int) {
        val zones: List<RunZone>
            get() = List(easy) { RunZone.EASY } + List(tempo) { RunZone.TEMPO } + List(interval) { RunZone.INTERVAL } + List(long) { RunZone.LONG }
    }

    /** Draft exercise with its name, for showing the proposal before it is stored. */
    data class NamedWorkout(val title: String, val exercises: List<Pair<String, PlanRepository.DraftExercise>>)

    data class Answer(
        val reply: String,
        /** Gym / calisthenics proposal, null while the model is still asking. */
        val draft: PlanRepository.Draft? = null,
        val preview: List<NamedWorkout> = emptyList(),
        val run: RunDraft? = null,
        /** Exercises the model named that are not in the catalogue (left out). */
        val unknown: List<String> = emptyList(),
        val failed: Boolean = false,
    )

    val enabled: Boolean get() = ai.config.value.usable

    suspend fun chat(
        discipline: Discipline,
        history: List<Turn>,
        message: String,
        current: Answer?,
        /** Running form values that must hold (null = free chat). */
        wish: RunWish? = null,
        /** Rest days (ISO 1–7) no plan may train on. */
        restDays: Set<Int> = emptySet(),
    ): Answer {
        if (!enabled) return Answer("Die KI ist aus oder ohne Schlüssel (Einstellungen → KI).", failed = true)
        val talk = history.takeLast(8).joinToString("\n") { "Nutzer: ${it.user}\nCoach: ${it.assistant}" }
        val rest = restDays.filter { it in 1..7 }.sorted()
        val restLine = if (rest.isEmpty()) "" else "\nRUHETAGE des Nutzers (dort NIE ein Training einplanen): " +
            rest.joinToString(", ") { java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.FULL, Locale.GERMAN) }
        val prompt = (if (discipline == Discipline.RUNNING) runPrompt(talk, message, current?.run) else strengthPrompt(discipline, talk, message, current)) + restLine
        val json = ai.chat(
            prompt, MODELS, maxTokens = 3000, timeoutMs = 60_000, configuredFirst = false,
            accept = { AiAssistant.extractJson(it) != null },
        )?.let { AiAssistant.extractJson(it) }
            ?: return Answer("Ich erreiche die KI gerade nicht (${ai.lastError ?: "keine Antwort"}). Versuch es gleich nochmal.", failed = true)
        val reply = json.optString("reply").takeIf { it.isNotBlank() && it != "null" } ?: "Hier ist mein Vorschlag."
        return if (discipline == Discipline.RUNNING) {
            // Times only as the user stated them: the model must not make up a goal time or a 5 km time.
            val said = (history.map { it.user } + message).joinToString(" ").lowercase(Locale.GERMAN)
            val hasTime = Regex("""\d{1,2}:\d{2}""").containsMatchIn(said) || Regex("""\d+\s*(min|minuten|stunden|h)\b""").containsMatchIn(said)
            val saidTarget = hasTime && Regex("zielzeit|unter |sub |in \\d|schaffen|wunschzeit").containsMatchIn(said)
            val said5k = hasTime && Regex("aktuell|bestzeit|derzeit|momentan|laufe ich|5-km-zeit|5 km in").containsMatchIn(said)
            val parsed = (json.optJSONObject("run")?.let(::parseRun) ?: current?.run)?.let { r ->
                r.copy(
                    targetTimeSec = r.targetTimeSec.takeIf { saidTarget } ?: current?.run?.targetTimeSec,
                    current5kSec = r.current5kSec.takeIf { said5k } ?: current?.run?.current5kSec,
                )
            }
            Answer(reply, run = parsed?.let { fit(it, wish, rest.map { d -> d - 1 }.toSet()) })
        } else {
            val plan = json.optJSONObject("plan") ?: return Answer(reply, current?.draft, current?.preview.orEmpty())
            parsePlan(discipline, reply, plan)
        }
    }

    /** Creates the running plan of [draft] with the app's generator (it becomes the active plan). */
    suspend fun createRunPlan(draft: RunDraft): String {
        val template = draft.sessions.sortedBy { it.first }.takeIf { it.size in 2..6 }
        return running.createPlan(
            goal = draft.goal,
            goalDate = draft.goalDate,
            runsPerWeek = template?.size ?: draft.runsPerWeek,
            current5kSec = draft.current5kSec,
            paceMethod = PaceMethod.VDOT,
            taper = true,
            targetTimeSec = draft.targetTimeSec,
            // An own mix already says which run is on which day.
            days = if (template != null) null else draft.days.takeIf { it.size == draft.runsPerWeek },
            volume = volumeOf(draft),
            template = template,
        )
    }

    /** Volume scale of a draft: from the wanted weekly kilometres when given. */
    fun volumeOf(draft: RunDraft): Float {
        val km = draft.weeklyKm ?: return draft.volume
        val template = draft.sessions.sortedBy { it.first }.ifEmpty { RunPlanMath.weeklyTemplate(draft.runsPerWeek) }
        val easyPace = draft.current5kSec?.let {
            app.tenet.android.core.common.RunPaceMath.targetPaceSecPerKm(RunZone.EASY, app.tenet.android.core.common.PaceAnchor(5_000, it), PaceMethod.VDOT)
        } ?: 360
        return RunPlanMath.volumeForWeeklyKm(draft.goal, km, draft.weeks, template, easyPace)
    }

    /** Kilometres of the biggest week of [draft], for the proposal card. */
    fun peakKm(draft: RunDraft): Int {
        val template = draft.sessions.sortedBy { it.first }.ifEmpty { RunPlanMath.weeklyTemplate(draft.runsPerWeek) }
        val easyPace = draft.current5kSec?.let {
            app.tenet.android.core.common.RunPaceMath.targetPaceSecPerKm(RunZone.EASY, app.tenet.android.core.common.PaceAnchor(5_000, it), PaceMethod.VDOT)
        } ?: 360
        val volume = volumeOf(draft)
        return (0 until draft.weeks.coerceAtLeast(1)).maxOf { w ->
            RunPlanMath.estimateWeekKm(RunPlanMath.planWeek(draft.goal, w, template.size, totalWeeks = draft.weeks, taper = true, volume = volume, template = template), easyPace)
        }.toInt()
    }

    /**
     * Makes a draft hold: the form's kilometres and run mix win over the model's, no
     * run lands on a rest day, at most six runs. The model's day choice is kept when
     * it fits; otherwise the runs are spread with hard days apart.
     */
    private fun fit(draft: RunDraft, wish: RunWish?, rest: Set<Int>): RunDraft {
        val wanted = wish?.zones?.take(6)
        var sessions = draft.sessions.filter { it.first !in rest }.distinctBy { it.first }.take(6)
        val counts = { list: List<RunZone> -> list.groupingBy { it }.eachCount() }
        if (wanted != null && (sessions.size != wanted.size || counts(sessions.map { it.second }) != counts(wanted))) {
            val free = (0..6).filter { it !in rest }
            val n = wanted.size.coerceAtMost(free.size)
            // The model's days when they fit, else evenly over the free days.
            val days = sessions.map { it.first }.sorted().takeIf { it.size == n }
                ?: List(n) { i -> free[(i * free.size) / n] }
            sessions = RunPlanMath.assignDays(wanted.take(n).mapIndexed { i, z -> i to z }, days)
        }
        if (sessions.size < 2) sessions = emptyList()
        return draft.copy(
            sessions = sessions.sortedBy { it.first },
            runsPerWeek = if (sessions.isEmpty()) draft.runsPerWeek else sessions.size,
            days = if (sessions.isEmpty()) draft.days.filter { it !in rest } else sessions.map { it.first }.sorted(),
            weeklyKm = wish?.weeklyKm ?: draft.weeklyKm,
        )
    }

    // ---- Strength (gym, calisthenics) ----------------------------------------------

    private suspend fun strengthPrompt(discipline: Discipline, talk: String, message: String, current: Answer?): String {
        val catalog = plans.catalog(discipline)
        val list = catalog.joinToString("\n") { e ->
            "- ${e.name} [${e.primaryMuscles}]" + if (e.measureType.timed()) " (Halten, Sekunden)" else ""
        }
        val now = current?.preview?.takeIf { it.isNotEmpty() }?.let { w ->
            "\n\nAKTUELLER ENTWURF (darauf aufbauen, nur ändern, was gewünscht ist):\n" + w.joinToString("\n") { wo ->
                wo.title + ": " + wo.exercises.joinToString(", ") { (n, e) -> "$n ${e.sets}×${e.reps}" }
            }
        }.orEmpty()
        val kind = if (discipline == Discipline.GYM) "Krafttraining im Gym" else "Calisthenics (Körpergewicht)"
        return """
            Du bist ein erfahrener Coach für $kind in der App Tenet. Antworte auf Deutsch, du duzt, kurz.
            Der Nutzer beschreibt, welchen Trainingsplan er will (Split, Tage pro Woche, Volumen, Übungen, Schwerpunkte, Ausschlüsse).
            Baue daraus einen vollständigen Plan. Antworte NUR mit JSON:
            {"reply": 1–3 Sätze: was du gebaut oder geändert hast und warum,
             "plan": {"name": kurzer Planname, "days": [ISO-Wochentage 1=Mo … 7=So], "workouts": [{"title": Name der Einheit, "exercises": [{"name": Übung, "sets": Zahl, "reps": Zahl, "rest": Pause in Sekunden}]}]}}
            Regeln:
            - Planname und Namen der Einheiten auf Deutsch (z. B. "Ganzkörper · Klimmzug-Fokus", "Push", "Beine").
            - Übungen NUR aus dem KATALOG, Name exakt wie dort. Was es dort nicht gibt, durch die ähnlichste Katalog-Übung ersetzen und das in reply sagen.
            - Nennt der Nutzer eine Split-Kombination (z. B. "Brust + Schultern / Rücken / Beine / Arme"), dann GENAU diese Einheiten, in dieser Reihenfolge, mit diesen Namen – und in jeder Einheit nur Übungen für die genannten Muskelgruppen.
            - Ziel Kraft, Hypertrophie (Muskelaufbau) oder Kraftausdauer bestimmt Wiederholungen und Pausen (siehe Volumen-Regel).
            - Split so, wie der Nutzer ihn nennt (2er = Oberkörper/Unterkörper, 3er = Push/Pull/Beine, 4er z. B. Brust+Trizeps / Rücken+Bizeps / Beine / Schultern+Bauch). Ohne Angabe: passend zu den Tagen.
            - Volumen: wenig ≈ 3–4 Übungen je Einheit, normal ≈ 5–6, viel ≈ 7–8; Sätze 2–5. Kraft: 3–6 Wdh. und 150–240 s Pause, Muskelaufbau: 8–12 Wdh. und 90–150 s, Ausdauer: 12–20 Wdh. und 45–75 s.
            - Schwere Grundübungen zuerst, Isolationsübungen danach. Keine Übung doppelt in einer Einheit.
            - Bei "(Halten, Sekunden)" ist "reps" die Haltezeit in Sekunden.
            - "days": so viele Tage wie gewünscht, sinnvoll verteilt (z. B. 3 Tage = [1,3,5]).
            - Fehlt etwas Wichtiges (z. B. gar keine Angabe zu Tagen und Split), triff eine sinnvolle Annahme und nenne sie in reply – trotzdem einen Plan liefern.
            - Nur wenn die Nachricht nichts mit einem Trainingsplan zu tun hat: "plan": null und in reply nachfragen.

            KATALOG:
            $list$now${if (talk.isNotBlank()) "\n\nBISHERIGES GESPRÄCH:\n$talk" else ""}

            NACHRICHT DES NUTZERS: $message
        """.trimIndent()
    }

    private suspend fun parsePlan(discipline: Discipline, reply: String, plan: JSONObject): Answer {
        val catalog = plans.catalog(discipline)
        val unknown = mutableListOf<String>()
        val workouts = plan.optJSONArray("workouts").objects().mapIndexedNotNull { index, w ->
            val exercises = w.optJSONArray("exercises").objects().mapNotNull { e ->
                val name = e.optString("name")
                val match = match(catalog, name) ?: run {
                    if (name.isNotBlank()) unknown += name
                    return@mapNotNull null
                }
                match.name to PlanRepository.DraftExercise(
                    exerciseId = match.id,
                    sets = e.optInt("sets", 3).coerceIn(1, 8),
                    reps = e.optInt("reps", 10).coerceIn(1, 300),
                    restSec = e.optInt("rest", 120).coerceIn(20, 360),
                )
            }.distinctBy { it.second.exerciseId }
            if (exercises.isEmpty()) null
            else NamedWorkout(w.optString("title").ifBlank { "Einheit ${index + 1}" }, exercises)
        }
            // Calisthenics trains one routine per plan.
            .let { if (discipline == Discipline.CALISTHENICS) it.take(1) else it.take(7) }
        if (workouts.isEmpty()) return Answer(reply, unknown = unknown)
        val days = plan.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.distinct().sorted() }.orEmpty()
        val draft = PlanRepository.Draft(
            name = plan.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "KI-Plan",
            days = days,
            workouts = workouts.map { w -> PlanRepository.DraftWorkout(w.title, w.exercises.map { it.second }) },
        )
        return Answer(reply, draft, workouts, unknown = unknown.distinct())
    }

    private fun MeasureType.timed() = this == MeasureType.DURATION || this == MeasureType.HOLD

    private fun key(t: String) = t.lowercase(Locale.GERMAN)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]"), "")

    private fun match(catalog: List<Exercise>, name: String): Exercise? {
        val k = key(name)
        if (k.length < 3) return null
        return catalog.firstOrNull { key(it.name) == k }
            ?: catalog.filter { key(it.name).contains(k) || k.contains(key(it.name)) }.minByOrNull { kotlin.math.abs(key(it.name).length - k.length) }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private inline fun <T, R : Any> List<T>.mapIndexedNotNull(transform: (Int, T) -> R?): List<R> {
        val out = mutableListOf<R>()
        forEachIndexed { i, t -> transform(i, t)?.let { out += it } }
        return out
    }

    // ---- Running ------------------------------------------------------------------

    private fun runPrompt(talk: String, message: String, current: RunDraft?): String {
        val now = current?.let {
            "\n\nAKTUELLER ENTWURF: Ziel ${it.goal.name}, Zieldatum ${it.goalDate}, ${it.runsPerWeek} Läufe/Woche, Tage ${it.days.map { d -> d + 1 }}, " +
                "5 km aktuell ${it.current5kSec ?: "unbekannt"} s, Zielzeit ${it.targetTimeSec ?: "keine"} s, Umfang ${it.volume}"
        }.orEmpty()
        return """
            Du bist ein Lauf-Coach in der App Tenet. Antworte auf Deutsch, du duzt, kurz.
            Der Nutzer beschreibt seinen Wunsch-Laufplan. Die App baut den Plan selbst (Wochen, Tempo, lange Läufe, Intervalle, Tapering) – du lieferst nur die Eckdaten.
            Heute ist ${LocalDate.now()}. Antworte NUR mit JSON:
            {"reply": 1–3 Sätze: welche Eckdaten du gewählt hast und warum,
             "run": {"goal": "FIVE_K"|"TEN_K"|"HALF"|"MARATHON"|"GENERAL", "date": "YYYY-MM-DD" (Wettkampf- bzw. Zieltag), "runsPerWeek": 2–6,
                     "days": [ISO-Wochentage 1=Mo … 7=So, genau so viele wie runsPerWeek], "current5k": "MM:SS" oder null, "target": "H:MM:SS" oder "MM:SS" oder null,
                     "weeklyKm": gewünschte Wochenkilometer als Zahl oder null,
                     "sessions": [{"day": ISO-Wochentag 1–7, "type": "EASY"|"LONG"|"TEMPO"|"INTERVAL"}] – genau ein Eintrag pro Lauf,
                     "volume": Umfangs-Faktor nach Wochenkilometern: bis 10 km → 0.8, 15–30 km → 1.0, 30–50 km → 1.15, 50–80 km → 1.5, 80–100 km und mehr → 2.1}}
            Regeln:
            - Ohne Zieldatum: 5 km → 8 Wochen, 10 km → 10, Halbmarathon → 12, Marathon → 16, "fit bleiben" → 8 Wochen ab heute.
            - "current5k" nur, wenn der Nutzer eine aktuelle Zeit nennt (auch aus 10-km- oder Pace-Angaben grob umrechnen), sonst null.
            - "target" ist die Zielzeit über die Zieldistanz, nur wenn genannt.
            - Lauf-Arten: EASY = lockerer Lauf, LONG = langer Lauf, TEMPO = Tempo-/Pace-Lauf, INTERVAL = Intervalle. Nennt der Nutzer eine Aufteilung (z. B. "1 Long Run, 1 Tempolauf, 1 Intervall, 2 lockere"), genau diese Anzahl je Art in "sessions". Ohne Angabe: 2 Läufe = EASY+LONG, 3 = EASY+INTERVAL+LONG, ab 4 zusätzlich TEMPO, Rest EASY.
            - Tage: LONG aufs Wochenende, TEMPO und INTERVAL nie an aufeinanderfolgenden Tagen und nicht direkt vor LONG, dazwischen EASY oder frei.
            - Nennt der Nutzer Wochenkilometer (z. B. "80–100 km"), danach "volume" wählen; ab 50 km pro Woche 5–6 Lauftage ansetzen.
            - Den langen Lauf aufs Wochenende legen, harte Einheiten nicht an aufeinanderfolgenden Tagen.
            - Fehlt etwas, triff eine sinnvolle Annahme und nenne sie in reply. Nur ohne jeden Bezug zum Laufen: "run": null und nachfragen.$now${if (talk.isNotBlank()) "\n\nBISHERIGES GESPRÄCH:\n$talk" else ""}

            NACHRICHT DES NUTZERS: $message
        """.trimIndent()
    }

    private fun parseRun(o: JSONObject): RunDraft? {
        val goal = RunPlanMath.RunGoal.entries.firstOrNull { it.name == o.optString("goal").uppercase() } ?: return null
        val today = LocalDate.now()
        val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()
            ?.takeIf { it.isAfter(today.plusDays(13)) && it.isBefore(today.plusWeeks(53)) }
            ?: today.plusWeeks(
                when (goal) {
                    RunPlanMath.RunGoal.FIVE_K, RunPlanMath.RunGoal.GENERAL -> 8L
                    RunPlanMath.RunGoal.TEN_K -> 10L
                    RunPlanMath.RunGoal.HALF -> 12L
                    RunPlanMath.RunGoal.MARATHON -> 16L
                },
            )
        val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.distinct().sorted().map { it - 1 } }.orEmpty()
        val runs = o.optInt("runsPerWeek", days.size.takeIf { it > 0 } ?: 3).coerceIn(2, 6)
        val sessions = o.optJSONArray("sessions").objects().mapNotNull { e ->
            val day = e.optInt("day", 0).takeIf { it in 1..7 } ?: return@mapNotNull null
            val zone = when (e.optString("type").uppercase()) {
                "EASY", "RECOVERY" -> RunZone.EASY
                "LONG" -> RunZone.LONG
                "TEMPO", "PACE" -> RunZone.TEMPO
                "INTERVAL", "INTERVALS" -> RunZone.INTERVAL
                else -> return@mapNotNull null
            }
            day - 1 to zone
        }.distinctBy { it.first }.take(6)
        return RunDraft(
            sessions = sessions,
            weeklyKm = o.optInt("weeklyKm", 0).takeIf { it in 5..250 },
            goal = goal,
            goalDate = date,
            runsPerWeek = runs,
            days = days.takeIf { it.size == runs }.orEmpty(),
            current5kSec = seconds(o.optString("current5k"))?.takeIf { it in 12 * 60..60 * 60 },
            targetTimeSec = seconds(o.optString("target"))?.takeIf { it in 12 * 60..7 * 3600 },
            volume = o.optDouble("volume", 1.0).toFloat().coerceIn(0.7f, 2.6f),
        )
    }

    /** "25:30" → 1530, "1:45:00" → 6300. */
    private fun seconds(text: String): Int? {
        val parts = text.trim().split(":").map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private companion object {
        val MODELS = listOf("nvidia/nemotron-3-super-120b-a12b", "openai/gpt-oss-20b")
    }
}
