package app.tenet.android.core.data.health

import app.tenet.android.core.common.Readiness
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.WeekCalendarRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The numbers of the "Heute" feed and the home screen widgets: active
 * minutes this week, steps today, readiness and last night's sleep.
 */
@Singleton
class TodayFeedRepository @Inject constructor(
    private val health: HealthConnectRepository,
    private val readiness: ReadinessRepository,
    private val weekCalendar: WeekCalendarRepository,
) {
    data class Snapshot(
        val weekActiveMin: Int = 0,
        val weekGoalMin: Int = WEEK_GOAL_MIN,
        val steps: Long? = null,
        val form: Int? = null,
        val sleepMin: Int? = null,
        val sleepScore: Int? = null,
    ) {
        val weekPercent: Int get() = weekActiveMin * 100 / weekGoalMin.coerceAtLeast(1)
        val sleepText: String? get() = sleepMin?.let { "${it / 60} h ${it % 60} min" }
        val sleepLabel: String? get() = sleepScore?.let { sleepLabel(it) }
    }

    suspend fun weekActiveMinutes(today: LocalDate = LocalDate.now()): Int =
        weekCalendar.observeSessionsBetween(WeekMath.weekStart(today), today.plusDays(1)).first()
            .map { it.session }
            .filter { it.endedAt != null }
            .sumOf { ((it.endedAt!! - it.startedAt) / 60_000L).coerceIn(0L, 240L) }
            .toInt()

    suspend fun snapshot(): Snapshot {
        val ready = runCatching { readiness.today() }.getOrNull()
        return Snapshot(
            weekActiveMin = weekActiveMinutes(),
            steps = runCatching { health.stepsToday() }.getOrNull(),
            form = ready?.result?.score,
            sleepMin = ready?.input?.sleep?.asleepMin,
            sleepScore = ready?.result?.contributors?.firstOrNull { it.kind == Readiness.Kind.SLEEP }?.score,
        )
    }

    companion object {
        /** WHO: at least 150 minutes of moderate activity per week. */
        const val WEEK_GOAL_MIN = 150

        fun sleepLabel(score: Int) = when {
            score >= 85 -> "Sehr gut"
            score >= 70 -> "Gut"
            score >= 50 -> "Mäßig"
            else -> "Schlecht"
        }
    }
}
