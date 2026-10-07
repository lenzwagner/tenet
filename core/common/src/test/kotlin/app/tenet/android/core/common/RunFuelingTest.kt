package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunFuelingTest {

    @Test
    fun shortEasyRunNeedsNothingSpecial() {
        val a = RunFueling.advise(30 * 60, RunZone.EASY, race = false, weightKg = 75f)
        assertNull(a.carbsBeforeG)
        assertNull(a.carbsPerHourG)
        assertNull(a.waterPerHourMl)
        // 5–7 ml/kg before.
        assertTrue(a.waterBeforeMl.first in 350..400 && a.waterBeforeMl.last in 500..550)
    }

    @Test
    fun intervalsGetASnackBefore() {
        val a = RunFueling.advise(40 * 60, RunZone.INTERVAL, race = false, weightKg = 75f)
        assertEquals(30..60, a.carbsBeforeG)
        assertNull(a.carbsPerHourG)
    }

    @Test
    fun longRunScalesWithWeightAndFuelsDuring() {
        val a = RunFueling.advise(105 * 60, RunZone.LONG, race = false, weightKg = 70f)
        assertEquals(70..140, a.carbsBeforeG)
        assertEquals(30..60, a.carbsPerHourG)
        assertNotNull(a.carbsDuringTotalG)
        assertEquals(400..800, a.waterPerHourMl)
        assertTrue(!a.sodium)
    }

    @Test
    fun marathonRaceHighCarbsAndSodium() {
        val a = RunFueling.advise(225 * 60, RunZone.TEMPO, race = true, weightKg = 75f, marathon = true)
        assertEquals(150..225, a.carbsBeforeG)
        assertEquals(60..90, a.carbsPerHourG)
        assertTrue(a.sodium)
    }
}
