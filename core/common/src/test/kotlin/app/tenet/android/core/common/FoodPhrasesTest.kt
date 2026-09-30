package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPhrasesTest {

    @Test
    fun parse_countsUnitsAndSplits() {
        val items = FoodPhraseParser.parse("2 Eier und eine Scheibe Vollkornbrot mit Butter")
        assertEquals(listOf("Ei", "Vollkornbrot", "Butter"), items.map { it.name })
        assertEquals(120f, items[0].grams)
        assertEquals(2f, items[0].count)
        assertEquals(40f, items[1].grams)
        assertEquals(10f, items[2].grams)
    }

    @Test
    fun parse_explicitWeightAndMeal() {
        val items = FoodPhraseParser.parse("zum Frühstück 200g Skyr und ein Glas Milch")
        assertEquals("Skyr", items[0].name)
        assertEquals(200f, items[0].grams)
        assertTrue(items[0].explicit)
        assertEquals("BREAKFAST", items[0].meal)
        assertEquals(200f, items[1].grams)
        assertFalse(items[1].explicit)
    }

    @Test
    fun portion_historyWinsWithoutAmount() {
        val history = listOf(55f, 60f, 70f)
        assertEquals(60f to true, PortionMath.adjust(FoodPhrase("Müsli", 60f), history))
        assertEquals(60f to true, PortionMath.adjust(FoodPhrase("Müsli", 80f), history))
        // Explicit grams and counted pieces stay as spoken.
        assertEquals(200f to false, PortionMath.adjust(FoodPhrase("Müsli", 200f, explicit = true), history))
        assertEquals(120f to false, PortionMath.adjust(FoodPhrase("Ei", 120f, count = 2f), history))
        // No history: keep the estimate.
        assertEquals(80f to false, PortionMath.adjust(FoodPhrase("Müsli", 80f), emptyList()))
    }
}
