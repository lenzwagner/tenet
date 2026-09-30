package app.tenet.android.core.common

import kotlin.math.roundToInt

/** One recognised item of a spoken meal ("2 Eier", "200 g Reis"). */
data class FoodPhrase(
    /** Simple food name for the database search. */
    val name: String,
    val grams: Float,
    /** Pieces/servings when counted ("2 Eier" = 2), else null. */
    val count: Float? = null,
    /** True when the amount was given in g/ml/kg/l – never overridden. */
    val explicit: Boolean = false,
    /** BREAKFAST, LUNCH, DINNER, SNACK or null. */
    val meal: String? = null,
)

/**
 * Portion sizes from the user's own history: without an explicit amount
 * (or for a single piece) the typical amount they log of that food wins
 * over a generic estimate.
 */
object PortionMath {

    /** Median of recent amounts, rounded to 5 g; null without history. */
    fun typical(amounts: List<Float>): Float? {
        val valid = amounts.filter { it > 0f }.sorted()
        if (valid.isEmpty()) return null
        val mid = valid.size / 2
        val median = if (valid.size % 2 == 1) valid[mid] else (valid[mid - 1] + valid[mid]) / 2f
        return ((median / 5f).roundToInt() * 5f).coerceAtLeast(5f)
    }

    /** Returns the grams to use and whether they came from the history. */
    fun adjust(phrase: FoodPhrase, history: List<Float>): Pair<Float, Boolean> {
        if (phrase.explicit) return phrase.grams to false
        val count = phrase.count
        if (count != null && count != 1f) return phrase.grams to false
        val typical = typical(history) ?: return phrase.grams to false
        return typical to true
    }
}

/**
 * Offline fallback for simple meal sentences: "2 Eier und eine Scheibe
 * Vollkornbrot mit Butter", "200 g Reis", "ein Glas Milch zum Frühstück".
 * Units and pieces become grams with household defaults.
 */
object FoodPhraseParser {

    private val numberWords = mapOf(
        "ein" to 1f, "eine" to 1f, "einen" to 1f, "einem" to 1f, "einer" to 1f, "eins" to 1f,
        "zwei" to 2f, "drei" to 3f, "vier" to 4f, "fünf" to 5f, "sechs" to 6f, "sieben" to 7f,
        "acht" to 8f, "neun" to 9f, "zehn" to 10f, "halb" to 0.5f, "halbe" to 0.5f, "halben" to 0.5f,
        "anderthalb" to 1.5f, "paar" to 2f,
    )

    /** Unit → grams per unit; null = explicit weight unit (factor in [weightUnits]). */
    private val householdUnits = mapOf(
        "scheibe" to 40f, "scheiben" to 40f,
        "glas" to 200f, "gläser" to 200f,
        "tasse" to 200f, "tassen" to 200f,
        "becher" to 150f,
        "el" to 12f, "esslöffel" to 12f,
        "tl" to 5f, "teelöffel" to 5f,
        "portion" to 150f, "portionen" to 150f,
        "schale" to 250f, "schalen" to 250f, "schüssel" to 300f,
        "handvoll" to 30f,
        "dose" to 200f,
        "stück" to 0f, "stk" to 0f,
        "schuss" to 30f,
    )

    private val weightUnits = mapOf(
        "g" to 1f, "gramm" to 1f, "gr" to 1f,
        "kg" to 1000f, "kilo" to 1000f,
        "ml" to 1f, "milliliter" to 1f,
        "l" to 1000f, "liter" to 1000f,
    )

    /** Singular search names for common plurals. */
    private val singular = mapOf(
        "eier" to "Ei", "äpfel" to "Apfel", "bananen" to "Banane", "brötchen" to "Brötchen",
        "tomaten" to "Tomate", "kartoffeln" to "Kartoffel", "nüsse" to "Nüsse", "orangen" to "Orange",
        "mandarinen" to "Mandarine", "kekse" to "Keks", "riegel" to "Riegel", "karotten" to "Karotte",
        "möhren" to "Möhre", "gurken" to "Gurke", "birnen" to "Birne", "pfirsiche" to "Pfirsich",
    )

    /** Grams per piece when counted without a unit. */
    private val pieceGrams = mapOf(
        "ei" to 60f, "banane" to 120f, "apfel" to 150f, "orange" to 150f, "birne" to 160f,
        "brötchen" to 60f, "toast" to 30f, "kiwi" to 70f, "mandarine" to 60f, "keks" to 10f,
        "riegel" to 40f, "tomate" to 80f, "kartoffel" to 90f, "karotte" to 70f, "möhre" to 70f,
        "croissant" to 60f, "brezel" to 80f, "pfirsich" to 150f, "avocado" to 150f,
    )

    /** Usual amount when nothing is counted ("mit Butter", "Kaffee"). */
    private val usualPortion = mapOf(
        "butter" to 10f, "margarine" to 10f, "marmelade" to 20f, "konfitüre" to 20f, "honig" to 15f,
        "nutella" to 20f, "käse" to 30f, "frischkäse" to 25f, "wurst" to 30f, "schinken" to 30f,
        "milch" to 200f, "kaffee" to 200f, "tee" to 250f, "saft" to 200f, "orangensaft" to 200f,
        "wasser" to 300f, "müsli" to 60f, "haferflocken" to 50f, "joghurt" to 150f, "quark" to 150f,
        "skyr" to 150f, "reis" to 150f, "nudeln" to 150f, "salat" to 100f, "zucker" to 5f,
    )

    private val mealWords = listOf(
        "frühstück" to "BREAKFAST", "morgens" to "BREAKFAST",
        "mittag" to "LUNCH", "mittagessen" to "LUNCH",
        "abend" to "DINNER", "abends" to "DINNER", "abendessen" to "DINNER",
        "snack" to "SNACK", "zwischendurch" to "SNACK",
    )

    private val fillers = setOf(
        "ich", "habe", "hab", "hatte", "gegessen", "getrunken", "heute", "zum", "zu", "als", "noch",
        "etwas", "bisschen", "so", "ca", "circa", "etwa", "ungefähr", "und", "dann", "auch", "gerade",
        "den", "die", "das", "der", "dem",
    )

    fun parse(text: String): List<FoodPhrase> {
        val lower = text.lowercase()
        val meal = mealWords.firstOrNull { (w, _) -> Regex("\\b$w\\b").containsMatchIn(lower) }?.second
        val cleaned = mealWords.fold(lower) { acc, (w, _) -> acc.replace(Regex("\\b(zum |zu |am |als )?$w\\b"), " ") }
        return cleaned
            .split(Regex(",|;|\\bund\\b|\\bmit\\b|\\bsowie\\b|\\bplus\\b|\\bdazu\\b"))
            .mapNotNull { parsePart(it.trim(), meal) }
            .take(12)
    }

    private fun parsePart(part: String, meal: String?): FoodPhrase? {
        var tokens = part.replace(Regex("(\\d)([a-zäöü])"), "$1 $2") // "200g" → "200 g"
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        var count: Float? = null
        var unitGrams: Float? = null
        var weightFactor: Float? = null

        // Amount: a number or number word at the start.
        tokens.firstOrNull()?.let { first ->
            val n = first.replace(',', '.').toFloatOrNull() ?: numberWords[first]
            if (n != null) {
                count = n
                tokens = tokens.drop(1)
            }
        }
        // Unit right after the amount (or at the start without one).
        tokens.firstOrNull()?.let { unit ->
            val clean = unit.trimEnd('.')
            when {
                clean in weightUnits -> { weightFactor = weightUnits.getValue(clean); tokens = tokens.drop(1) }
                clean in householdUnits -> { unitGrams = householdUnits.getValue(clean); tokens = tokens.drop(1) }
            }
        }
        val nameTokens = tokens.filter { it !in fillers && it !in numberWords }
        if (nameTokens.isEmpty()) return null
        val raw = nameTokens.joinToString(" ")
        val name = singular[raw] ?: raw.replaceFirstChar { it.uppercase() }
        val key = name.lowercase()

        val factor = weightFactor
        val perUnit = unitGrams
        return when {
            factor != null -> FoodPhrase(name, ((count ?: 1f) * factor).coerceIn(1f, 3000f), explicit = true, meal = meal)
            perUnit != null && perUnit > 0f -> FoodPhrase(name, (count ?: 1f) * perUnit, count = count ?: 1f, meal = meal)
            count == null -> FoodPhrase(name, usualPortion[key] ?: pieceGrams[key] ?: 100f, meal = meal)
            else -> FoodPhrase(name, count!! * (pieceGrams[key] ?: 100f), count = count, meal = meal)
        }
    }
}
