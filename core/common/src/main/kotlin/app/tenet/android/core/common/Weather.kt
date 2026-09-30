package app.tenet.android.core.common

import java.util.Locale
import kotlin.math.roundToInt

/** Weather of one day (Open-Meteo, WMO weather codes). */
data class WeatherDay(
    val date: String,
    val code: Int,
    val maxC: Float,
    val minC: Float,
    /** Current temperature, only for today. */
    val nowC: Float? = null,
    val precipitationMm: Float = 0f,
) {
    val label: String get() = WeatherCodes.label(code)
    val emoji: String get() = WeatherCodes.emoji(code)

    /** "☀️ 18° / 9° · Sonnig" (today: current temperature first). */
    fun summary(): String = buildString {
        append(emoji).append(' ')
        if (nowC != null) append("${nowC.roundToInt()}° · ")
        append("${maxC.roundToInt()}° / ${minC.roundToInt()}° · ").append(label)
    }
}

/** WMO weather interpretation codes → German label and emoji. */
object WeatherCodes {
    fun label(code: Int): String = when (code) {
        0 -> "Klar"
        1 -> "Überwiegend klar"
        2 -> "Teilweise bewölkt"
        3 -> "Bedeckt"
        45, 48 -> "Nebel"
        51, 53, 55 -> "Nieselregen"
        56, 57 -> "Gefrierender Niesel"
        61 -> "Leichter Regen"
        63 -> "Regen"
        65 -> "Starker Regen"
        66, 67 -> "Gefrierender Regen"
        71 -> "Leichter Schneefall"
        73 -> "Schneefall"
        75 -> "Starker Schneefall"
        77 -> "Schneegriesel"
        80 -> "Leichte Schauer"
        81 -> "Schauer"
        82 -> "Heftige Schauer"
        85, 86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> "Wetter"
    }

    fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57, 61, 80 -> "🌦️"
        63, 65, 66, 67, 81, 82 -> "🌧️"
        in 71..77, 85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    /** Rounded to ~1 km: enough for the weather, not an exact position. */
    fun coarse(value: Double): String = String.format(Locale.US, "%.2f", value)
}
