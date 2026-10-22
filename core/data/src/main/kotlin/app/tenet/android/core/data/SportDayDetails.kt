package app.tenet.android.core.data

import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RunType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

/**
 * What a run is about, in one short line for the day view on "Heute": its kind,
 * kilometres and pace. Gym and calisthenics say enough with their title
 * ("Push", "Skill + Kraft") – a list of exercises or skills was too much there.
 */
@Singleton
class SportDayDetails @Inject constructor(
    private val runDao: RunDao,
) {
    suspend fun planned(p: PlannedWorkout): String? = runCatching {
        if (p.discipline == Discipline.RUNNING) plannedRun(p.id, p.title) else null
    }.getOrNull()

    /** A finished run: kilometres, pace, heart rate. */
    suspend fun done(sessionId: String, discipline: Discipline): String? = runCatching {
        if (discipline != Discipline.RUNNING) return@runCatching null
        val run = runDao.runSessionOnce(sessionId) ?: return@runCatching null
        listOfNotNull(km(run.distanceM), pace(run.avgPaceSecPerKm), run.avgHr?.let { "$it bpm" }).joinToString(" · ").ifBlank { null }
    }.getOrNull()

    private suspend fun plannedRun(plannedId: String, title: String): String? {
        val w = runDao.runPlanWorkoutOnce(plannedId) ?: return null
        val intervals = w.intervalsJson?.let { json ->
            runCatching {
                val first = JSONArray(json).optJSONObject(0) ?: return@runCatching null
                val reps = first.optInt("reps", 0)
                val length = first.optInt("lengthM", 0)
                if (reps > 0 && length > 0) "$reps × ${if (length >= 1000 && length % 1000 == 0) "${length / 1000} km" else "$length m"}" else null
            }.getOrNull()
        }
        val targetPace = w.targetPaceSecPerKm?.takeIf { it > 0 }
        // Without a set distance: what the planned time comes to at the target pace, else the time itself.
        val distance = w.targetDistanceM?.let { km(it.toFloat()) }
            ?: w.targetDurationSec?.let { sec -> targetPace?.let { "ca. " + km(sec * 1000f / it) } ?: "${sec / 60} min" }
        val label = w.runType.label()
        return listOfNotNull(
            // The title often says the kind already ("Lockerlauf").
            label.takeIf { !title.startsWith(it.take(4), ignoreCase = true) },
            intervals,
            distance,
            targetPace?.let(::pace),
        ).joinToString(" · ")
    }

    private fun km(meters: Float): String {
        val km = meters / 1000f
        return (if (km >= 10f || km % 1f < 0.05f) "%.0f".format(Locale.GERMAN, km) else "%.1f".format(Locale.GERMAN, km)) + " km"
    }

    private fun pace(secPerKm: Int): String? = secPerKm.takeIf { it > 0 }?.let { "${it / 60}:${"%02d".format(it % 60)} /km" }

    private fun RunType.label() = when (this) {
        RunType.EASY -> "Lockerer Lauf"
        RunType.LONG -> "Long Run"
        RunType.TEMPO -> "Tempolauf"
        RunType.INTERVAL -> "Intervalle"
        RunType.RECOVERY -> "Regenerationslauf"
    }
}
