package app.tenet.android.core.common

import kotlin.math.ln

/**
 * Relevance score from SQLite FTS4 `matchinfo(table, 'pcnx')`
 * (App_Konzept.md 6 "Globale Suche"). Layout: p = phrases, c = columns,
 * n = rows in the index, then per phrase and column three values:
 * hits in this row, hits in all rows, rows with a hit. Score is a small
 * TF-IDF: hits in this row × column weight × log(n / rows with hit).
 */
object FtsRank {

    fun score(info: IntArray, columnWeights: DoubleArray): Double {
        if (info.size < 3) return 0.0
        val phrases = info[0]
        val columns = info[1]
        val rows = info[2].coerceAtLeast(1)
        var score = 0.0
        for (p in 0 until phrases) {
            for (c in 0 until columns) {
                val base = 3 + 3 * (p * columns + c)
                if (base + 2 >= info.size) return score
                val hitsHere = info[base]
                val docsWithHit = info[base + 2].coerceAtLeast(1)
                if (hitsHere == 0) continue
                val idf = ln(1.0 + rows.toDouble() / docsWithHit)
                score += hitsHere * columnWeights.getOrElse(c) { 1.0 } * idf
            }
        }
        return score
    }

    /** matchinfo blob → ints (unsigned 32-bit, native = little endian on Android). */
    fun decode(blob: ByteArray): IntArray = IntArray(blob.size / 4) { i ->
        (blob[i * 4].toInt() and 0xFF) or
            ((blob[i * 4 + 1].toInt() and 0xFF) shl 8) or
            ((blob[i * 4 + 2].toInt() and 0xFF) shl 16) or
            ((blob[i * 4 + 3].toInt() and 0xFF) shl 24)
    }

    /**
     * User input → FTS4 MATCH expression: every word becomes a required
     * prefix term ("lauf mor" finds "Lauftreff morgen"). Quotes, dashes and
     * other FTS syntax are dropped. Null when nothing searchable is left.
     */
    fun matchExpression(query: String): String? {
        val words = Regex("[\\p{L}\\p{N}]+").findAll(query).map { it.value.lowercase() }.toList()
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }
}
