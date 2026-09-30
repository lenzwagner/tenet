package app.tenet.android.core.common

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Fixed training weekdays of an undated plan, stored as ISO numbers
 * ("1,3,5" = Mo/Mi/Fr). An empty set means "no fixed days" (every day).
 */
object TrainingDays {

    fun parse(value: String?): Set<DayOfWeek> =
        value.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 } }
            .map { DayOfWeek.of(it) }.toSet()

    fun format(days: Set<DayOfWeek>): String? =
        days.takeIf { it.isNotEmpty() }?.map { it.value }?.sorted()?.joinToString(",")

    fun isTrainingDay(days: Set<DayOfWeek>, date: LocalDate): Boolean =
        days.isEmpty() || date.dayOfWeek in days

    /** First training day strictly after [date], or null without fixed days. */
    fun next(days: Set<DayOfWeek>, date: LocalDate): LocalDate? {
        if (days.isEmpty()) return null
        return (1L..7L).map { date.plusDays(it) }.first { it.dayOfWeek in days }
    }
}
