package app.tenet.android.core.common

import app.tenet.android.core.common.RunPlanMath.RunGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPaceProgressionTest {

    private val current5k = 25 * 60 // 25:00

    @Test
    fun halfMarathonGoalTimeBecomesFaster5kForm() {
        // 1:50 half marathon ≈ 24:09 on 5 km (Riegel) – faster than today's 25:00.
        val goal = RunPlanMath.goal5kSec(current5k, RunGoal.HALF, 110 * 60, totalWeeks = 13, taper = true)
        assertTrue(goal in (23 * 60)..(current5k - 30))
    }

    @Test
    fun paceGetsFasterWeekByWeekAndTaperUsesGoal() {
        val goal = RunPlanMath.goal5kSec(current5k, RunGoal.HALF, 110 * 60, totalWeeks = 13, taper = true)
        val anchors = (0 until 13).map { RunPlanMath.anchor5kForWeek(current5k, goal, it, 13, RunGoal.HALF, taper = true) }
        assertEquals(current5k, anchors.first())
        // Never slower than the week before.
        anchors.zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", b <= a) }
        // Last build week and the two taper weeks run at the goal form.
        assertEquals(goal, anchors[10])
        assertEquals(goal, anchors[11])
        assertEquals(goal, anchors[12])
        // Unload week (4th) keeps the pace of week 3.
        assertEquals(anchors[2], anchors[3])
    }

    @Test
    fun withoutGoalTimeARealisticGain() {
        val goal = RunPlanMath.goal5kSec(current5k, RunGoal.HALF, null, totalWeeks = 13, taper = true)
        // 11 build weeks → ~4 % faster.
        assertTrue(goal in (current5k * 0.95).toInt()..(current5k * 0.97).toInt())
    }

    @Test
    fun unrealisticGoalIsCapped() {
        val goal = RunPlanMath.goal5kSec(current5k, RunGoal.HALF, 80 * 60, totalWeeks = 13, taper = true)
        assertEquals((current5k * 0.9).toInt(), goal)
    }

    @Test
    fun slowerGoalTimeNeverSlowsThePaces() {
        val goal = RunPlanMath.goal5kSec(current5k, RunGoal.HALF, 150 * 60, totalWeeks = 13, taper = true)
        assertEquals(current5k, goal)
    }
}

class GoalCheckTest {
    private val current5k = 25 * 60

    @Test
    fun realisticAmbitiousUnrealistic() {
        // 25:00 on 5 km ≈ 1:55:15 half marathon today; 13 weeks with 2 taper → ~4 % faster ≈ 1:50:40.
        val realistic = RunPlanMath.checkGoal(current5k, RunGoal.HALF, 112 * 60, 13, true)!!
        assertEquals(RunPlanMath.GoalRealism.REALISTIC, realistic.realism)
        val ambitious = RunPlanMath.checkGoal(current5k, RunGoal.HALF, 106 * 60, 13, true)!!
        assertEquals(RunPlanMath.GoalRealism.AMBITIOUS, ambitious.realism)
        val unrealistic = RunPlanMath.checkGoal(current5k, RunGoal.HALF, 95 * 60, 13, true)!!
        assertEquals(RunPlanMath.GoalRealism.UNREALISTIC, unrealistic.realism)
        assertTrue(unrealistic.expectedRaceDaySec < unrealistic.predictedNowSec)
    }

    @Test
    fun noRaceNoCheck() {
        assertEquals(null, RunPlanMath.checkGoal(current5k, RunGoal.GENERAL, 3600, 13, false))
    }
}
