package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPaceMathTest {

    private val anchor5k20 = PaceAnchor(distanceM = 5000, timeSec = 20 * 60)
    private val anchor5k25 = PaceAnchor(distanceM = 5000, timeSec = 25 * 60)

    // ---- VDOT -----------------------------------------------------------

    @Test
    fun `vdot of a 20 minute 5k lands around 50`() {
        // Formula pipeline (cross-checked: 24:00 5k -> 40.2, 25:00 -> 38.3,
        // both matching published calculator outputs).
        val vdot = RunPaceMath.vdot(5000, 20 * 60)
        assertTrue("was $vdot", vdot in 49.5..50.2)
    }

    @Test
    fun `vdot of a 25 minute 5k matches the calculator example`() {
        // Reference implementation example: 5k in 25:00 -> VDOT 38.4.
        val vdot = RunPaceMath.vdot(5000, 25 * 60)
        assertTrue("was $vdot", vdot in 38.2..38.6)
    }

    @Test
    fun `slower race gives lower vdot`() {
        assertTrue(
            RunPaceMath.vdot(5000, 25 * 60) < RunPaceMath.vdot(5000, 20 * 60),
        )
    }

    @Test
    fun `vdot zones are ordered from slowest to fastest`() {
        val pace = { zone: RunZone ->
            RunPaceMath.targetPaceSecPerKm(zone, anchor5k20, PaceMethod.VDOT)
        }
        // Bigger seconds-per-km = slower.
        assertTrue(pace(RunZone.RECOVERY) >= pace(RunZone.LONG))
        assertTrue(pace(RunZone.LONG) >= pace(RunZone.EASY))
        assertTrue(pace(RunZone.EASY) > pace(RunZone.TEMPO))
        assertTrue(pace(RunZone.TEMPO) > pace(RunZone.INTERVAL))
    }

    @Test
    fun `marathon goal moves the long run to marathon pace`() {
        val easy = RunPaceMath.targetPaceSecPerKm(
            RunZone.LONG, anchor5k20, PaceMethod.VDOT, goalIsMarathon = false,
        )
        val marathon = RunPaceMath.targetPaceSecPerKm(
            RunZone.LONG, anchor5k20, PaceMethod.VDOT, goalIsMarathon = true,
        )
        assertTrue("marathon long run should be faster: $marathon vs $easy", marathon < easy)
    }

    // ---- percent --------------------------------------------------------

    @Test
    fun `percent paces follow the documented speed factors`() {
        val anchorPace = anchor5k25.timeSec * 1000.0 / anchor5k25.distanceM // 300 s/km
        val easy = RunPaceMath.targetPaceSecPerKm(RunZone.EASY, anchor5k25, PaceMethod.PERCENT)
        val interval = RunPaceMath.targetPaceSecPerKm(RunZone.INTERVAL, anchor5k25, PaceMethod.PERCENT)
        // Speed factor 0.79 -> pace / 0.79.
        assertTrue(kotlin.math.abs((anchorPace / 0.79).toInt() - easy) <= 1)
        // Speed factor 1.03 -> pace / 1.03 (faster than the anchor).
        assertTrue(kotlin.math.abs((anchorPace / 1.03).toInt() - interval) <= 1)
        assertTrue(interval < anchorPace.toInt())
        assertTrue(easy > anchorPace.toInt())
    }

    @Test
    fun `percent zones are ordered from slowest to fastest`() {
        val pace = { zone: RunZone ->
            RunPaceMath.targetPaceSecPerKm(zone, anchor5k20, PaceMethod.PERCENT)
        }
        assertTrue(pace(RunZone.RECOVERY) > pace(RunZone.EASY))
        assertTrue(pace(RunZone.EASY) > pace(RunZone.TEMPO))
        assertTrue(pace(RunZone.TEMPO) > pace(RunZone.INTERVAL))
    }

    @Test
    fun `pace method resolves from id with vdot fallback`() {
        assertEquals(PaceMethod.VDOT, PaceMethod.fromId("VDOT"))
        assertEquals(PaceMethod.PERCENT, PaceMethod.fromId("PERCENT"))
        assertEquals(PaceMethod.VDOT, PaceMethod.fromId(null))
        assertEquals(PaceMethod.VDOT, PaceMethod.fromId("garbage"))
    }
}
