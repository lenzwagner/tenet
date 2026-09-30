package app.tenet.android.core.common

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Target-pace method for running plans (App_Konzept.md 5.2.3: "Die Methode
 * in den Einstellungen wählbar"). Stored as names so :core:common keeps no
 * dependency on :core:datastore.
 */
enum class PaceMethod(val id: String, val label: String, val description: String) {
    VDOT(
        id = "VDOT",
        label = "VDOT (Daniels)",
        description = "Zonen aus dem VDOT-Modell von Jack Daniels",
    ),
    PERCENT(
        id = "PERCENT",
        label = "Prozent-Zonen",
        description = "Feste Prozente relativ zur Bezugs-Pace (Faustregel)",
    ),
    ;

    companion object {
        fun fromId(id: String?): PaceMethod =
            entries.firstOrNull { it.id == id } ?: VDOT
    }
}

/** Training zones a planned run can target (name-compatible with RunType). */
enum class RunZone { EASY, LONG, TEMPO, INTERVAL, RECOVERY }

/** A finished race used as the pace anchor (e.g. the current 5-km form). */
data class PaceAnchor(val distanceM: Int, val timeSec: Int)

/**
 * Pace derivation for running plans (App_Konzept.md 5.2.3).
 *
 * **VDOT** follows Jack Daniels' model (Daniels' Running Formula, 3rd ed.):
 * 1. `VO2 = -4.60 + 0.182258 v + 0.000104 v²` (v in m/min)
 * 2. `%util = 0.8 + 0.1894 e^(−0.01278 t) + 0.2989 e^(−0.1933 t)` (t in min)
 * 3. `VDOT = VO2(v) / %util`
 * 4. Zone pace: solve `VO2(v) = fraction × VDOT` for v (quadratic).
 *
 * **PERCENT** multiplies the anchor race speed by fixed factors — a
 * transparent heuristic without any tables.
 */
object RunPaceMath {

    // Zone fractions of VDOT (Daniels zone ranges, using the stated point).
    private const val VDOT_EASY = 0.66        // E: 59–74 %, midpoint
    private const val VDOT_LONG_EASY = 0.62    // long runs a notch below E
    private const val VDOT_LONG_MARATHON = 0.84 // M: 75–84 %, top end
    private const val VDOT_TEMPO = 0.88        // T: 83–88 %, top end
    private const val VDOT_INTERVAL = 1.00     // I: 97–100 %, top end
    private const val VDOT_RECOVERY = 0.59     // slow end of E

    // Speed factors of the anchor race speed (percent method, Faustregel).
    private const val PCT_EASY = 0.79
    private const val PCT_LONG = 0.77
    private const val PCT_TEMPO = 0.95
    private const val PCT_INTERVAL = 1.03
    private const val PCT_RECOVERY = 0.74

    /** Oxygen cost of running at [velocityPerMin] m/min. */
    fun vo2(velocityPerMin: Double): Double =
        -4.60 + 0.182258 * velocityPerMin + 0.000104 * velocityPerMin * velocityPerMin

    /** Utilization of VO2max during a race of [raceMinutes] minutes. */
    fun percentUtilization(raceMinutes: Double): Double =
        0.8 + 0.1894 * exp(-0.01278 * raceMinutes) + 0.2989 * exp(-0.1933 * raceMinutes)

    /** VDOT achieved in a race of [distanceM] meters in [timeSec] seconds. */
    fun vdot(distanceM: Int, timeSec: Int): Double {
        val minutes = timeSec / 60.0
        val velocity = distanceM / minutes
        return vo2(velocity) / percentUtilization(minutes)
    }

    /** Velocity [m/min] at which the oxygen cost equals [targetVo2]. */
    private fun velocityForVo2(targetVo2: Double): Double {
        val a = 0.000104
        val b = 0.182258
        val c = -(4.60 + targetVo2)
        return (-b + sqrt(b * b - 4.0 * a * c)) / (2.0 * a)
    }

    /**
     * Target pace [sec/km] for [zone]. [goalIsMarathon] switches the long
     * run to marathon pace in the VDOT method.
     */
    fun targetPaceSecPerKm(
        zone: RunZone,
        anchor: PaceAnchor,
        method: PaceMethod,
        goalIsMarathon: Boolean = false,
    ): Int = when (method) {
        PaceMethod.VDOT -> {
            val vdot = vdot(anchor.distanceM, anchor.timeSec)
            val fraction = when (zone) {
                RunZone.EASY -> VDOT_EASY
                RunZone.LONG -> if (goalIsMarathon) VDOT_LONG_MARATHON else VDOT_LONG_EASY
                RunZone.TEMPO -> VDOT_TEMPO
                RunZone.INTERVAL -> VDOT_INTERVAL
                RunZone.RECOVERY -> VDOT_RECOVERY
            }
            (60_000.0 / velocityForVo2(vdot * fraction)).toInt()
        }

        PaceMethod.PERCENT -> {
            val anchorPaceSecPerKm = anchor.timeSec * 1000.0 / anchor.distanceM
            val factor = when (zone) {
                RunZone.EASY -> PCT_EASY
                RunZone.LONG -> PCT_LONG
                RunZone.TEMPO -> PCT_TEMPO
                RunZone.INTERVAL -> PCT_INTERVAL
                RunZone.RECOVERY -> PCT_RECOVERY
            }
            // Slower pace = lower speed = divide by the speed factor.
            (anchorPaceSecPerKm / factor).toInt()
        }
    }
}
