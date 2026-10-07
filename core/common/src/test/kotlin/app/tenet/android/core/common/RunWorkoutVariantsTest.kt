package app.tenet.android.core.common

import app.tenet.android.core.common.RunPlanMath.RunGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunWorkoutVariantsTest {

    private fun intervals(week: Int) =
        RunPlanMath.planWeek(RunGoal.HALF, week, 3, totalWeeks = 13, taper = true).first { it.zone == RunZone.INTERVAL }

    @Test
    fun intervalsRotateClassicProgressivePyramid() {
        assertEquals("3 × 1000 m", intervals(0).title)
        assertTrue(intervals(1).title.endsWith("progressiv"))
        assertTrue(intervals(2).title.startsWith("Pyramide"))
        // Unload week stays simple.
        assertTrue(intervals(3).title.matches(Regex("\\d × \\d+ m")))
    }

    @Test
    fun progressiveRepsGetFaster() {
        val blocks = RunWorkoutStructure.parseBlocks(intervals(1).intervalsJson)
        assertTrue(blocks.size >= 3)
        blocks.zipWithNext().forEach { (a, b) -> assertTrue(b.deltaSec <= a.deltaSec) }
        assertTrue(blocks.first().deltaSec > 0 && blocks.last().deltaSec < 0)
    }

    @Test
    fun longRunGetsRacePaceFinish() {
        val long = RunPlanMath.planWeek(RunGoal.HALF, 2, 3, totalWeeks = 13, taper = true).first { it.zone == RunZone.LONG }
        assertTrue(long.title.contains("Renntempo"))
        val w = RunWorkoutStructure.build(RunZone.LONG, long.targetDurationSec, null, 400, long.intervalsJson, RunWorkoutStructure.Paces(380, 400, race = 270))
        assertEquals(RunWorkoutStructure.Kind.WORK, w.segments.last().kind)
        assertEquals(270, w.segments.last().paceSecPerKm)
    }

    @Test
    fun tempoBlocksEveryOtherWeek() {
        val tempo = RunPlanMath.planWeek(RunGoal.HALF, 1, 4, totalWeeks = 13, taper = true).first { it.zone == RunZone.TEMPO }
        assertTrue(tempo.title.contains("min Schwelle"))
        val phases = RunGuidance.parseIntervals(tempo.intervalsJson, 250)
        assertTrue(phases.any { it is RunGuidance.Phase.Tempo })
    }

    @Test
    fun kmSplitsCoverTheSession() {
        val unit = intervals(1)
        val w = RunWorkoutStructure.build(RunZone.INTERVAL, null, unit.targetDistanceM, 240, unit.intervalsJson, RunWorkoutStructure.Paces(330, 360))
        val splits = RunWorkoutStructure.kmSplits(w)
        assertEquals(w.estDistanceM, splits.sumOf { it.distanceM }, 100)
        assertTrue(splits.any { it.kind == RunWorkoutStructure.Kind.WORK && it.paceSecPerKm < 260 })
    }

    @Test
    fun oldPlansStillParse() {
        val old = """[{"reps":4,"lengthM":800,"restSec":90}]"""
        assertEquals(RunWorkoutStructure.IntervalSpec(4, 800, 90), RunWorkoutStructure.parseIntervals(old))
        assertEquals(4 * 2 - 1, RunGuidance.parseIntervals(old).size)
    }
}

private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
    assertTrue("$actual not within $tolerance of $expected", kotlin.math.abs(expected - actual) <= tolerance)
