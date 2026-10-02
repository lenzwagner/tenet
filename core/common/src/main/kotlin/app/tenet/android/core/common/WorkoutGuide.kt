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
        /** "80 kg × 8", "× 12" (bodyweight), "45 s". */
        val plannedText: String
            get() = when {
                timed -> "${plannedSec ?: 0} s"
                plannedKg > 0f -> "${fmt(plannedKg)} kg × $plannedReps"
                else -> "× $plannedReps"
            }
    }

    fun next(blocks: List<GuideBlock>): Next? {
        blocks.forEachIndexed { bi, block ->
            val ordered = block.sets.sortedBy { it.sortOrder }
            val index = ordered.indexOfFirst { !it.completed }
            if (index >= 0) return describe(bi, block, ordered, index)
        }
        return null
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
