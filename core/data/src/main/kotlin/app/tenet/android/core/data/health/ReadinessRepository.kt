package app.tenet.android.core.data.health

import app.tenet.android.core.common.Readiness
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds today's [Readiness] from Health Connect (sleep, resting heart rate,
 * HRV with 30-day baselines, sleep of the last 7 nights).
 */
@Singleton
class ReadinessRepository @Inject constructor(
    private val health: HealthConnectRepository,
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

        val input = Readiness.Input(
            sleep = nights[today],
            sleepBaselineMin = sleepBase.takeIf { it.size >= 3 }?.average()?.toInt(),
            restingHr = hearts[today]?.restingHr,
            restingHrBaseline = rhrBase.takeIf { it.size >= 5 }?.sorted()?.let { it[it.size / 2].toDouble() },
            hrvMs = hearts[today]?.hrvMs,
            hrvBaselineMs = hrvBase.takeIf { it.size >= 5 }?.average(),
            previousNights = nights.filterKeys { it.isBefore(today) }.entries.sortedByDescending { it.key }
                .take(Readiness.SLEEP_NIGHTS - 1).map { it.value },
        )
        val result = Readiness.compute(input) ?: return null
        return Today(result, input)
    }
}
