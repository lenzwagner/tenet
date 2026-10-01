package app.tenet.android.core.common

/**
 * Structured social-media captions ("Ingredients … Method 1. 2. 3.") read
 * without AI: exact amounts and every step, where the small fallback model
 * tends to drop steps or invent times. Returns null when the caption has no
 * recognizable ingredients section (then the AI reads the free text).
 */
object CaptionRecipe {
    data class Parsed(
        val title: String,
        val ingredients: List<String>,
        val steps: List<String>,
        val servings: Int?,
        val minutes: Int?,
    )

    private val INGREDIENTS = Regex(
        """^(ingredients?|zutaten|ingredienti|ingrédients|ingredientes|you(?:'ll)? need|what you need|einkaufsliste)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val METHOD = Regex(
        """^(method|instructions?|directions?|steps?|preparation|how to make( it)?|how to|zubereitung|anleitung|so geht'?s|schritte|procedure)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val FOOTER = Regex("""^(#|follow|save this|comment|tag a|like for|folg|speicher)""", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("""^\s*(?:[-–—•*·▪️✔✅]+|\d{1,2}[.)](?=\s)|step\s*\d+\s*[:.)-]?|schritt\s*\d+\s*[:.)-]?)\s*""", RegexOption.IGNORE_CASE)
    private val SERVES = Regex("""(?:serves|servings?|portionen|personen)\s*:?\s*(\d{1,2})\b|(\d{1,2})\s*(?:servings|portionen|personen|people|persons)\b""", RegexOption.IGNORE_CASE)
    private val TOTAL = Regex("""(?:total time|gesamtzeit|zeit|ready in)\s*:?\s*(\d{1,3})\s*(?:min|minuten|minutes|mins)\b""", RegexOption.IGNORE_CASE)

    fun parse(caption: String): Parsed? {
        val lines = caption.lines().map { it.trim().trimStart('﻿') }
        val ingStart = lines.indexOfFirst { INGREDIENTS.matches(stripDecor(it)) }
        if (ingStart < 0) return null
        val methodStart = (ingStart + 1 until lines.size).firstOrNull { METHOD.matches(stripDecor(lines[it])) } ?: -1
        val ingEnd = if (methodStart > ingStart) methodStart else lines.size
        val ingredients = lines.subList(ingStart + 1, ingEnd)
            .takeWhile { !FOOTER.containsMatchIn(it) }
            .map(::clean).filter { it.isNotEmpty() }
        if (ingredients.size < 2) return null
        val steps = if (methodStart > ingStart) {
            lines.subList(methodStart + 1, lines.size)
                .takeWhile { !FOOTER.containsMatchIn(it) }
                .map(::clean).filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        val servings = servings(caption)
        val minutes = TOTAL.find(caption)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..1440 }
        val title = cleanTitle(lines.take(ingStart).firstOrNull { it.isNotBlank() && !FOOTER.containsMatchIn(it) }.orEmpty())
        return Parsed(title, ingredients, steps, servings, minutes)
    }

    /** "für 4 Personen", "serves 2" … anywhere in the text; null if not stated. */
    fun servings(text: String): Int? =
        SERVES.find(text)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] }.toIntOrNull() }?.takeIf { it in 1..24 }

    /** "WARM" → "Warm"; mixed case ("BBQ-Sauce", "Low Carb") stays. */
    fun cleanTag(tag: String): String {
        // "Küche: Deutsch" (the AI echoing the prompt's category) → "Deutsch".
        val t = tag.trim().removePrefix("#").substringAfter(':').trim()
        return if (t.length > 3 && t == t.uppercase() && t.any(Char::isLetter)) t.lowercase().replaceFirstChar { it.uppercase() } else t
    }

    /**
     * Social titles: "EP 14. MICHELIN ON A MINIMUM WAGE || Chicken Alfredo🍗" → "Chicken Alfredo".
     * Drops series prefixes before "|", emojis, hashtags, shouting caps.
     */
    fun cleanTitle(raw: String): String {
        var t = stripDecor(raw).replace(Regex("""#\S+"""), "").trim()
        if ('|' in t) t = t.split('|').map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: t
        t = t.trim(' ', '-', '–', ':', '.', '!').replace(Regex("""\s{2,}"""), " ")
        val letters = t.filter { it.isLetter() }
        if (letters.length > 4 && letters == letters.uppercase()) {
            t = t.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        return t
    }

    private fun stripDecor(s: String): String =
        s.replace(Regex("""[\p{So}\p{Sk}\p{Cs}️‍]"""), "").trim()

    private fun clean(line: String): String =
        stripDecor(line).replace(BULLET, "").trim()
}
