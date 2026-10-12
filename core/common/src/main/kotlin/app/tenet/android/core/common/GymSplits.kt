package app.tenet.android.core.common

import app.tenet.android.core.common.GymPlanBuilder.Split

/**
 * Which split a gym plan follows and which muscle groups train together: the
 * ready-made combinations per split length and an own one, put together from
 * muscle groups per unit. Shared by the setup, the plan builder and the AI coach.
 */
object GymSplits {

    /** A muscle group to plan with; [catalog] = the catalogue's muscle names it covers. */
    enum class Muscle(val label: String, val catalog: Set<String>) {
        CHEST("Brust", setOf("Brust")),
        BACK("Rücken", setOf("Rücken")),
        SHOULDERS("Schultern", setOf("Schulter")),
        BICEPS("Bizeps", setOf("Bizeps", "Unterarme")),
        TRICEPS("Trizeps", setOf("Trizeps")),
        LEGS("Beine", setOf("Beine", "Beinbeuger", "Gesäß", "Waden")),
        CORE("Bauch", setOf("Core")),
    }

    /** One workout of the rotation. */
    data class SplitUnit(val title: String, val muscles: List<Muscle>)

    /** [builtin]: the builder's hand-made routines for this combination, when there are any. */
    data class Combo(val units: List<SplitUnit>, val builtin: Split? = null) {
        val label: String get() = units.joinToString(" / ") { it.title }
    }

    data class Size(val label: String, val unitCount: Int, val combos: List<Combo>)

    private val UPPER = listOf(Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS)
    private val LOWER = listOf(Muscle.LEGS, Muscle.CORE)
    private val PUSH = listOf(Muscle.CHEST, Muscle.SHOULDERS, Muscle.TRICEPS)
    private val PULL = listOf(Muscle.BACK, Muscle.BICEPS)
    private val ARMS = listOf(Muscle.BICEPS, Muscle.TRICEPS)

    private fun unit(vararg muscles: Muscle) = SplitUnit(title(muscles.toList()), muscles.toList())

    val sizes: List<Size> = listOf(
        Size(
            "Ganzkörper", 2,
            listOf(Combo(listOf(SplitUnit("Ganzkörper A", Muscle.entries), SplitUnit("Ganzkörper B", Muscle.entries)), Split.FULL_BODY)),
        ),
        Size(
            "2er-Split", 2,
            listOf(
                Combo(listOf(SplitUnit("Oberkörper", UPPER), SplitUnit("Unterkörper", LOWER)), Split.UPPER_LOWER),
                Combo(listOf(SplitUnit("Push + Beine", PUSH + Muscle.LEGS), SplitUnit("Pull + Bauch", PULL + Muscle.CORE))),
            ),
        ),
        Size(
            "3er-Split", 3,
            listOf(
                Combo(listOf(SplitUnit("Push", PUSH), SplitUnit("Pull", PULL), unit(Muscle.LEGS)), Split.PPL),
                Combo(listOf(unit(Muscle.CHEST, Muscle.BACK), unit(Muscle.LEGS), SplitUnit("Schultern + Arme", listOf(Muscle.SHOULDERS) + ARMS))),
                Combo(listOf(unit(Muscle.CHEST, Muscle.TRICEPS), unit(Muscle.BACK, Muscle.BICEPS), unit(Muscle.LEGS, Muscle.SHOULDERS))),
            ),
        ),
        Size(
            "4er-Split", 4,
            listOf(
                Combo(
                    listOf(unit(Muscle.CHEST, Muscle.TRICEPS), unit(Muscle.BACK, Muscle.BICEPS), unit(Muscle.LEGS), unit(Muscle.SHOULDERS, Muscle.CORE)),
                    Split.FOUR_DAY,
                ),
                Combo(listOf(unit(Muscle.CHEST, Muscle.SHOULDERS), unit(Muscle.BACK), unit(Muscle.LEGS), SplitUnit("Arme", ARMS))),
                Combo(listOf(unit(Muscle.CHEST, Muscle.BICEPS), unit(Muscle.BACK, Muscle.TRICEPS), unit(Muscle.LEGS), unit(Muscle.SHOULDERS))),
                Combo(
                    listOf(
                        SplitUnit("Oberkörper Kraft", UPPER), SplitUnit("Unterkörper Kraft", LOWER),
                        SplitUnit("Oberkörper Volumen", UPPER), SplitUnit("Unterkörper Volumen", LOWER),
                    ),
                ),
            ),
        ),
        Size(
            "5er-Split", 5,
            listOf(
                Combo(listOf(unit(Muscle.CHEST), unit(Muscle.BACK), unit(Muscle.LEGS), unit(Muscle.SHOULDERS), SplitUnit("Arme", ARMS))),
                Combo(listOf(SplitUnit("Push", PUSH), SplitUnit("Pull", PULL), unit(Muscle.LEGS), SplitUnit("Oberkörper", UPPER), SplitUnit("Unterkörper", LOWER))),
            ),
        ),
    )

    /** "Brust + Trizeps"; biceps and triceps together read "Arme". */
    fun title(muscles: Collection<Muscle>): String {
        val sorted = muscles.sortedBy { it.ordinal }
        val arms = Muscle.BICEPS in sorted && Muscle.TRICEPS in sorted
        return sorted.mapNotNull { m ->
            when {
                arms && m == Muscle.BICEPS -> "Arme"
                arms && m == Muscle.TRICEPS -> null
                else -> m.label
            }
        }.joinToString(" + ")
    }

    /** Muscle groups a workout title names ("Push", "Brust + Arme"), for suggestions in the editor. */
    fun musclesOf(title: String): List<Muscle> {
        val t = title.lowercase()
        val found = mutableSetOf<Muscle>()
        if ("ganzk" in t) found += Muscle.entries
        if ("oberk" in t) found += UPPER
        if ("unterk" in t) found += LOWER
        if ("push" in t) found += PUSH
        if ("pull" in t) found += PULL
        if ("arm" in t) found += ARMS
        if ("brust" in t) found += Muscle.CHEST
        if ("rück" in t || "rueck" in t) found += Muscle.BACK
        if ("schulter" in t) found += Muscle.SHOULDERS
        if ("bizeps" in t) found += Muscle.BICEPS
        if ("trizeps" in t) found += Muscle.TRICEPS
        if ("bein" in t) found += Muscle.LEGS
        if ("bauch" in t || "core" in t) found += Muscle.CORE
        return found.sortedBy { it.ordinal }
    }

    /**
     * What the user picked: split length ([size] = index in [sizes]), one of its
     * combinations – or [own], the muscle groups of every unit chosen by hand.
     */
    data class Choice(val size: Int = 2, val combo: Int = 0, val own: List<Set<Muscle>>? = null) {
        private val of: Size get() = sizes[size.coerceIn(0, sizes.lastIndex)]
        val sizeLabel: String get() = of.label
        val preset: Combo? get() = if (own != null) null else of.combos[combo.coerceIn(0, of.combos.lastIndex)]
        val units: List<SplitUnit>
            get() = preset?.units ?: own.orEmpty().mapIndexed { i, m ->
                SplitUnit(title(m).ifEmpty { "Einheit ${i + 1}" }, m.sortedBy { it.ordinal })
            }
        val builtin: Split? get() = preset?.builtin
        val label: String get() = units.joinToString(" / ") { it.title }

        /** An own combination needs muscle groups in every unit. */
        val valid: Boolean get() = own?.all { it.isNotEmpty() } ?: true

        /** Starts an own combination from the preset shown so far. */
        fun toOwn(): Choice = copy(own = own ?: units.map { it.muscles.toSet() })

        fun withSize(index: Int): Choice {
            val next = sizes[index.coerceIn(0, sizes.lastIndex)]
            return Choice(index, 0, own?.let { o -> List(next.unitCount) { o.getOrNull(it) ?: emptySet() } })
        }

        fun encode(): String = "$size;$combo;" + (own?.joinToString("|") { u -> u.joinToString(",") { it.name } } ?: "-")

        companion object {
            /** Ganzkörper up to three days, upper/lower for four, push/pull/legs above. */
            fun recommended(days: Int): Choice = when {
                days <= 3 -> Choice(0)
                days == 4 -> Choice(1)
                else -> Choice(2)
            }

            fun decode(text: String): Choice {
                val parts = text.split(';')
                val own = parts.getOrNull(2)?.takeIf { it != "-" }?.split('|')?.map { u ->
                    u.split(',').mapNotNull { n -> Muscle.entries.firstOrNull { it.name == n } }.toSet()
                }
                return Choice(parts.getOrNull(0)?.toIntOrNull() ?: 2, parts.getOrNull(1)?.toIntOrNull() ?: 0, own)
            }
        }
    }
}
