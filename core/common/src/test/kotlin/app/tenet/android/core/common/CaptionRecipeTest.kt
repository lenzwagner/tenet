package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionRecipeTest {
    private val alfredo = """
        EP 14. MICHELIN ON A MINIMUM WAGE || Chicken Alfredo🍗🍝

        Ingredients (serves 4):
        - 350g fettuccine
        - 30g butter
        - Salt and pepper

        - 500g chicken thighs marinated
        - 2 tbsp Cajun seasoning
        Method

        	1.	Fry the thighs in a hot pan for 5-6 mins each side.
        	2.	Boil the pasta in salted water.
        	3.	Top with the chicken.
    """.trimIndent()

    @Test fun parsesSections() {
        val p = CaptionRecipe.parse(alfredo)!!
        assertEquals("Chicken Alfredo", p.title)
        assertEquals(listOf("350g fettuccine", "30g butter", "Salt and pepper", "500g chicken thighs marinated", "2 tbsp Cajun seasoning"), p.ingredients)
        assertEquals(3, p.steps.size)
        assertEquals("Boil the pasta in salted water.", p.steps[1])
        assertEquals(4, p.servings)
        assertNull(p.minutes)
    }

    @Test fun noIngredientsHeaderMeansAi() = assertNull(CaptionRecipe.parse("Crispy chicken\n3 chicken breasts\n2 eggs"))

    @Test fun germanCaption() {
        val p = CaptionRecipe.parse("Pasta für 2 Personen\nZutaten:\n• 200 g Nudeln\n• 1 Zwiebel\nZubereitung:\nSchritt 1: Kochen\nSchritt 2: Essen\n#pasta")!!
        assertEquals(listOf("200 g Nudeln", "1 Zwiebel"), p.ingredients)
        assertEquals(listOf("Kochen", "Essen"), p.steps)
        assertEquals(2, p.servings)
    }

    @Test fun shoutingTitle() = assertEquals("Best Pasta Ever", CaptionRecipe.cleanTitle("BEST PASTA EVER 🔥"))

    @Test fun servingsInText() {
        assertEquals(4, CaptionRecipe.servings("Omas Pfannkuchen für 4 Personen. 250g Mehl"))
        assertEquals(2, CaptionRecipe.servings("Serves 2"))
        assertNull(CaptionRecipe.servings("Im Ofen für 20 Minuten backen"))
    }

    @Test fun tagCase() {
        assertEquals("Warm", CaptionRecipe.cleanTag("WARM"))
        assertEquals("BBQ", CaptionRecipe.cleanTag("BBQ"))
        assertEquals("Low Carb", CaptionRecipe.cleanTag("#Low Carb"))
        assertEquals("Deutsch", CaptionRecipe.cleanTag("Küche: Deutsch"))
    }
}
