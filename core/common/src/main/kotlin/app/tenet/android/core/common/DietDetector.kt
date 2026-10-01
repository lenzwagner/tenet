package app.tenet.android.core.common

/**
 * Rule check on top of the AI's diet tag (like Saffron's TagDetector):
 * meat or fish among the ingredients always means "Nicht-Vegetarisch",
 * dairy, eggs or honey rule out "Vegan".
 */
object DietDetector {
    const val MEAT = "Nicht-Vegetarisch"
    const val VEGETARIAN = "Vegetarisch"
    const val VEGAN = "Vegan"

    private val MEAT_WORDS = listOf(
        "hackfleisch", "hack ", "fleisch", "rind", "schwein", "kalb", "lamm", "hähnchen", "hühn", "huhn", "hendl", "pute", "ente",
        "gans", "wild", "reh", "hirsch", "speck", "schinken", "bacon", "salami", "wurst", "chorizo", "pancetta", "guanciale",
        "fisch", "lachs", "thunfisch", "forelle", "kabeljau", "dorsch", "sardelle", "sardine", "garnele", "shrimp", "krabbe",
        "scampi", "muschel", "tintenfisch", "calamari", "gelatine", "chicken", "beef", "pork", "lamb", "turkey", "duck",
        "ham", "prosciutto", "sausage", "fish", "salmon", "tuna", "prawn", "anchov",
    )
    private val ANIMAL_WORDS = listOf(
        "käse", "parmesan", "mozzarella", "feta", "milch", "sahne", "butter", "joghurt", "quark", "schmand", "crème", "creme fraiche",
        "ei ", "eier", "eigelb", "eiweiß", "honig", "cheese", "milk", "cream", "yogurt", "egg", "honey",
    )

    /** Plant-based look-alikes ("Kokosmilch", "vegane Butter") are not animal products. */
    private val PLANT = Regex("(kokos|hafer|soja|mandel|reis|erbsen|cashew|pflanzen|vegan\\w*)\\s*-?(milch|sahne|butter|joghurt|käse|drink)|margarine")

    /** Tags with the diet tag first, corrected by the ingredient list. */
    fun correct(tags: List<String>, ingredients: List<String>): List<String> {
        val text = " " + ingredients.joinToString(" ").lowercase().replace(PLANT, " ") + " "
        val diet = when {
            MEAT_WORDS.any { text.contains(it) } -> MEAT
            tags.firstOrNull() == VEGAN && ANIMAL_WORDS.any { text.contains(it) } -> VEGETARIAN
            tags.firstOrNull() in setOf(VEGAN, VEGETARIAN) -> tags.first()
            // AI said meat, rules found none: trust it only when the ingredients are
            // unknown or sparse ("full recipe in bio"); a full list without meat wins.
            tags.firstOrNull() == MEAT && ingredients.size < 3 -> MEAT
            else -> if (ANIMAL_WORDS.any { text.contains(it) }) VEGETARIAN else VEGAN
        }
        return listOf(diet) + tags.filter { it != MEAT && it != VEGETARIAN && it != VEGAN }
    }
}
