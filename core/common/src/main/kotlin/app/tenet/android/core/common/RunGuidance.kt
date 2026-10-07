package app.tenet.android.core.common

/**
 * Live logic of the active run (App_Konzept.md 5.2.3 "Aktiver Lauf"):
 * auto-pause, guided intervals and the spoken announcements. Pure and
 * clock-free, so the tracking service only feeds numbers in.
 */
object RunGuidance {

    // ---- Auto-pause ---------------------------------------------------------

    /**
     * Pauses after [stopAfterMs] below [stopSpeed] m/s and resumes as soon
     * as the speed is above [resumeSpeed] again (hysteresis against jitter).
     */
    class AutoPause(
        private val stopSpeed: Float = 0.7f,
        private val resumeSpeed: Float = 1.4f,
        private val stopAfterMs: Long = 8_000L,
    ) {
        private var slowSince: Long? = null

        /** Returns the new auto-paused state. */
        fun update(speedMps: Float, now: Long, paused: Boolean): Boolean {
            if (paused) {
                if (speedMps >= resumeSpeed) {
                    slowSince = null
                    return false
                }
                return true
            }
            if (speedMps < stopSpeed) {
                val since = slowSince ?: now.also { slowSince = it }
                return now - since >= stopAfterMs
            }
            slowSince = null
            return false
        }

        fun reset() {
            slowSince = null
        }
    }

    // ---- Guided intervals ----------------------------------------------------

    sealed interface Phase {
        val rep: Int
        val reps: Int

        data class Work(override val rep: Int, override val reps: Int, val distanceM: Int, val paceSecPerKm: Int?) : Phase
        data class Rest(override val rep: Int, override val reps: Int, val durationSec: Int) : Phase

        /** Steady tempo block at the target pace, ends by time. */
        data class Tempo(val durationSec: Int, val paceSecPerKm: Int?) : Phase {
            override val rep: Int get() = 1
            override val reps: Int get() = 1
        }

        /** Easy warm-up before the first rep (Runna-style), ends by time. */
        data class Warmup(val durationSec: Int) : Phase {
            override val rep: Int get() = 0
            override val reps: Int get() = 0
        }
    }

    /**
     * Expands the plan's compact spec (`[{"reps":4,"lengthM":800,"restSec":90}]`)
     * into alternating work/rest phases; no rest after the very last rep.
     */
    /** Guided tempo run: warm-up, then one timed block at [paceSecPerKm]. */
    fun tempoPhases(tempoSec: Int, paceSecPerKm: Int?, warmupSec: Int): List<Phase> =
        if (tempoSec <= 0) emptyList() else listOf(Phase.Warmup(warmupSec), Phase.Tempo(tempoSec, paceSecPerKm))

    fun parseIntervals(json: String?, paceSecPerKm: Int? = null, warmupSec: Int = 0): List<Phase> {
        val blocks = RunWorkoutStructure.parseBlocks(json)
        if (blocks.isEmpty()) return emptyList()
        val total = blocks.sumOf { it.reps }
        val phases = mutableListOf<Phase>()
        var rep = 0
        for (b in blocks) {
            repeat(b.reps) {
                rep++
                val pace = paceSecPerKm?.let { it + b.deltaSec }
                // Distance reps end by metres, time blocks (Schwellen-Blöcke) by time.
                phases += if (b.lengthM != null) Phase.Work(rep, total, b.lengthM, pace) else Phase.Tempo(b.workSec ?: 0, pace)
                if (b.restSec > 0) phases += Phase.Rest(rep, total, b.restSec)
            }
        }
        if (phases.lastOrNull() is Phase.Rest) phases.removeAt(phases.lastIndex)
        if (warmupSec > 0 && phases.isNotEmpty()) phases.add(0, Phase.Warmup(warmupSec))
        return phases
    }

    data class IntervalState(
        /** Index into the phase list; == size when all phases are done. */
        val index: Int = 0,
        val phaseStartM: Double = 0.0,
        val phaseStartMs: Long = 0L,
        /** "Noch 100 Meter" already said for this phase. */
        val warned: Boolean = false,
    )

    sealed interface IntervalEvent {
        data class PhaseStarted(val phase: Phase) : IntervalEvent
        data class AlmostDone(val phase: Phase.Work) : IntervalEvent
        data object Finished : IntervalEvent
    }

    /**
     * Advances the interval state with the current moving distance/time.
     * Work phases end by distance, rest phases by time.
     */
    fun advance(
        phases: List<Phase>,
        state: IntervalState,
        distanceM: Double,
        movingMs: Long,
    ): Pair<IntervalState, IntervalEvent?> {
        val phase = phases.getOrNull(state.index) ?: return state to null
        val done = when (phase) {
            is Phase.Work -> distanceM - state.phaseStartM >= phase.distanceM
            is Phase.Rest -> movingMs - state.phaseStartMs >= phase.durationSec * 1000L
            is Phase.Warmup -> movingMs - state.phaseStartMs >= phase.durationSec * 1000L
            is Phase.Tempo -> movingMs - state.phaseStartMs >= phase.durationSec * 1000L
        }
        if (done) {
            val next = IntervalState(index = state.index + 1, phaseStartM = distanceM, phaseStartMs = movingMs)
            val nextPhase = phases.getOrNull(next.index)
            return next to (nextPhase?.let { IntervalEvent.PhaseStarted(it) } ?: IntervalEvent.Finished)
        }
        if (phase is Phase.Work && !state.warned && phase.distanceM >= 300 &&
            phase.distanceM - (distanceM - state.phaseStartM) <= 100
        ) {
            return state.copy(warned = true) to IntervalEvent.AlmostDone(phase)
        }
        return state to null
    }

    /** Remaining meters (work) or seconds (rest) of the current phase. */
    fun remaining(phases: List<Phase>, state: IntervalState, distanceM: Double, movingMs: Long): Int? =
        when (val p = phases.getOrNull(state.index)) {
            is Phase.Work -> (p.distanceM - (distanceM - state.phaseStartM)).toInt().coerceAtLeast(0)
            is Phase.Rest -> (p.durationSec - ((movingMs - state.phaseStartMs) / 1000)).toInt().coerceAtLeast(0)
            is Phase.Warmup -> (p.durationSec - ((movingMs - state.phaseStartMs) / 1000)).toInt().coerceAtLeast(0)
            is Phase.Tempo -> (p.durationSec - ((movingMs - state.phaseStartMs) / 1000)).toInt().coerceAtLeast(0)
            null -> null
        }

    // ---- Announcements (German, for TextToSpeech) -----------------------------

    fun spokenDuration(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return buildList {
            if (h > 0) add(if (h == 1) "eine Stunde" else "$h Stunden")
            if (m > 0) add(if (m == 1) "eine Minute" else "$m Minuten")
            if (s > 0 || (h == 0 && m == 0)) add(if (s == 1) "eine Sekunde" else "$s Sekunden")
        }.joinToString(" ")
    }

    /** Pace as "5 Minuten 30" per kilometer. */
    fun spokenPace(secPerKm: Int): String {
        val m = secPerKm / 60
        val s = secPerKm % 60
        return if (s == 0) "$m Minuten" else "$m Minuten $s"
    }

    fun kilometerAnnouncement(km: Int, movingSec: Int, lastKmSec: Int): String =
        "Kilometer $km. Zeit ${spokenDuration(movingSec)}. " +
            "Letzter Kilometer ${spokenPace(lastKmSec)}."

    fun phaseAnnouncement(phase: Phase): String = when (phase) {
        is Phase.Work -> "Intervall ${phase.rep} von ${phase.reps}: ${phase.distanceM} Meter" +
            (phase.paceSecPerKm?.let { " in ${spokenPace(it)} pro Kilometer" } ?: "") + ". Los!"
        is Phase.Rest -> "Pause, ${spokenDuration(phase.durationSec)} locker traben."
        is Phase.Warmup -> "Einlaufen: ${spokenDuration(phase.durationSec)} ganz locker. Danach geht es automatisch weiter."
        is Phase.Tempo -> "Tempoblock: ${spokenDuration(phase.durationSec)}" +
            (phase.paceSecPerKm?.let { " in ${spokenPace(it)} pro Kilometer" } ?: " angenehm hart") + ". Los!"
    }

    fun almostDoneAnnouncement(phase: Phase.Work): String = "Noch 100 Meter."

    /** Tolerance of steady runs: faster than target − 15 s is too fast, slower than + 25 s too slow. */
    const val STEADY_FAST_SEC = 15
    const val STEADY_SLOW_SEC = 25

    /** Start of an easy/long run with a pace target (like Runna). */
    fun steadyStartAnnouncement(paceSecPerKm: Int, distanceM: Int?): String =
        "Lauf gestartet. Ziel-Pace ${spokenPace(paceSecPerKm)} pro Kilometer" +
            (distanceM?.takeIf { it > 0 }?.let { " über ${spokenKm(it)}" } ?: "") + ". Locker bleiben."

    /** Pace feedback after each kilometer of a steady run. */
    fun steadyPaceHint(lastKmSec: Int, targetSecPerKm: Int): String = when {
        lastKmSec < targetSecPerKm - STEADY_FAST_SEC -> "Etwas zu schnell. Nimm Tempo raus, Ziel ${spokenPace(targetSecPerKm)}."
        lastKmSec > targetSecPerKm + STEADY_SLOW_SEC -> "Etwas langsamer als geplant. Ziel ${spokenPace(targetSecPerKm)}."
        else -> "Pace passt."
    }

    fun steadyHalfway(distanceM: Int): String = "Halbzeit. Noch ${spokenKm(distanceM / 2)}."

    fun steadyGoalReached(distanceM: Int): String = "${spokenKm(distanceM)} geschafft. Stark! Du kannst jetzt auslaufen."

    private fun spokenKm(m: Int): String {
        val km = m / 1000
        val rest = (m % 1000) / 100
        return if (rest == 0) "$km Kilometer" else "$km Komma $rest Kilometer"
    }

    const val FINISHED_ANNOUNCEMENT = "Alle Intervalle geschafft. Jetzt locker auslaufen."
}
