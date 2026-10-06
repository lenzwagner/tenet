package app.tenet.android.feature.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.WeekCalendarRepository
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.PlannedWorkout
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** One session of a calendar day (discipline-colored). */
data class WeekSessionUi(
    val id: String,
    val discipline: Discipline,
    val workoutTitle: String?,
    val startMillis: Long,
    /** Null while the session is still running. */
    val durationMin: Long?,
    val running: Boolean,
)

/** A dated planned workout of a calendar day. */
data class WeekPlannedUi(
    val title: String,
    val discipline: Discipline,
)

data class WeekDayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val sessions: List<WeekSessionUi> = emptyList(),
    val planned: List<WeekPlannedUi> = emptyList(),
)

data class WeekCalendarUiState(
    val weekStart: LocalDate = WeekMath.weekStart(LocalDate.now()),
    val days: List<WeekDayUi> = emptyList(),
    /** Active plans without fixed training days (any day): not on single days, named in a hint. */
    val flexiblePlans: List<WeekPlannedUi> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Cross-discipline week calendar (App_Konzept.md 5.2: "Wochenkalender
 * aller Einheiten farbcodiert"). Navigates week by week; grouping happens
 * with the ISO helpers of [WeekMath].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekCalendarViewModel @Inject constructor(
    private val repository: WeekCalendarRepository,
) : ViewModel() {

    private val anchor = MutableStateFlow(LocalDate.now())
    private val today = LocalDate.now()

    val uiState: StateFlow<WeekCalendarUiState> = anchor
        .flatMapLatest { date ->
            val start = WeekMath.weekStart(date)
            combine(
                repository.observeSessionsBetween(start, start.plusDays(7)),
                repository.observePlannedBetween(start, start.plusDays(6)),
                repository.observeActivePlanHeads(),
            ) { sessions, planned, heads ->
                buildState(start, sessions, planned).copy(
                    flexiblePlans = heads
                        .filter { app.tenet.android.core.common.TrainingDays.parse(it.plan.trainingDays).isEmpty() }
                        .map { WeekPlannedUi(it.workout.title, it.plan.discipline) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekCalendarUiState())

    private fun buildState(
        weekStart: LocalDate,
        sessions: List<SportDao.SessionWithWorkout>,
        planned: List<PlannedWorkout>,
    ): WeekCalendarUiState {
        val sessionsByDay = sessions.groupBy { WeekMath.dayIndex(it.session.startedAt) }
        val plannedByDay = planned.groupBy { row ->
            row.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        }

        val days = WeekMath.weekDays(weekStart).mapIndexed { index, date ->
            WeekDayUi(
                date = date,
                isToday = date == today,
                sessions = sessionsByDay[index].orEmpty().map { row ->
                    val session = row.session
                    val running = session.endedAt == null
                    WeekSessionUi(
                        id = session.id,
                        discipline = session.discipline,
                        workoutTitle = row.workoutTitle,
                        startMillis = session.startedAt,
                        durationMin = session.endedAt
                            ?.let { (it - session.startedAt) / 60_000L },
                        running = running,
                    )
                },
                planned = plannedByDay[date].orEmpty().map { entry ->
                    WeekPlannedUi(entry.title, entry.discipline)
                },
            )
        }
        return WeekCalendarUiState(
            weekStart = weekStart,
            days = days,
            loading = false,
        )
    }

    fun previousWeek() {
        anchor.value = anchor.value.minusWeeks(1)
    }

    fun nextWeek() {
        anchor.value = anchor.value.plusWeeks(1)
    }

    fun goToCurrentWeek() {
        anchor.value = LocalDate.now()
    }
}
