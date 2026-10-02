package app.tenet.android.core.common

import app.tenet.android.core.common.RunPlanMath.RunGoal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanMathTest {

    // ---- Race prediction ----

    @Test
    fun riegel_5kTo10k() {
        // 25:00 5 km → ~52:07 10 km
        assertEquals(3127.0, RacePrediction.riegel(5_000, 1500, 10_000).toDouble(), 3.0)
    }

    @Test
    fun predict_prefersLongEffortsForMarathon() {
        val today = LocalDate.of(2026, 9, 24)
        val efforts = listOf(
            RacePrediction.Effort(today.minusDays(3), 1_000, 210), // fast km
            RacePrediction.Effort(today.minusDays(10), 10_000, 2_700),
        )
        val p = RacePrediction.predict(efforts, 42_195, today)!!
        assertEquals(10_000, p.basis.distanceM)
    }

    @Test
    fun predict_ignoresOldEfforts() {
        val today = LocalDate.of(2026, 9, 24)
        assertNull(RacePrediction.predict(listOf(RacePrediction.Effort(today.minusDays(90), 5_000, 1500)), 10_000, today))
    }

    // ---- Taper ----

    @Test
    fun taper_reducesLastWeeksVolume() {
        val total = 16
        fun longRun(week: Int) = RunPlanMath.planWeek(RunGoal.MARATHON, week, 4, total, taper = true)
            .first { it.zone == RunZone.LONG }.targetDurationSec!!
        val peak = longRun(total - 4)
        assertTrue(longRun(total - 3) < peak)
        assertTrue(longRun(total - 2) < longRun(total - 3))
        assertTrue(longRun(total - 1) < longRun(total - 2))
    }

    @Test
    fun noTaper_keepsBuilding() {
        val total = 16
        val a = RunPlanMath.planWeek(RunGoal.MARATHON, total - 2, 4, total, taper = false).first { it.zone == RunZone.LONG }
        val b = RunPlanMath.planWeek(RunGoal.MARATHON, total - 2, 4, total, taper = true).first { it.zone == RunZone.LONG }
        assertTrue(a.targetDurationSec!! > b.targetDurationSec!!)
    }

    // ---- Overload ----

    private fun sets(vararg s: Pair<Float, Int>) = s.map { OverloadMath.WorkSet(it.first, it.second) }

    @Test
    fun overload_increasesWhenTargetHit() {
        val s = OverloadMath.suggest(listOf(sets(80f to 8, 80f to 8, 80f to 8)), 8, "Brust")
        assertEquals(OverloadMath.Decision.INCREASE, s.decision)
        assertEquals(82.5f, s.weightKg)
    }

    @Test
    fun overload_lowerBodyBiggerStep() {
        val s = OverloadMath.suggest(listOf(sets(100f to 8, 100f to 8)), 8, "Beine, Gesäß")
        assertEquals(105f, s.weightKg)
    }

    @Test
    fun overload_keepsAfterOneMiss_deloadsAfterTwo() {
        val miss = sets(80f to 8, 80f to 7)
        assertEquals(OverloadMath.Decision.KEEP, OverloadMath.suggest(listOf(miss), 8, "Brust").decision)
        val two = OverloadMath.suggest(listOf(miss, sets(80f to 6)), 8, "Brust")
        assertEquals(OverloadMath.Decision.DELOAD, two.decision)
        assertEquals(72.5f, two.weightKg)
    }

    @Test
    fun overload_firstTime() {
        assertEquals(OverloadMath.Decision.FIRST_TIME, OverloadMath.suggest(emptyList(), 8, "Brust").decision)
    }

    // ---- Gym stats ----

    @Test
    fun gymStats_volumeAndE1rm() {
        val d1 = LocalDate.of(2026, 9, 1)
        val d2 = LocalDate.of(2026, 9, 8)
        val rows = listOf(
            GymPlanStats.SetRow("a", d1, "Bankdrücken", 80f, 8),
            GymPlanStats.SetRow("a", d1, "Bankdrücken", 80f, 8),
            GymPlanStats.SetRow("b", d2, "Bankdrücken", 85f, 8),
        )
        val sessions = GymPlanStats.sessions(rows)
        assertEquals(1280f, sessions[0].volumeKg)
        val ex = GymPlanStats.exercises(rows).single()
        assertEquals(2, ex.points.size)
        assertTrue(ex.progressPercent!! > 0)
    }

    // ---- Progression ----

    private fun ms(d: LocalDate) = d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun progression_strengthE1rmPerDay() {
        val d1 = LocalDate.of(2026, 9, 1)
        val d2 = LocalDate.of(2026, 9, 8)
        val sets = listOf(
            ProgressionMath.SetRecord("Bank", "REPS", 80f, 8, null, ms(d1)),
            ProgressionMath.SetRecord("Bank", "REPS", 85f, 8, null, ms(d2)),
        )
        val s = ProgressionMath.strength(sets, zone = java.time.ZoneOffset.UTC).single()
        assertEquals(2, s.points.size)
        assertTrue(s.improvement!! > 0f)
        assertEquals(ProgressionMath.Unit.KG, s.unit)
    }

    @Test
    fun progression_caliHoldUsesSeconds() {
        val d = LocalDate.of(2026, 9, 1)
        val s = ProgressionMath.calisthenics(
            listOf(ProgressionMath.SetRecord("L-Sit", "HOLD", 0f, 0, 20, ms(d))),
            java.time.ZoneOffset.UTC,
        ).single()
        assertEquals(ProgressionMath.Unit.SECONDS, s.unit)
        assertEquals(20f, s.last)
    }

    @Test
    fun progression_runFormLowerIsBetter() {
        val runs = listOf(
            RunVolumeMath.Run(LocalDate.of(2026, 9, 1), 5_000f, 1_500),
            RunVolumeMath.Run(LocalDate.of(2026, 9, 15), 5_000f, 1_440),
            RunVolumeMath.Run(LocalDate.of(2026, 9, 16), 1_000f, 200), // too short for form
        )
        val all = ProgressionMath.running(runs, LocalDate.of(2026, 9, 20))
        val form = all.first { it.key == "form" }
        assertEquals(2, form.points.size)
        assertEquals(60f, form.improvement)
        // Week of 14 Sept is still running on 20 Sept → only two full weeks.
        assertEquals(2, all.first { it.key == "weekly" }.points.size)
    }

    @Test
    fun repRangeWaitsForTopEnd() {
        val rule = OverloadMath.Rule(repMax = 12, stepKg = 2f)
        val keep = OverloadMath.suggest(listOf(sets(40f to 10, 40f to 10)), 8, "Brust", rule)
        assertEquals(OverloadMath.Decision.KEEP, keep.decision)
        val up = OverloadMath.suggest(listOf(sets(40f to 12, 40f to 12)), 8, "Brust", rule)
        assertEquals(42f, up.weightKg)
    }

    @Test
    fun customDeload() {
        val miss = sets(100f to 5)
        val s = OverloadMath.suggest(listOf(miss, miss), 8, "Brust", OverloadMath.Rule(deloadPercent = 20))
        assertEquals(80f, s.weightKg)
    }
}

class OverloadGridTest {
    @org.junit.Test fun increaseKeepsOffGridWeights() {
        val s = OverloadMath.suggest(listOf(listOf(OverloadMath.WorkSet(62.5f, 8), OverloadMath.WorkSet(62.5f, 8))), 8, "Quadrizeps")
        org.junit.Assert.assertEquals(67.5f, s.weightKg)
    }
}
