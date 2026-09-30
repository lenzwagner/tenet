package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherTest {
    @Test fun labels() {
        assertEquals("Klar", WeatherCodes.label(0))
        assertEquals("Regen", WeatherCodes.label(63))
        assertEquals("Gewitter mit Hagel", WeatherCodes.label(99))
    }

    @Test fun summaryToday() =
        assertEquals("⛅ 14° · 18° / 9° · Teilweise bewölkt", WeatherDay("2026-09-29", 2, 17.6f, 8.8f, nowC = 13.7f).summary())

    @Test fun summaryPast() =
        assertEquals("🌧️ 12° / 7° · Regen", WeatherDay("2026-09-28", 63, 12.2f, 6.9f).summary())

    @Test fun coarseLocation() = assertEquals("48.14", WeatherCodes.coarse(48.13743))
}
