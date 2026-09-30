package app.tenet.android.feature.sport

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.MeasureType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ExerciseLibraryState(
    val query: String = "",
    val muscleFilter: String? = null,
    val equipmentFilter: String? = null,
    val exercises: List<Exercise> = emptyList(),
) {
    val muscles: List<String> by lazy {
        exercises.flatMap { it.primaryMuscles.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val equipment: List<String> by lazy {
        exercises.map { it.equipment }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filtered: List<Exercise>
        get() = exercises.filter { exercise ->
            val matchesQuery = query.isBlank() ||
                exercise.name.contains(query, ignoreCase = true) ||
                exercise.primaryMuscles.contains(query, ignoreCase = true)
            val matchesMuscle = muscleFilter == null ||
                exercise.primaryMuscles.split(',').any {
                    it.trim().equals(muscleFilter, ignoreCase = true)
                }
            val matchesEquipment = equipmentFilter == null ||
                exercise.equipment == equipmentFilter
            matchesQuery && matchesMuscle && matchesEquipment
        }
}

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: SportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseLibraryState())
    val state: StateFlow<ExerciseLibraryState> = _state

    private val _events = Channel<String>(Channel.BUFFERED)
    /** Emits an error message the screen should surface in a snackbar. */
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            repository.observeExercises(Discipline.GYM)
                .collect { exercises ->
                    _state.value = _state.value.copy(exercises = exercises)
                }
        }
    }

    fun onQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun toggleMuscle(muscle: String) {
        _state.value = _state.value.copy(
            muscleFilter = _state.value.muscleFilter.takeIf { it != muscle } ?: muscle,
        )
    }

    fun toggleEquipment(item: String) {
        _state.value = _state.value.copy(
            equipmentFilter = _state.value.equipmentFilter.takeIf { it != item } ?: item,
        )
    }

    fun clearFilters() {
        _state.value = _state.value.copy(muscleFilter = null, equipmentFilter = null)
    }

    fun save(
        id: String?,
        name: String,
        primaryMuscles: String,
        secondaryMuscles: String,
        equipment: String,
        measureType: MeasureType,
        notes: String,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val existing = id?.let { repository.exerciseById(it) }
            repository.upsertExercise(
                Exercise(
                    id = id ?: newUuid(),
                    name = name.trim(),
                    discipline = Discipline.GYM,
                    primaryMuscles = primaryMuscles.trim(),
                    secondaryMuscles = secondaryMuscles.trim(),
                    equipment = equipment.trim(),
                    measureType = measureType,
                    notes = notes.trim(),
                    custom = existing?.custom ?: true,
                ),
            )
        }
    }

    fun delete(exercise: Exercise) {
        viewModelScope.launch {
            if (!exercise.custom) {
                _events.send("Vorgefertigte Übungen können nicht gelöscht werden.")
                return@launch
            }
            if (!repository.deleteCustomExercise(exercise.id)) {
                _events.send(
                    "Übung steht noch im Trainingsplan. Entferne sie zuerst aus dem Plan.",
                )
            }
        }
    }

    fun isInRoutine(exerciseId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.isExerciseInRoutine(exerciseId)) }
    }
}
