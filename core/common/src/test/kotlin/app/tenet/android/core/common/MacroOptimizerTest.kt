package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MacroOptimizerTest {

    private val chicken = MacroCandidate("chicken", "Hähnchen mit Reis", "Portion", Macros(550f, 45f, 60f, 12f))
    private val quark = MacroCandidate("quark", "Magerquark", "250 g", Macros(170f, 30f, 10f, 1f), maxUnits = 2)
    private val oats = MacroCandidate("oats", "Haferflocken", "80 g", Macros(300f, 11f, 48f, 6f))
    private val nuts = MacroCandidate("nuts", "Nüsse", "30 g", Macros(190f, 6f, 4f, 17f))
    private val all = listOf(chicken, quark, oats, nuts)

    @Test
    fun hitsAnExactlyReachableTarget() {
        // 1 × chicken + 1 × quark + 1 × nuts.
        val target = Macros(910f, 81f, 74f, 30f)
        val plan = MacroOptimizer.optimize(all, target)
        assertEquals(setOf("chicken", "quark", "nuts"), plan.items.map { it.candidate.id }.toSet())
        assertTrue(abs(plan.total.kcal - target.kcal) < 1f)
    }

    @Test
    fun respectsMaxUnitsAndMaxItems() {
        val plan = MacroOptimizer.optimize(all, Macros(4000f, 300f, 400f, 120f), maxItems = 2)
        assertTrue(plan.items.size <= 2)
        plan.items.forEach { assertTrue(it.units <= it.candidate.maxUnits) }
    }

    @Test
    fun avoidedDishIsReplacedWhenThereIsAnAlternative() {
        val target = Macros(550f, 45f, 60f, 12f)
        val first = MacroOptimizer.optimize(all, target)
        assertEquals(listOf("chicken"), first.items.map { it.candidate.id })
        val second = MacroOptimizer.optimize(all, target, avoid = setOf("chicken"))
        assertTrue(second.items.none { it.candidate.id == "chicken" })
        assertTrue(second.items.isNotEmpty())
    }

    @Test
    fun noSingleDishCarriesAWholeDay() {
        val pizza = MacroCandidate("pizza", "Pizza", "350 g", Macros(875f, 35f, 112f, 31f))
        val tuna = MacroCandidate("tuna", "Thunfisch", "150 g", Macros(174f, 39f, 0f, 1.5f))
        val plan = MacroOptimizer.optimize(all + pizza + tuna, Macros(2000f, 150f, 200f, 60f))
        val total = plan.total.kcal
        plan.items.forEach { assertTrue("${it.candidate.id} too big", it.macros.kcal / 2000f <= 0.5f) }
        assertTrue(abs(total - 2000f) / 2000f < 0.15f)
    }

    @Test
    fun emptyWithoutCandidatesOrTarget() {
        assertTrue(MacroOptimizer.optimize(emptyList(), Macros(500f, 30f, 50f, 10f)).isEmpty)
        assertTrue(MacroOptimizer.optimize(all, Macros()).isEmpty)
    }

    @Test
    fun fastOnARealisticInstance() {
        val many = (0 until 40).map { i ->
            MacroCandidate("c$i", "Gericht $i", "Portion", Macros(150f + i * 17 % 500, 5f + i % 35, 10f + i * 7 % 70, 2f + i % 25), maxUnits = 2)
        }
        val start = System.nanoTime()
        val plan = MacroOptimizer.optimize(many, Macros(2100f, 160f, 210f, 70f))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2000)
        assertTrue(abs(plan.total.kcal - 2100f) / 2100f < 0.1f)
    }
}
