package app.tenet.android.core.data

import app.tenet.android.core.database.entity.TrainingPlan
import app.tenet.android.core.common.TrainingDays
import app.tenet.android.core.database.entity.Discipline
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.WorkoutSession
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Week calendar over all disciplines (App_Konzept.md 5.2: works only with
 * [PlannedWorkout] and [WorkoutSession], so it stays discipline-neutral —
 * the details live in discipline-specific tables).
 */
/** Active undated plan with its main workout. */
data class PlanHead(val plan: TrainingPlan, val workout: PlannedWorkout)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class WeekCalendarRepository @Inject constructor(
    private val dao: SportDao,
) {
    /** All sessions (finished or running) started within [start, end). */
    fun observeSessionsBetween(
        start: LocalDate,
        end: LocalDate,
    ): Flow<List<SportDao.SessionWithWorkout>> =
        dao.observeSessionsBetween(start.atStartOfDay(zone).toInstant().toEpochMilli(),
            end.atStartOfDay(zone).toInstant().toEpochMilli())

    /**
     * Planned workouts within [start, end]: dated ones directly plus the
     * recurring weekly structure (weekIndex + dayIndex) of active plans,
     * resolved against the plan's start Monday. The running plan lands
     * here without further changes (App_Konzept.md 5.2 Wochenkalender).
     */
    fun observePlannedBetween(
        start: LocalDate,
        end: LocalDate,
    ): Flow<List<PlannedWorkout>> =
        combine(
            dao.observePlannedBetween(start.toString(), end.toString()),
            dao.observeRecurringPlanned(),
            observeActivePlanHeads(),
        ) { dated, recurring, heads ->
            val resolved = recurring.mapNotNull { row ->
                val planStart = row.planStartDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@mapNotNull null
                val weekIndex = row.planned.weekIndex ?: return@mapNotNull null
                val dayIndex = row.planned.dayIndex ?: return@mapNotNull null
                val targetMonday = WeekMath.weekStart(planStart).plusWeeks(weekIndex.toLong())
                if (targetMonday != start) return@mapNotNull null
                val date = targetMonday.plusDays(dayIndex.toLong())
                if (date.isBefore(start) || date.isAfter(end)) return@mapNotNull null
                row.planned.copy(date = date.toString())
            }
            // Undated plans with fixed training days land on those weekdays.
            val fixedDays = heads.flatMap { head ->
                val days = TrainingDays.parse(head.plan.trainingDays)
                if (days.isEmpty()) {
                    emptyList()
                } else {
                    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
                        .filter { it.dayOfWeek in days }
                        .map { head.workout.copy(date = it.toString()) }
                        .toList()
                }
            }
            (dated + resolved + fixedDays).sortedBy { it.date ?: "" }
        }

    /**
     * Main workout of each active undated plan (Gym, Calisthenics) with its
     * plan: these plans have no calendar dates, only optional training days.
     */
    fun observeActivePlanHeads(): Flow<List<PlanHead>> =
        combine(
            listOf(Discipline.GYM, Discipline.CALISTHENICS).map { discipline ->
                dao.observeActivePlan(discipline).flatMapLatest { plan ->
                    if (plan == null) {
                        flowOf(null)
                    } else {
                        dao.observeWorkouts(plan.id).map { workouts -> workouts.firstOrNull()?.let { PlanHead(plan, it) } }
                    }
                }
            },
        ) { heads -> heads.filterNotNull() }

    /** Sets the fixed training weekdays of a plan (empty = every day). */
    suspend fun setTrainingDays(planId: String, days: Set<java.time.DayOfWeek>) =
        dao.setTrainingDays(planId, TrainingDays.format(days))

    /** Local dates of all finished sessions (training streak). */
    fun observeTrainingDates(): Flow<List<String>> =
        dao.observeFinishedSessionStarts().map { starts ->
            starts.map { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }.distinct()
        }

    private companion object {
        val zone: ZoneId = ZoneId.systemDefault()
    }
}
