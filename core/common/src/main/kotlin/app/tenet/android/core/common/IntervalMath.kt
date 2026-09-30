package app.tenet.android.core.common

/**
 * Pure timer math for the calisthenics interval modes
 * (App_Konzept.md 5.2.2: "Zirkel- und EMOM-Modus mit Runden- und
 * Intervalltimer"). Given the elapsed seconds it derives the exact phase,
 * so the UI only ticks once per second and stays unit testable.
 */
object IntervalMath {

    enum class Phase { WORK, REST }

    /** State of a circuit at [elapsedSec] seconds after the start tap. */
    data class CircuitState(
        /** 1-based round. */
        val round: Int,
        /** 0-based station index within the round. */
        val station: Int,
        val phase: Phase,
        val remainingSec: Int,
        /** Total configured rounds. */
        val rounds: Int,
        val totalStations: Int,
        val done: Boolean,
    )

    /** State of an EMOM at [elapsedSec] seconds after the start tap. */
    data class EmomState(
        /** 1-based minute. */
        val round: Int,
        val remainingSec: Int,
        /** Total configured minutes. */
        val minutes: Int,
        val done: Boolean,
    )

    /**
     * Circuit layout: every station is [workSec] of work followed by
     * [restSec] of transition (also after the last station, so rounds are
     * uniform). All inputs are clamped to sane minimums.
     */
    fun circuit(
        elapsedSec: Long,
        stations: Int,
        workSec: Int,
        restSec: Int,
        rounds: Int,
    ): CircuitState {
        val stationCount = stations.coerceAtLeast(1)
        val work = workSec.coerceAtLeast(1)
        val rest = restSec.coerceAtLeast(0)
        val totalRounds = rounds.coerceAtLeast(1)
        val cycleLen = stationCount * (work + rest)
        val totalLen = cycleLen.toLong() * totalRounds

        if (elapsedSec >= totalLen) {
            return CircuitState(
                round = totalRounds,
                station = stationCount - 1,
                phase = Phase.WORK,
                remainingSec = 0,
                rounds = totalRounds,
                totalStations = stationCount,
                done = true,
            )
        }

        val elapsed = elapsedSec.coerceAtLeast(0)
        val round = (elapsed / cycleLen).toInt() + 1
        val intoCycle = (elapsed % cycleLen).toInt()
        val station = intoCycle / (work + rest)
        val intoStation = intoCycle % (work + rest)
        val phase = if (intoStation < work) Phase.WORK else Phase.REST
        val phaseElapsed = if (phase == Phase.WORK) intoStation else intoStation - work
        val phaseLen = if (phase == Phase.WORK) work else rest

        return CircuitState(
            round = round,
            station = station,
            phase = phase,
            remainingSec = phaseLen - phaseElapsed,
            rounds = totalRounds,
            totalStations = stationCount,
            done = false,
        )
    }

    /**
     * EMOM layout: every [intervalSec] seconds a new minute starts with a
     * signal; the remaining time of the current minute counts down.
     */
    fun emom(
        elapsedSec: Long,
        minutes: Int,
        intervalSec: Int,
    ): EmomState {
        val interval = intervalSec.coerceAtLeast(1)
        val totalMinutes = minutes.coerceAtLeast(1)
        val elapsed = elapsedSec.coerceAtLeast(0)

        if (elapsed >= interval.toLong() * totalMinutes) {
            return EmomState(
                round = totalMinutes,
                remainingSec = 0,
                minutes = totalMinutes,
                done = true,
            )
        }
        return EmomState(
            round = (elapsed / interval).toInt() + 1,
            remainingSec = interval - (elapsed % interval).toInt(),
            minutes = totalMinutes,
            done = false,
        )
    }

    /**
     * How many station work phases are complete after [elapsedSec] — one
     * set is logged per completed work phase (capped at the configured
     * rounds), so re-entering a session never double-logs.
     */
    fun completedWorks(
        elapsedSec: Long,
        stations: Int,
        workSec: Int,
        restSec: Int,
        rounds: Int,
    ): Int {
        val stationCount = stations.coerceAtLeast(1)
        val work = workSec.coerceAtLeast(1)
        val rest = restSec.coerceAtLeast(0)
        val totalRounds = rounds.coerceAtLeast(1)
        val cycleLen = stationCount * (work + rest)
        return (0 until stationCount * totalRounds).count { k ->
            val intoCycle = (k % stationCount) * (work + rest) + work
            val workEnd = (k / stationCount) * cycleLen + intoCycle
            workEnd <= elapsedSec
        }
    }

    /**
     * How many EMOM minutes are complete after [elapsedSec] — one set per
     * finished minute for the station of that minute (rotation by index).
     */
    /** State of an AMRAP ("as many rounds as possible") with a time cap. */
    data class AmrapState(val remainingSec: Int, val minutes: Int, val done: Boolean)

    fun amrap(elapsedSec: Long, minutes: Int): AmrapState {
        val cap = minutes.coerceAtLeast(1) * 60L
        val elapsed = elapsedSec.coerceAtLeast(0)
        return AmrapState(
            remainingSec = (cap - elapsed).coerceAtLeast(0).toInt(),
            minutes = minutes.coerceAtLeast(1),
            done = elapsed >= cap,
        )
    }

    fun completedMinutes(elapsedSec: Long, minutes: Int, intervalSec: Int): Int {
        val interval = intervalSec.coerceAtLeast(1)
        val totalMinutes = minutes.coerceAtLeast(1)
        return ((elapsedSec.coerceAtLeast(0)) / interval).toInt().coerceAtMost(totalMinutes)
    }
}
