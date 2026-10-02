package app.tenet.android.feature.sport.gym

import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.common.OverloadMath
import app.tenet.android.core.data.ai.AiFiller
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.RoutineExercise
import app.tenet.android.core.database.entity.SetEntry
import app.tenet.android.core.database.entity.SetType
import app.tenet.android.core.database.entity.WorkoutSession
import app.tenet.android.core.data.SessionBlock
import app.tenet.android.core.data.SportRepository
import app.tenet.android.feature.sport.RestTimerService
import app.tenet.android.core.common.WorkoutGuide
import app.tenet.android.core.data.guideBlock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetRowUi(
    val set: SetEntry,
    /** "weight x reps" of the previous session, same set index. */
    val previous: String?,
    val kgText: String,
    val repsText: String,
    val rpeText: String,
)

data class BlockUi(
    val exercise: Exercise,
    val sessionExerciseId: String,
    val target: RoutineExercise?,
    val rows: List<SetRowUi>,
    val suggestion: OverloadMath.Suggestion? = null,
    val lastE1rm: Float? = null,
    val bestE1rm: Float? = null,
    val notes: String = "",
    val previousNote: String? = null,
)

data class SessionUiState(
    val loading: Boolean = true,
    val session: WorkoutSession? = null,
    val title: String = "Workout",
    val blocks: List<BlockUi> = emptyList(),
)

data class RestUiState(val remainingSec: Int, val totalSec: Int)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val repository: SportRepository,
    private val application: Application,
    private val aiFiller: AiFiller,
) : AndroidViewModel(application) {

    val aiAvailable: Boolean get() = aiFiller.enabled
    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /**
     * "Bankdrücken 3 Sätze à 8 mit 80 kg, Kniebeugen 100 kg 10 und 8" →
     * fills the next open sets of those exercises and ticks them off.
     */
    fun voiceSets(text: String) {
        val blocks = _uiState.value.blocks
        if (blocks.isEmpty() || _aiBusy.value) return
        _aiBusy.value = true
        viewModelScope.launch {
            val fills = aiFiller.gym(text, blocks.map { it.exercise.name })
            _aiBusy.value = false
            if (fills == null) {
                _messages.send("KI nicht erreichbar – bitte von Hand eintragen.")
                return@launch
            }
            var logged = 0
            for (fill in fills) {
                val block = _uiState.value.blocks.firstOrNull { it.exercise.name == fill.exercise } ?: continue
                val open = block.rows.filter { !it.set.completed }
                fill.sets.zip(open).forEach { (spoken, row) ->
                    val updated = row.set.copy(
                        weight = spoken.weightKg ?: row.set.weight,
                        reps = spoken.reps,
                        completed = true,
                    )
                    applyRow(block.sessionExerciseId, row.set.id) {
                        it.copy(set = updated, kgText = formatNumber(updated.weight), repsText = updated.reps.toString())
                    }
                    repository.upsertSet(updated)
                    logged++
                }
            }
            _messages.send(
                if (logged == 0) "Keine passenden offenen Sätze gefunden."
                else "$logged ${if (logged == 1) "Satz" else "Sätze"} eingetragen",
            )
        }
    }

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    /** Countdown state, sourced from the foreground timer service. */
    val rest: StateFlow<RestUiState?> = RestTimerService.state

    /** Next open set with planned values and rest (training mode, notification). */
    val next: StateFlow<WorkoutGuide.Next?> = uiState
        .map { state -> WorkoutGuide.next(state.blocks.map { it.toGuide() }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sets still open in this workout. */
    val remaining: StateFlow<Int> = uiState
        .map { state -> WorkoutGuide.remaining(state.blocks.map { it.toGuide() }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        // Sets logged from the notification: reload the table.
        viewModelScope.launch { RestTimerService.changes.collect { if (it == sessionId) refresh() } }
    }

    private val _finished = Channel<Unit>(Channel.BUFFERED)
    val finished = _finished.receiveAsFlow()

    private val _endRequested = MutableStateFlow(false)
    /** True while the user has the "end session" sheet open. */
    val endRequested: StateFlow<Boolean> = _endRequested.asStateFlow()

    private var sessionId: String? = null

    fun load(id: String) {
        if (sessionId == id) return
        sessionId = id
        refresh()
        viewModelScope.launch {
            val discipline = repository.observeSession(id).first()?.discipline ?: Discipline.GYM
            _catalog.value = repository.catalog(discipline)
            _hasPlan.value = repository.sessionHasPlan(id)
        }
    }

    // ---- Swap / add / remove exercises ---------------------------------

    private val _catalog = MutableStateFlow<List<Exercise>>(emptyList())
    /** Exercise catalog of the session's discipline, for the picker. */
    val catalog: StateFlow<List<Exercise>> = _catalog.asStateFlow()
    private val _hasPlan = MutableStateFlow(false)
    /** Whether changes can also be saved to a plan ("Dauerhaft"). */
    val hasPlan: StateFlow<Boolean> = _hasPlan.asStateFlow()

    suspend fun alternatives(exerciseId: String): List<Exercise> = repository.alternatives(exerciseId)

    fun swapExercise(sessionExerciseId: String, exercise: Exercise, permanent: Boolean) {
        viewModelScope.launch {
            repository.swapSessionExercise(sessionExerciseId, exercise.id, permanent)
            refresh()
            _messages.send(if (permanent) "${exercise.name} steht jetzt im Plan" else "${exercise.name} nur für heute")
        }
    }

    fun addExercise(exercise: Exercise, permanent: Boolean) {
        val id = sessionId ?: return
        viewModelScope.launch {
            repository.addSessionExercise(id, exercise.id, permanent)
            refresh()
            _messages.send(if (permanent) "${exercise.name} zum Plan hinzugefügt" else "${exercise.name} nur für heute hinzugefügt")
        }
    }

    fun addWarmups(sessionExerciseId: String) {
        viewModelScope.launch {
            val added = repository.addWarmupSets(sessionExerciseId)
            refresh()
            _messages.send(if (added) "3 Aufwärmsätze hinzugefügt" else "Aufwärmen lohnt sich erst ab 25 kg Arbeitsgewicht")
        }
    }

    fun removeExercise(sessionExerciseId: String, name: String, permanent: Boolean) {
        viewModelScope.launch {
            repository.removeSessionExercise(sessionExerciseId, permanent)
            refresh()
            _messages.send(if (permanent) "$name aus dem Plan entfernt" else "$name für heute entfernt")
        }
    }

    private fun refresh() {
        val id = sessionId ?: return
        viewModelScope.launch {
            val blocks = repository.loadBlocks(id)
            _uiState.value = SessionUiState(
                loading = false,
                session = repository.observeSession(id).first(),
                title = repository.sessionTitle(id) ?: "Workout",
                blocks = blocks.map { it.toUi() },
            )
        }
    }

    private fun SessionBlock.toUi() = BlockUi(
        exercise = exercise,
        sessionExerciseId = sessionExercise.id,
        target = target,
        suggestion = suggestion,
        lastE1rm = lastE1rm,
        bestE1rm = bestE1rm,
        notes = sessionExercise.notes,
        previousNote = previousNote,
        rows = sets.map { set ->
            SetRowUi(
                set = set,
                previous = previousBySortOrder[set.sortOrder],
                kgText = formatNumber(set.weight),
                repsText = if (set.reps > 0) set.reps.toString() else "",
                rpeText = set.rpe?.let { formatNumber(it) } ?: "",
            )
        },
    )

    // ---- Field editing (updates locally, persists async) ---------------

    enum class Field { KG, REPS, RPE }

    /** Note per exercise; saved as typed, kept locally so a refresh cannot eat keystrokes. */
    fun onNote(sessionExerciseId: String, text: String) {
        _uiState.update { state ->
            state.copy(blocks = state.blocks.map { if (it.sessionExerciseId == sessionExerciseId) it.copy(notes = text) else it })
        }
        viewModelScope.launch { repository.setExerciseNote(sessionExerciseId, text) }
    }

    fun onSetText(sessionExerciseId: String, setId: String, field: Field, text: String) {
        var target: SetEntry? = null
        val propagated = mutableListOf<SetEntry>()
        _uiState.update { state ->
            SessionUiState(
                loading = state.loading,
                session = state.session,
                title = state.title,
                blocks = state.blocks.map { block ->
                    if (block.sessionExerciseId != sessionExerciseId) return@map block
                    var oldWeight = 0f
                    val edited = block.rows.map { row ->
                        if (row.set.id != setId) return@map row
                        oldWeight = row.set.weight
                        target = when (field) {
                            Field.KG -> row.set.copy(weight = text.toFloatCompat() ?: 0f)
                            Field.REPS -> row.set.copy(reps = text.toIntCompat() ?: 0)
                            Field.RPE -> row.set.copy(rpe = text.toFloatCompat())
                        }
                        when (field) {
                            Field.KG -> row.copy(kgText = text, set = target)
                            Field.REPS -> row.copy(repsText = text, set = target)
                            Field.RPE -> row.copy(rpeText = text, set = target)
                        }
                    }
                    // Like Strong/Hevy: a weight typed in fills the following empty working sets.
                    val weight = target?.weight?.takeIf { field == Field.KG && it > 0f && target?.type != SetType.WARMUP }
                    val index = edited.indexOfFirst { it.set.id == setId }
                    block.copy(
                        rows = if (weight == null) edited else edited.mapIndexed { i, row ->
                            // Empty sets, or ones still following the old value, take the new weight.
                            val follows = row.set.weight == 0f || row.set.weight == oldWeight
                            if (i > index && !row.set.completed && row.set.type != SetType.WARMUP && follows) {
                                val filled = row.set.copy(weight = weight)
                                propagated += filled
                                row.copy(set = filled, kgText = text)
                            } else row
                        },
                    )
                },
            )
        }
        target?.let { set ->
            viewModelScope.launch {
                repository.upsertSet(set)
                propagated.forEach { repository.upsertSet(it) }
            }
        }
    }

    fun toggleComplete(sessionExerciseId: String, setId: String) {
        val block = _uiState.value.blocks.firstOrNull {
            it.sessionExerciseId == sessionExerciseId
        } ?: return
        val row = block.rows.firstOrNull { it.set.id == setId } ?: return
        val completed = !row.set.completed
        val updated = row.set.copy(completed = completed)

        applyRow(sessionExerciseId, setId) { it.copy(set = updated) }
        viewModelScope.launch { repository.upsertSet(updated) }

        if (completed) {
            startRest(restAfter(block, updated))
        }
    }

    fun onSetType(sessionExerciseId: String, setId: String, type: SetType) {
        val block = _uiState.value.blocks.firstOrNull {
            it.sessionExerciseId == sessionExerciseId
        } ?: return
        val row = block.rows.firstOrNull { it.set.id == setId } ?: return
        val updated = row.set.copy(type = type)
        applyRow(sessionExerciseId, setId) { it.copy(set = updated) }
        viewModelScope.launch { repository.upsertSet(updated) }
    }

    fun addSet(sessionExerciseId: String) {
        viewModelScope.launch {
            repository.addSet(sessionExerciseId)
            refresh()
        }
    }

    /** Opens the end-session sheet (notes + perceived effort). */
    fun requestEnd() {
        _endRequested.value = true
    }

    fun cancelEnd() {
        _endRequested.value = false
    }

    fun endSession(notes: String = "", perceivedEffort: Int? = null) {
        val id = sessionId ?: return
        _endRequested.value = false
        RestTimerService.cancel(application)
        viewModelScope.launch {
            repository.finishSession(id, notes.trim(), perceivedEffort)
            _finished.send(Unit)
        }
    }

    // ---- Rest timer + live notification (foreground service) ------------

    /** Ideal rest after [set]: the routine's own value, else advised per exercise and reps. */
    private fun restAfter(block: BlockUi, set: SetEntry): Int {
        val g = block.toGuide()
        val warmup = set.type == SetType.WARMUP
        g.routineRestSec?.takeIf { it > 0 && !warmup }?.let { return it }
        val reps = if (g.timed) 0 else set.reps.takeIf { it > 0 } ?: g.targetReps ?: 8
        return app.tenet.android.core.common.RestAdvisor.advise(g.pattern, g.primaryMuscles, reps, warmup, set.rpe).seconds
    }

    private fun startRest(totalSec: Int) {
        val id = sessionId ?: return
        // Last set of the workout: no rest, the notification says "geschafft".
        if (_uiState.value.blocks.all { b -> b.rows.all { it.set.completed } }) {
            RestTimerService.guide(application, id)
        } else {
            RestTimerService.start(application, totalSec, id)
        }
    }

    fun addRestSeconds(seconds: Int) {
        if (rest.value == null) return
        RestTimerService.add(application, seconds)
    }

    /** Ends the rest early; the notification switches to the next set. */
    fun cancelRest() {
        RestTimerService.skip(application)
    }

    /** Training mode opened: live notification with the next set. */
    fun startGuide() {
        val id = sessionId ?: return
        if (rest.value == null) RestTimerService.guide(application, id)
    }

    /** Training mode: logs the next set with the entered values and starts its rest. */
    fun logNext(weightKg: Float?, reps: Int?, seconds: Int?) {
        val n = next.value ?: return
        val block = _uiState.value.blocks.getOrNull(n.blockIndex) ?: return
        val row = block.rows.firstOrNull { it.set.id == n.set.id } ?: return
        viewModelScope.launch {
            val done = repository.completeSet(row.set, weightKg, reps, seconds)
            applyRow(block.sessionExerciseId, row.set.id) {
                it.copy(set = done, kgText = formatNumber(done.weight), repsText = if (done.reps > 0) done.reps.toString() else "")
            }
            startRest(restAfter(block, done))
        }
    }

    private fun BlockUi.toGuide() = guideBlock(exercise, target, suggestion, rows.map { it.set })

    private fun applyRow(
        sessionExerciseId: String,
        setId: String,
        transform: (SetRowUi) -> SetRowUi,
    ) {
        _uiState.update { state ->
            state.copy(
                blocks = state.blocks.map { block ->
                    if (block.sessionExerciseId != sessionExerciseId) block
                    else block.copy(rows = block.rows.map {
                        if (it.set.id == setId) transform(it) else it
                    })
                },
            )
        }
    }

    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()

    private fun String.toFloatCompat(): Float? =
        if (isBlank()) null else trim().replace(',', '.').toFloatOrNull() ?: if (isEmpty()) 0f else null

    private fun String.toIntCompat(): Int? =
        if (isBlank()) 0 else trim().replace(',', '.').toFloatOrNull()?.toInt()

    companion object {
        const val DEFAULT_REST_SEC = 120
    }
}
