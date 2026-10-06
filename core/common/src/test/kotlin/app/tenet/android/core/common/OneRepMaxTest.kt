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
        // 1RM = 100 × 36 / (37 − 8) = 124.14
        assertEquals(124.14f, OneRepMax.brzycki(100f, 8), 0.01f)
    }

    @Test
    fun `brzycki is undefined at 37 reps and returns zero`() {
        assertEquals(0f, OneRepMax.brzycki(100f, 37), 0.001f)
        assertEquals(0f, OneRepMax.brzycki(100f, 40), 0.001f)
    }

    @Test
    fun `formula selection changes the estimate`() {
        val epley = OneRepMax.oneRepMax(100f, 8, OneRepMaxFormula.EPLEY)
        val brzycki = OneRepMax.oneRepMax(100f, 8, OneRepMaxFormula.BRZYCKI)
        assertEquals(126.67f, epley, 0.01f)
        assertEquals(124.14f, brzycki, 0.01f)
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

    @Test
    fun `realistic bench loads match independently calculated reference values`() {
        // kg, repetitions, Epley, Brzycki. Arithmetic fixtures, not measured user maxima.
        val cases = listOf(
            floatArrayOf(60f, 10f, 80f, 80f),
            floatArrayOf(70f, 8f, 88.66667f, 86.89655f),
            floatArrayOf(80f, 5f, 93.33333f, 90f),
            floatArrayOf(100f, 3f, 110f, 105.88235f),
            floatArrayOf(120f, 1f, 120f, 120f),
        )
        cases.forEach { (weight, reps, epley, brzycki) ->
            assertEquals(epley, OneRepMax.epley(weight, reps.toInt()), 0.001f)
            assertEquals(brzycki, OneRepMax.brzycki(weight, reps.toInt()), 0.001f)
        }
    }

    @Test
    fun `both formulas agree at ten reps and invert correctly`() {
        OneRepMaxFormula.entries.forEach { formula ->
            assertEquals(80f, OneRepMax.oneRepMax(60f, 10, formula), 0.001f)
            for (reps in 1..10) {
                val max = OneRepMax.oneRepMax(80f, reps, formula)
                assertEquals(80f, OneRepMax.weightForReps(max, reps, formula), 0.001f)
            }
        }
    }

    @Test
    fun `invalid floating point values do not poison maxima`() {
        OneRepMaxFormula.entries.forEach { formula ->
            listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { weight ->
                assertEquals(0f, OneRepMax.oneRepMax(weight, 5, formula), 0f)
            }
            assertEquals(80f, OneRepMax.best(sequenceOf(Float.NaN to 5, 60f to 10), formula), 0.001f)
        }
    }
}
