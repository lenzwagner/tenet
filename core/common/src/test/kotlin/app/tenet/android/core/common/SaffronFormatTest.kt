package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SaffronFormatTest {
    @Test fun ingredientsAndTags() {
        assertEquals(listOf("200 g Mehl", "2 Eier"), SaffronFormat.strings("""["200 g Mehl", " 2 Eier ", ""]"""))
        assertEquals(emptyList<String>(), SaffronFormat.strings("kaputt"))
        assertEquals(listOf("Käse \"alt\""), SaffronFormat.strings("""["Käse \"alt\""]"""))
    }

    @Test fun structuredSteps() {
        val steps = SaffronFormat.steps(
            """[{"text":"Nudeln kochen","timeMinutes":10,"stepIngredients":["500 g Nudeln"]},{"text":"Servieren","timeMinutes":0,"stepIngredients":[]}]""",
        )
        assertEquals(2, steps.size)
        assertEquals(CookStep("Nudeln kochen", 10, listOf("500 g Nudeln")), steps[0])
        assertEquals(0, steps[1].minutes)
    }

    @Test fun plainStringSteps() {
        val steps = SaffronFormat.steps("""["Zwiebeln 5 Minuten anbraten", "Fertig"]""")
        assertEquals(5, steps[0].minutes)
        assertEquals("Fertig", steps[1].text)
    }

    @Test fun scaleIngredients() {
        assertEquals("400 g Mehl", IngredientScaler.scale("200 g Mehl", 2.0))
        assertEquals("1 TL Salz", IngredientScaler.scale("1/2 TL Salz", 2.0))
        assertEquals("3 kg Kartoffeln", IngredientScaler.scale("1,5 kg Kartoffeln", 2.0))
        assertEquals("1 Eier", IngredientScaler.scale("2 Eier", 0.5))
        assertEquals("0,8 Zwiebel", IngredientScaler.scale("½ Zwiebel", 1.5).replace("0,8", "0,8"))
        assertEquals("Salz nach Geschmack", IngredientScaler.scale("Salz nach Geschmack", 3.0))
        assertEquals("4–6 Zehen Knoblauch", IngredientScaler.scale("2-3 Zehen Knoblauch", 2.0))
        assertEquals("200 g Mehl", IngredientScaler.scale("200 g Mehl", 1.0))
    }

    @Test fun writesWhatItReads() {
        val steps = listOf(
            CookStep("Sahne \"kräftig\" schlagen\nund kühlen", 5, listOf("200 ml Sahne")),
            CookStep("Servieren", 0, emptyList()),
        )
        assertEquals(steps, SaffronFormat.steps(SaffronFormat.stepsJson(steps)))
        val items = listOf("1/2 TL Salz", "Back\\slash")
        assertEquals(items, SaffronFormat.strings(SaffronFormat.stringsJson(items)))
    }
}
