package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    @Test
    fun `empty list has no streak`() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), "2026-09-22"))
    }

    @Test
    fun `entry today starts streak`() {
        assertEquals(1, StreakCalculator.currentStreak(listOf("2026-09-22"), "2026-09-22"))
    }

    @Test
    fun `consecutive days count`() {
        val dates = listOf("2026-09-22", "2026-09-21", "2026-09-20")
        assertEquals(3, StreakCalculator.currentStreak(dates, "2026-09-22"))
    }

    @Test
    fun `no entry today but yesterday keeps streak alive`() {
        val dates = listOf("2026-09-21", "2026-09-20")
        assertEquals(2, StreakCalculator.currentStreak(dates, "2026-09-22"))
    }

    @Test
    fun `gap breaks streak`() {
        val dates = listOf("2026-09-22", "2026-09-19")
        assertEquals(1, StreakCalculator.currentStreak(dates, "2026-09-22"))
    }

    @Test
    fun `two days without entry ends streak`() {
        val dates = listOf("2026-09-20")
        assertEquals(0, StreakCalculator.currentStreak(dates, "2026-09-22"))
    }

    @Test
    fun `duplicate dates do not inflate streak`() {
        val dates = listOf("2026-09-22", "2026-09-22", "2026-09-21")
        assertEquals(2, StreakCalculator.currentStreak(dates, "2026-09-22"))
    }

    @Test
    fun `epley 1RM`() {
        assertEquals(100f, OneRepMax.epley(100f, 1), 0.01f)
        assertEquals(126.67f, OneRepMax.epley(100f, 8), 0.01f)
        assertEquals(0f, OneRepMax.epley(0f, 8), 0.01f)
    }
}

class WeekStreakTest {
    // 2026-09-24 is a Thursday; its week starts Monday 2026-09-21.
    private val today = "2026-09-24"

    @Test
    fun `counts consecutive weeks including current`() {
        val dates = listOf("2026-09-22", "2026-09-15", "2026-09-08")
        org.junit.Assert.assertEquals(3, StreakCalculator.currentWeekStreak(dates, today))
    }

    @Test
    fun `current week without training still counts last weeks`() {
        val dates = listOf("2026-09-17", "2026-09-10")
        org.junit.Assert.assertEquals(2, StreakCalculator.currentWeekStreak(dates, today))
    }

    @Test
    fun `gap breaks the streak`() {
        val dates = listOf("2026-09-23", "2026-09-02")
        org.junit.Assert.assertEquals(1, StreakCalculator.currentWeekStreak(dates, today))
    }

    @Test
    fun `no dates no streak`() {
        org.junit.Assert.assertEquals(0, StreakCalculator.currentWeekStreak(emptyList(), today))
    }
}
