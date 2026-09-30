package app.tenet.android.feature.sport

import app.tenet.android.feature.sport.run.RunPlanUiBuilder
import app.tenet.android.feature.sport.run.PlanRunUnit
import app.tenet.android.feature.sport.run.PlanWeekUi
import app.tenet.android.core.data.ai.AiFiller
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import app.tenet.android.core.database.entity.WorkoutSession
import app.tenet.android.core.database.entity.PersonalBest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.RunningOverview
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.data.health.HealthConnectRepository
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import app.tenet.android.core.common.RunVolumeMath
import kotlinx.coroutines.launch

/** One planned run of the displayed week, ready for the UI. */
data class PlannedRunUi(
    val dayIndex: Int,
    val title: String,
    val targetDurationSec: Int?,
    val targetPaceSecPerKm: Int?,
)

data class RunningUiState(
    val overview: RunningOverview = RunningOverview(),
    val paceMethod: PaceMethod = PaceMethod.VDOT,
    /** Anchor Monday of the active plan. */
    val planStart: LocalDate? = null,
    val goalDate: LocalDate? = null,
    /** Total weeks of the plan (derived like at creation time). */
    val weeks: Int = 0,
    /** 0-based week currently being trained. */
    val currentWeek: Int = 0,
    /** Units of [currentWeek], Monday first. */
    val weekRuns: List<PlannedRunUi> = emptyList(),
    /** Last 8 weeks incl. the current one, oldest first. */
    val weeklyVolume: List<RunVolumeMath.Period> = emptyList(),
    /** Last 6 months incl. the current one, oldest first. */
    val monthlyVolume: List<RunVolumeMath.Period> = emptyList(),
    val jumpWarning: RunVolumeMath.JumpWarning? = null,
    /** Record per standard distance. */
    val records: List<PersonalBest> = emptyList(),
    /** Whole plan week by week, with status and structure (Runna-style). */
    val planWeeks: List<PlanWeekUi> = emptyList(),
    /** Today's run or the next open one. */
    val nextUnit: PlanRunUnit? = null,
    val loading: Boolean = true,
)

/**
 * Backs the running page (App_Konzept.md 5.2.3: plan templates by goal,
 * weekly structure, moderate progression). There is no seed plan — the
 * user creates one from the setup sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RunningViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val aiFiller: AiFiller,
    settingsRepository: UserSettingsRepository,
    private val healthConnect: HealthConnectRepository,
) : ViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Pull to refresh: imports new runs from Health Connect (when connected). */
    fun refresh(onMessage: (String) -> Unit) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            if (!healthConnect.status.value.enabled) {
                onMessage("Health Connect ist nicht verbunden – in den Einstellungen aktivieren.")
            } else {
                healthConnect.sync().fold(
                    onSuccess = { n -> onMessage(if (n > 0) "$n neue Läufe aus Health Connect" else "Alles aktuell") },
                    onFailure = { onMessage(it.message ?: "Abgleich fehlgeschlagen") },
                )
            }
            _refreshing.value = false
        }
    }

    fun importGpx(xml: String, onDone: (String?) -> Unit) {
        viewModelScope.launch { onDone(runCatching { repository.importGpx(xml) }.getOrNull()) }
    }

    private val paceMethodFlow = settingsRepository.settings.map { it.paceMethod }

    val uiState: StateFlow<RunningUiState> = paceMethodFlow
        .flatMapLatest { method ->
            combine(repository.observeOverview(), repository.observeVolumeRuns(), repository.observeRecords()) { overview, runs, records ->
                val today = LocalDate.now()
                val weekly = RunVolumeMath.weekly(runs, today)
                buildState(overview, method).copy(
                    weeklyVolume = weekly,
                    monthlyVolume = RunVolumeMath.monthly(runs, today),
                    jumpWarning = RunVolumeMath.jumpWarning(weekly),
                    records = records,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunningUiState())

    private fun buildState(overview: RunningOverview, method: PaceMethod): RunningUiState {
        val planStart = overview.plan?.startDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val goalDate = overview.plan?.endDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val weeks = if (planStart != null && goalDate != null) {
            RunPlanMath.weekCount(planStart, goalDate)
        } else {
            0
        }
        val currentWeek = if (planStart == null || weeks == 0) {
            0
        } else {
            ChronoUnit.WEEKS.between(planStart, WeekMath.weekStart(LocalDate.now()))
                .toInt()
                .coerceIn(0, weeks - 1)
        }
        val weekRuns = overview.plannedUnits
            .filter { it.planned.weekIndex == currentWeek }
            .sortedBy { it.planned.dayIndex ?: 0 }
            .map { row ->
                PlannedRunUi(
                    dayIndex = row.planned.dayIndex ?: 0,
                    title = row.planned.title,
                    targetDurationSec = row.runWorkout?.targetDurationSec,
                    targetPaceSecPerKm = row.runWorkout?.targetPaceSecPerKm,
                )
            }
        val planMethod = overview.detail?.paceMethodId?.let { PaceMethod.fromId(it) } ?: method
        val planWeeks = RunPlanUiBuilder.build(overview, planMethod)
        return RunningUiState(
            planWeeks = planWeeks,
            nextUnit = RunPlanUiBuilder.nextUnit(planWeeks),
            overview = overview,
            paceMethod = method,
            planStart = planStart,
            goalDate = goalDate,
            weeks = weeks,
            currentWeek = currentWeek,
            weekRuns = weekRuns,
            loading = false,
        )
    }

    fun moveRun(plannedId: String, dayIndex: Int) {
        viewModelScope.launch { repository.movePlannedRun(plannedId, dayIndex) }
    }

    fun setSkipped(plannedId: String, skipped: Boolean) {
        viewModelScope.launch { repository.setPlannedRunSkipped(plannedId, skipped) }
    }

    private val _unfinished = MutableStateFlow<WorkoutSession?>(null)
    /** GPS run left open by a killed app (no live recording running). */
    val unfinished: StateFlow<WorkoutSession?> = _unfinished

    fun checkUnfinished(recording: Boolean) {
        viewModelScope.launch {
            _unfinished.value = if (recording) null else repository.unfinishedRecordedRun()
        }
    }

    fun saveUnfinished(onSaved: (String) -> Unit) {
        val session = _unfinished.value ?: return
        viewModelScope.launch {
            _unfinished.value = null
            if (repository.finishRecordedRun(session.id)) onSaved(session.id)
        }
    }

    fun discardUnfinished() {
        val session = _unfinished.value ?: return
        viewModelScope.launch {
            _unfinished.value = null
            repository.deleteRun(session.id)
        }
    }

    val aiAvailable: Boolean get() = aiFiller.enabled

    /** Dictated run ("gestern 8 km in 45 Minuten, Puls 150") → sheet fields. */
    suspend fun aiRun(text: String): AiFiller.RunFill? = aiFiller.run(text)

    fun addManualRun(start: LocalDateTime, distanceM: Float, durationSec: Int, avgHr: Int?, notes: String, onSaved: (String) -> Unit) {
        viewModelScope.launch { onSaved(repository.addManualRun(start, distanceM, durationSec, avgHr, notes)) }
    }

    fun createPlan(
        goal: RunPlanMath.RunGoal,
        goalDate: LocalDate,
        runsPerWeek: Int,
        current5kSec: Int?,
        taper: Boolean = true,
        targetTimeSec: Int? = null,
    ) {
        viewModelScope.launch {
            repository.createPlan(
                goal = goal,
                goalDate = goalDate,
                runsPerWeek = runsPerWeek,
                current5kSec = current5kSec,
                paceMethod = uiState.value.paceMethod,
                taper = taper,
                targetTimeSec = targetTimeSec,
            )
        }
    }
}
