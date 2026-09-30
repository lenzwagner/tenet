package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTextParserTest {

    private val withHeaders = """
        Pfannkuchen
        Für 4 Portionen
        Zubereitungszeit: 25 Minuten

        Zutaten
        - 250 g Mehl
        - 500 ml Milch
        - 3 Eier
        - 1 Prise Salz
        - 2 EL Öl
        - ½ TL Zucker

        Zubereitung
        1. Mehl, Milch und Eier verrühren.
        2. Teig 10 Min quellen lassen.
        3. In Öl portionsweise ausbacken.
    """.trimIndent()

    @Test
    fun headers_titleServingsTime() {
        val p = RecipeTextParser.parse(withHeaders)
        assertEquals("Pfannkuchen", p.title)
        assertEquals(4, p.servings)
        assertEquals(25, p.minutes)
        assertEquals(6, p.ingredients.size)
        assertEquals(3, p.steps.size)
        assertEquals("Teig 10 Min quellen lassen.", p.steps[1])
    }

    @Test
    fun headers_ingredientAmounts() {
        val i = RecipeTextParser.parse(withHeaders).ingredients
        assertEquals("Mehl", i[0].name)
        assertEquals(250f, i[0].grams!!, 0.1f)
        assertFalse(i[0].estimated)
        assertEquals(500f, i[1].grams!!, 0.1f)
        assertEquals("Eier", i[2].name)
        assertEquals(180f, i[2].grams!!, 0.1f)
        assertTrue(i[2].estimated)
        assertEquals(30f, i[4].grams!!, 0.1f)
        assertEquals(2.5f, i[5].grams!!, 0.1f)
    }

    @Test
    fun ingredient_variants() {
        assertNull(RecipeTextParser.ingredient("Salz und Pfeffer").grams)
        assertEquals(1500f, RecipeTextParser.ingredient("1,5 kg Kartoffeln, festkochend").grams!!, 0.1f)
        assertEquals("Kartoffeln", RecipeTextParser.ingredient("1,5 kg Kartoffeln, festkochend").name)
        assertEquals(8f, RecipeTextParser.ingredient("2 Zehen Knoblauch").grams!!, 0.1f)
        assertEquals(250f, RecipeTextParser.ingredient("2-3 Zwiebeln").grams!!, 0.1f)
        assertEquals(400f, RecipeTextParser.ingredient("1 Dose Tomaten (gehackt)").grams!!, 0.1f)
        assertEquals("Tomaten", RecipeTextParser.ingredient("1 Dose Tomaten (gehackt)").name)
    }

    @Test
    fun noHeaders_splitsByAmount() {
        val p = RecipeTextParser.parse(
            """
            Overnight Oats
            50 g Haferflocken
            150 ml Milch
            1 Banane
            Alles verrühren und über Nacht in den Kühlschrank stellen.
            """.trimIndent(),
        )
        assertEquals("Overnight Oats", p.title)
        assertEquals(3, p.ingredients.size)
        assertEquals(120f, p.ingredients[2].grams!!, 0.1f)
        assertEquals(1, p.steps.size)
    }
}
