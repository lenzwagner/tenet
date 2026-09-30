package app.tenet.android.core.common

import app.tenet.android.core.common.TrainingReminderText.Unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingReminderTextTest {
    @Test fun intervals() =
        assertEquals("Intervalle 3 × 1 km", TrainingReminderText.line(Unit.Run(RunZone.INTERVAL, null, null, 3, 1000)))

    @Test fun shortIntervals() =
        assertEquals("Intervalle 6 × 400 m", TrainingReminderText.line(Unit.Run(RunZone.INTERVAL, null, null, 6, 400)))

    @Test fun easyWithPace() =
        assertEquals("Lockerer Lauf 8 km @ 5:45 /km", TrainingReminderText.line(Unit.Run(RunZone.EASY, 8000, null, paceSecPerKm = 345)))

    @Test fun longHalfKm() =
        assertEquals("Langer Lauf 16,5 km", TrainingReminderText.line(Unit.Run(RunZone.LONG, 16500, null)))

    @Test fun combined() {
        val n = TrainingReminderText.notification(listOf(Unit.Gym("Push", 5), Unit.Run(RunZone.EASY, null, 1800)))!!
        assertEquals("Heute: Push · 5 Übungen", n.first)
        assertEquals("Außerdem: Lockerer Lauf 30 min", n.second)
    }

    @Test fun nothing() = assertNull(TrainingReminderText.notification(emptyList()))
}
