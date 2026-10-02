package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestAdvisorTest {
    @Test fun heavySquatRestsLongest() {
        val squat = RestAdvisor.advise(MovementPattern.SQUAT, "Quadrizeps", reps = 5)
        val curl = RestAdvisor.advise(MovementPattern.ELBOW_FLEXION, "Bizeps", reps = 12)
        assertEquals(240, squat.seconds)
        assertEquals("3–5 min", squat.range)
        assertEquals(75, curl.seconds)
        assertTrue(squat.seconds > RestAdvisor.advise(MovementPattern.HORIZONTAL_PUSH, "Brust", 10).seconds)
    }

    @Test fun compoundHypertrophy() = assertEquals(150, RestAdvisor.advise(MovementPattern.HORIZONTAL_PUSH, "Brust", 8).seconds)

    @Test fun warmupAndHardSets() {
        assertEquals(45, RestAdvisor.advise(MovementPattern.SQUAT, "", 5, warmup = true).seconds)
        assertEquals(180, RestAdvisor.advise(MovementPattern.HORIZONTAL_PUSH, "Brust", 8, rpe = 9.5f).seconds)
    }

    @Test fun withoutPatternUsesMuscles() {
        assertEquals(RestAdvisor.Kind.COMPOUND, RestAdvisor.kind(null, "Rücken, Bizeps"))
        assertEquals(RestAdvisor.Kind.SMALL, RestAdvisor.kind(null, "Bauch"))
    }

    @Test fun parsesWeightTimesReps() {
        assertEquals(SetInputParser.Parsed(80f, 8, null), SetInputParser.parse("80x8"))
        assertEquals(SetInputParser.Parsed(82.5f, 6, null), SetInputParser.parse("82,5 × 6"))
        assertEquals(SetInputParser.Parsed(null, 10, null), SetInputParser.parse("10"))
        assertEquals(SetInputParser.Parsed(80f, 8, null), SetInputParser.parse("8 Wdh 80 kg"))
        assertEquals(SetInputParser.Parsed(null, null, 90), SetInputParser.parse("1:30", timed = true))
        assertNull(SetInputParser.parse("  "))
    }
}

class WorkoutGuideTest {
    private fun set(id: String, order: Int, kg: Float = 0f, reps: Int = 0, done: Boolean = false, warmup: Boolean = false) =
        WorkoutGuide.GuideSet(id, order, warmup, kg, reps, null, done)

    private val bench = WorkoutGuide.GuideBlock(
        "Bankdrücken", false, MovementPattern.HORIZONTAL_PUSH, "Brust", 8, 80f, null,
        listOf(set("a", 0, 80f, 8, done = true), set("b", 1), set("c", 2)),
    )
    private val curl = WorkoutGuide.GuideBlock(
        "Bizepscurl", false, MovementPattern.ELBOW_FLEXION, "Bizeps", 12, 14f, 0,
        listOf(set("d", 0), set("e", 1)),
    )

    @Test fun nextOpenSetWithPlannedValues() {
        val next = WorkoutGuide.next(listOf(bench, curl))!!
        org.junit.Assert.assertEquals("b", next.set.id)
        org.junit.Assert.assertEquals(2, next.number)
        org.junit.Assert.assertEquals("80 kg × 8", next.plannedText)
        org.junit.Assert.assertEquals(150, next.restSec)
    }

    @Test fun movesToNextExerciseAndEnds() {
        val benchDone = bench.copy(sets = bench.sets.map { it.copy(completed = true) })
        val next = WorkoutGuide.next(listOf(benchDone, curl))!!
        org.junit.Assert.assertEquals("Bizepscurl", next.name)
        org.junit.Assert.assertEquals("14 kg × 12", next.plannedText)
        val all = listOf(benchDone, curl.copy(sets = curl.sets.map { it.copy(completed = true) }))
        org.junit.Assert.assertNull(WorkoutGuide.next(all))
    }

    @Test fun routineRestWins() {
        val custom = curl.copy(routineRestSec = 100)
        org.junit.Assert.assertEquals(100, WorkoutGuide.next(listOf(custom))!!.restSec)
    }

    @Test fun supersetAlternates() {
        val a = curl.copy(name = "A", superset = 1, sets = listOf(set("a1", 0), set("a2", 1)))
        val b = curl.copy(name = "B", superset = 1, sets = listOf(set("b1", 0), set("b2", 1)))
        org.junit.Assert.assertEquals(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), WorkoutGuide.sequence(listOf(a, b)))
        val first = WorkoutGuide.next(listOf(a, b))!!
        org.junit.Assert.assertEquals("a1", first.set.id)
        org.junit.Assert.assertEquals(WorkoutGuide.SUPERSET_SWITCH_SEC, first.restSec)
        val aDone = a.copy(sets = listOf(set("a1", 0, done = true), set("a2", 1)))
        val second = WorkoutGuide.next(listOf(aDone, b))!!
        org.junit.Assert.assertEquals("b1", second.set.id)
        org.junit.Assert.assertTrue(second.restSec > WorkoutGuide.SUPERSET_SWITCH_SEC)
        org.junit.Assert.assertEquals(WorkoutGuide.SUPERSET_SWITCH_SEC, WorkoutGuide.restAfter(listOf(a, b), 0, "a1", 12))
    }
}
