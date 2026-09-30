package app.tenet.android.feature.sport.run

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.RunAnalysis
import app.tenet.android.core.data.RunDetail
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Route value for "Freier Lauf": ignore today's planned unit. */
const val FREE_RUN = "free"

/** Planned unit a live run follows (title, target, guided intervals). */
data class PlannedRunInfo(
    val id: String,
    val title: String,
    val intervalsJson: String?,
    val targetPaceSecPerKm: Int?,
    val targetDistanceM: Int?,
    val targetDurationSec: Int?,
    /** Tempo block length for guided tempo runs (0 = none). */
    val tempoSec: Int = 0,
)

@HiltViewModel
class ActiveRunViewModel @Inject constructor(
    private val repository: RunningRepository,
) : ViewModel() {

    private val _planned = MutableStateFlow<PlannedRunInfo?>(null)
    val planned: StateFlow<PlannedRunInfo?> = _planned.asStateFlow()
    private var loaded = false

    /** [plannedId] from the route; empty = today's open planned run, if any. */
    fun load(plannedId: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val pair = when (plannedId) {
                FREE_RUN -> null
                "" -> repository.todaysPlannedRun()
                else -> repository.plannedRun(plannedId)
            }
            _planned.value = pair?.let { (workout, run) ->
                PlannedRunInfo(
                    id = workout.id,
                    title = workout.title,
                    intervalsJson = run?.intervalsJson,
                    targetPaceSecPerKm = run?.targetPaceSecPerKm,
                    targetDistanceM = run?.targetDistanceM,
                    targetDurationSec = run?.targetDurationSec,
                    tempoSec = if (run?.runType?.name == "TEMPO" && run.intervalsJson == null && !workout.title.startsWith("Wettkampf")) {
                        run.targetDurationSec ?: 0
                    } else 0,
                )
            }
        }
    }

    fun skipPlanned() {
        _planned.value = null
    }
}

/** Run detail with the track-derived analysis. */
data class RunDetailUi(
    val detail: RunDetail? = null,
    val track: List<RunAnalysis.Point> = emptyList(),
    val profile: List<RunAnalysis.ProfilePoint> = emptyList(),
    val zoneSeconds: IntArray? = null,
    val maxHr: Int = 0,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RunDetailViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {

    /** GPX of this run (null without a GPS track). */
    suspend fun gpx(sessionId: String): String? = repository.exportGpx(sessionId)


    private data class TrackData(
        val points: List<RunAnalysis.Point>,
        val profile: List<RunAnalysis.ProfilePoint>,
        val zones: IntArray?,
        val maxHr: Int,
    )

    private val sessionId = MutableStateFlow<String?>(null)
    private val track = MutableStateFlow<TrackData?>(null)

    val state: StateFlow<RunDetailUi> = sessionId
        .filterNotNull()
        .flatMapLatest { id -> repository.observeRunDetail(id) }
        .combine(track) { detail, t ->
            RunDetailUi(
                detail = detail,
                track = t?.points.orEmpty(),
                profile = t?.profile.orEmpty(),
                zoneSeconds = t?.zones,
                maxHr = t?.maxHr ?: 0,
                loading = t == null,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunDetailUi())

    private val _planUnit = MutableStateFlow<Pair<String, String>?>(null)
    /** Plan unit this run completed: planned id to "title · Woche n". */
    val planUnit: StateFlow<Pair<String, String>?> = _planUnit.asStateFlow()

    fun load(id: String) {
        if (sessionId.value == id) return
        sessionId.value = id
        viewModelScope.launch {
            repository.observeRunDetail(id).first()?.row?.session?.plannedWorkoutId?.let { plannedId ->
                repository.plannedRun(plannedId)?.let { (workout, _) ->
                    _planUnit.value = plannedId to (workout.title + (workout.weekIndex?.let { " · Woche ${it + 1}" } ?: ""))
                }
            }
        }
        viewModelScope.launch {
            val age = settingsRepository.settings.first().profile?.age ?: 30
            val maxHr = RunAnalysis.estimatedMaxHr(age)
            val points = repository.trackOnce(id)
            track.value = TrackData(
                points = points,
                profile = RunAnalysis.profile(points),
                zones = if (points.any { it.hr != null }) RunAnalysis.hrZoneSeconds(points, maxHr) else null,
                maxHr = maxHr,
            )
        }
    }

    fun saveNotes(notes: String) {
        val id = sessionId.value ?: return
        viewModelScope.launch { repository.updateRunNotes(id, notes) }
    }

    fun delete(onDone: () -> Unit) {
        val id = sessionId.value ?: return
        viewModelScope.launch {
            repository.deleteRun(id)
            onDone()
        }
    }
}
