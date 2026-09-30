package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorTest {

    @Test
    fun `100 kg on a 20 kg bar needs 40 kg per side`() {
        val result = PlateCalculator.calculate(100f, 20f)
        // Greedy: 40 = 25 + 15
        assertEquals(listOf(25f, 15f), result.plates)
        assertTrue(result.exact)
        assertEquals(100f, result.achievedKg, 0.001f)
    }

    @Test
    fun `empty bar is just the barbell`() {
        val result = PlateCalculator.calculate(20f, 20f)
        assertTrue(result.plates.isEmpty())
        assertTrue(result.exact)
    }

    @Test
    fun `target below bar weight is not loadable`() {
        val result = PlateCalculator.calculate(15f, 20f)
        assertTrue(result.plates.isEmpty())
        assertFalse(result.exact)
    }

    @Test
    fun `small plates complete non-round targets`() {
        val result = PlateCalculator.calculate(62.5f, 20f)
        // 21.25 per side = 20 + 1.25
        assertEquals(listOf(20f, 1.25f), result.plates)
        assertTrue(result.exact)
    }

    @Test
    fun `unloadable remainder is reported`() {
        // 41 kg per side cannot be built exactly from the standard set (0.5 missing in math:
        // 20 + 10 + 5 + 2.5 + 1.25 = 38.75, remainder 2.25 -> use explicit plates instead)
        val result = PlateCalculator.calculate(
            targetKg = 97.5f,
            barKg = 20f,
            plates = listOf(25f, 20f, 15f, 10f),
        )
        // Per side 38.75 -> 25 + 10 = 35, remainder 3.75
        assertEquals(listOf(25f, 10f), result.plates)
        assertFalse(result.exact)
        assertEquals(3.75f, result.remainderPerSide, 0.001f)
        assertEquals(90f, result.achievedKg, 0.001f)
    }

    @Test
    fun `floating point noise counts as exact`() {
        // (102.5 − 20) / 2 = 41.25 → 25 + 15 + 1.25
        val result = PlateCalculator.calculate(102.5f, 20f)
        assertTrue(result.exact)
        assertEquals(102.5f, result.achievedKg, 0.01f)
    }

    @Test
    fun `plates are ordered heaviest first`() {
        // Per side 60 = 25 + 25 + 10
        val result = PlateCalculator.calculate(140f, 20f)
        assertEquals(listOf(25f, 25f, 10f), result.plates)
        val sortedDesc = result.plates.sortedDescending()
        assertEquals(sortedDesc, result.plates)
    }
}
