package app.tenet.android.feature.sport

import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.GymInsights
import app.tenet.android.core.data.WeekCalendarRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.ProgressMath
import app.tenet.android.core.data.GymOverview
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.database.entity.BodyMetric
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the progress cards need, precomputed off the UI thread. */
data class ProgressState(
    val sets: List<ProgressMath.SetRecord> = emptyList(),
    val formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    val exercises: List<String> = emptyList(),
    val muscleGroups: List<String> = emptyList(),
    val personalBests: List<ProgressMath.PersonalBest> = emptyList(),
    val bodyMetrics: List<BodyMetric> = emptyList(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class GymViewModel @Inject constructor(
    private val repository: SportRepository,
    private val weekCalendarRepository: WeekCalendarRepository,
    settingsRepository: UserSettingsRepository,
    private val insights: GymInsights,
) : ViewModel() {

    init {
        viewModelScope.launch { repository.ensureSeedData() }
    }

    private val formulaFlow = settingsRepository.settings.map { it.oneRepMaxFormula }

    val overview: StateFlow<GymOverview> = formulaFlow
        .flatMapLatest { formula -> repository.observeGymOverview(formula) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymOverview())

    /** Working sets per main muscle since Monday (recomputed when sessions change). */
    val muscleWeek: StateFlow<List<Pair<String, Int>>> = overview
        .map { it.sessions.size }
        .distinctUntilChanged()
        .mapLatest {
            val monday = WeekMath.weekStart(java.time.LocalDate.now())
            insights.muscleSetsSince(monday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progressState: StateFlow<ProgressState> = combine(
        formulaFlow,
        repository.observeBodyMetrics(),
    ) { formula, metrics -> formula to metrics }
        .flatMapLatest { (formula, metrics) ->
            // Re-run whenever the overview changes (new finished session).
            overview.map { overview ->
                val sets = repository.progressSets()
                ProgressState(
                    sets = sets,
                    formula = formula,
                    exercises = ProgressMath.exercisesWithData(sets),
                    muscleGroups = ProgressMath.muscleGroups(sets),
                    personalBests = ProgressMath.personalBests(sets, formula),
                    bodyMetrics = metrics,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun saveBodyMetric(weight: Float?, bodyFat: Float?, measurements: Map<String, Float>) {
        viewModelScope.launch {
            repository.saveBodyMetric(
                date = java.time.LocalDate.now().toString(),
                weight = weight,
                bodyFat = bodyFat,
                measurements = measurements,
            )
        }
    }

    private val _sessionEvents = Channel<String>(Channel.BUFFERED)
    val sessionEvents = _sessionEvents.receiveAsFlow()

    fun startWorkout() {
        viewModelScope.launch {
            _sessionEvents.send(repository.startOrResumeSession())
        }
    }

    /** Discards the currently active session incl. its logged sets. */
    /** Finishes a forgotten session (duration estimated from its sets), then [then]. */
    fun finishSession(sessionId: String, then: () -> Unit) {
        viewModelScope.launch {
            repository.endSession(sessionId)
            then()
        }
    }

    fun discardActiveSession() {
        viewModelScope.launch { repository.discardActiveSession() }
    }

    /** Fixed training weekdays of the active plan (empty = every day). */
    fun setTrainingDays(planId: String, days: Set<java.time.DayOfWeek>) {
        viewModelScope.launch { weekCalendarRepository.setTrainingDays(planId, days) }
    }
}
