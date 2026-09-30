package app.tenet.android.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * ISO week helpers for the cross-discipline week calendar
 * (App_Konzept.md 5.2: "Wochenkalender aller Einheiten"). Weeks run
 * Monday..Sunday (ISO-8601), matching the German KW convention.
 */
object WeekMath {

    /** Monday of the week that contains [date]. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(WeekFields.ISO.dayOfWeek(), 1L)

    /** The seven dates of [date]'s week, Monday first. */
    fun weekDays(date: LocalDate): List<LocalDate> {
        val monday = weekStart(date)
        return (0L..6L).map(monday::plusDays)
    }

    /** ISO week number (1..53) of the week that contains [date]. */
    fun weekOfYear(date: LocalDate): Int =
        date.get(WeekFields.ISO.weekOfWeekBasedYear())

    /** Day index of [epochMillis] within its week: Monday = 0 ... Sunday = 6. */
    fun dayIndex(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.dayOfWeek.value - 1
    }
}
