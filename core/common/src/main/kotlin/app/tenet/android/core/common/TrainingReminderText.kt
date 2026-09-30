package app.tenet.android.core.common

import java.util.Locale

/**
 * Morning training reminder like Runna: "Heute: Intervalle 3 × 1000 m".
 * Pure text building; the worker collects today's planned units.
 */
object TrainingReminderText {

    sealed interface Unit {
        data class Run(
            val zone: RunZone,
            val distanceM: Int?,
            val durationSec: Int?,
            val intervalReps: Int? = null,
            val intervalLengthM: Int? = null,
            val paceSecPerKm: Int? = null,
        ) : Unit
        data class Gym(val title: String, val exercises: Int) : Unit
        data class Cali(val title: String, val exercises: Int) : Unit
    }

    fun line(unit: Unit): String = when (unit) {
        is Unit.Run -> runLine(unit)
        is Unit.Gym -> "${unit.title}${if (unit.exercises > 0) " · ${unit.exercises} Übungen" else ""}"
        is Unit.Cali -> "Calisthenics ${unit.title}".trim() + if (unit.exercises > 0) " · ${unit.exercises} Übungen" else ""
    }

    private fun runLine(r: Unit.Run): String {
        if (r.zone == RunZone.INTERVAL && r.intervalReps != null && r.intervalLengthM != null) {
            val len = if (r.intervalLengthM >= 1000 && r.intervalLengthM % 1000 == 0) "${r.intervalLengthM / 1000} km" else "${r.intervalLengthM} m"
            return "Intervalle ${r.intervalReps} × $len"
        }
        val name = when (r.zone) {
            RunZone.EASY -> "Lockerer Lauf"
            RunZone.LONG -> "Langer Lauf"
            RunZone.TEMPO -> "Tempolauf"
            RunZone.INTERVAL -> "Intervalle"
            RunZone.RECOVERY -> "Regenerationslauf"
        }
        val amount = when {
            r.distanceM != null && r.distanceM > 0 -> String.format(Locale.GERMAN, "%.1f km", r.distanceM / 1000f).replace(",0 ", " ")
            r.durationSec != null && r.durationSec > 0 -> "${r.durationSec / 60} min"
            else -> null
        }
        val pace = r.paceSecPerKm?.takeIf { it > 0 }?.let { "@ %d:%02d /km".format(it / 60, it % 60) }
        return listOfNotNull(name, amount, pace).joinToString(" ")
    }

    /** Title + body of the notification, null when nothing is planned. */
    fun notification(units: List<Unit>): Pair<String, String>? {
        if (units.isEmpty()) return null
        val title = "Heute: " + line(units.first())
        val body = if (units.size > 1) {
            "Außerdem: " + units.drop(1).joinToString(" · ") { line(it) }
        } else {
            when (val u = units.first()) {
                is Unit.Run -> when (u.zone) {
                    RunZone.EASY, RunZone.RECOVERY, RunZone.LONG -> "Ruhig bleiben – du solltest dich noch unterhalten können."
                    else -> "Gut aufwärmen, dann fokussiert durchziehen."
                }
                else -> "Dein Training wartet. Tippen zum Starten."
            }
        }
        return title to body
    }
}
