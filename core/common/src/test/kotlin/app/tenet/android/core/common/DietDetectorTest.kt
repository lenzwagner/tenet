package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DietDetectorTest {
    @Test fun meatBeatsAiVegan() =
        assertEquals(listOf("Nicht-Vegetarisch", "Warm"), DietDetector.correct(listOf("Vegan", "Warm"), listOf("500 g Hackfleisch, gemischtes", "Käse")))

    @Test fun cheeseIsNotVegan() =
        assertEquals("Vegetarisch", DietDetector.correct(listOf("Vegan"), listOf("200 g Mozzarella", "Tomaten")).first())

    @Test fun veganStays() =
        assertEquals("Vegan", DietDetector.correct(listOf("Vegan"), listOf("Linsen", "Kokosmilch", "Curry")).first())

    @Test fun plantMilkIsVegan() =
        assertEquals("Vegan", DietDetector.correct(listOf("Vegan"), listOf("Hafermilch", "vegane Butter", "Mehl")).first())

    @Test fun missingTagIsAdded() =
        assertEquals(listOf("Vegetarisch", "Pasta"), DietDetector.correct(listOf("Pasta"), listOf("Nudeln", "Eier")))

    @Test fun eiDoesNotMatchInsideWords() =
        assertEquals("Vegan", DietDetector.correct(listOf("Vegan"), listOf("Weißwein", "Reis", "Zwiebel")).first())

    @Test fun fullMeatlessListBeatsAi() = assertEquals(
        "Vegetarisch",
        DietDetector.correct(listOf("Nicht-Vegetarisch", "Warm"), listOf("250g Mehl", "3 Eier", "0,5 Liter Milch", "Prise Salz")).first(),
    )

    @Test fun sparseListTrustsAi() =
        assertEquals("Nicht-Vegetarisch", DietDetector.correct(listOf("Nicht-Vegetarisch"), emptyList()).first())
}
