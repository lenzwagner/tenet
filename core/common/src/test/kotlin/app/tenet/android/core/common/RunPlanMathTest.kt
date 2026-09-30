package app.tenet.android.core.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPlanMathTest {

    // ---- weeklyTemplate --------------------------------------------------

    @Test
    fun `template has one entry per run and includes the long run`() {
        for (runs in 2..6) {
            val template = RunPlanMath.weeklyTemplate(runs)
            assertEquals(runs, template.size)
            assertTrue(template.any { it.second == RunZone.LONG })
            // No two runs on the same day.
            assertEquals(runs, template.map { it.first }.toSet().size)
            template.forEach { (day, _) -> assertTrue(day in 0..6) }
        }
    }

    @Test
    fun `template clamps runs per week between two and six`() {
        assertEquals(2, RunPlanMath.weeklyTemplate(1).size)
        assertEquals(2, RunPlanMath.weeklyTemplate(0).size)
        assertEquals(6, RunPlanMath.weeklyTemplate(9).size)
    }

    // ---- weekCount -------------------------------------------------------

    @Test
    fun `week count spans start to goal date inclusive`() {
        val start = LocalDate.of(2026, 9, 21) // Monday
        // Monday of week 8: inclusive count = 8 weeks.
        assertEquals(8, RunPlanMath.weekCount(start, start.plusWeeks(7)))
        // The Sunday before belongs to week 7.
        assertEquals(7, RunPlanMath.weekCount(start, start.plusWeeks(7).minusDays(1)))
    }

    @Test
    fun `week count clamps tiny and huge ranges`() {
        val start = LocalDate.of(2026, 9, 21)
        assertTrue(RunPlanMath.weekCount(start, start) >= 4)
        assertEquals(4, RunPlanMath.weekCount(start, start.plusDays(2)))
        assertEquals(24, RunPlanMath.weekCount(start, start.plusDays(365 * 2)))
    }

    // ---- loadFactor ------------------------------------------------------

    @Test
    fun `every fourth week is an unload week`() {
        // Unload weeks are 1-based weeks 4, 8, 12 ... = index 3, 7, 11.
        val level = RunPlanMath.loadFactor(6)
        assertTrue(RunPlanMath.loadFactor(7) < level)
        assertTrue(RunPlanMath.loadFactor(3) < RunPlanMath.loadFactor(2))
        assertTrue(RunPlanMath.loadFactor(11) < RunPlanMath.loadFactor(10))
    }

    @Test
    fun `load grows across build cycles`() {
        assertTrue(RunPlanMath.loadFactor(4) > RunPlanMath.loadFactor(0))
        assertTrue(RunPlanMath.loadFactor(8) > RunPlanMath.loadFactor(4))
    }

    // ---- planWeek --------------------------------------------------------

    @Test
    fun `plan week matches the runs-per-week template`() {
        for (runs in 2..6) {
            val week = RunPlanMath.planWeek(RunPlanMath.RunGoal.GENERAL, 0, runs)
            assertEquals(runs, week.size)
            week.forEach { unit ->
                val hasTarget = unit.targetDurationSec != null || unit.targetDistanceM != null
                assertTrue(hasTarget)
                assertTrue(unit.title.isNotBlank())
            }
        }
    }

    @Test
    fun `interval units carry distance and interval json`() {
        val week = RunPlanMath.planWeek(RunPlanMath.RunGoal.FIVE_K, 0, 3)
        val interval = week.first { it.zone == RunZone.INTERVAL }
        assertEquals(3 * 800, interval.targetDistanceM)
        assertEquals(null, interval.targetDurationSec)
        val json = interval.intervalsJson.orEmpty()
        assertTrue(json.contains("\"reps\":3"))
        assertTrue(json.contains("\"lengthM\":800"))
        assertTrue(interval.title.contains("×"))
    }

    @Test
    fun `unload week long run is shorter than the week before`() {
        val before = RunPlanMath.planWeek(RunPlanMath.RunGoal.MARATHON, 2, 3)
            .first { it.zone == RunZone.LONG }.targetDurationSec!!
        val unload = RunPlanMath.planWeek(RunPlanMath.RunGoal.MARATHON, 3, 3)
            .first { it.zone == RunZone.LONG }.targetDurationSec!!
        assertTrue(unload < before)
    }

    @Test
    fun `long run respects the goal specific cap`() {
        val marathonWeek = RunPlanMath.planWeek(RunPlanMath.RunGoal.MARATHON, 20, 3)
            .first { it.zone == RunZone.LONG }.targetDurationSec!!
        assertTrue(marathonWeek <= 180 * 60)

        val fiveKWeek = RunPlanMath.planWeek(RunPlanMath.RunGoal.FIVE_K, 20, 3)
            .first { it.zone == RunZone.LONG }.targetDurationSec!!
        assertTrue(fiveKWeek <= 90 * 60)
    }

    @Test
    fun `interval json roundtrips through a manual parse`() {
        val json = RunPlanMath.intervalSpecJson(5, 800, 90)
        assertEquals("""[{"reps":5,"lengthM":800,"restSec":90}]""", json)
    }
}
