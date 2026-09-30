package app.tenet.android.core.common

import kotlin.math.roundToInt

/**
 * Builds the calisthenics routine from the first-run setup: skill work for
 * the chosen focus skills at the step the user stands on, then basic
 * strength with targets at ~70 % of the tested max (easier variants when a
 * max is 0). Pure, unit tested.
 */
object CaliPlanBuilder {

    /** Max test answered in the setup. */
    data class Maxes(
        val pushUps: Int,
        val pullUps: Int,
        val dips: Int,
        val squats: Int,
        /** Plank in seconds. */
        val plankSec: Int,
    )

    /** One step of a skill tree (mirror of the SkillStep row). */
    data class Step(
        val id: String,
        val skillId: String,
        val sortOrder: Int,
        val exerciseId: String,
        val sets: Int,
        /** Seconds for holds, reps otherwise. */
        val target: Int,
    )

    data class Input(
        val maxes: Maxes,
        /** skillId → id of the step the user currently trains. */
        val currentSteps: Map<String, String>,
        /** Skills to practise in every session. */
        val focusSkills: List<String>,
        val steps: List<Step>,
    )

    data class PlannedExercise(val exerciseId: String, val sets: Int, val target: Int, val restSec: Int)

    data class Plan(
        val routine: List<PlannedExercise>,
        /** skillId → current step id, for every skill with a known step. */
        val progress: Map<String, String>,
    )

    /** Working target: 70 % of the max, at least [min]. */
    fun target(max: Int, min: Int = 3, cap: Int = 30): Int = (max * 0.7f).roundToInt().coerceIn(min, cap)

    fun build(input: Input): Plan {
        val m = input.maxes
        val skill = input.focusSkills.mapNotNull { skillId ->
            val stepId = input.currentSteps[skillId]
            val step = input.steps.firstOrNull { it.id == stepId }
                ?: input.steps.filter { it.skillId == skillId }.minByOrNull { it.sortOrder }
            step?.let { PlannedExercise(it.exerciseId, it.sets, it.target, restSec = 150) }
        }
        val strength = buildList {
            add(
                if (m.pullUps >= 1) PlannedExercise("ex-mu-pullup", setsFor(m.pullUps), target(m.pullUps, min = 2, cap = 15), 180)
                else PlannedExercise("ex-cs-row", 3, 8, 120),
            )
            add(
                if (m.pushUps >= 5) PlannedExercise("ex-oap-pushup", setsFor(m.pushUps), target(m.pushUps, min = 5), 120)
                else PlannedExercise("ex-cs-knee-pushup", 3, 8, 90),
            )
            add(
                if (m.dips >= 1) PlannedExercise("ex-cs-dips", setsFor(m.dips), target(m.dips, min = 2, cap = 20), 120)
                else PlannedExercise("ex-cs-bench-dips", 3, 10, 90),
            )
            add(PlannedExercise("ex-ps-squat", setsFor(m.squats), target(m.squats, min = 8), 90))
            add(PlannedExercise("ex-cs-hollow", 3, (m.plankSec * 0.4f).roundToInt().coerceIn(15, 60), 60))
        }
        // A skill step can be the same exercise as a basic (e.g. pull-ups): keep the skill entry.
        val routine = skill + strength.filter { s -> skill.none { it.exerciseId == s.exerciseId } }
        val progress = input.currentSteps.filterValues { id -> input.steps.any { it.id == id } }
        return Plan(routine, progress)
    }

    private fun setsFor(max: Int) = if (max >= 10) 4 else 3
}
