package app.tenet.android.core.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTest {
    private val z = ZoneOffset.UTC
    private fun t(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, d, h, m).toInstant(z)

    @Test fun nightBelongsToWakeDay() {
        val n = SleepMath.nights(listOf(SleepNight(LocalDate.MIN, t(28, 23, 30), t(29, 7), 420, deepMin = 80, remMin = 95)), z)
        assertEquals(setOf(LocalDate.of(2026, 9, 29)), n.keys)
        assertEquals("7 h 00", n.values.first().durationText)
    }

    @Test fun interruptedNightIsSummed() {
        val n = SleepMath.nights(
            listOf(
                SleepNight(LocalDate.MIN, t(28, 23), t(29, 3), 230),
                SleepNight(LocalDate.MIN, t(29, 3, 30), t(29, 7), 200),
            ),
            z,
        ).getValue(LocalDate.of(2026, 9, 29))
        assertEquals(430, n.asleepMin)
        assertNull(n.deepMin)
    }

    @Test fun afternoonNapIsNotTheNight() =
        assertEquals(emptyMap<LocalDate, SleepNight>(), SleepMath.nights(listOf(SleepNight(LocalDate.MIN, t(29, 14), t(29, 15), 50)), z))
}
