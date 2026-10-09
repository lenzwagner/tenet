package app.tenet.android.core.data.health

import app.tenet.android.core.common.SleepNight
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.data.WeekCalendarRepository
import app.tenet.android.core.database.entity.BodyMetric
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Everything for the "Gesundheit" page: sleep nights, resting heart rate and
 * HRV with their baselines, today's heart rate range, steps and active
 * minutes per day, readiness and the weight history.
 */
@Singleton
class HealthOverviewRepository @Inject constructor(
    private val health: HealthConnectRepository,
    private val readiness: ReadinessRepository,
    private val weekCalendar: WeekCalendarRepository,
    private val sport: SportRepository,
    private val feed: TodayFeedRepository,
) {
    data class DayValue<T>(val date: LocalDate, val value: T)

    data class Overview(
        val connected: Boolean,
        val readiness: ReadinessRepository.Today?,
        /** Newest first, up to 14 nights. */
        val nights: List<SleepNight>,
        val sleepAvgMin: Int?,
        /** Oldest first, 14 days. */
        val restingHr: List<DayValue<Int>>,
        val hrv: List<DayValue<Double>>,
        val restingHrAvg: Int?,
        val hrvAvg: Double?,
        val heartToday: HealthConnectRepository.HeartRange?,
        /** Oldest first, 7 days. */
        val steps: List<DayValue<Long>>,
        val activeMin: List<DayValue<Int>>,
        val weekActiveMin: Int,
        /** Oldest first, last 90 days. */
        val weights: List<BodyMetric>,
    )

    suspend fun load(): Overview = coroutineScope {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val from = today.minusDays(30).atStartOfDay(zone).toInstant()
        val ready = async { runCatching { readiness.today() }.getOrNull() }
        val steps = async { runCatching { health.stepsByDay(7) }.getOrDefault(emptyMap()) }
        val heartToday = async { runCatching { health.heartRange(today) }.getOrNull() }
        val nights = runCatching { health.sleepNights(from) }.getOrDefault(emptyMap())
        val hearts = runCatching { health.heartDays(from, nights) }.getOrDefault(emptyMap())

        val days14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
        val rhr = days14.mapNotNull { d -> hearts[d]?.restingHr?.let { DayValue(d, it) } }
        val hrv = days14.mapNotNull { d -> hearts[d]?.hrvMs?.let { DayValue(d, it) } }
        val recentNights = nights.entries.sortedByDescending { it.key }.take(14).map { it.value }

        val sessions = weekCalendar.observeSessionsBetween(today.minusDays(6), today.plusDays(1)).first()
            .map { it.session }.filter { it.endedAt != null }
        val active = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { d ->
            DayValue(
                d,
                sessions.filter { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == d }
                    .sumOf { ((it.endedAt!! - it.startedAt) / 60_000L).coerceIn(0L, 240L) }.toInt(),
            )
        }
        val stepMap = steps.await()
        Overview(
            connected = health.status.value.enabled,
            readiness = ready.await(),
            nights = recentNights,
            sleepAvgMin = recentNights.takeIf { it.size >= 2 }?.map { it.asleepMin }?.average()?.toInt(),
            restingHr = rhr,
            hrv = hrv,
            restingHrAvg = rhr.takeIf { it.size >= 3 }?.map { it.value }?.average()?.toInt(),
            hrvAvg = hrv.takeIf { it.size >= 3 }?.map { it.value }?.average(),
            heartToday = heartToday.await(),
            steps = if (stepMap.isEmpty()) emptyList() else (6 downTo 0).map { today.minusDays(it.toLong()) }.map { DayValue(it, stepMap[it] ?: 0L) },
            activeMin = active,
            weekActiveMin = runCatching { feed.weekActiveMinutes(today) }.getOrDefault(0),
            weights = sport.observeBodyMetrics().first()
                .filter { runCatching { !LocalDate.parse(it.date).isBefore(today.minusDays(90)) }.getOrDefault(false) }
                .sortedBy { it.date },
        )
    }
}
