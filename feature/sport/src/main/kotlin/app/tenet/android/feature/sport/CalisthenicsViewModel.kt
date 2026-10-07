package app.tenet.android.feature.sport

import app.tenet.android.core.data.WeekCalendarRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.SkillMath
import app.tenet.android.core.data.CalisthenicsOverview
import app.tenet.android.core.data.SkillRepository
import app.tenet.android.core.database.entity.CriterionType
import app.tenet.android.core.database.entity.SessionMode
import app.tenet.android.core.database.entity.SkillStep
import app.tenet.android.core.database.entity.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ladder row state of one skill step. */
enum class StepState { MASTERED, CURRENT, LOCKED }

/** One row of the skill-tree ladder. */
data class StepUi(
    val step: SkillStep,
    val state: StepState,
    /** Best value reached so far, e.g. "15 s" or "12 Wdh"; null = none. */
    val bestText: String? = null,
)

/** One entry of the "Stufenaufstiege" timeline. */
data class AchievementUi(
    val skillName: String,
    val stepLabel: String,
    val achievedAt: Long,
)

/** One card of the skill carousel. */
data class SkillCardUi(
    val skillId: String,
    val name: String,
    val stepLabel: String,
    /** 1-based index of the current step. */
    val stepNumber: Int,
    val stepCount: Int,
    /** Best value vs. the current step's criterion, 0..1. */
    val criterionProgress: Float,
    val bestText: String?,
    val targetText: String,
)

data class CalisthenicsUiState(
    val overview: CalisthenicsOverview = CalisthenicsOverview(),
    val selectedSkillId: String? = null,
    val ladder: List<StepUi> = emptyList(),
    val currentStep: SkillStep? = null,
    val nextStep: SkillStep? = null,
    /** Criterion met in two sessions -> promotion may be confirmed. */
    val suggestPromotion: Boolean = false,
    val timeline: List<AchievementUi> = emptyList(),
    val skillCards: List<SkillCardUi> = emptyList(),
)

/** Where a calisthenics session should open: which screen hosts it. */
enum class CsNavKind {
    /** Single-exercise skill session with hold timer. */
    SKILL,

    /** Strength session in classic sets (generic session screen). */
    STRENGTH_SETS,

    /** Strength session as circuit/EMOM (interval workout screen). */
    STRENGTH_INTERVAL,
}

data class CsNavEvent(
    val sessionId: String,
    val kind: CsNavKind,
)

@HiltViewModel
class CalisthenicsViewModel @Inject constructor(
    private val repository: SkillRepository,
    private val weekCalendarRepository: WeekCalendarRepository,
    private val planRepository: app.tenet.android.core.data.PlanRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { repository.ensureSeedData() }
    }

    private val overview: StateFlow<CalisthenicsOverview> = repository
        .observeCalisthenicsOverview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalisthenicsOverview())

    private val selectedSkillId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CalisthenicsUiState> =
        combine(overview, selectedSkillId) { ov, selected -> buildState(ov, selected) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalisthenicsUiState())

    /** Form videos of the selected skill, newest first (local only). */
    val formVideos: StateFlow<List<app.tenet.android.core.database.entity.FormVideo>> =
        combine(repository.allFormVideos(), uiState) { videos, state ->
            videos.filter { it.skillId == state.selectedSkillId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteFormVideo(video: app.tenet.android.core.database.entity.FormVideo) {
        viewModelScope.launch { repository.deleteFormVideo(video) }
    }

    private fun buildState(ov: CalisthenicsOverview, selected: String?): CalisthenicsUiState {
        val skill = ov.skills.firstOrNull { it.id == selected } ?: ov.skills.firstOrNull()
        val steps = ov.steps
            .filter { it.skillId == skill?.id }
            .sortedBy { it.sortOrder }
        val current = steps.firstOrNull { step ->
            step.id == ov.progress.firstOrNull { it.skillId == skill?.id }?.currentStepId
        } ?: steps.firstOrNull()
        val currentIndex = steps.indexOfFirst { it.id == current?.id }

        val ladder = steps.mapIndexed { index, step ->
            val best = ov.bests[step.exerciseId]
            val isHold = step.criterionType == CriterionType.HOLD
            val bestValue = if (isHold) best?.bestHold else best?.bestReps
            StepUi(
                step = step,
                state = when {
                    index < currentIndex -> StepState.MASTERED
                    index == currentIndex -> StepState.CURRENT
                    else -> StepState.LOCKED
                },
                bestText = bestValue?.let { if (isHold) "$it s" else "$it Wdh" },
            )
        }

        val achievementsForCurrent = if (current == null) {
            0
        } else {
            ov.achievements.count { it.stepId == current.id }
        }
        val next = steps.getOrNull(currentIndex + 1)

        val stepById = ov.steps.associateBy { it.id }
        val skillById = ov.skills.associateBy { it.id }
        val timeline = ov.achievements
            .sortedByDescending { it.achievedAt }
            .mapNotNull { achievement ->
                val step = stepById[achievement.stepId] ?: return@mapNotNull null
                val achievementSkill = skillById[step.skillId] ?: return@mapNotNull null
                AchievementUi(
                    skillName = achievementSkill.name,
                    stepLabel = step.label,
                    achievedAt = achievement.achievedAt,
                )
            }

        val skillCards = ov.skills.sortedBy { it.sortOrder }.mapNotNull { sk ->
            val skSteps = ov.steps.filter { it.skillId == sk.id }.sortedBy { it.sortOrder }
            if (skSteps.isEmpty()) return@mapNotNull null
            val cur = skSteps.firstOrNull { st -> st.id == ov.progress.firstOrNull { it.skillId == sk.id }?.currentStepId }
                ?: skSteps.first()
            val hold = cur.criterionType == CriterionType.HOLD
            val best = ov.bests[cur.exerciseId]?.let { if (hold) it.bestHold else it.bestReps }
            SkillCardUi(
                skillId = sk.id,
                name = sk.name,
                stepLabel = cur.label,
                stepNumber = skSteps.indexOf(cur) + 1,
                stepCount = skSteps.size,
                criterionProgress = ((best ?: 0).toFloat() / cur.criterionValue.coerceAtLeast(1)).coerceIn(0f, 1f),
                bestText = best?.let { if (hold) "$it s" else "$it Wdh" },
                targetText = "${cur.criterionSets} × ${cur.criterionValue}${if (hold) " s" else ""}",
            )
        }

        return CalisthenicsUiState(
            skillCards = skillCards,
            overview = ov,
            selectedSkillId = skill?.id,
            ladder = ladder,
            currentStep = current,
            nextStep = next,
            suggestPromotion = next != null &&
                SkillMath.shouldSuggestPromotion(achievementsForCurrent),
            timeline = timeline,
        )
    }

    fun selectSkill(skillId: String) {
        selectedSkillId.value = skillId
    }

    fun confirmPromotion() {
        val skillId = uiState.value.selectedSkillId ?: return
        viewModelScope.launch { repository.confirmPromotion(skillId) }
    }

    private val _sessionEvents = Channel<CsNavEvent>(Channel.BUFFERED)
    val sessionEvents = _sessionEvents.receiveAsFlow()

    private fun navKindFor(session: WorkoutSession): CsNavKind = when {
        session.plannedWorkoutId == null -> CsNavKind.SKILL
        (session.mode ?: SessionMode.SETS) == SessionMode.SETS -> CsNavKind.STRENGTH_SETS
        else -> CsNavKind.STRENGTH_INTERVAL
    }

    /**
     * FAB action: resumes whatever calisthenics session is active, or
     * starts the skill session of the selected skill.
     */
    fun startOrResume() {
        uiState.value.overview.activeSession?.let { active ->
            viewModelScope.launch { _sessionEvents.send(CsNavEvent(active.id, navKindFor(active))) }
            return
        }
        val skillId = uiState.value.selectedSkillId ?: return
        viewModelScope.launch {
            repository.startSkillSession(skillId)
                ?.let { _sessionEvents.send(CsNavEvent(it, CsNavKind.SKILL)) }
        }
    }

    /** Resumes the active session (TodayCard button). */
    fun resumeSession() {
        val active = uiState.value.overview.activeSession ?: return
        viewModelScope.launch { _sessionEvents.send(CsNavEvent(active.id, navKindFor(active))) }
    }

    /** Starts the skill session of the selected skill (TodayCard button). */
    fun startSkillSession() {
        val skillId = uiState.value.selectedSkillId ?: return
        viewModelScope.launch {
            repository.startSkillSession(skillId)
                ?.let { _sessionEvents.send(CsNavEvent(it, CsNavKind.SKILL)) }
        }
    }

    /** Starts the strength block in the chosen format (mode sheet). */
    fun startStrengthSession(
        mode: SessionMode,
        rounds: Int? = null,
        workSec: Int? = null,
        restSec: Int? = null,
        intervalSec: Int? = null,
    ) {
        viewModelScope.launch {
            val id = repository.startStrengthSession(
                mode = mode,
                rounds = rounds,
                workSec = workSec,
                restSec = restSec,
                intervalSec = intervalSec,
            ) ?: return@launch
            _sessionEvents.send(
                CsNavEvent(
                    sessionId = id,
                    kind = if (mode == SessionMode.SETS) {
                        CsNavKind.STRENGTH_SETS
                    } else {
                        CsNavKind.STRENGTH_INTERVAL
                    },
                ),
            )
        }
    }

    fun discardActiveSession() {
        viewModelScope.launch { repository.discardActiveSession() }
    }

    /** Fixed training weekdays of the active plan (empty = every day). */
    fun setTrainingDays(planId: String, days: Set<java.time.DayOfWeek>) {
        viewModelScope.launch { weekCalendarRepository.setTrainingDays(planId, days) }
    }
    /** Every plan of this discipline (active first) for switching. */
    val plans: kotlinx.coroutines.flow.StateFlow<List<app.tenet.android.core.database.entity.TrainingPlan>> =
        planRepository.observePlans(app.tenet.android.core.database.entity.Discipline.CALISTHENICS)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    fun switchPlan(planId: String) {
        viewModelScope.launch { planRepository.activate(app.tenet.android.core.database.entity.Discipline.CALISTHENICS, planId) }
    }

}
