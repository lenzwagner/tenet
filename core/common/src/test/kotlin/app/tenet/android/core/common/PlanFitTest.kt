package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanFitTest {
    @Test fun onTrack() = assertEquals(PlanFit.Goal.ON_TRACK, PlanFit.assess(6000, 5900, 1500, 1500).goal)

    @Test fun ambitious() = assertEquals(PlanFit.Goal.AMBITIOUS, PlanFit.assess(6000, 6150, 1500, 1500).goal)

    @Test fun unrealisticSuggestsPrognosis() {
        val a = PlanFit.assess(6000, 6700, 1500, 1500)
        assertEquals(PlanFit.Goal.UNREALISTIC, a.goal)
        assertEquals(6720, a.suggestedTargetSec)
        assertTrue(a.suggestAdjust)
    }

    @Test fun fasterFormOutdatesPaces() {
        val a = PlanFit.assess(null, null, 1500, 1420)
        assertTrue(a.pacesOutdated)
        assertTrue(a.suggestAdjust)
    }

    @Test fun smallDriftIsFine() = assertFalse(PlanFit.assess(null, null, 1500, 1480).suggestAdjust)
}
