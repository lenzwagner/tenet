package app.tenet.android.core.common

/**
 * Double progression for bodyweight exercises: add a rep (or 5 s) once every
 * target set was hit; past the cap the next harder variation is due.
 */
object CaliProgression {

    enum class Kind { FIRST, REPEAT, UP, HARDER }

    data class Suggestion(val kind: Kind, val target: Int)

    const val REPS_CAP = 15
    const val HOLD_CAP = 60

    fun next(target: Int, sets: Int, lastSession: List<Int>, hold: Boolean): Suggestion {
        if (lastSession.isEmpty()) return Suggestion(Kind.FIRST, target)
        val hit = lastSession.count { it >= target } >= sets.coerceAtLeast(1)
        if (!hit) return Suggestion(Kind.REPEAT, target)
        val step = if (hold) 5 else 1
        val cap = if (hold) HOLD_CAP else REPS_CAP
        return if (target + step > cap) Suggestion(Kind.HARDER, target) else Suggestion(Kind.UP, target + step)
    }

    fun label(s: Suggestion, hold: Boolean): String {
        val unit = if (hold) "s" else "Wdh"
        return when (s.kind) {
            Kind.FIRST -> "Erstes Training: Ziel ${s.target} $unit pro Satz"
            Kind.REPEAT -> "Ziel ${s.target} $unit halten, bis alle Sätze sitzen"
            Kind.UP -> "Alle Sätze geschafft: nächstes Mal ${s.target} $unit"
            Kind.HARDER -> "Obergrenze erreicht: Zeit für die schwerere Variante"
        }
    }
}
