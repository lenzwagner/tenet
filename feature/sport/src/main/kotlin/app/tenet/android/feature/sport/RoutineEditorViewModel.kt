package app.tenet.android.feature.sport

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RoutineExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineEditorState(
    val loading: Boolean = true,
    val workoutId: String? = null,
    val workoutTitle: String = "Trainingsplan",
    val routine: List<RoutineExercise> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    /** Exercises not yet in the routine, for the add-exercise sheet. */
    val available: List<Exercise> = emptyList(),
    val addSheetVisible: Boolean = false,
    /** All workouts of the split (Push, Pull, Beine …) to switch between. */
    val workouts: List<PlannedWorkout> = emptyList(),
    /** Exercise being swapped (picker open) and its suggested replacements. */
    val swapping: String? = null,
    val suggestions: List<Exercise> = emptyList(),
) {
    val exerciseById: Map<String, Exercise> by lazy { exercises.associateBy { it.id } }
}

/**
 * Editor for the plan of the active workout (App_Konzept.md 5.2.1
 * "Pläne/Routinen"): change target sets/reps/rest, reorder, add and
 * remove exercises. Every change is persisted immediately.
 */
@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val repository: SportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RoutineEditorState())
    val state: StateFlow<RoutineEditorState> = _state

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            refresh()
        }
    }

    private suspend fun refresh(workoutId: String? = _state.value.workoutId) {
        val plan = repository.activePlanOnce() ?: return
        val workouts = repository.workoutsOnce(plan.id)
        // Starts on the workout that is due next; keeps the one picked by the user.
        val workout = workouts.firstOrNull { it.id == workoutId } ?: repository.nextWorkoutOnce(plan.id) ?: return
        val routine = repository.routineOnce(workout.id)
        val exercises = repository.observeExercisesOnce(Discipline.GYM)
        _state.value = RoutineEditorState(
            loading = false,
            workoutId = workout.id,
            workoutTitle = workout.title,
            routine = routine,
            exercises = exercises,
            available = exercises.filter { ex -> routine.none { it.exerciseId == ex.id } },
            workouts = workouts,
        )
    }

    fun selectWorkout(id: String) {
        viewModelScope.launch { refresh(id) }
    }

    fun openSwap(exerciseId: String) {
        _state.update { it.copy(swapping = exerciseId, suggestions = emptyList()) }
        viewModelScope.launch {
            val tips = repository.alternatives(exerciseId)
            _state.update { if (it.swapping == exerciseId) it.copy(suggestions = tips) else it }
        }
    }

    fun closeSwap() = _state.update { it.copy(swapping = null) }

    fun swap(newExerciseId: String) {
        val state = _state.value
        val workoutId = state.workoutId ?: return
        val old = state.swapping ?: return
        _state.update { it.copy(swapping = null) }
        viewModelScope.launch {
            repository.swapRoutineExercise(workoutId, old, newExerciseId)
            refresh()
        }
    }

    fun onTargetChanged(exerciseId: String, field: TargetField, value: Int) {
        _state.update { state ->
            val workoutId = state.workoutId ?: return@update state
            state.copy(
                routine = state.routine.map { target ->
                    if (target.exerciseId != exerciseId) target
                    else when (field) {
                        TargetField.SETS -> target.copy(targetSets = value.coerceIn(1, 20))
                        TargetField.REPS -> target.copy(targetReps = value.coerceIn(1, 100))
                        TargetField.REST -> target.copy(restSec = value.coerceIn(0, 600))
                    }
                },
            )
        }
        persist()
    }

    /** Per-exercise overload rule; nulls restore the defaults. */
    fun setRule(exerciseId: String, repMax: Int?, stepKg: Float?, deloadPercent: Int?) {
        _state.update { state ->
            state.copy(
                routine = state.routine.map { t ->
                    if (t.exerciseId != exerciseId) t
                    else t.copy(
                        repMax = repMax?.takeIf { it > t.targetReps }?.coerceAtMost(50),
                        stepKg = stepKg?.takeIf { it > 0f }?.coerceAtMost(20f),
                        deloadPercent = deloadPercent?.coerceIn(0, 50),
                    )
                },
            )
        }
        persist()
    }

    fun onSupersetToggle(exerciseId: String, group: Int?) {
        _state.update { state ->
            state.copy(
                routine = state.routine.map { target ->
                    if (target.exerciseId == exerciseId) target.copy(supersetGroup = group)
                    else target
                },
            )
        }
        persist()
    }

    fun move(exerciseId: String, up: Boolean) {
        val workoutId = _state.value.workoutId ?: return
        viewModelScope.launch {
            repository.moveRoutineExercise(workoutId, exerciseId, up)
            refresh()
        }
    }

    fun remove(exerciseId: String) {
        val workoutId = _state.value.workoutId ?: return
        viewModelScope.launch {
            repository.deleteRoutineExercise(workoutId, exerciseId)
            refresh()
        }
    }

    fun openAddSheet() = _state.update { it.copy(addSheetVisible = true) }

    fun closeAddSheet() = _state.update { it.copy(addSheetVisible = false) }

    fun add(exerciseId: String) {
        val workoutId = _state.value.workoutId ?: return
        viewModelScope.launch {
            repository.addRoutineExercise(workoutId, exerciseId)
            refresh()
        }
    }

    private fun persist() {
        val state = _state.value
        val workoutId = state.workoutId ?: return
        viewModelScope.launch { repository.replaceRoutine(workoutId, state.routine) }
    }

    enum class TargetField { SETS, REPS, REST }
}
