package app.tenet.android.core.common

import java.time.LocalDate

/**
 * Pure calculation of the current diary streak: consecutive days with at
 * least one entry, counting today (or yesterday, if today has no entry yet)
 * backwards.
 */
object StreakCalculator {

    fun currentStreak(isoDates: List<String>, todayIso: String): Int {
        val days = isoDates
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        if (days.isEmpty()) return 0

        val today = runCatching { LocalDate.parse(todayIso) }.getOrNull() ?: return 0
        var cursor = if (today in days) today else today.minusDays(1)
        if (cursor !in days) return 0

        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /**
     * Consecutive calendar weeks (Monday-based) with at least one date,
     * counting the current week, or last week if this one has none yet
     * (training streak: "Wochen in Folge").
     */
    fun currentWeekStreak(isoDates: List<String>, todayIso: String): Int {
        val weeks = isoDates
            .mapNotNull { runCatching { WeekMath.weekStart(LocalDate.parse(it)) }.getOrNull() }
            .toSet()
        if (weeks.isEmpty()) return 0
        val today = runCatching { LocalDate.parse(todayIso) }.getOrNull() ?: return 0
        var cursor = WeekMath.weekStart(today)
        if (cursor !in weeks) cursor = cursor.minusWeeks(1)
        var streak = 0
        while (cursor in weeks) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }
}
