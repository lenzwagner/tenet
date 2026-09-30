package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeMathTest {

    @Test
    fun `minutes are detected`() {
        assertEquals(600, RecipeMath.stepTimerSeconds("Nudeln 10 Min kochen"))
        assertEquals(300, RecipeMath.stepTimerSeconds("5 Minuten ruhen lassen"))
        assertEquals(120, RecipeMath.stepTimerSeconds("2 min. anbraten"))
    }

    @Test
    fun `seconds are detected`() {
        assertEquals(30, RecipeMath.stepTimerSeconds("30 Sek mixen"))
        assertEquals(45, RecipeMath.stepTimerSeconds("45 s pürieren"))
    }

    @Test
    fun `no time no timer`() {
        assertNull(RecipeMath.stepTimerSeconds("Zwiebeln schneiden"))
        assertNull(RecipeMath.stepTimerSeconds("200 g Mehl sieben"))
        assertNull(RecipeMath.stepTimerSeconds("3 Stück Sellerie"))
    }

    @Test
    fun `scaling by servings`() {
        assertEquals(300f, RecipeMath.scale(200f, 2, 3), 0.01f)
        assertEquals(50f, RecipeMath.scale(200f, 4, 1), 0.01f)
    }
}
