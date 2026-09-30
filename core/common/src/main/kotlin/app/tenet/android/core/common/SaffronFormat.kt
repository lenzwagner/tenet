package app.tenet.android.core.common

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One cooking step: text, optional timer and the ingredients it uses. */
data class CookStep(val text: String, val minutes: Int = 0, val ingredients: List<String> = emptyList())

/**
 * Recipes saved by the Saffron app (Firestore users/{uid}/recipes). Lists
 * are stored there as JSON strings: ingredients and tags as string arrays,
 * steps as objects {text, timeMinutes, stepIngredients} (older recipes: plain
 * strings).
 */
object SaffronFormat {

    fun strings(json: String?): List<String> =
        (runCatching { MiniJson.parse(json.orEmpty()) }.getOrNull() as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()

    fun steps(json: String?): List<CookStep> =
        (runCatching { MiniJson.parse(json.orEmpty()) }.getOrNull() as? List<*>)?.mapNotNull { item ->
            when (item) {
                is String -> item.trim().takeIf { it.isNotEmpty() }?.let { CookStep(it, RecipeMath.stepTimerSeconds(it)?.div(60) ?: 0) }
                is Map<*, *> -> {
                    val text = (item["text"] as? String)?.trim().orEmpty()
                    if (text.isEmpty()) null
                    else CookStep(
                        text = text,
                        minutes = (item["timeMinutes"] as? Number)?.toInt() ?: 0,
                        ingredients = (item["stepIngredients"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                    )
                }
                else -> null
            }
        }.orEmpty()

    /** JSON string array as Saffron stores ingredients and tags. */
    fun stringsJson(items: List<String>): String = items.joinToString(",", "[", "]") { quote(it) }

    /** Steps as Saffron objects {text, timeMinutes, stepIngredients}. */
    fun stepsJson(steps: List<CookStep>): String = steps.joinToString(",", "[", "]") { st ->
        "{\"text\":${quote(st.text)},\"timeMinutes\":${st.minutes},\"stepIngredients\":${stringsJson(st.ingredients)}}"
    }

    private fun quote(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    /** Stable Tenet id for a Saffron document. */
    fun recipeId(docId: String) = "saffron-$docId"
}

/**
 * Scales the leading amount of a free-text ingredient ("200 g Mehl",
 * "1/2 TL Salz", "1,5 kg Kartoffeln", "2 Eier"). Lines without a leading
 * number ("Salz nach Geschmack") stay as they are.
 */
object IngredientScaler {
    private val LEADING = Regex("""^\s*(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)(\s*[-–]\s*(\d+(?:[.,]\d+)?))?""")
    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

    fun scale(line: String, factor: Double): String {
        if (abs(factor - 1.0) < 1e-6) return line
        FRACTIONS.entries.firstOrNull { line.trimStart().startsWith(it.key) }?.let { (ch, v) ->
            val rest = line.trimStart().drop(1)
            return format(v * factor) + rest
        }
        val m = LEADING.find(line) ?: return line
        val first = number(m.groupValues[1]) ?: return line
        val scaled = format(first * factor)
        val range = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { number(it) }?.let { "–" + format(it * factor) } ?: ""
        return scaled + range + line.substring(m.range.last + 1)
    }

    private fun number(s: String): Double? {
        val t = s.trim()
        if (' ' in t) { // mixed number "1 1/2"
            val (whole, frac) = t.split(Regex("\\s+"), limit = 2)
            return (whole.toDoubleOrNull() ?: return null) + (number(frac) ?: return null)
        }
        if ('/' in t) {
            val (a, b) = t.split('/')
            val d = b.toDoubleOrNull() ?: return null
            return if (d == 0.0) null else (a.toDoubleOrNull() ?: return null) / d
        }
        return t.replace(',', '.').toDoubleOrNull()
    }

    private fun format(v: Double): String = when {
        v >= 10 -> v.roundToInt().toString()
        abs(v - v.roundToInt()) < 0.05 -> v.roundToInt().toString()
        else -> String.format(Locale.GERMAN, "%.1f", v)
    }
}

/** Minimal JSON reader (objects, arrays, strings, numbers, booleans, null). */
object MiniJson {
    fun parse(text: String): Any? = Reader(text).run { val v = value(); skipWs(); v }

    private class Reader(private val s: String) {
        private var i = 0

        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Any? {
            skipWs()
            require(i < s.length) { "unexpected end" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("unexpected '$c'")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                val key = str()
                skipWs(); require(s[i] == ':'); i++
                out[key] = value()
                skipWs()
                when (s[i++]) { ',' -> continue; '}' -> return out; else -> error("bad object") }
            }
        }

        private fun arr(): List<Any?> {
            val out = mutableListOf<Any?>()
            i++
            skipWs()
            if (s[i] == ']') { i++; return out }
            while (true) {
                out += value()
                skipWs()
                when (s[i++]) { ',' -> continue; ']' -> return out; else -> error("bad array") }
            }
        }

        private fun str(): String {
            require(s[i] == '"')
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
