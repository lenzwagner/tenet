package app.tenet.android.core.common

import org.junit.Assert.assertTrue
import org.junit.Test

/** The volume levels of the setup land in the weekly kilometres they promise. */
class RunVolumeLevelTest {
    private fun peakKm(goal: RunPlanMath.RunGoal, runs: Int, volume: Float, weeks: Int = 16, easyPace: Int = 330): Float =
        (0 until weeks).maxOf { RunPlanMath.estimateWeekKm(RunPlanMath.planWeek(goal, it, runs, totalWeeks = weeks, taper = true, volume = volume), easyPace) }

    @Test
    fun highLevelReachesEightyToHundredKm() {
        for (goal in listOf(RunPlanMath.RunGoal.TEN_K, RunPlanMath.RunGoal.HALF, RunPlanMath.RunGoal.MARATHON)) {
            val km = peakKm(goal, runs = 6, volume = 2.1f)
            println("HIGH $goal: $km km")
            assertTrue("$goal: $km km", km in 80f..110f)
        }
    }

    @Test
    fun advancedLevelIsFiftyToEightyKm() {
        for (goal in listOf(RunPlanMath.RunGoal.TEN_K, RunPlanMath.RunGoal.HALF, RunPlanMath.RunGoal.MARATHON)) {
            val km = peakKm(goal, runs = 6, volume = 1.5f)
            println("ADVANCED $goal: $km km")
            assertTrue("$goal: $km km", km in 50f..85f)
        }
    }

    @Test
    fun regularLevelStaysModest() {
        val km = peakKm(RunPlanMath.RunGoal.TEN_K, runs = 3, volume = 1f, easyPace = 390)
        println("REGULAR 10k 3 runs: $km km")
        assertTrue("$km km", km in 12f..35f)
    }
}
