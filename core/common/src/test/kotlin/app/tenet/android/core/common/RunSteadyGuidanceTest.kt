package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSteadyGuidanceTest {
    @Test fun tooFast() = assertTrue(RunGuidance.steadyPaceHint(320, 345).startsWith("Etwas zu schnell"))
    @Test fun onPace() = assertEquals("Pace passt.", RunGuidance.steadyPaceHint(350, 345))
    @Test fun tooSlow() = assertTrue(RunGuidance.steadyPaceHint(380, 345).startsWith("Etwas langsamer"))
    @Test fun start() = assertEquals(
        "Lauf gestartet. Ziel-Pace 5 Minuten 45 pro Kilometer über 8 Komma 5 Kilometer. Locker bleiben.",
        RunGuidance.steadyStartAnnouncement(345, 8500),
    )
}
