package app.tenet.android.core.common

/**
 * Guided training ("Trainingsmodus", notification): which set comes next
 * and what is planned for it. Order: exercise by exercise, set by set
 * (classic straight sets), skipping completed ones.
 */
object WorkoutGuide {

    data class GuideSet(
        val id: String,
        val sortOrder: Int,
        val warmup: Boolean,
        val weightKg: Float,
        val reps: Int,
        val durationSec: Int?,
        val completed: Boolean,
    )

    data class GuideBlock(
        val name: String,
        /** Hold/duration exercises are entered in seconds. */
        val timed: Boolean,
        val pattern: MovementPattern?,
        val primaryMuscles: String,
        val targetReps: Int?,
        val suggestedKg: Float?,
        /** Rest set in the routine (> 0 = chosen by the user), else the advisor's. */
        val routineRestSec: Int?,
        val sets: List<GuideSet>,
        /** Bodyweight exercise: no weight field unless extra load was entered. */
        val bodyweight: Boolean = false,
        /** Superset group: neighbouring blocks with the same group alternate set by set. */
        val superset: Int? = null,
    )

    data class Next(
        val blockIndex: Int,
        val set: GuideSet,
        val name: String,
        val timed: Boolean,
        /** 1-based number of the set within the exercise, and how many it has. */
        val number: Int,
        val count: Int,
        val plannedKg: Float,
        val plannedReps: Int,
        val plannedSec: Int?,
        /** Rest after this set (before the next one). */
        val restSec: Int,
        val advice: RestAdvisor.Advice,
        val bodyweight: Boolean = false,
    ) {
        /** First time with weights: nothing to suggest yet, the user picks the weight. */
        val weightUnknown: Boolean get() = !timed && !bodyweight && !set.warmup && plannedKg <= 0f

        /** Next set is the other exercise of a superset (short switch, no real rest). */
        val supersetSwitch: Boolean get() = restSec == SUPERSET_SWITCH_SEC

        /** "80 kg × 8", "× 12" (bodyweight), "? kg × 8" (weight still open), "45 s". */
        val plannedText: String
            get() = when {
                timed -> "${plannedSec ?: 0} s"
                plannedKg > 0f -> "${fmt(plannedKg)} kg × $plannedReps"
                weightUnknown -> "? kg × $plannedReps"
                else -> "× $plannedReps"
            }
    }

    /** Short switch between the exercises of a superset (rest comes after the round). */
    const val SUPERSET_SWITCH_SEC = 20

    /**
     * Order in which the sets are done: block by block, but neighbouring
     * blocks of one superset alternate (A1, B1, A2, B2 …).
     */
    fun sequence(blocks: List<GuideBlock>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < blocks.size) {
            val group = blocks[i].superset
            var end = i + 1
            if (group != null) while (end < blocks.size && blocks[end].superset == group) end++
            val run = (i until end).toList()
            val rounds = run.maxOf { blocks[it].sets.size }
            for (k in 0 until rounds) for (b in run) if (k < blocks[b].sets.size) out += b to k
            i = end
        }
        return out
    }

    fun next(blocks: List<GuideBlock>): Next? {
        val seq = sequence(blocks)
        val pos = seq.indexOfFirst { (b, k) -> !blocks[b].sets.sortedBy { it.sortOrder }[k].completed }
        if (pos < 0) return null
        val (bi, k) = seq[pos]
        val ordered = blocks[bi].sets.sortedBy { it.sortOrder }
        val described = describe(bi, blocks[bi], ordered, k)
        return if (switchesWithinSuperset(blocks, seq, pos)) described.copy(restSec = SUPERSET_SWITCH_SEC) else described
    }

    /**
     * Rest after a set that was just done with [reps]: a short switch inside
     * a superset round, else the routine's own value or the advised one.
     */
    fun restAfter(blocks: List<GuideBlock>, blockIndex: Int, setId: String, reps: Int, rpe: Float? = null): Int {
        val block = blocks.getOrNull(blockIndex) ?: return RestAdvisor.advise(null, "", reps).seconds
        val ordered = block.sets.sortedBy { it.sortOrder }
        val k = ordered.indexOfFirst { it.id == setId }.coerceAtLeast(0)
        val set = ordered.getOrNull(k)
        val seq = sequence(blocks)
        val pos = seq.indexOf(blockIndex to k)
        if (pos >= 0 && switchesWithinSuperset(blocks, seq, pos)) return SUPERSET_SWITCH_SEC
        val warmup = set?.warmup == true
        block.routineRestSec?.takeIf { it > 0 && !warmup }?.let { return it }
        val r = if (block.timed) 0 else reps.takeIf { it > 0 } ?: block.targetReps ?: 8
        return RestAdvisor.advise(block.pattern, block.primaryMuscles, r, warmup, rpe).seconds
    }

    /** The set after [pos] is another exercise of the same superset (same round). */
    private fun switchesWithinSuperset(blocks: List<GuideBlock>, seq: List<Pair<Int, Int>>, pos: Int): Boolean {
        val (b, k) = seq[pos]
        val group = blocks[b].superset ?: return false
        val following = seq.getOrNull(pos + 1) ?: return false
        return following.first != b && blocks[following.first].superset == group && following.second == k
    }

    /** Sets still open in the whole workout (for "noch 7 Sätze"). */
    fun remaining(blocks: List<GuideBlock>): Int = blocks.sumOf { b -> b.sets.count { !it.completed } }

    fun describe(blockIndex: Int, block: GuideBlock, ordered: List<GuideSet>, index: Int): Next {
        val set = ordered[index]
        val previousDone = ordered.take(index).lastOrNull { it.completed && !it.warmup }
        val kg = when {
            set.weightKg > 0f -> set.weightKg
            set.warmup -> 0f
            else -> previousDone?.weightKg ?: block.suggestedKg ?: 0f
        }
        val reps = when {
            set.reps > 0 -> set.reps
            else -> previousDone?.reps?.takeIf { it > 0 } ?: block.targetReps ?: 8
        }
        val advice = RestAdvisor.advise(block.pattern, block.primaryMuscles, if (block.timed) 0 else reps, set.warmup)
        val rest = block.routineRestSec?.takeIf { it > 0 && !set.warmup } ?: advice.seconds
        return Next(
            blockIndex = blockIndex,
            set = set,
            name = block.name,
            timed = block.timed,
            number = index + 1,
            count = ordered.size,
            plannedKg = kg,
            plannedReps = reps,
            plannedSec = if (block.timed) set.durationSec ?: previousDone?.durationSec ?: 30 else null,
            restSec = rest,
            advice = advice,
            bodyweight = block.bodyweight,
        )
    }

    fun fmt(kg: Float): String = if (kg % 1f == 0f) kg.toInt().toString() else kg.toString().replace('.', ',')
}
