package app.tenet.android.core.common

import app.tenet.android.core.common.GymPlanBuilder.Goal
import app.tenet.android.core.common.GymPlanBuilder.Level
import app.tenet.android.core.common.GymPlanBuilder.Lift
import app.tenet.android.core.common.GymPlanBuilder.LiftInput
import app.tenet.android.core.common.GymPlanBuilder.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupBuilderTest {

    // ---- Gym ----

    @Test
    fun workingWeight_strengthFromOneRepMax() {
        // 100 kg 1RM, 5 reps with ~2 in reserve → 100 / (1 + 7/30) ≈ 81 → 80 kg
        assertEquals(80f, GymPlanBuilder.workingWeight(100f, 5))
    }

    @Test
    fun recommendedSplit_byDays() {
        assertEquals(Split.FULL_BODY, GymPlanBuilder.recommendedSplit(3))
        assertEquals(Split.UPPER_LOWER, GymPlanBuilder.recommendedSplit(4))
        assertEquals(Split.PPL, GymPlanBuilder.recommendedSplit(6))
    }

    @Test
    fun customFourRoutineSplit_withSixTrainingDays_keepsFourRoutineCycle() {
        val plan = GymPlanBuilder.build(
            GymPlanBuilder.Input(
                goal = Goal.HYPERTROPHY,
                level = Level.INTERMEDIATE,
                split = Split.CUSTOM,
                days = listOf(1, 2, 3, 4, 5, 6),
                bodyweightKg = 80f,
                customRoutineTitles = listOf("Brust", "Rücken", "Arme", "Beine"),
            ),
        )

        assertEquals(listOf("Brust", "Rücken", "Arme", "Beine"), plan.routines.map { it.title })
        assertTrue(plan.routines[0].exercises.any { it.exerciseId == "ex-bankdruecken" })
        assertTrue(plan.routines[1].exercises.any { it.exerciseId == "ex-rudern" })
        assertTrue(plan.routines[2].exercises.any { it.exerciseId == "ex-bizepscurl" })
        assertTrue(plan.routines[3].exercises.any { it.exerciseId == "ex-kniebeugen" })
    }

    @Test
    fun build_ppl_usesEnteredLiftsAndEstimatesTheRest() {
        val plan = GymPlanBuilder.build(
            GymPlanBuilder.Input(
                goal = Goal.HYPERTROPHY,
                level = Level.INTERMEDIATE,
                split = Split.PPL,
                days = listOf(1, 2, 3, 5, 6, 7),
                bodyweightKg = 80f,
                lifts = mapOf(Lift.BENCH to LiftInput(80f, 8)),
            ),
        )
        assertEquals(listOf("Push", "Pull", "Beine"), plan.routines.map { it.title })
        assertTrue(Lift.BENCH !in plan.estimated)
        assertTrue(Lift.SQUAT in plan.estimated)
        val bench = plan.routines[0].exercises.first { it.exerciseId == "ex-bankdruecken" }
        // 80×8 → 1RM ≈ 101 → 8 reps with reserve ≈ 76 → 75 kg
        assertEquals(75f, bench.startWeightKg)
        assertEquals(8, bench.reps)
        // Estimated intermediate pull-ups: about bodyweight, no extra weight yet.
        assertNull(plan.routines[1].exercises.first { it.exerciseId == "ex-klimmzuege" }.startWeightKg)
        // Push: dumbbell shoulder press, dips and lateral raises.
        val push = plan.routines[0].exercises.map { it.exerciseId }
        assertTrue("ex-kh-schulter" in push && "ex-g-dips" in push && "ex-seitheben" in push)
    }

    @Test
    fun build_weightedPullupsAndDips() {
        val plan = GymPlanBuilder.build(
            GymPlanBuilder.Input(
                goal = Goal.HYPERTROPHY,
                level = Level.ADVANCED,
                split = Split.UPPER_LOWER,
                days = listOf(1, 2, 4, 5),
                bodyweightKg = 80f,
                lifts = mapOf(
                    Lift.PULLUP to LiftInput(20f, 8),
                    Lift.DIPS to LiftInput(0f, 12),
                    Lift.OHP to LiftInput(24f, 10),
                ),
            ),
        )
        val upper = plan.routines[0].exercises.associateBy { it.exerciseId }
        // (80+20)×8 → 1RM ≈ 127 → 8 reps with reserve ≈ 95 total → +15 kg
        assertEquals(15f, upper.getValue("ex-klimmzuege").startWeightKg)
        // 80×12 → 1RM 112 → 8 reps ≈ 84 total → +5 kg
        assertEquals(5f, upper.getValue("ex-g-dips").startWeightKg)
        assertTrue(Lift.DIPS !in plan.estimated)
        // Beginner without bodyweight reps: lat pulldown and bench dips instead.
        val weak = GymPlanBuilder.build(GymPlanBuilder.Input(Goal.FITNESS, Level.BEGINNER, Split.FULL_BODY, listOf(1, 3, 5), 70f))
        val ids = weak.routines.flatMap { it.exercises }.map { it.exerciseId }
        assertTrue("ex-latzzug" in ids && "ex-g-bankdips" in ids && "ex-klimmzuege" !in ids)
        // Dumbbells per hand: 24×10 → 1RM 32 → 8 reps ≈ 24 → 25 kg (2,5er-Schritte)
        assertEquals(25f, upper.getValue("ex-kh-schulter").startWeightKg)
    }

    @Test
    fun build_beginnerPullsOnLatMachine() {
        val plan = GymPlanBuilder.build(
            GymPlanBuilder.Input(Goal.FITNESS, Level.BEGINNER, Split.FULL_BODY, listOf(1, 3, 5), 70f),
        )
        assertTrue(plan.routines.flatMap { it.exercises }.any { it.exerciseId == "ex-latzzug" })
        assertTrue(plan.routines.flatMap { it.exercises }.none { it.exerciseId == "ex-klimmzuege" })
    }

    // ---- Running ----

    @Test
    fun assignDays_longRunOnWeekend() {
        val template = RunPlanMath.weeklyTemplate(3)
        val assigned = RunPlanMath.assignDays(template, listOf(1, 3, 6)) // Di, Do, So
        assertEquals(6, assigned.first { it.second == RunZone.LONG }.first)
        assertEquals(listOf(1, 3), assigned.filter { it.second != RunZone.LONG }.map { it.first })
    }

    @Test
    fun assignDays_spreadsHardSessions() {
        // Di, Mi, Do, Sa, So – five runs; tempo and intervals must not touch.
        val assigned = RunPlanMath.assignDays(RunPlanMath.weeklyTemplate(5), listOf(1, 2, 3, 5, 6))
        val hard = assigned.filter { it.second in setOf(RunZone.TEMPO, RunZone.INTERVAL, RunZone.LONG) }.map { it.first }.toSet()
        val backToBack = hard.count { (it + 1) % 7 in hard }
        assertTrue("hard days $hard", backToBack <= 1)
        assertEquals(6, assigned.first { it.second == RunZone.LONG }.first)
    }

    @Test
    fun assignDays_wrongCountKeepsTemplate() {
        val template = RunPlanMath.weeklyTemplate(3)
        assertEquals(template, RunPlanMath.assignDays(template, listOf(0, 2)))
    }

    // ---- Calisthenics ----

    private val steps = listOf(
        CaliPlanBuilder.Step("st-fl-1", "sk-fl", 0, "ex-fl-tuck", 3, 10),
        CaliPlanBuilder.Step("st-fl-2", "sk-fl", 1, "ex-fl-adv", 3, 10),
        CaliPlanBuilder.Step("st-mu-1", "sk-mu", 0, "ex-mu-pullup", 3, 10),
    )

    @Test
    fun cali_easierVariantsForZeroMax() {
        val plan = CaliPlanBuilder.build(
            CaliPlanBuilder.Input(CaliPlanBuilder.Maxes(0, 0, 0, 15, 30), emptyMap(), emptyList(), steps),
        )
        val ids = plan.routine.map { it.exerciseId }
        assertTrue("ex-cs-row" in ids && "ex-cs-knee-pushup" in ids && "ex-cs-bench-dips" in ids)
    }

    @Test
    fun cali_skillStepFirstAndNoDuplicatePullUps() {
        val plan = CaliPlanBuilder.build(
            CaliPlanBuilder.Input(
                CaliPlanBuilder.Maxes(20, 10, 12, 30, 60),
                currentSteps = mapOf("sk-fl" to "st-fl-2"),
                focusSkills = listOf("sk-fl", "sk-mu"),
                steps = steps,
            ),
        )
        assertEquals("ex-fl-adv", plan.routine.first().exerciseId)
        assertEquals(1, plan.routine.count { it.exerciseId == "ex-mu-pullup" })
        assertEquals(mapOf("sk-fl" to "st-fl-2"), plan.progress)
        // 70 % of 20 push-ups
        assertEquals(14, plan.routine.first { it.exerciseId == "ex-oap-pushup" }.target)
    }

    // ---- Exercise alternatives ----

    @Test
    fun alternatives_samePatternFirstThenMuscle() {
        val c = { id: String, p: MovementPattern?, m: String -> ExerciseAlternatives.Candidate(id, p, m) }
        val bench = c("bench", MovementPattern.HORIZONTAL_PUSH, "Brust")
        val pool = listOf(
            bench,
            c("fly", MovementPattern.CHEST_FLY, "Brust"),
            c("dbbench", MovementPattern.HORIZONTAL_PUSH, "Brust"),
            c("squat", MovementPattern.SQUAT, "Beine"),
            c("press", MovementPattern.HORIZONTAL_PUSH, "Brust"),
        )
        assertEquals(listOf("dbbench", "press", "fly"), ExerciseAlternatives.rank(bench, pool))
    }
}
