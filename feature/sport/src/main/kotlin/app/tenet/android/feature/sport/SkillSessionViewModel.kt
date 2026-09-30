package app.tenet.android.feature.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.SkillRepository
import app.tenet.android.core.data.SkillSessionInfo
import app.tenet.android.core.database.entity.FormQuality
import app.tenet.android.core.database.entity.SetEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SkillSessionUiState(
    val sessionId: String? = null,
    val info: SkillSessionInfo? = null,
    val sessionExerciseId: String? = null,
    val sets: List<SetEntry> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Backs the calisthenics skill session: set editing with quality marks,
 * criterion evaluation (drives the promotion suggestion) and finishing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SkillSessionViewModel @Inject constructor(
    private val repository: SkillRepository,
) : ViewModel() {

    private val sessionId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SkillSessionUiState> = sessionId
        .filterNotNull()
        .flatMapLatest { id ->
            flow { emit(repository.skillSessionInfo(id)) }
                .flatMapLatest { info ->
                    if (info == null) {
                        flowOf(SkillSessionUiState(sessionId = id, loaded = true))
                    } else {
                        repository.observeSets(info.sessionExerciseId).map { sets ->
                            SkillSessionUiState(
                                sessionId = id,
                                info = info,
                                sessionExerciseId = info.sessionExerciseId,
                                sets = sets,
                                loaded = true,
                            )
                        }
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkillSessionUiState())

    private val _achievementEvents = Channel<String>(Channel.BUFFERED)
    val achievementEvents = _achievementEvents.receiveAsFlow()

    private val _finishedEvents = Channel<Unit>(Channel.BUFFERED)
    val finishedEvents = _finishedEvents.receiveAsFlow()

    fun load(id: String) {
        if (sessionId.value != id) sessionId.value = id
    }

    /** Ticks/unticks a set; ticking evaluates the step criterion. */
    fun setCompleted(set: SetEntry, completed: Boolean) {
        val sessionId = uiState.value.sessionId ?: return
        val exerciseId = uiState.value.info?.step?.exerciseId ?: return
        viewModelScope.launch {
            repository.upsertSet(set.copy(completed = completed))
            if (completed && repository.checkAchievement(sessionId, exerciseId)) {
                _achievementEvents.send("Kriterium erfüllt – Aufstieg kann bestätigt werden!")
            }
        }
    }

    fun setSeconds(set: SetEntry, seconds: Int) {
        viewModelScope.launch {
            repository.upsertSet(set.copy(durationSec = seconds.coerceIn(0, 3600)))
        }
    }

    fun setReps(set: SetEntry, reps: Int) {
        viewModelScope.launch {
            repository.upsertSet(set.copy(reps = reps.coerceAtLeast(0)))
        }
    }

    /** Cycles null -> sauber -> mit Fehlern -> null. */
    fun cycleQuality(set: SetEntry) {
        val next = when (set.formQuality) {
            null -> FormQuality.CLEAN
            FormQuality.CLEAN -> FormQuality.SLOPPY
            FormQuality.SLOPPY -> null
        }
        viewModelScope.launch { repository.upsertSet(set.copy(formQuality = next)) }
    }

    fun addSet() {
        val sessionExerciseId = uiState.value.sessionExerciseId ?: return
        viewModelScope.launch { repository.addSet(sessionExerciseId) }
    }

    fun finish() {
        val id = uiState.value.sessionId ?: return
        viewModelScope.launch {
            repository.endSession(id)
            _finishedEvents.send(Unit)
        }
    }
}
