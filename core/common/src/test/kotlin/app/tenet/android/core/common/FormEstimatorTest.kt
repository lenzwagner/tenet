package app.tenet.android.core.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormEstimatorTest {
    private val today = LocalDate.of(2026, 10, 2)

    @Test fun danielsTable() {
        // Daniels: VDOT 50 ≈ 19:57 for 5 km, 41:21 for 10 km, 3:10:49 marathon.
        assertEquals(50.0, FormEstimator.vdot(5_000.0, 19 * 60 + 57.0), 0.4)
        val t10 = FormEstimator.timeFor(50.0, 10_000)
        assertTrue("10k $t10", t10 in (41 * 60)..(41 * 60 + 40))
        val tm = FormEstimator.timeFor(50.0, 42_195)
        assertTrue("marathon $tm", tm in (3 * 3600 + 9 * 60)..(3 * 3600 + 13 * 60))
    }

    @Test fun raceEffortSetsForm() {
        val form = FormEstimator.estimate(listOf(FormEstimator.Run(today.minusDays(3), 5_000f, 20 * 60, null)), today)!!
        assertEquals(1200.0, form.time(5_000)!!.toDouble(), 5.0)
        assertTrue(form.time(42_195)!! > 3 * 3600)
    }

    @Test fun easyRunWithHeartRateCountsMoreThanPaceAlone() {
        // 10 km in 55 min at HR 145: easy for someone who can race 5 km well under 25 min.
        val easy = FormEstimator.Run(today.minusDays(2), 10_000f, 55 * 60, 145)
        val withHr = FormEstimator.estimate(listOf(easy), today, age = 30)!!
        val noHr = FormEstimator.estimate(listOf(easy.copy(avgHr = null)), today, age = 30)!!
        assertTrue(withHr.vdot > noHr.vdot)
        assertTrue("5k ${withHr.time(5_000)}", withHr.time(5_000)!! in (20 * 60)..(25 * 60))
        assertEquals(1, withHr.withHr)
    }

    @Test fun oldRunsIgnored() =
        assertNull(FormEstimator.estimate(listOf(FormEstimator.Run(today.minusDays(120), 5_000f, 1200, null)), today))
}
