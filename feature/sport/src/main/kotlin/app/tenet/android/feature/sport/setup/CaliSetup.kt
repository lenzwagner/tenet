package app.tenet.android.feature.sport.setup

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.CaliPlanBuilder
import app.tenet.android.core.data.CaliSetupData
import app.tenet.android.core.data.SkillRepository
import app.tenet.android.core.database.entity.CriterionType
import app.tenet.android.core.datastore.UserSettingsRepository
import app.tenet.android.feature.sport.TrainingDaysRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaliSetupState(
    val step: Int = 0,
    val pushUps: String = "",
    val pullUps: String = "",
    val dips: String = "",
    val squats: String = "",
    val plank: String = "",
    /** skillId → current step id (absent = not started, first step). */
    val currentSteps: Map<String, String> = emptyMap(),
    val focus: List<String> = emptyList(),
    val days: Set<Int> = setOf(1, 3, 5),
    val data: CaliSetupData? = null,
    val busy: Boolean = false,
) {
    fun input(builderSteps: List<CaliPlanBuilder.Step>) = CaliPlanBuilder.Input(
        maxes = CaliPlanBuilder.Maxes(
            pushUps = pushUps.toIntOrNull() ?: 0,
            pullUps = pullUps.toIntOrNull() ?: 0,
            dips = dips.toIntOrNull() ?: 0,
            squats = squats.toIntOrNull() ?: 0,
            plankSec = plank.toIntOrNull() ?: 30,
        ),
        currentSteps = currentSteps,
        focusSkills = focus,
        steps = builderSteps,
    )
}

@HiltViewModel
class CaliSetupViewModel @Inject constructor(
    private val repository: SkillRepository,
    private val settings: UserSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CaliSetupState())
    val state: StateFlow<CaliSetupState> = _state
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    init {
        viewModelScope.launch {
            val data = repository.setupData()
            _state.update { it.copy(data = data, currentSteps = data.currentSteps) }
        }
    }

    fun steps(): List<CaliPlanBuilder.Step> = repository.builderSteps(_state.value.data?.steps.orEmpty())

    fun update(block: CaliSetupState.() -> CaliSetupState) = _state.update(block)

    fun finish() {
        val s = _state.value
        if (s.busy || s.data == null) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repository.applyCaliSetup(CaliPlanBuilder.build(s.input(steps())), s.days.toList())
            settings.markSportSetupDone("CALISTHENICS")
            _done.emit(Unit)
        }
    }
}

private val STEPS = listOf(
    "Was schaffst du am Stück?",
    "Wo stehst du bei den Skills?",
    "Worauf willst du hinarbeiten?",
    "An welchen Tagen trainierst du?",
    "Dein Plan",
)

private const val MAX_FOCUS = 3

@Composable
fun CaliSetupScreen(onDone: () -> Unit, viewModel: CaliSetupViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }
    SetupScaffold(
        title = "Calisthenics einrichten",
        step = s.step,
        stepTitles = STEPS,
        canContinue = s.data != null && (s.step != 3 || s.days.isNotEmpty()),
        busy = s.busy,
        finishLabel = "Plan erstellen",
        onStep = { viewModel.update { copy(step = it) } },
        onClose = onDone,
        onFinish = viewModel::finish,
    ) { step ->
        when (step) {
            0 -> {
                SetupHint("Saubere Wiederholungen ohne Pause. 0 ist völlig okay – dann startest du mit einer leichteren Variante.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(s.pushUps, { viewModel.update { copy(pushUps = it) } }, "Liegestütze", Modifier.weight(1f))
                    NumberField(s.pullUps, { viewModel.update { copy(pullUps = it) } }, "Klimmzüge", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(s.dips, { viewModel.update { copy(dips = it) } }, "Dips", Modifier.weight(1f))
                    NumberField(s.squats, { viewModel.update { copy(squats = it) } }, "Kniebeugen", Modifier.weight(1f))
                }
                NumberField(s.plank, { viewModel.update { copy(plank = it) } }, "Plank halten", Modifier.fillMaxWidth(), suffix = "s")
            }
            1 -> {
                SetupHint("Wähle pro Skill die Stufe, an der du gerade arbeitest.")
                val data = s.data ?: return@SetupScaffold
                data.skills.sortedBy { it.sortOrder }.forEach { skill ->
                    val steps = data.steps.filter { it.skillId == skill.id }.sortedBy { it.sortOrder }
                    val current = s.currentSteps[skill.id] ?: steps.firstOrNull()?.id
                    Text(skill.name, style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        steps.forEach { step ->
                            app.tenet.android.core.designsystem.component.TenetFilterChip(
                                selected = step.id == current,
                                onClick = { viewModel.update { copy(currentSteps = currentSteps + (skill.id to step.id)) } },
                                label = { Text(step.label) },
                            )
                        }
                    }
                }
            }
            2 -> {
                SetupHint("Diese Skills übst du in jedem Training zuerst, solange du frisch bist. Höchstens $MAX_FOCUS.")
                val data = s.data ?: return@SetupScaffold
                data.skills.sortedBy { it.sortOrder }.forEach { skill ->
                    val checked = skill.id in s.focus
                    val stepLabel = data.steps.firstOrNull { it.id == (s.currentSteps[skill.id]) }?.label
                        ?: data.steps.filter { it.skillId == skill.id }.minByOrNull { it.sortOrder }?.label
                    TenetCard(
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                enabled = checked || s.focus.size < MAX_FOCUS,
                                role = Role.Checkbox,
                                onValueChange = { on ->
                                    viewModel.update { copy(focus = if (on) focus + skill.id else focus - skill.id) }
                                },
                            ),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, style = MaterialTheme.typography.titleMedium)
                                stepLabel?.let { Text("Stufe: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            app.tenet.android.core.designsystem.component.TenetCheckbox(checked = checked, onCheckedChange = null, enabled = checked || s.focus.size < MAX_FOCUS)
                        }
                    }
                }
            }
            3 -> {
                TrainingDaysRow(
                    value = s.days.sorted().joinToString(","),
                    onChange = { set -> viewModel.update { copy(days = set.map { it.value }.toSet()) } },
                )
                SetupHint("Für Einsteiger reichen 2–3 Tage. Zwischen zwei Einheiten ein Ruhetag hilft Sehnen und Gelenken.")
            }
            else -> {
                val data = s.data ?: return@SetupScaffold
                val plan = CaliPlanBuilder.build(s.input(viewModel.steps()))
                val holds = data.steps.filter { it.criterionType == CriterionType.HOLD }.map { it.exerciseId }.toSet() + "ex-cs-hollow"
                TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Skill + Kraft", style = MaterialTheme.typography.titleMedium)
                        plan.routine.forEach { ex ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(data.names[ex.exerciseId] ?: ex.exerciseId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${ex.sets} × ${ex.target}" + if (ex.exerciseId in holds) " s" else "",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                SetupHint(
                    "Die Zielwerte liegen bei etwa 70 % deines Maximums. Schaffst du alle Sätze sauber, " +
                        "steigerst du die Wiederholungen; der Skill-Baum schlägt dir die nächste Stufe vor.",
                )
            }
        }
    }
}
