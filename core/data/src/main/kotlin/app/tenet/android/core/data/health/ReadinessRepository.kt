package app.tenet.android.core.data.health

import app.tenet.android.core.common.Readiness
import app.tenet.android.core.data.WeekCalendarRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Builds today's [Readiness] from Health Connect (sleep, resting heart rate,
 * HRV with 30-day baselines) and Tenet's own sessions (training load).
 */
@Singleton
class ReadinessRepository @Inject constructor(
    private val health: HealthConnectRepository,
    private val weekCalendar: WeekCalendarRepository,
) {
    data class Today(val result: Readiness.Result, val input: Readiness.Input)

    /** Null when Health Connect is off or has neither sleep nor heart data for today. */
    suspend fun today(today: LocalDate = LocalDate.now()): Today? {
        val zone = ZoneId.systemDefault()
        val from = today.minusDays(31).atStartOfDay(zone).toInstant()
        val nights = runCatching { health.sleepNights(from) }.getOrDefault(emptyMap())
        val hearts = runCatching { health.heartDays(from, nights) }.getOrDefault(emptyMap())
        val before = { day: LocalDate -> day.isBefore(today) && !day.isBefore(today.minusDays(30)) }

        val sleepBase = nights.filterKeys { before(it) && it.isAfter(today.minusDays(15)) }.values.map { it.asleepMin }
        val rhrBase = hearts.filterKeys(before).values.mapNotNull { it.restingHr }
        val hrvBase = hearts.filterKeys(before).values.mapNotNull { it.hrvMs }

        val sessions = weekCalendar.observeSessionsBetween(today.minusDays(28), today).first()
            .map { it.session }
            .filter { it.endedAt != null }
        fun minutes(days: Long) = sessions
            .filter { it.startedAt >= today.minusDays(days).atStartOfDay(zone).toInstant().toEpochMilli() }
            .sumOf { ((it.endedAt!! - it.startedAt) / 60_000L).coerceIn(0L, 240L) }
            .toInt()

        val input = Readiness.Input(
            sleep = nights[today],
            sleepBaselineMin = sleepBase.takeIf { it.size >= 3 }?.average()?.toInt(),
            restingHr = hearts[today]?.restingHr,
            restingHrBaseline = rhrBase.takeIf { it.size >= 5 }?.sorted()?.let { it[it.size / 2].toDouble() },
            hrvMs = hearts[today]?.hrvMs,
            hrvBaselineMs = hrvBase.takeIf { it.size >= 5 }?.average(),
            acuteLoadMin = minutes(7),
            chronicLoadMin = minutes(28),
            hasTrainingHistory = sessions.any { ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate(), today) >= 7 },
        )
        val result = Readiness.compute(input) ?: return null
        return Today(result, input)
    }
}
