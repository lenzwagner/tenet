package app.tenet.android.core.common

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMathTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun set(
        exercise: String = "Bankdrücken",
        muscles: String = "Brust",
        weight: Float = 80f,
        reps: Int = 8,
        day: LocalDate = LocalDate.of(2026, 9, 1),
    ) = ProgressMath.SetRecord(exercise, muscles, weight, reps, at(day))

    // ---- 1RM history -----------------------------------------------------

    @Test
    fun `one rep max history aggregates per day`() {
        val sets = listOf(
            set(weight = 80f, reps = 8, day = LocalDate.of(2026, 9, 1)),
            set(weight = 82.5f, reps = 6, day = LocalDate.of(2026, 9, 1)),
            set(weight = 85f, reps = 5, day = LocalDate.of(2026, 9, 8)),
        )
        val history = ProgressMath.oneRepMaxHistory(
            sets, "Bankdrücken", OneRepMaxFormula.EPLEY, zone,
        )
        assertEquals(2, history.size)
        assertEquals(LocalDate.of(2026, 9, 1), history[0].date)
        assertEquals(LocalDate.of(2026, 9, 8), history[1].date)
        // Day 1: max(epley(80,8)=101.3, epley(82.5,6)=99) = 101.33
        assertEquals(OneRepMax.epley(80f, 8), history[0].value, 0.01f)
        assertEquals(OneRepMax.epley(85f, 5), history[1].value, 0.01f)
    }

    @Test
    fun `history of another exercise is empty`() {
        val history = ProgressMath.oneRepMaxHistory(listOf(set()), "Kniebeugen", OneRepMaxFormula.EPLEY, zone)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `exercises with data are distinct and sorted`() {
        val sets = listOf(
            set(exercise = "Rudern"),
            set(exercise = "Bankdrücken"),
            set(exercise = "Rudern"),
        )
        assertEquals(listOf("Bankdrücken", "Rudern"), ProgressMath.exercisesWithData(sets))
    }

    // ---- Weekly volume ---------------------------------------------------

    @Test
    fun `weekly volume returns eight contiguous weeks ending this week`() {
        val reference = LocalDate.of(2026, 9, 23) // a Wednesday
        val thisWeek = reference.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1L)
        val sets = listOf(set(weight = 100f, reps = 10, day = thisWeek))
        val weeks = ProgressMath.weeklyVolume(sets, muscle = null, weeksBack = 7, reference = reference, zone = zone)

        assertEquals(8, weeks.size)
        assertEquals(thisWeek, weeks.last().weekStart)
        weeks.zipWithNext { a, b ->
            assertEquals(a.weekStart.plusWeeks(1), b.weekStart)
        }
        // Only the current week has volume: 100 × 10 = 1000 kg.
        assertEquals(1000f, weeks.last().volumeKg, 0.001f)
        assertEquals(0f, weeks.dropLast(1).sumOf { it.volumeKg.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `muscle filter only counts matching groups`() {
        val reference = LocalDate.of(2026, 9, 23)
        val thisWeek = reference.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1L)
        val sets = listOf(
            set(exercise = "Bankdrücken", muscles = "Brust, Trizeps", weight = 100f, reps = 10, day = thisWeek),
            set(exercise = "Rudern", muscles = "Rücken", weight = 50f, reps = 10, day = thisWeek),
        )
        val brust = ProgressMath.weeklyVolume(sets, muscle = "Brust", weeksBack = 7, reference = reference, zone = zone)
        val ruecken = ProgressMath.weeklyVolume(sets, muscle = "Rücken", weeksBack = 7, reference = reference, zone = zone)
        val alle = ProgressMath.weeklyVolume(sets, muscle = null, weeksBack = 7, reference = reference, zone = zone)

        assertEquals(1000f, brust.last().volumeKg, 0.001f)
        assertEquals(500f, ruecken.last().volumeKg, 0.001f)
        assertEquals(1500f, alle.last().volumeKg, 0.001f)
    }

    @Test
    fun `muscle groups are distinct and sorted`() {
        val sets = listOf(
            set(muscles = "Brust, Trizeps"),
            set(muscles = "Rücken"),
            set(exercise = "Rudern", muscles = "Brust"),
        )
        assertEquals(listOf("Brust", "Rücken", "Trizeps"), ProgressMath.muscleGroups(sets))
    }

    // ---- PR detection ----------------------------------------------------

    @Test
    fun `first record of a kind counts as a PR`() {
        val prs = ProgressMath.personalBests(listOf(set(weight = 80f, reps = 8)))
        val kinds = prs.map { it.kind }.toSet()
        assertTrue(ProgressMath.PersonalBest.Kind.WEIGHT in kinds)
        assertTrue(ProgressMath.PersonalBest.Kind.REPS in kinds)
        assertTrue(ProgressMath.PersonalBest.Kind.ONE_REP_MAX in kinds)
    }

    @Test
    fun `only improvements are reported as PRs`() {
        val day1 = LocalDate.of(2026, 9, 1)
        val day2 = LocalDate.of(2026, 9, 8)
        val sets = listOf(
            set(weight = 100f, reps = 5, day = day1), // best weight 100, reps 5
            set(weight = 90f, reps = 5, day = day2),   // nothing new
        )
        val prs = ProgressMath.personalBests(sets, OneRepMaxFormula.EPLEY)
        // Only the first session produces events; the second beats nothing.
        assertTrue(prs.all { it.at == at(day1) })
    }

    @Test
    fun `a heavier set later is a weight PR`() {
        val day1 = LocalDate.of(2026, 9, 1)
        val day2 = LocalDate.of(2026, 9, 8)
        val sets = listOf(
            set(weight = 80f, reps = 8, day = day1),
            set(weight = 85f, reps = 5, day = day2),
        )
        val prs = ProgressMath.personalBests(sets, OneRepMaxFormula.EPLEY)
        val weightPrs = prs.filter { it.kind == ProgressMath.PersonalBest.Kind.WEIGHT }
        assertEquals(2, weightPrs.size)
        val latest = weightPrs.first()
        assertEquals(85f, latest.value, 0.001f)
        assertEquals(at(day2), latest.at)
    }

    @Test
    fun `session volume record is detected once per improvement`() {
        val day1 = LocalDate.of(2026, 9, 1)
        val day2 = LocalDate.of(2026, 9, 8)
        val day3 = LocalDate.of(2026, 9, 15)
        val sets = listOf(
            set(weight = 100f, reps = 10, day = day1), // 1000 kg
            set(weight = 100f, reps = 10, day = day2), // 1000 kg → not better
            set(weight = 120f, reps = 10, day = day3), // 1200 kg → new record
        )
        val prs = ProgressMath.personalBests(sets)
            .filter { it.kind == ProgressMath.PersonalBest.Kind.VOLUME }
        assertEquals(2, prs.size)
        assertEquals(at(day1), prs[1].at)
        assertEquals(at(day3), prs[0].at)
        assertEquals(1200f, prs[0].value, 0.001f)
    }

    @Test
    fun `results are sorted newest first`() {
        val sets = listOf(
            set(weight = 80f, reps = 5, day = LocalDate.of(2026, 9, 1)),
            set(weight = 90f, reps = 6, day = LocalDate.of(2026, 9, 15)),
        )
        val prs = ProgressMath.personalBests(sets)
        val times = prs.map { it.at }
        assertEquals(times.sortedDescending(), times)
    }
}
