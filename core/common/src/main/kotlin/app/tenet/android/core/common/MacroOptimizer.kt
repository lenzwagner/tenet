package app.tenet.android.core.common

import kotlin.math.abs
import kotlin.math.max

/** Calories and macros (g); used as target and as nutrients per unit. */
data class Macros(
    val kcal: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
    operator fun minus(o: Macros) = Macros(kcal - o.kcal, protein - o.protein, carbs - o.carbs, fat - o.fat)
    operator fun times(k: Float) = Macros(kcal * k, protein * k, carbs * k, fat * k)
    fun atLeastZero() = Macros(max(kcal, 0f), max(protein, 0f), max(carbs, 0f), max(fat, 0f))
}

/**
 * Something the optimizer may put on the plan: a recipe portion or a usual
 * amount of a food. [perUnit] holds the nutrients of one unit; the plan
 * uses whole units from 0 to [maxUnits].
 */
data class MacroCandidate(
    val id: String,
    val label: String,
    /** e.g. "Portion", "150 g". */
    val unitLabel: String,
    val perUnit: Macros,
    val maxUnits: Int = 2,
)

data class MacroPlanItem(val candidate: MacroCandidate, val units: Int) {
    val macros: Macros get() = candidate.perUnit * units.toFloat()
}

data class MacroPlan(val items: List<MacroPlanItem>, val target: Macros) {
    val total: Macros = items.fold(Macros()) { acc, it -> acc + it.macros }
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Makro-Optimierer (App_Konzept.md 5.4, "Diet Problem"): picks whole
 * portions x_i ∈ {0 … max_i} of the candidates so that calories and macros
 * hit the target as closely as possible.
 *
 * Objective (minimised): Σ_m w_m · max(0, |Σ_i a_im·x_i − t_m| − tol_m) / t_m,
 * i.e. weighted relative deviation outside a tolerance corridor, plus a
 * small cost per distinct dish and per repeated portion (variety), a cost
 * when one dish brings more than [Weights.maxShare] of a big calorie target
 * (balance) and a cost for dishes in [avoid] (for "anderer Vorschlag").
 * At most [maxItems] different dishes.
 *
 * The instances are tiny (tens of candidates, a few units each), so instead
 * of a MILP solver (OR-Tools ships no Android ARM natives) a deterministic
 * steepest-descent local search with add / remove / swap moves runs from
 * several starts; it reaches the optimum or a very close plan in a few ms.
 */
object MacroOptimizer {

    data class Weights(
        val kcal: Float = 1.0f,
        val protein: Float = 1.2f,
        val carbs: Float = 0.6f,
        val fat: Float = 0.8f,
        /** Relative corridor without penalty: kcal ±5 %, macros ±10 %. */
        val kcalTolerance: Float = 0.05f,
        val macroTolerance: Float = 0.10f,
        /** Cost per distinct dish (prefers fewer, simpler plans). */
        val perItem: Float = 0.015f,
        /** Cost per extra portion of the same dish (variety). */
        val repeat: Float = 0.02f,
        /** Cost per portion of an avoided dish. */
        val avoid: Float = 1.0f,
        /**
         * Balance for big targets (a whole day or most of it): one dish should
         * not bring more than this share of the calories (no "2 × Pizza" days).
         */
        val maxShare: Float = 0.4f,
        val share: Float = 2.0f,
        /** Below this many kcal one dish may well cover everything (e.g. dinner). */
        val shareFromKcal: Float = 900f,
    )

    fun optimize(
        candidates: List<MacroCandidate>,
        target: Macros,
        maxItems: Int = 4,
        avoid: Set<String> = emptySet(),
        weights: Weights = Weights(),
    ): MacroPlan {
        val cands = candidates.filter { it.maxUnits > 0 && it.perUnit.kcal > 0f }
        if (cands.isEmpty() || target.kcal < 1f) return MacroPlan(emptyList(), target)
        val problem = Problem(cands, target, maxItems.coerceAtLeast(1), avoid, weights)

        var best = IntArray(cands.size)
        var bestCost = problem.cost(best)
        // Starts: empty plan and one unit of each candidate (covers different "anchors").
        val starts = buildList {
            add(IntArray(cands.size))
            cands.indices.forEach { i -> add(IntArray(cands.size).also { it[i] = 1 }) }
        }
        for (start in starts) {
            val x = problem.descend(start)
            val c = problem.cost(x)
            if (c < bestCost - 1e-9) {
                best = x
                bestCost = c
            }
        }
        val items = cands.indices.filter { best[it] > 0 }.map { MacroPlanItem(cands[it], best[it]) }
            .sortedByDescending { it.macros.kcal }
        return MacroPlan(items, target)
    }

    private class Problem(
        val cands: List<MacroCandidate>,
        val target: Macros,
        val maxItems: Int,
        avoid: Set<String>,
        val w: Weights,
    ) {
        private val n = cands.size
        private val a = Array(n) { floatArrayOf(cands[it].perUnit.kcal, cands[it].perUnit.protein, cands[it].perUnit.carbs, cands[it].perUnit.fat) }
        private val t = floatArrayOf(target.kcal, target.protein, target.carbs, target.fat)
        // Tiny targets (e.g. 3 g fat left) would blow relative errors up: floor the scale.
        private val scale = floatArrayOf(max(t[0], 150f), max(t[1], 10f), max(t[2], 15f), max(t[3], 8f))
        private val weight = floatArrayOf(w.kcal, w.protein, w.carbs, w.fat)
        private val tol = floatArrayOf(w.kcalTolerance, w.macroTolerance, w.macroTolerance, w.macroTolerance)
        private val avoided = BooleanArray(n) { cands[it].id in avoid }
        private val balanced = t[0] >= w.shareFromKcal

        fun cost(x: IntArray): Double {
            val sum = FloatArray(4)
            var items = 0
            var extra = 0.0
            for (i in 0 until n) {
                val u = x[i]
                if (u == 0) continue
                items++
                for (m in 0..3) sum[m] += a[i][m] * u
                extra += w.perItem + w.repeat * (u - 1)
                if (avoided[i]) extra += w.avoid * u
                if (balanced) extra += w.share * max(0f, a[i][0] * u / t[0] - w.maxShare)
            }
            var dev = 0.0
            for (m in 0..3) {
                val rel = abs(sum[m] - t[m]) / scale[m]
                dev += weight[m] * max(0f, rel - tol[m])
                // Small pull toward the exact value inside the corridor, so ties resolve sensibly.
                dev += weight[m] * 0.05 * rel
            }
            return dev + extra + if (items > maxItems) 10.0 * (items - maxItems) else 0.0
        }

        /** Steepest descent over add / remove / swap one unit until no move improves. */
        fun descend(start: IntArray): IntArray {
            val x = start.copyOf()
            var current = cost(x)
            repeat(200) {
                var bestCost = current
                var bestI = -1
                var bestJ = -1
                for (i in 0 until n) {
                    if (x[i] < cands[i].maxUnits) {
                        x[i]++
                        val c = cost(x)
                        if (c < bestCost - 1e-9) { bestCost = c; bestI = i; bestJ = -1 }
                        x[i]--
                    }
                    if (x[i] > 0) {
                        x[i]--
                        val c = cost(x)
                        if (c < bestCost - 1e-9) { bestCost = c; bestI = -1; bestJ = i }
                        // Swap: this unit for one of another candidate.
                        for (j in 0 until n) {
                            if (j == i || x[j] >= cands[j].maxUnits) continue
                            x[j]++
                            val s = cost(x)
                            if (s < bestCost - 1e-9) { bestCost = s; bestI = j; bestJ = i }
                            x[j]--
                        }
                        x[i]++
                    }
                }
                if (bestI < 0 && bestJ < 0) return x
                if (bestI >= 0) x[bestI]++
                if (bestJ >= 0) x[bestJ]--
                current = bestCost
            }
            return x
        }
    }
}
