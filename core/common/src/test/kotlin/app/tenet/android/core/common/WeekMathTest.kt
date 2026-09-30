package app.tenet.android.core.common

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekMathTest {

    // ---- weekStart ------------------------------------------------------

    @Test
    fun `monday stays the week start`() {
        val monday = LocalDate.of(2026, 9, 21)
        assertEquals(monday, WeekMath.weekStart(monday))
    }

    @Test
    fun `sunday belongs to the monday before it`() {
        val sunday = LocalDate.of(2026, 9, 27)
        assertEquals(LocalDate.of(2026, 9, 21), WeekMath.weekStart(sunday))
    }

    @Test
    fun `week of january first starts in the previous year`() {
        // 2026-01-01 is a Thursday; ISO week 1 of 2026 starts 2025-12-29.
        val jan1 = LocalDate.of(2026, 1, 1)
        assertEquals(LocalDate.of(2025, 12, 29), WeekMath.weekStart(jan1))
        assertEquals(1, WeekMath.weekOfYear(jan1))
    }

    @Test
    fun `late december can belong to week 53`() {
        val dec28 = LocalDate.of(2026, 12, 28) // Monday
        assertEquals(53, WeekMath.weekOfYear(dec28))
        // 2027-01-01 (Friday) still lies in KW 53 of 2026.
        assertEquals(53, WeekMath.weekOfYear(LocalDate.of(2027, 1, 1)))
    }

    // ---- weekDays -------------------------------------------------------

    @Test
    fun `week days are seven consecutive dates from monday to sunday`() {
        val days = WeekMath.weekDays(LocalDate.of(2026, 9, 23)) // Wednesday
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 21), days.first())
        assertEquals(LocalDate.of(2026, 9, 27), days.last())
        days.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
    }

    // ---- dayIndex -------------------------------------------------------

    @Test
    fun `monday is zero and sunday is six`() {
        val zone = ZoneId.of("Europe/Berlin")
        val monday = ZonedDateTime.of(2026, 9, 21, 10, 0, 0, 0, zone)
        val sunday = ZonedDateTime.of(2026, 9, 27, 23, 30, 0, 0, zone)
        assertEquals(0, WeekMath.dayIndex(monday.toInstant().toEpochMilli(), zone))
        assertEquals(6, WeekMath.dayIndex(sunday.toInstant().toEpochMilli(), zone))
    }

    @Test
    fun `day index respects the zone around midnight`() {
        // 2026-09-21T00:30 Berlin = 2026-09-20T22:30 UTC (Sunday UTC).
        val berlin = ZonedDateTime.of(2026, 9, 21, 0, 30, 0, 0, ZoneId.of("Europe/Berlin"))
        val epoch = berlin.toInstant().toEpochMilli()
        assertEquals(0, WeekMath.dayIndex(epoch, ZoneId.of("Europe/Berlin")))
        assertEquals(6, WeekMath.dayIndex(epoch, ZoneId.of("UTC")))
    }
}
