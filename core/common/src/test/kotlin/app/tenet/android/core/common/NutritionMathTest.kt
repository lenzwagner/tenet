package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionMathTest {

    // ---- fraction -------------------------------------------------------

    @Test
    fun `fraction is zero when no goal is set`() {
        assertEquals(0f, NutritionMath.fraction(500f, 0f), 0.0001f)
        assertEquals(0f, NutritionMath.fraction(500f, -10f), 0.0001f)
    }

    @Test
    fun `fraction is clamped to one`() {
        assertEquals(1f, NutritionMath.fraction(3000f, 2000f), 0.0001f)
    }

    @Test
    fun `fraction of half goal`() {
        assertEquals(0.5f, NutritionMath.fraction(1000f, 2000f), 0.0001f)
    }

    // ---- remaining grams ------------------------------------------------

    @Test
    fun `remaining grams below and above goal`() {
        assertEquals(100f, NutritionMath.remainingGrams(200f, 300f), 0.0001f)
        assertEquals(-50f, NutritionMath.remainingGrams(350f, 300f), 0.0001f)
    }

    // ---- segments -------------------------------------------------------

    @Test
    fun `empty input has no segments`() {
        assertTrue(NutritionMath.macroSegments(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `mismatched sizes are rejected`() {
        val result = runCatching { NutritionMath.macroSegments(listOf(1f), emptyList()) }
        assertTrue(result.isFailure)
    }

    @Test
    fun `segments split evenly when nothing is consumed`() {
        val segments = NutritionMath.macroSegments(
            consumed = listOf(0f, 0f, 0f),
            goals = listOf(150f, 200f, 60f),
            gapDegrees = 4f,
        )
        assertEquals(3, segments.size)
        segments.forEach { segment ->
            assertEquals((360f - 12f) / 3f, segment.sweep, 0.01f)
            assertEquals(0f, segment.progress, 0.0001f)
            assertEquals(0f, segment.drawnSweep, 0.0001f)
        }
    }

    @Test
    fun `segment sweeps follow macro distribution and cover the full ring`() {
        // 150 + 50 + 100 = 300 g -> shares 50 %, ~16.7 %, ~33.3 %
        val segments = NutritionMath.macroSegments(
            consumed = listOf(150f, 50f, 100f),
            goals = listOf(150f, 200f, 60f),
            gapDegrees = 4f,
        )
        val sweepBase = 360f - 12f
        assertEquals(sweepBase * 0.5f, segments[0].sweep, 0.01f)
        assertEquals(sweepBase / 6f, segments[1].sweep, 0.01f)
        assertEquals(sweepBase / 3f, segments[2].sweep, 0.01f)

        // Gaps included, the segments must add up to the full circle.
        val covered = segments.sumOf { it.sweep.toDouble() } + 3 * 4f
        assertEquals(360.0, covered, 0.01)

        // First segment starts at 12 o'clock (half the gap before it).
        assertEquals(-88f, segments[0].startAngle, 0.0001f)
    }

    @Test
    fun `progress and over flag reflect the per-macro goal`() {
        val segments = NutritionMath.macroSegments(
            consumed = listOf(75f, 400f, 60f),
            goals = listOf(150f, 200f, 60f),
        )
        assertEquals(0.5f, segments[0].progress, 0.0001f)
        assertFalse(segments[0].over)
        assertEquals(2f, segments[1].progress, 0.0001f)
        assertTrue(segments[1].over)
        assertEquals(1f, segments[2].progress, 0.0001f)
        assertFalse(segments[2].over)
    }

    @Test
    fun `drawn sweep never exceeds the segment`() {
        val segments = NutritionMath.macroSegments(
            consumed = listOf(300f),
            goals = listOf(100f),
        )
        assertEquals(segments[0].sweep, segments[0].drawnSweep, 0.0001f)
    }

    @Test
    fun `zero goal yields zero progress instead of division by zero`() {
        val segments = NutritionMath.macroSegments(
            consumed = listOf(100f),
            goals = listOf(0f),
        )
        assertEquals(0f, segments[0].progress, 0.0001f)
        assertFalse(segments[0].over)
    }

    @Test
    fun `negative values are treated as zero`() {
        val segments = NutritionMath.macroSegments(
            consumed = listOf(-10f, 100f),
            goals = listOf(50f, 50f),
        )
        assertEquals(0f, segments[0].progress, 0.0001f)
        assertEquals(0f, segments[0].sweep, 0.0001f)
        assertEquals(2f, segments[1].progress, 0.0001f)
        assertTrue(segments[1].over)
    }

    @Test
    fun `segments are laid out sequentially with gaps`() {
        val gap = 6f
        val segments = NutritionMath.macroSegments(
            consumed = listOf(10f, 10f, 10f, 10f),
            goals = listOf(10f, 10f, 10f, 10f),
            gapDegrees = gap,
        )
        for (i in 1 until segments.size) {
            val expected = segments[i - 1].startAngle + segments[i - 1].sweep + gap
            assertEquals(expected, segments[i].startAngle, 0.001f)
        }
    }
}
