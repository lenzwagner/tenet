package app.tenet.android.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One night's sleep (Health Connect), keyed by the day you woke up (= the dream's day). */
data class SleepNight(
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    /** Asleep time (awake phases removed when stages are known). */
    val asleepMin: Int,
    val deepMin: Int? = null,
    val remMin: Int? = null,
    val lightMin: Int? = null,
    val awakeMin: Int? = null,
) {
    /** "7 h 20" */
    val durationText: String get() = SleepMath.duration(asleepMin)
}

object SleepMath {
    fun duration(min: Int): String = if (min >= 60) "${min / 60} h ${(min % 60).toString().padStart(2, '0')}" else "$min min"

    /**
     * Sessions → one night per wake-up day. Several sessions ending on the
     * same day (interrupted night, nap) are summed; naps (< 3 h, ending after
     * 14:00) do not count as the night.
     */
    fun nights(sessions: List<SleepNight>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, SleepNight> =
        sessions
            .filter { s ->
                val endHour = s.end.atZone(zone).hour
                s.asleepMin >= 180 || endHour < 14
            }
            .groupBy { it.end.atZone(zone).toLocalDate() }
            .mapValues { (day, list) ->
                fun sum(f: (SleepNight) -> Int?) = list.mapNotNull(f).takeIf { it.isNotEmpty() }?.sum()
                SleepNight(
                    date = day,
                    start = list.minOf { it.start },
                    end = list.maxOf { it.end },
                    asleepMin = list.sumOf { it.asleepMin },
                    deepMin = sum { it.deepMin },
                    remMin = sum { it.remMin },
                    lightMin = sum { it.lightMin },
                    awakeMin = sum { it.awakeMin },
                )
            }
}
