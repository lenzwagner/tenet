package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class OneRepMaxTest {

    @Test
    fun `epley matches the formula from the concept`() {
        // 1RM = 100 × (1 + 8 / 30) = 126.67
        assertEquals(126.67f, OneRepMax.epley(100f, 8), 0.01f)
    }

    @Test
    fun `epley of a single rep is the weight itself`() {
        assertEquals(100f, OneRepMax.epley(100f, 1), 0.001f)
    }

    @Test
    fun `epley guards invalid input`() {
        assertEquals(0f, OneRepMax.epley(0f, 8), 0.001f)
        assertEquals(0f, OneRepMax.epley(100f, 0), 0.001f)
        assertEquals(0f, OneRepMax.epley(-50f, 8), 0.001f)
    }

    @Test
    fun `brzycki matches the standard formula`() {
        // 1RM = 100 × 36 / (36 − 8) = 128.57
        assertEquals(128.57f, OneRepMax.brzycki(100f, 8), 0.01f)
    }

    @Test
    fun `brzycki is undefined at 36 reps and returns zero`() {
        assertEquals(0f, OneRepMax.brzycki(100f, 36), 0.001f)
        assertEquals(0f, OneRepMax.brzycki(100f, 40), 0.001f)
    }

    @Test
    fun `formula selection changes the estimate`() {
        val epley = OneRepMax.oneRepMax(100f, 8, OneRepMaxFormula.EPLEY)
        val brzycki = OneRepMax.oneRepMax(100f, 8, OneRepMaxFormula.BRZYCKI)
        assertEquals(126.67f, epley, 0.01f)
        assertEquals(128.57f, brzycki, 0.01f)
    }

    @Test
    fun `best picks the highest estimate of the set`() {
        // epley(60,10)=80, epley(100,5)=116.67, epley(80,8)=101.33 → 100×5 wins.
        val sets = sequenceOf(60f to 10, 100f to 5, 80f to 8)
        assertEquals(
            OneRepMax.epley(100f, 5),
            OneRepMax.best(sets, OneRepMaxFormula.EPLEY),
            0.01f,
        )
    }

    @Test
    fun `best of an empty sequence is zero`() {
        assertEquals(0f, OneRepMax.best(emptySequence(), OneRepMaxFormula.EPLEY), 0.001f)
    }
}
