package app.tenet.android.core.common

import app.tenet.android.core.common.SkillMath.Attempt
import app.tenet.android.core.common.SkillMath.Criterion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillMathTest {

    private val hold3x10 = Criterion(isHold = true, sets = 3, value = 10)
    private val reps3x8 = Criterion(isHold = false, sets = 3, value = 8)

    private fun hold(sec: Int, sloppy: Boolean = false) =
        Attempt(completed = true, seconds = sec, sloppy = sloppy)

    private fun rep(reps: Int, sloppy: Boolean = false) =
        Attempt(completed = true, reps = reps, sloppy = sloppy)

    // ---- sessionMeets ---------------------------------------------------

    @Test
    fun `hold criterion met when three sets reach ten seconds`() {
        val attempts = listOf(hold(12), hold(10), hold(15))
        assertTrue(SkillMath.sessionMeets(attempts, hold3x10))
    }

    @Test
    fun `hold criterion not met with only two qualifying sets`() {
        val attempts = listOf(hold(12), hold(10), hold(7))
        assertFalse(SkillMath.sessionMeets(attempts, hold3x10))
    }

    @Test
    fun `sloppy sets do not count towards the criterion`() {
        val attempts = listOf(hold(12), hold(20, sloppy = true), hold(20, sloppy = true))
        assertFalse(SkillMath.sessionMeets(attempts, hold3x10))
    }

    @Test
    fun `incomplete sets do not count towards the criterion`() {
        val attempts = listOf(
            hold(12),
            hold(12),
            Attempt(completed = false, seconds = 12),
        )
        assertFalse(SkillMath.sessionMeets(attempts, hold3x10))
    }

    @Test
    fun `rep criterion uses reps not seconds`() {
        val attempts = listOf(rep(8), rep(9), rep(8))
        assertTrue(SkillMath.sessionMeets(attempts, reps3x8))

        val tooFew = listOf(rep(8), rep(7), rep(9))
        assertFalse(SkillMath.sessionMeets(tooFew, reps3x8))
    }

    @Test
    fun `empty session never meets a criterion`() {
        assertFalse(SkillMath.sessionMeets(emptyList(), hold3x10))
        assertFalse(SkillMath.sessionMeets(emptyList(), reps3x8))
    }

    // ---- bestValue ------------------------------------------------------

    @Test
    fun `best hold value is the longest clean hold`() {
        val attempts = listOf(hold(8), hold(15), hold(12, sloppy = true))
        assertEquals(15, SkillMath.bestValue(attempts, isHold = true))
    }

    @Test
    fun `best rep value is the highest clean set`() {
        val attempts = listOf(rep(6), rep(10), rep(10, sloppy = true))
        assertEquals(10, SkillMath.bestValue(attempts, isHold = false))
    }

    @Test
    fun `best value is null without completed sets`() {
        assertNull(SkillMath.bestValue(emptyList(), isHold = true))
        assertNull(
            SkillMath.bestValue(listOf(Attempt(completed = false, seconds = 30)), isHold = true),
        )
    }

    // ---- shouldSuggestPromotion ----------------------------------------

    @Test
    fun `promotion needs two confirmed sessions`() {
        assertFalse(SkillMath.shouldSuggestPromotion(0))
        assertFalse(SkillMath.shouldSuggestPromotion(1))
        assertTrue(SkillMath.shouldSuggestPromotion(2))
        assertTrue(SkillMath.shouldSuggestPromotion(5))
    }
}
