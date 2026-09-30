package app.tenet.android.core.common

import app.tenet.android.core.common.IntervalMath.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalMathTest {

    // 2 stations x (30 s Arbeit + 15 s Pause) = 90 s pro Runde, 2 Runden.
    private fun circuit(elapsed: Long) =
        IntervalMath.circuit(elapsed, stations = 2, workSec = 30, restSec = 15, rounds = 2)

    // ---- circuit --------------------------------------------------------

    @Test
    fun `circuit starts at station one with full work time`() {
        val state = circuit(0)
        assertEquals(1, state.round)
        assertEquals(0, state.station)
        assertEquals(Phase.WORK, state.phase)
        assertEquals(30, state.remainingSec)
        assertFalse(state.done)
    }

    @Test
    fun `circuit switches from work to rest after the work seconds`() {
        assertEquals(Phase.WORK, circuit(29).phase)
        val rest = circuit(30)
        assertEquals(Phase.REST, rest.phase)
        assertEquals(15, rest.remainingSec)
        // The rest still belongs to station 0.
        assertEquals(0, rest.station)
        assertEquals(1, circuit(44).remainingSec)
    }

    @Test
    fun `circuit advances to the second station after the rest`() {
        assertEquals(Phase.REST, circuit(44).phase)

        val next = circuit(45)
        assertEquals(1, next.station)
        assertEquals(Phase.WORK, next.phase)
        assertEquals(30, next.remainingSec)
    }

    @Test
    fun `circuit wraps to round two after one full cycle`() {
        val state = circuit(90)
        assertEquals(2, state.round)
        assertEquals(0, state.station)
        assertEquals(Phase.WORK, state.phase)
        assertEquals(30, state.remainingSec)
    }

    @Test
    fun `circuit is done after all rounds`() {
        val lastSecond = circuit(179)
        assertFalse(lastSecond.done)
        assertEquals(Phase.REST, lastSecond.phase)

        val done = circuit(180)
        assertTrue(done.done)
        assertEquals(0, done.remainingSec)
        assertEquals(2, done.round)

        // Way past the end stays done.
        assertTrue(circuit(10_000).done)
    }

    @Test
    fun `circuit without rest never enters the rest phase`() {
        val state = { elapsed: Long ->
            IntervalMath.circuit(elapsed, stations = 2, workSec = 10, restSec = 0, rounds = 1)
        }
        assertEquals(Phase.WORK, state(10).phase)
        assertEquals(1, state(10).station)
        assertTrue(state(10).remainingSec > 0)
        assertTrue(state(20).done)
    }

    @Test
    fun `circuit clamps invalid configuration and negative elapsed time`() {
        val state = IntervalMath.circuit(
            elapsedSec = -5,
            stations = 0,
            workSec = 0,
            restSec = -1,
            rounds = 0,
        )
        assertEquals(1, state.totalStations)
        assertEquals(1, state.rounds)
        assertEquals(Phase.WORK, state.phase)
        assertFalse(state.done)
    }

    // ---- emom -----------------------------------------------------------

    @Test
    fun `emom counts down within the minute`() {
        val emom = { elapsed: Long -> IntervalMath.emom(elapsed, minutes = 3, intervalSec = 60) }
        assertEquals(1, emom(0).round)
        assertEquals(60, emom(0).remainingSec)
        assertEquals(1, emom(59).round)
        assertEquals(1, emom(59).remainingSec)
        assertEquals(2, emom(60).round)
        assertEquals(60, emom(60).remainingSec)
        assertEquals(3, emom(179).round)
        assertEquals(1, emom(179).remainingSec)
    }

    @Test
    fun `emom is done after all minutes`() {
        val emom = { elapsed: Long -> IntervalMath.emom(elapsed, minutes = 3, intervalSec = 60) }
        assertFalse(emom(179).done)
        assertTrue(emom(180).done)
        assertEquals(0, emom(180).remainingSec)
        assertTrue(emom(999).done)
    }

    @Test
    fun `emom supports other interval lengths`() {
        val state = IntervalMath.emom(elapsedSec = 44, minutes = 5, intervalSec = 45)
        assertEquals(1, state.round)
        assertEquals(1, state.remainingSec)
        assertEquals(2, IntervalMath.emom(45, 5, 45).round)
    }

    @Test
    fun `emom clamps invalid configuration`() {
        val state = IntervalMath.emom(elapsedSec = -1, minutes = 0, intervalSec = 0)
        assertEquals(1, state.minutes)
        assertEquals(1, state.round)
        assertFalse(state.done)
    }

    // ---- completed counters (idempotent set logging) ---------------------

    @Test
    fun `completed works counts each finished work phase once`() {
        // work ends at 30, 75, 120, 165 seconds (see circuit layout above).
        assertEquals(0, IntervalMath.completedWorks(29, 2, 30, 15, 2))
        assertEquals(1, IntervalMath.completedWorks(30, 2, 30, 15, 2))
        assertEquals(2, IntervalMath.completedWorks(100, 2, 30, 15, 2))
        assertEquals(3, IntervalMath.completedWorks(164, 2, 30, 15, 2))
        assertEquals(4, IntervalMath.completedWorks(165, 2, 30, 15, 2))
        assertEquals(4, IntervalMath.completedWorks(180, 2, 30, 15, 2))
        // Capped at rounds x stations even long after the end.
        assertEquals(4, IntervalMath.completedWorks(10_000, 2, 30, 15, 2))
    }

    @Test
    fun `completed minutes counts each finished emom minute once`() {
        assertEquals(0, IntervalMath.completedMinutes(59, 3, 60))
        assertEquals(1, IntervalMath.completedMinutes(60, 3, 60))
        // Minute 2 läuft, aber ist noch nicht abgeschlossen.
        assertEquals(1, IntervalMath.completedMinutes(119, 3, 60))
        assertEquals(2, IntervalMath.completedMinutes(120, 3, 60))
        assertEquals(3, IntervalMath.completedMinutes(180, 3, 60))
        assertEquals(3, IntervalMath.completedMinutes(10_000, 3, 60))
    }
}
