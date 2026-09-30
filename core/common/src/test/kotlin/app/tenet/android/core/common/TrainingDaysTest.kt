package app.tenet.android.core.common

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDaysTest {
    private val monWedFri = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    private val thursday = LocalDate.of(2026, 9, 24)

    @Test
    fun `round trip`() {
        assertEquals("1,3,5", TrainingDays.format(monWedFri))
        assertEquals(monWedFri, TrainingDays.parse("5,1,3"))
        assertNull(TrainingDays.format(emptySet()))
        assertTrue(TrainingDays.parse(null).isEmpty())
        assertTrue(TrainingDays.parse("0,9,x").isEmpty())
    }

    @Test
    fun `training day check`() {
        assertFalse(TrainingDays.isTrainingDay(monWedFri, thursday))
        assertTrue(TrainingDays.isTrainingDay(monWedFri, thursday.plusDays(1)))
        assertTrue(TrainingDays.isTrainingDay(emptySet(), thursday))
    }

    @Test
    fun `next training day`() {
        assertEquals(LocalDate.of(2026, 9, 25), TrainingDays.next(monWedFri, thursday))
        assertEquals(LocalDate.of(2026, 9, 28), TrainingDays.next(monWedFri, LocalDate.of(2026, 9, 25)))
        assertEquals(thursday.plusDays(7), TrainingDays.next(setOf(DayOfWeek.THURSDAY), thursday))
        assertNull(TrainingDays.next(emptySet(), thursday))
    }
}
