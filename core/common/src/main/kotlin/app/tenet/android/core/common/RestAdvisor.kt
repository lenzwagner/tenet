package app.tenet.android.core.common

import kotlin.math.roundToInt

/**
 * Ideal rest between sets, from the evidence summarized by NSCA/ACSM and
 * newer trials (Schoenfeld et al. 2016: 3 min beat 1 min for strength and
 * size on compound lifts; de Salles et al. 2009): heavy multi-joint lifts
 * need the longest rest, small isolation work the shortest.
 *
 * - Heavy compound, ≤ 5 reps: 3–4 min (squat/deadlift a bit longer)
 * - Compound, 6–12 reps: 2–3 min
 * - Isolation: 60–90 s, core/calves/grip about 60 s
 * - Warm-up sets: about 45 s
 * - Hard sets (RPE ≥ 9) get 30 s extra
 */
object RestAdvisor {

    enum class Kind(val label: String) {
        HEAVY_COMPOUND("schwere Grundübung"),
        COMPOUND("Grundübung"),
        ISOLATION("Isolationsübung"),
        SMALL("kleine Muskelgruppe"),
    }

    data class Advice(val seconds: Int, val minSec: Int, val maxSec: Int, val kind: Kind) {
        /** "2–3 min", "60–90 s". */
        val range: String
            get() = if (maxSec < 120) "$minSec–$maxSec s" else "${fmtMin(minSec)}–${fmtMin(maxSec)} min"

        private fun fmtMin(sec: Int): String {
            val m = sec / 60.0
            return if (m % 1.0 == 0.0) m.toInt().toString() else "%.1f".format(java.util.Locale.GERMAN, m)
        }
    }

    private val HEAVIEST = setOf(MovementPattern.SQUAT, MovementPattern.HINGE)
    private val COMPOUND = setOf(
        MovementPattern.HORIZONTAL_PUSH, MovementPattern.INCLINE_PUSH, MovementPattern.VERTICAL_PUSH,
        MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL, MovementPattern.SQUAT,
        MovementPattern.HINGE, MovementPattern.LUNGE,
    )
    private val SMALL = setOf(
        MovementPattern.CALF, MovementPattern.CORE_STABILITY, MovementPattern.CORE_FLEXION,
        MovementPattern.GRIP, MovementPattern.LATERAL_RAISE, MovementPattern.REAR_DELT,
    )

    /** Big muscle groups: without a pattern, an exercise hitting one of these counts as compound. */
    private val BIG_MUSCLES = listOf("brust", "rücken", "latissimus", "quadrizeps", "oberschenkel", "bein", "gesäß", "glute", "hamstring")
    private val SMALL_MUSCLES = listOf("bauch", "core", "rumpf", "wade", "unterarm", "griff")

    fun kind(pattern: MovementPattern?, primaryMuscles: String): Kind = when {
        pattern in HEAVIEST -> Kind.HEAVY_COMPOUND
        pattern in COMPOUND -> Kind.COMPOUND
        pattern in SMALL -> Kind.SMALL
        pattern != null -> Kind.ISOLATION
        else -> {
            val m = primaryMuscles.lowercase()
            when {
                BIG_MUSCLES.any { it in m } -> Kind.COMPOUND
                SMALL_MUSCLES.any { it in m } -> Kind.SMALL
                else -> Kind.ISOLATION
            }
        }
    }

    /**
     * @param reps planned (or done) repetitions of the set; 0 = unknown (hold/duration sets).
     */
    fun advise(
        pattern: MovementPattern?,
        primaryMuscles: String,
        reps: Int,
        warmup: Boolean = false,
        rpe: Float? = null,
    ): Advice {
        val kind = kind(pattern, primaryMuscles)
        if (warmup) return Advice(45, 30, 60, kind)
        val heavy = reps in 1..5
        val light = reps > 12
        val (min, max) = when (kind) {
            Kind.HEAVY_COMPOUND -> when {
                heavy -> 180 to 300
                light -> 120 to 180
                else -> 150 to 210
            }
            Kind.COMPOUND -> when {
                heavy -> 180 to 240
                light -> 90 to 150
                else -> 120 to 180
            }
            Kind.ISOLATION -> when {
                heavy -> 90 to 120
                light -> 45 to 75
                else -> 60 to 90
            }
            Kind.SMALL -> when {
                light -> 30 to 60
                else -> 45 to 75
            }
        }
        val hard = if (rpe != null && rpe >= 9f) 30 else 0
        // Middle of the range, on 15 s steps.
        val sec = (((min + max) / 2.0 + hard) / 15.0).roundToInt() * 15
        return Advice(sec, min, max + hard, kind)
    }
}

/**
 * Set values typed in a notification or quick field: "80x8", "80 × 8",
 * "80kg 8", "82,5 x 6" → weight and reps; a single number means reps
 * (weight stays as planned). For hold/duration exercises a single number
 * is seconds ("45", "45s", "1:30").
 */
object SetInputParser {
    data class Parsed(val weightKg: Float?, val reps: Int?, val seconds: Int?)

    private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
    private val CLOCK = Regex("""(\d{1,2}):(\d{2})""")

    fun parse(text: String, timed: Boolean = false): Parsed? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        if (timed) {
            CLOCK.find(t)?.let { m -> return Parsed(null, null, m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()) }
            val n = NUMBER.find(t)?.value?.replace(',', '.')?.toFloatOrNull() ?: return null
            return Parsed(null, null, if ("min" in t) (n * 60).roundToInt() else n.roundToInt())
        }
        val numbers = NUMBER.findAll(t).map { it.value.replace(',', '.') }.toList()
        return when (numbers.size) {
            0 -> null
            1 -> numbers[0].toFloatOrNull()?.let { Parsed(null, it.roundToInt(), null) }
            else -> {
                val a = numbers[0].toFloatOrNull() ?: return null
                val b = numbers[1].toFloatOrNull() ?: return null
                // "8 Wdh 80 kg" / "8 reps @ 80": reps first when the text says so.
                val repsFirst = Regex("""^\s*\d+\s*(wdh|wh|reps?|x\s*\d+\s*kg)""").containsMatchIn(t) || "@" in t
                if (repsFirst) Parsed(b, a.roundToInt(), null) else Parsed(a, b.roundToInt(), null)
            }
        }
    }
}
