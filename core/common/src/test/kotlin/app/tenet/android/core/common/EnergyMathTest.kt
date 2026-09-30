package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyMathTest {

    private val man = BodyProfile(Sex.MALE, age = 30, heightCm = 180f, weightKg = 80f)

    @Test
    fun `bmr follows mifflin st jeor`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780f, EnergyMath.bmr(man), 0.01f)
        // Female: same body, -161 instead of +5 → 1614
        assertEquals(1614f, EnergyMath.bmr(man.copy(sex = Sex.FEMALE)), 0.01f)
    }

    @Test
    fun `tdee multiplies activity factor`() {
        assertEquals(1780f * 1.55f, EnergyMath.tdee(man), 0.01f)
    }

    @Test
    fun `target applies goal and rounds to ten`() {
        // 2759 - 500 = 2259 → 2260
        assertEquals(2260f, EnergyMath.targetKcal(man.copy(goal = WeightGoal.LOSE)), 0.01f)
    }

    @Test
    fun `target never drops below 1200`() {
        val tiny = BodyProfile(Sex.FEMALE, 80, 150f, 40f, ActivityLevel.SEDENTARY, WeightGoal.LOSE)
        assertEquals(1200f, EnergyMath.targetKcal(tiny), 0.01f)
    }

    @Test
    fun `macros by body weight fill carbs with the rest`() {
        val m = EnergyMath.macrosByBodyWeight(kcal = 2500f, weightKg = 80f, proteinPerKg = 2f, fatPerKg = 1f)
        assertEquals(160f, m.protein, 0.01f)
        assertEquals(80f, m.fat, 0.01f)
        // (2500 - 640 - 720) / 4 = 285
        assertEquals(285f, m.carbs, 0.01f)
    }

    @Test
    fun `carbs never negative`() {
        val m = EnergyMath.macrosByBodyWeight(kcal = 1200f, weightKg = 120f, proteinPerKg = 2.5f, fatPerKg = 1.2f)
        assertEquals(0f, m.carbs, 0.01f)
    }

    @Test
    fun `macros by percent`() {
        val m = EnergyMath.macrosByPercent(2000f, 30, 40, 30)
        assertEquals(150f, m.protein, 0.01f)
        assertEquals(200f, m.carbs, 0.01f)
        assertEquals(65f, m.fat, 0.01f) // 66.7 → 65
    }

    @Test
    fun `per 100 scaling`() {
        assertEquals(185f, EnergyMath.per100(370f, 50f), 0.01f)
    }
}
