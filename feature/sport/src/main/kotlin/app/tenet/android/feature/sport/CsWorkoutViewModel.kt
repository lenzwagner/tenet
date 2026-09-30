package app.tenet.android.feature.sport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.IntervalMath
import app.tenet.android.core.data.CsStrengthInfo
import app.tenet.android.core.data.SkillRepository
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import kotlinx.coroutines.flow.combine
import app.tenet.android.core.database.entity.SessionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Static data of the interval workout screen (mode, config, stations). */
data class CsWorkoutUiState(
    val sessionId: String? = null,
    val info: CsStrengthInfo? = null,
    val loaded: Boolean = false,
)

/** Live timer position; [elapsedSec] is derived from a wall-clock anchor. */
data class TimerSnapshot(
    val elapsedSec: Long = 0L,
    val running: Boolean = false,
    /** Finished AMRAP rounds (AMRAP mode only). */
    val amrapRounds: Int = 0,
)

/**
 * Drives the circuit/EMOM screen (App_Konzept.md 5.2.2: "Runden- und
 * Intervalltimer"). The timer is anchored to the wall clock, so pausing
 * only freezes it and a process restart (SavedStateHandle) keeps it honest.
 * Completed work phases/minutes are logged idempotently to the session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CsWorkoutViewModel @Inject constructor(
    private val repository: SkillRepository,
    private val sportRepository: SportRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId = MutableStateFlow<String?>(null)
    private val reload = MutableStateFlow(0)

    private val _catalog = MutableStateFlow<List<Exercise>>(emptyList())
    val catalog: StateFlow<List<Exercise>> = _catalog

    suspend fun alternatives(exerciseId: String): List<Exercise> = sportRepository.alternatives(exerciseId)

    /** Swaps one station (only today, or also in the plan); logged sets move along. */
    fun swapStation(sessionExerciseId: String, exercise: Exercise, permanent: Boolean) {
        viewModelScope.launch {
            sportRepository.swapSessionExercise(sessionExerciseId, exercise.id, permanent)
            reload.value++
        }
    }

    val uiState: StateFlow<CsWorkoutUiState> = sessionId
        .filterNotNull()
        .combine(reload) { id, _ -> id }
        .flatMapLatest { id ->
            flow { emit(repository.strengthSessionInfo(id)) }
                .map { info ->
                    CsWorkoutUiState(sessionId = id, info = info, loaded = true)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CsWorkoutUiState())

    private val _timer = MutableStateFlow(TimerSnapshot())
    val timer: StateFlow<TimerSnapshot> = _timer

    /** Acoustic signal on every phase/round boundary (and on finish). */
    private val _signals = Channel<Unit>(Channel.BUFFERED)
    val signals = _signals.receiveAsFlow()

    private val _finishedEvents = Channel<Unit>(Channel.BUFFERED)
    val finishedEvents = _finishedEvents.receiveAsFlow()

    private var loopRunning = false
    private var lastSignalKey: String? = null
    private var toldDone = false

    private var amrapRounds = 0

    fun load(id: String) {
        if (sessionId.value != id) {
            sessionId.value = id
            viewModelScope.launch {
                _catalog.value = sportRepository.catalog(Discipline.CALISTHENICS)
                amrapRounds = repository.amrapRounds(id)
                emitTimer()
            }
        }
        if (!loopRunning) {
            loopRunning = true
            viewModelScope.launch { timerLoop() }
        }
    }

    /** Start/pause toggle (wall-clock anchored). */
    fun toggle() {
        val now = System.currentTimeMillis()
        val anchor = anchorMs()
        if (anchor > 0L) {
            setAccumulated(accruedMs(now))
            setAnchor(0L)
        } else {
            setAnchor(now)
        }
        emitTimer(now)
    }

    /** Resets the timer to zero; already logged sets stay. */
    fun reset() {
        setAnchor(0L)
        setAccumulated(0L)
        lastSignalKey = null
        toldDone = false
        emitTimer(System.currentTimeMillis())
    }

    /** AMRAP: one more round done (logs a set for every station). */
    fun completeRound() {
        val id = uiState.value.sessionId ?: return
        viewModelScope.launch {
            amrapRounds = repository.logAmrapRound(id)
            emitTimer()
        }
    }

    fun undoRound() {
        val id = uiState.value.sessionId ?: return
        viewModelScope.launch {
            amrapRounds = repository.undoAmrapRound(id)
            emitTimer()
        }
    }

    fun finish() {
        val id = uiState.value.sessionId ?: return
        setAnchor(0L)
        viewModelScope.launch {
            repository.endSession(id)
            _finishedEvents.send(Unit)
        }
    }

    // ---- Timer plumbing -------------------------------------------------

    private fun anchorMs(): Long = savedStateHandle.get<Long>(KEY_ANCHOR) ?: 0L

    private fun setAnchor(value: Long) {
        savedStateHandle[KEY_ANCHOR] = value
    }

    private fun accumulatedMs(): Long = savedStateHandle.get<Long>(KEY_ACCUMULATED) ?: 0L

    private fun setAccumulated(value: Long) {
        savedStateHandle[KEY_ACCUMULATED] = value
    }

    private fun accruedMs(now: Long): Long {
        val anchor = anchorMs()
        return accumulatedMs() + if (anchor > 0L) (now - anchor).coerceAtLeast(0L) else 0L
    }

    private fun emitTimer(now: Long = System.currentTimeMillis()) {
        _timer.value = TimerSnapshot(
            elapsedSec = accruedMs(now) / 1_000L,
            running = anchorMs() > 0L,
            amrapRounds = amrapRounds,
        )
    }

    private suspend fun timerLoop() {
        while (true) {
            val now = System.currentTimeMillis()
            val running = anchorMs() > 0L
            emitTimer(now)

            // While paused nothing advances: skip DB sync and signals.
            if (running) {
                val elapsed = accruedMs(now) / 1_000L
                val state = uiState.value
                val info = state.info
                val mode = info?.session?.mode ?: SessionMode.SETS
                if (info != null && state.loaded) {
                    when (mode) {
                    SessionMode.CIRCUIT -> {
                        val circuit = IntervalMath.circuit(
                            elapsedSec = elapsed,
                            stations = info.stations.size,
                            workSec = info.session.workSec ?: 40,
                            restSec = info.session.restSec ?: 20,
                            rounds = info.session.rounds ?: 4,
                        )
                        repository.syncCircuitSets(state.sessionId!!, elapsed)
                        signalOn(
                            key = "${circuit.round}/${circuit.station}/${circuit.phase}",
                            done = circuit.done,
                            stopTimer = { if (anchorMs() > 0L) setAnchor(0L) },
                        )
                    }

                    SessionMode.EMOM -> {
                        val emom = IntervalMath.emom(
                            elapsedSec = elapsed,
                            minutes = info.session.rounds ?: 10,
                            intervalSec = info.session.intervalSec ?: 60,
                        )
                        repository.syncEmomSets(state.sessionId!!, elapsed)
                        signalOn(
                            key = "${emom.round}",
                            done = emom.done,
                            stopTimer = { if (anchorMs() > 0L) setAnchor(0L) },
                        )
                    }

                    SessionMode.AMRAP -> {
                        val amrap = IntervalMath.amrap(elapsed, info.session.rounds ?: 12)
                        // Beep every full minute and at the time cap.
                        signalOn(
                            key = "${elapsed / 60}",
                            done = amrap.done,
                            stopTimer = { if (anchorMs() > 0L) setAnchor(0L) },
                        )
                    }

                        SessionMode.SETS -> Unit // not hosted by this screen
                    }
                }
            }
            delay(1_000L)
        }
    }

    /** Emits a beep when the phase/round key changes, once at "done". */
    private suspend fun signalOn(
        key: String,
        done: Boolean,
        stopTimer: () -> Unit,
    ) {
        if (done) {
            if (!toldDone) {
                toldDone = true
                stopTimer()
                _signals.send(Unit)
            }
            return
        }
        val previous = lastSignalKey
        lastSignalKey = key
        if (previous != null && previous != key) {
            _signals.send(Unit)
        }
    }

    private companion object {
        const val KEY_ANCHOR = "csWorkoutAnchorMs"
        const val KEY_ACCUMULATED = "csWorkoutAccumulatedMs"
    }
}
