package app.tenet.android.core.data.ai

import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.common.RunPlanMath
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
    ) {
        val weeks: Int get() = RunPlanMath.weekCount(app.tenet.android.core.common.WeekMath.weekStart(LocalDate.now()), goalDate)
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

    suspend fun chat(discipline: Discipline, history: List<Turn>, message: String, current: Answer?): Answer {
        if (!enabled) return Answer("Die KI ist aus oder ohne Schlüssel (Einstellungen → KI).", failed = true)
        val talk = history.takeLast(8).joinToString("\n") { "Nutzer: ${it.user}\nCoach: ${it.assistant}" }
        val prompt = if (discipline == Discipline.RUNNING) runPrompt(talk, message, current?.run) else strengthPrompt(discipline, talk, message, current)
        val json = ai.chat(
            prompt, MODELS, maxTokens = 3000, timeoutMs = 60_000, configuredFirst = false,
            accept = { AiAssistant.extractJson(it) != null },
        )?.let { AiAssistant.extractJson(it) }
            ?: return Answer("Ich erreiche die KI gerade nicht (${ai.lastError ?: "keine Antwort"}). Versuch es gleich nochmal.", failed = true)
        val reply = json.optString("reply").takeIf { it.isNotBlank() && it != "null" } ?: "Hier ist mein Vorschlag."
        return if (discipline == Discipline.RUNNING) {
            Answer(reply, run = json.optJSONObject("run")?.let(::parseRun) ?: current?.run)
        } else {
            val plan = json.optJSONObject("plan") ?: return Answer(reply, current?.draft, current?.preview.orEmpty())
            parsePlan(discipline, reply, plan)
        }
    }

    /** Creates the running plan of [draft] with the app's generator (it becomes the active plan). */
    suspend fun createRunPlan(draft: RunDraft): String = running.createPlan(
        goal = draft.goal,
        goalDate = draft.goalDate,
        runsPerWeek = draft.runsPerWeek,
        current5kSec = draft.current5kSec,
        paceMethod = PaceMethod.VDOT,
        taper = true,
        targetTimeSec = draft.targetTimeSec,
        days = draft.days.takeIf { it.size == draft.runsPerWeek },
        volume = draft.volume,
    )

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
                     "volume": 0.7 (wenig) … 1.0 (normal) … 1.3 (viel)}}
            Regeln:
            - Ohne Zieldatum: 5 km → 8 Wochen, 10 km → 10, Halbmarathon → 12, Marathon → 16, "fit bleiben" → 8 Wochen ab heute.
            - "current5k" nur, wenn der Nutzer eine aktuelle Zeit nennt (auch aus 10-km- oder Pace-Angaben grob umrechnen), sonst null.
            - "target" ist die Zielzeit über die Zieldistanz, nur wenn genannt.
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
        return RunDraft(
            goal = goal,
            goalDate = date,
            runsPerWeek = runs,
            days = days.takeIf { it.size == runs }.orEmpty(),
            current5kSec = seconds(o.optString("current5k"))?.takeIf { it in 12 * 60..60 * 60 },
            targetTimeSec = seconds(o.optString("target"))?.takeIf { it in 12 * 60..7 * 3600 },
            volume = o.optDouble("volume", 1.0).toFloat().coerceIn(0.7f, 1.3f),
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
