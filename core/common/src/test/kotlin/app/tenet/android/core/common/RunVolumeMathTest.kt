package app.tenet.android.core.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RunVolumeMathTest {
    private val today = LocalDate.of(2026, 9, 24) // Thursday

    private fun run(date: String, km: Float) = RunVolumeMath.Run(LocalDate.parse(date), km * 1000, (km * 360).toInt())

    @Test
    fun weekly_groupsByIsoWeek_oldestFirst() {
        val weeks = RunVolumeMath.weekly(
            listOf(run("2026-09-21", 5f), run("2026-09-24", 7f), run("2026-09-20", 10f)),
            today,
            count = 3,
        )
        assertEquals(listOf("2026-09-07", "2026-09-14", "2026-09-21"), weeks.map { it.start.toString() })
        assertEquals(12_000f, weeks[2].distanceM)
        assertEquals(2, weeks[2].runs)
        assertEquals(10_000f, weeks[1].distanceM)
    }

    @Test
    fun monthly_groupsByMonth() {
        val months = RunVolumeMath.monthly(listOf(run("2026-08-31", 4f), run("2026-09-01", 6f)), today, count = 2)
        assertEquals(4_000f, months[0].distanceM)
        assertEquals(6_000f, months[1].distanceM)
    }

    @Test
    fun jump_warnsAboveThirtyPercent() {
        val runs = listOf(
            run("2026-09-01", 10f), run("2026-09-08", 10f), run("2026-09-15", 10f),
            run("2026-09-21", 10f), run("2026-09-23", 6f),
        )
        val warning = RunVolumeMath.jumpWarning(RunVolumeMath.weekly(runs, today, 4))
        assertNotNull(warning)
        assertEquals(60, warning!!.increasePercent)
    }

    @Test
    fun jump_quietForModerateIncrease() {
        val runs = listOf(run("2026-09-01", 10f), run("2026-09-08", 10f), run("2026-09-15", 10f), run("2026-09-21", 12f))
        assertNull(RunVolumeMath.jumpWarning(RunVolumeMath.weekly(runs, today, 4)))
    }

    @Test
    fun jump_needsBaseline() {
        val runs = listOf(run("2026-09-15", 3f), run("2026-09-21", 20f))
        assertNull(RunVolumeMath.jumpWarning(RunVolumeMath.weekly(runs, today, 4)))
    }

    @Test
    fun jump_ignoresTinyAbsoluteDelta() {
        val runs = listOf(run("2026-09-01", 2f), run("2026-09-08", 2f), run("2026-09-15", 2f), run("2026-09-21", 4f))
        assertNull(RunVolumeMath.jumpWarning(RunVolumeMath.weekly(runs, today, 4)))
    }
}
