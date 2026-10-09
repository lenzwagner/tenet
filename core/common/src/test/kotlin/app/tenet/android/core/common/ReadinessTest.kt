package app.tenet.android.core.common

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessTest {
    private fun night(min: Int, deep: Int? = null, rem: Int? = null) =
        SleepNight(LocalDate.of(2026, 10, 7), Instant.EPOCH, Instant.EPOCH.plusSeconds(min * 60L), min, deep, rem)

    @Test
    fun noRecoveryDataMeansNoScore() {
        assertNull(Readiness.compute(Readiness.Input()))
    }

    @Test
    fun wellRestedIsHigh() {
        val r = Readiness.compute(
            Readiness.Input(
                sleep = night(500, deep = 90, rem = 110),
                restingHr = 50, restingHrBaseline = 54.0,
                hrvMs = 70.0, hrvBaselineMs = 60.0,
            ),
        )!!
        assertTrue(r.score >= 80)
        assertEquals(Readiness.Level.HIGH, r.level)
    }

    @Test
    fun shortNightLowHrvAndHighPulseIsLow() {
        val r = Readiness.compute(
            Readiness.Input(
                sleep = night(300),
                restingHr = 62, restingHrBaseline = 54.0,
                hrvMs = 40.0, hrvBaselineMs = 60.0,
            ),
        )!!
        assertTrue("score ${r.score}", r.score < 40)
        assertEquals(Readiness.Level.LOW, r.level)
        assertTrue(r.advice.contains("Erholung"))
    }

    @Test
    fun sleepOnlyStillScores() {
        val r = Readiness.compute(Readiness.Input(sleep = night(480)))!!
        assertEquals(1, r.contributors.size)
        assertTrue(r.score >= 95)
    }

    @Test
    fun oneShortNightAfterGoodWeekHurtsLess() {
        val alone = Readiness.compute(Readiness.Input(sleep = night(300)))!!
        val afterGoodWeek = Readiness.compute(Readiness.Input(sleep = night(300), previousNights = List(6) { night(480) }))!!
        assertTrue(afterGoodWeek.score > alone.score + 20)
    }
}
