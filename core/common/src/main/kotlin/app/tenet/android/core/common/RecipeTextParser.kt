package app.tenet.android.core.common

import kotlin.math.roundToInt

/**
 * Turns a pasted recipe (website, chat, cookbook scan) into title,
 * servings, time, ingredients with estimated grams and steps
 * (App_Konzept.md 5.4 "Import: Text einfügen und parsen").
 *
 * Works with and without section headers ("Zutaten", "Zubereitung");
 * without headers, lines that start with an amount are ingredients.
 */
object RecipeTextParser {

    data class Ingredient(
        /** Food name without amount/unit, e.g. "Mehl". */
        val name: String,
        /** Estimated weight in g; null when the line has no amount ("Salz"). */
        val grams: Float?,
        /** True when grams come from a rough unit guess (EL, Stück, Dose …). */
        val estimated: Boolean,
        val original: String,
    )

    data class Parsed(
        val title: String,
        val servings: Int?,
        val minutes: Int?,
        val ingredients: List<Ingredient>,
        val steps: List<String>,
    )

    private enum class Section { NONE, INGREDIENTS, STEPS }

    private val ingredientHeader = Regex("^(zutaten|ingredients|einkaufsliste)\\b.*", RegexOption.IGNORE_CASE)
    private val stepHeader = Regex(
        "^(zubereitung|anleitung|schritte|so geht'?s|so wird'?s gemacht|instructions|method|directions)\\b.*",
        RegexOption.IGNORE_CASE,
    )
    private val bullet = Regex("^\\s*([-•*·▪◦]|\\d+[.)]|schritt\\s*\\d+[:.]?)\\s*", RegexOption.IGNORE_CASE)
    private val servingsRx = Regex("(?:für\\s*)?(\\d{1,2})\\s*(portionen|personen|stück|servings)|portionen\\s*[:=]?\\s*(\\d{1,2})", RegexOption.IGNORE_CASE)
    private val hoursRx = Regex("(\\d+(?:[.,]\\d+)?)\\s*(std\\.?|stunden?|h)\\b", RegexOption.IGNORE_CASE)
    private val minutesRx = Regex("(\\d{1,3})\\s*(min\\.?|minuten)\\b", RegexOption.IGNORE_CASE)
    private val timeLine = Regex("(zeit|dauer|zubereitungszeit|gesamtzeit|arbeitszeit|kochzeit)", RegexOption.IGNORE_CASE)

    /** Amount at the start: 200, 1,5, 1.5, 1/2, ½, 1 ½, 2-3. */
    private val amountRx = Regex(
        "^(\\d+\\s*[½¼¾]|[½¼¾]|\\d+\\s+\\d/\\d|\\d+/\\d|\\d+(?:[.,]\\d+)?(?:\\s*[-–]\\s*\\d+(?:[.,]\\d+)?)?)\\s*",
    )

    /** Unit → grams per unit (ml ≈ g). */
    private val units: List<Pair<Regex, Float>> = listOf(
        "kg" to 1000f, "g|gr|gramm" to 1f, "mg" to 0.001f,
        "l|liter" to 1000f, "ml" to 1f, "cl" to 10f, "dl" to 100f,
        "el|essl[öo]ffel|tbsp" to 15f, "tl|teel[öo]ffel|tsp" to 5f,
        "prise[n]?|msp\\.?|messerspitze[n]?" to 0.5f,
        "tasse[n]?|cup[s]?" to 240f, "becher" to 200f, "dose[n]?" to 400f,
        "p[ck]?k?\\.?|p[äa]ckchen|packung(en)?" to 10f, "bund" to 50f,
        "zehe[n]?" to 4f, "scheibe[n]?" to 25f, "handvoll|hand voll" to 30f,
        "st[üu]ck|stk\\.?" to 0f, // 0 = per-item guess below
    ).map { (pattern, grams) -> Regex("^($pattern)(?=\\s|$|\\.)\\s*", RegexOption.IGNORE_CASE) to grams }

    /** Rough weights for counted items. */
    private val itemWeights: List<Pair<Regex, Float>> = listOf(
        "ei(er)?\\b" to 60f, "zwiebel" to 100f, "knoblauch" to 4f, "tomate" to 100f, "kartoffel" to 150f,
        "karotte|möhre" to 80f, "paprika" to 160f, "zucchini" to 200f, "apfel|äpfel" to 150f, "banane" to 120f,
        "zitrone|limette" to 80f, "gurke" to 400f, "avocado" to 150f, "brötchen" to 60f, "tortilla|wrap" to 60f,
    ).map { (p, g) -> Regex(p, RegexOption.IGNORE_CASE) to g }

    fun parse(text: String): Parsed {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var title = ""
        var servings: Int? = null
        var minutes: Int? = null
        val ingredients = mutableListOf<Ingredient>()
        val steps = mutableListOf<String>()
        val hasHeaders = lines.any { ingredientHeader.matches(it) || stepHeader.matches(it) }
        var section = Section.NONE

        for (line in lines) {
            when {
                ingredientHeader.matches(line) -> {
                    section = Section.INGREDIENTS
                    servingsRx.find(line)?.let { servings = servings ?: servingsOf(it) }
                    continue
                }
                stepHeader.matches(line) -> {
                    section = Section.STEPS
                    continue
                }
            }
            // Meta lines (servings / time) anywhere before the steps.
            if (section != Section.STEPS && line.length < 60) {
                val s = servingsRx.find(line)
                if (s != null && amountRx.find(line)?.range?.first != 0) {
                    servings = servings ?: servingsOf(s)
                    if (title.isNotEmpty()) continue
                }
                if (timeLine.containsMatchIn(line) && (minutesRx.containsMatchIn(line) || hoursRx.containsMatchIn(line))) {
                    minutes = minutes ?: timeOf(line)
                    continue
                }
            }
            if (title.isEmpty() && section == Section.NONE && !startsWithAmount(line)) {
                title = line.removePrefix("#").trim()
                continue
            }
            val clean = line.replace(bullet, "").trim()
            if (clean.isEmpty()) continue
            val isIngredient = when (section) {
                Section.INGREDIENTS -> true
                Section.STEPS -> false
                Section.NONE -> !hasHeaders && (startsWithAmount(clean) || clean.length <= 30 && !clean.endsWith("."))
            }
            if (isIngredient) ingredients += ingredient(clean) else steps += clean
        }
        return Parsed(title.ifEmpty { "Importiertes Rezept" }, servings, minutes, ingredients, steps)
    }

    fun ingredient(line: String): Ingredient {
        var rest = line.trim()
        val amountMatch = amountRx.find(rest)
        val amount = amountMatch?.let { parseAmount(it.groupValues[1]) }
        if (amountMatch != null) rest = rest.substring(amountMatch.range.last + 1)
        var perUnit: Float? = null
        for ((rx, grams) in units) {
            val m = rx.find(rest) ?: continue
            perUnit = grams
            rest = rest.substring(m.range.last + 1)
            break
        }
        val name = rest.trim().trimStart(',', '.', ' ').substringBefore(",").substringBefore("(").trim()
            .ifEmpty { line.trim() }
        val grams: Float?
        val estimated: Boolean
        when {
            amount == null -> {
                grams = null
                estimated = false
            }
            perUnit != null && perUnit > 0f -> {
                grams = amount * perUnit
                estimated = perUnit != 1f && perUnit != 1000f
            }
            else -> {
                // Counted items ("2 Eier", "1 Stück Ingwer").
                val each = itemWeights.firstOrNull { it.first.containsMatchIn(name) }?.second ?: 100f
                grams = amount * each
                estimated = true
            }
        }
        return Ingredient(name = name, grams = grams?.let { (it * 10).roundToInt() / 10f }, estimated = estimated, original = line)
    }

    private fun startsWithAmount(line: String) = amountRx.find(line.replace(bullet, ""))?.range?.first == 0 &&
        line.replace(bullet, "").firstOrNull()?.let { it.isDigit() || it in "½¼¾" } == true

    private fun parseAmount(raw: String): Float? {
        val t = raw.trim().replace(',', '.')
        val fraction = mapOf('½' to 0.5f, '¼' to 0.25f, '¾' to 0.75f)
        return when {
            t.length == 1 && t[0] in fraction -> fraction[t[0]]
            t.any { it in fraction } -> t.filter { it.isDigit() }.toFloatOrNull()?.plus(fraction[t.last { it in fraction }] ?: 0f)
            t.contains('/') -> {
                val parts = t.split(Regex("\\s+"))
                val frac = parts.last().split('/')
                val f = frac[0].toFloat() / frac[1].toFloat()
                if (parts.size > 1) parts[0].toFloat() + f else f
            }
            // Range "2-3": take the middle.
            t.contains('-') || t.contains('–') -> t.split('-', '–').mapNotNull { it.trim().toFloatOrNull() }.average().toFloat()
            else -> t.toFloatOrNull()
        }
    }

    private fun servingsOf(m: MatchResult): Int? =
        (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull()?.takeIf { it in 1..24 }

    private fun timeOf(line: String): Int? {
        val h = hoursRx.find(line)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull() ?: 0f
        val m = minutesRx.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return ((h * 60).roundToInt() + m).takeIf { it > 0 }
    }
}
