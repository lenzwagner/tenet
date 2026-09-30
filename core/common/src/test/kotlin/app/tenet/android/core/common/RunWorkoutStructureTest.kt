package app.tenet.android.core.common

import app.tenet.android.core.common.RunWorkoutStructure.Kind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunWorkoutStructureTest {

    private val paces = RunWorkoutStructure.Paces(easy = 360, recovery = 400)

    @Test
    fun intervals_warmupRepsRecoveriesCooldown() {
        val w = RunWorkoutStructure.build(RunZone.INTERVAL, null, 4000, 270, """[{"reps":5,"lengthM":800,"restSec":90}]""", paces)
        assertEquals(Kind.WARMUP, w.segments.first().kind)
        assertEquals(Kind.COOLDOWN, w.segments.last().kind)
        assertEquals(5, w.segments.count { it.kind == Kind.WORK })
        assertEquals(4, w.segments.count { it.kind == Kind.RECOVERY })
        // 10 min + 5×800 m + 4×90 s + 10 min at the given paces.
        val expected = 600 + 5 * (800 * 270 / 1000) + 4 * 90 + 600
        assertEquals(expected.toFloat(), w.estDurationSec.toFloat(), 5f)
    }

    @Test
    fun easyRun_singleSteadyBlock() {
        val w = RunWorkoutStructure.build(RunZone.EASY, 40 * 60, null, 360, null, paces)
        assertEquals(1, w.segments.size)
        assertEquals(6667f, w.estDistanceM.toFloat(), 5f) // 40 min at 6:00/km
    }

    @Test
    fun matcher_linkedFirstThenSameDay() {
        val d = LocalDate.of(2026, 9, 29)
        val planned = listOf(RunPlanMatcher.Planned("p1", d), RunPlanMatcher.Planned("p2", d.plusDays(2)))
        val runs = listOf(
            RunPlanMatcher.Run("r1", d.plusDays(2), null),
            RunPlanMatcher.Run("r2", d, "p1"),
        )
        val m = RunPlanMatcher.match(planned, runs)
        assertEquals("r2", m["p1"])
        assertEquals("r1", m["p2"])
        assertTrue(RunPlanMatcher.match(planned, emptyList()).isEmpty())
    }

    @Test
    fun guidance_warmupBeforeFirstRep() {
        val phases = RunGuidance.parseIntervals("""[{"reps":2,"lengthM":400,"restSec":60}]""", 270, warmupSec = 600)
        assertTrue(phases.first() is RunGuidance.Phase.Warmup)
        // Still warming up after 5 min, first rep starts after 10 min.
        val (s1, e1) = RunGuidance.advance(phases, RunGuidance.IntervalState(), 900.0, 300_000)
        assertEquals(0, s1.index)
        assertEquals(null, e1)
        val (s2, e2) = RunGuidance.advance(phases, s1, 1800.0, 600_000)
        assertEquals(1, s2.index)
        assertTrue((e2 as RunGuidance.IntervalEvent.PhaseStarted).phase is RunGuidance.Phase.Work)
    }

    @Test
    fun guidance_tempoBlockAfterWarmup() {
        val phases = RunGuidance.tempoPhases(20 * 60, 300, 600)
        assertEquals(2, phases.size)
        val (s1, e1) = RunGuidance.advance(phases, RunGuidance.IntervalState(), 1500.0, 600_000)
        assertTrue((e1 as RunGuidance.IntervalEvent.PhaseStarted).phase is RunGuidance.Phase.Tempo)
        val (_, e2) = RunGuidance.advance(phases, s1, 5500.0, 1_800_000)
        assertEquals(RunGuidance.IntervalEvent.Finished, e2)
        assertTrue(RunGuidance.phaseAnnouncement(phases[1]).startsWith("Tempoblock: 20 Minuten"))
    }
}
