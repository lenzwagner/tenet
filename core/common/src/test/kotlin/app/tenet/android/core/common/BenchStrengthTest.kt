package app.tenet.android.core.common

import app.tenet.android.core.common.BenchStrength.Variant.*
import app.tenet.android.core.common.GymPlanBuilder.Goal
import app.tenet.android.core.common.GymPlanBuilder.Level
import app.tenet.android.core.common.GymPlanBuilder.Split
import app.tenet.android.core.common.GymPlanBuilder.Lift
import app.tenet.android.core.common.GymPlanBuilder.LiftInput
import app.tenet.android.core.common.GymPlanBuilder.Input
import org.junit.Assert.*
import org.junit.Test

class BenchStrengthTest {
    private val now = 1_800_000_000_000L
    private val first = OverloadMath.suggest(emptyList(), 8, "Brust")

    @Test
    fun `published group means reproduce the 30 degree barbell ratio`() {
        // Rodriguez-Ridao 2020, Table 1: observed group means, not individual validation.
        val flat = BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, 63.3f, 1)!!
        assertEquals(81.4f, flat.oneRepMaxKg, 0.001f)
        val incline = BenchStrength.estimate(FLAT_BARBELL, INCLINE_BARBELL, 81.4f, 1)!!
        assertEquals(63.3f, incline.oneRepMaxKg, 0.001f)
    }

    @Test
    fun `realistic incline barbell set yields labelled flat bench starting load`() {
        val estimate = BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, 60f, 8)!!
        assertEquals(97.73144f, estimate.oneRepMaxKg, 0.001f)
        val suggestion = BenchStrength.applyToSuggestion(first, estimate, 8)
        assertEquals(72.5f, suggestion.weightKg, 0.001f)
        assertTrue(OverloadMath.label(suggestion).contains("30°"))
        assertNull(suggestion.lastWeightKg)
        assertEquals(OverloadMath.Decision.FIRST_TIME, suggestion.decision)
    }

    @Test
    fun `dumbbell weight is per hand not combined load`() {
        val estimate = BenchStrength.estimate(INCLINE_DUMBBELL, FLAT_BARBELL, 30f, 8)!!
        assertEquals(117.74872f, estimate.oneRepMaxKg, 0.001f)
        assertEquals(87.5f, BenchStrength.workingWeight(estimate, 8, OneRepMaxFormula.EPLEY), 0.001f)
        assertTrue(estimate.label.contains("je Hand"))
        // Saeterbakken: total dumbbell 1RM was 83% of barbell load. 41.5 kg per hand = 83 kg total.
        assertEquals(100f, BenchStrength.estimate(FLAT_DUMBBELL, FLAT_BARBELL, 41.5f, 1)!!.oneRepMaxKg, 0.001f)
    }

    @Test
    fun `own bench history beats even a much higher incline estimate`() {
        val own = OverloadMath.suggest(listOf(listOf(OverloadMath.WorkSet(70f, 8))), 8, "Brust")
        val estimate = BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, 100f, 10)
        assertEquals(own, BenchStrength.applyToSuggestion(own, estimate, 8, 200f))
    }

    @Test
    fun `cross exercise estimate beats generic setup weight only without own history`() {
        val estimate = BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, 60f, 8)
        assertEquals(72.5f, BenchStrength.applyToSuggestion(first, estimate, 8, 30f).weightKg, 0.001f)
        assertEquals(30f, BenchStrength.applyToSuggestion(first, null, 8, 30f).weightKg, 0.001f)
    }

    private fun performance(weight: Float = 60f, time: Long = now, session: String = "recent") =
        BenchStrength.Performance(INCLINE_BARBELL, weight, 8, session, time, completed = true, warmup = false)

    @Test
    fun `only completed recent non warmup low rep sets qualify`() {
        val invalid = listOf(
            performance(200f).copy(completed = false),
            performance(200f).copy(warmup = true),
            performance(200f).copy(reps = 20),
            performance(200f, now - 91L * 86_400_000),
            performance(200f, now + 1),
            performance(Float.NaN),
            performance(Float.POSITIVE_INFINITY),
        )
        assertNull(BenchStrength.fromHistory(FLAT_BARBELL, invalid, now))
        assertEquals(97.73144f, BenchStrength.fromHistory(FLAT_BARBELL, invalid + performance(), now)!!.oneRepMaxKg, 0.001f)
    }

    @Test
    fun `recent session wins over an older personal best`() {
        val sets = listOf(performance(100f, now - 86_400_000, "old"), performance())
        assertEquals(97.73144f, BenchStrength.fromHistory(FLAT_BARBELL, sets, now)!!.oneRepMaxKg, 0.001f)
    }

    @Test
    fun `machines and unrelated exercises never enter free weight conversion`() {
        assertNull(BenchStrength.variant("ex-schraeg-maschine"))
        assertNull(BenchStrength.variant("ex-smith-bank"))
        assertNull(BenchStrength.variant("ex-kh-schulter"))
        assertNull(BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, 60f, 11))
        assertNull(BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, 0f, 8))
    }

    @Test
    fun `chosen formula also controls inverse transfer load`() {
        val estimate = BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, 60f, 8, OneRepMaxFormula.BRZYCKI)!!
        assertEquals(95.78036f, estimate.oneRepMaxKg, 0.001f)
        assertEquals(70f, BenchStrength.workingWeight(estimate, 8, OneRepMaxFormula.BRZYCKI), 0.001f)
    }

    private fun input(lifts: Map<Lift, LiftInput>) =
        Input(Goal.HYPERTROPHY, Level.BEGINNER, Split.PPL, listOf(1, 3, 5), 80f, lifts = lifts)

    @Test
    fun `setup uses incline before bodyweight heuristic but own flat bench first`() {
        val fromIncline = GymPlanBuilder.build(input(mapOf(Lift.INCLINE_BENCH to LiftInput(60f, 8))))
        assertEquals(97.73144f, fromIncline.oneRepMax.getValue(Lift.BENCH), 0.001f)
        assertNotNull(fromIncline.benchEstimate)
        assertTrue(Lift.BENCH in fromIncline.estimated)
        val own = GymPlanBuilder.build(input(mapOf(Lift.INCLINE_BENCH to LiftInput(60f, 8), Lift.BENCH to LiftInput(80f, 5))))
        assertEquals(93.33333f, own.oneRepMax.getValue(Lift.BENCH), 0.001f)
        assertNull(own.benchEstimate)
        assertFalse(Lift.BENCH in own.estimated)
    }

    @Test
    fun `setup can estimate bench from dumbbells and respects formula selection`() {
        val fromDb = GymPlanBuilder.build(input(mapOf(Lift.INCLINE_DUMBBELL to LiftInput(30f, 8))))
        assertEquals(117.74872f, fromDb.oneRepMax.getValue(Lift.BENCH), 0.001f)
        val brzycki = GymPlanBuilder.build(input(mapOf(Lift.INCLINE_BENCH to LiftInput(60f, 8))).copy(formula = OneRepMaxFormula.BRZYCKI))
        assertEquals(95.78036f, brzycki.oneRepMax.getValue(Lift.BENCH), 0.001f)
    }

    @Test
    fun `five rep estimates compare with independent Reynolds bench regression`() {
        // Reynolds 2006 CP regression: 1.1307 * 80 + 0.6999 = 91.1559 kg.
        // This compares equations at a realistic 5RM load; it is not a measured individual 1RM.
        val publishedPrediction = 91.1559f
        assertEquals(publishedPrediction, OneRepMax.brzycki(80f, 5), 2.98f)
        assertEquals(publishedPrediction, OneRepMax.epley(80f, 5), 2.98f)
    }

    @Test
    fun `entered incline dumbbell load drives that accessory even with own flat bench`() {
        val plan = GymPlanBuilder.build(input(mapOf(
            Lift.BENCH to LiftInput(80f, 5),
            Lift.INCLINE_DUMBBELL to LiftInput(32f, 8),
        )))
        val incline = plan.routines.flatMap { it.exercises }.first { it.exerciseId == "ex-schraegbank" }
        assertEquals(27.5f, incline.startWeightKg!!, 0.001f)
        assertEquals(93.33333f, plan.oneRepMax.getValue(Lift.BENCH), 0.001f)
    }

    @Test
    fun `setup high rep incline is not used to infer flat bench`() {
        val plan = GymPlanBuilder.build(input(mapOf(Lift.INCLINE_BENCH to LiftInput(60f, 20))))
        assertNull(plan.benchEstimate)
        assertEquals(48f, plan.oneRepMax.getValue(Lift.BENCH), 0.001f)
    }

    @Test
    fun `inverse loads and rounding never exceed selected model target load`() {
        OneRepMaxFormula.entries.forEach { formula ->
            for (kg in 20..150 step 5) {
                val estimate = BenchStrength.estimate(INCLINE_BARBELL, FLAT_BARBELL, kg.toFloat(), 5, formula)!!
                for (reps in 1..10) {
                    val planned = BenchStrength.workingWeight(estimate, reps, formula)
                    val raw = OneRepMax.weightForReps(estimate.oneRepMaxKg, reps + 2, formula)
                    assertTrue(planned <= raw + 0.001f)
                    assertTrue(raw - planned < 2.501f)
                }
            }
        }
    }
}
