package app.tenet.android.feature.sport.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.WeekPlanRepository
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.datastore.UserSettingsRepository
import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.component.TenetSpinner
import app.tenet.android.core.designsystem.component.TenetSwitch
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.theme.TenetCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun rules(names: Set<String>): Set<WeekPlanRepository.Rule> =
    names.mapNotNull { n -> WeekPlanRepository.Rule.entries.firstOrNull { it.name == n } }.toSet()

/**
 * Keeps the plans in step while the app is open: whenever a plan, its days,
 * the rest days or a rule change – a new plan from the setup, the AI coach or
 * built by hand, a switch to another plan – the active plans are brought back
 * in line. Lives app-wide (created in the app shell).
 */
@HiltViewModel
class PlanSyncViewModel @Inject constructor(
    private val week: WeekPlanRepository,
    settings: UserSettingsRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            combine(
                week.observe(),
                settings.settings.map { it.restDays to rules(it.planRules) }.distinctUntilChanged(),
            ) { _, s -> s }.collectLatest { (rest, rules) ->
                if (rest.isNotEmpty() || rules.isNotEmpty()) runCatching { week.enforce(rest, rules) }
            }
        }
    }

    /** Touching the view model starts it. */
    fun start() = Unit
}

data class WeekPlanState(
    val week: WeekPlanRepository.Week,
    val rest: Set<Int>,
    val rules: Set<WeekPlanRepository.Rule>,
)

@HiltViewModel
class WeekPlanViewModel @Inject constructor(
    private val week: WeekPlanRepository,
    private val settings: UserSettingsRepository,
) : ViewModel() {
    val state: StateFlow<WeekPlanState?> = combine(week.observe(), settings.settings) { w, s ->
        WeekPlanState(w, s.restDays, rules(s.planRules))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun blocked(discipline: Discipline, s: WeekPlanState) = week.blocked(discipline, s.week, s.rest, s.rules)

    fun setRest(day: Int, rest: Boolean) = viewModelScope.launch { settings.setRestDay(day, rest) }
    fun setRule(rule: WeekPlanRepository.Rule, on: Boolean) = viewModelScope.launch { settings.setPlanRule(rule.name, on) }
    fun toggle(discipline: Discipline, current: Set<Int>, day: Int) = viewModelScope.launch {
        // Stored empty = every day: the first tap turns that into "all but this one".
        val base = current.ifEmpty { (1..7).toSet() }
        val next = if (day in base) base - day else base + day
        if (next.isNotEmpty()) week.setDays(discipline, next)
    }
    fun moveRun(from: Int, to: Int) = viewModelScope.launch { week.moveRunDay(from, to) }
}

/**
 * One week for all sports: rest days that hold for every plan, which sport
 * trains on which day, and rules between them. Changes go straight into the
 * active plans and stay in force when a plan is replaced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanScreen(onBack: () -> Unit, viewModel: WeekPlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Running: tap a run day, then the day it should move to.
    var moving by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text("Wochenplan") },
                subtitle = { Text("Alle Sportarten aufeinander abstimmen") },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { TenetSpinner() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "rest") {
                TenetCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CardHeader(Icons.Outlined.Bedtime, "Ruhetage")
                        DayRow(
                            selected = s.rest,
                            blocked = emptySet(),
                            color = MaterialTheme.colorScheme.tertiary,
                            label = "Ruhetag",
                        ) { day -> viewModel.setRest(day, day !in s.rest) }
                        Hint("An Ruhetagen plant keine Sportart ein Training. Läufe rücken auf den nächsten freien Tag.")
                    }
                }
            }
            item(key = "week") {
                TenetCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardHeader(Icons.Outlined.FitnessCenter, "Trainingstage")
                        val w = s.week
                        if (w.gym == null && w.cali == null && w.run == null) {
                            Hint("Noch kein aktiver Plan. Leg unter „Pläne“ einen an – er übernimmt Ruhetage und Regeln automatisch.")
                        }
                        w.gym?.let { days ->
                            SportRow(Icons.Outlined.FitnessCenter, "Gym", w.gymPlan?.name, days.ifEmpty { (1..7).toSet() } - viewModel.blocked(Discipline.GYM, s), viewModel.blocked(Discipline.GYM, s)) { day ->
                                viewModel.toggle(Discipline.GYM, days, day)
                            }
                        }
                        w.cali?.let { days ->
                            SportRow(Icons.Outlined.SelfImprovement, "Calisthenics", w.caliPlan?.name, days.ifEmpty { (1..7).toSet() } - viewModel.blocked(Discipline.CALISTHENICS, s), viewModel.blocked(Discipline.CALISTHENICS, s)) { day ->
                                viewModel.toggle(Discipline.CALISTHENICS, days, day)
                            }
                        }
                        w.run?.let { days ->
                            SportRow(
                                Icons.AutoMirrored.Outlined.DirectionsRun,
                                "Laufen",
                                w.runPlan?.name,
                                days,
                                viewModel.blocked(Discipline.RUNNING, s),
                                highlight = moving,
                            ) { day ->
                                val from = moving
                                when {
                                    from == null && day in days -> moving = day
                                    from != null && day !in days -> {
                                        viewModel.moveRun(from, day)
                                        moving = null
                                    }
                                    else -> moving = null
                                }
                            }
                            Hint(
                                if (moving != null) "Jetzt den Tag antippen, auf den der Lauf wandern soll."
                                else "Lauf verschieben: erst den Lauftag antippen, dann den neuen Tag. Die Anzahl der Läufe pro Woche bleibt.",
                            )
                        }
                    }
                }
            }
            item(key = "rules") {
                TenetCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardHeader(Icons.Outlined.Rule, "Regeln")
                        WeekPlanRepository.Rule.entries.forEach { rule ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rule.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(rule.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(12.dp))
                                TenetSwitch(checked = rule in s.rules, onCheckedChange = { viewModel.setRule(rule, it) })
                            }
                        }
                    }
                }
            }
            item(key = "note") {
                Hint(
                    "Gilt für alle Pläne – aus dem Setup, vom KI-Coach und selbst gebaute. Wechselst du den Plan oder legst einen neuen an, " +
                        "werden seine Tage automatisch an Ruhetage und Regeln angepasst.",
                    Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun SportRow(
    icon: ImageVector,
    name: String,
    plan: String?,
    selected: Set<Int>,
    blocked: Set<Int>,
    highlight: Int? = null,
    onDay: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.titleSmall)
            plan?.let {
                Text(" · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        DayRow(selected, blocked, MaterialTheme.colorScheme.primary, name, highlight, onDay)
    }
}

/** Seven round day toggles Mo–So; [blocked] days are greyed out and do nothing. */
@Composable
private fun DayRow(
    selected: Set<Int>,
    blocked: Set<Int>,
    color: Color,
    label: String,
    highlight: Int? = null,
    onDay: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            val on = day in selected
            val off = day in blocked && !on
            val short = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2)
            val full = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.GERMAN)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            day == highlight -> MaterialTheme.colorScheme.tertiary
                            on -> color
                            else -> app.tenet.android.core.designsystem.component.tenetFill
                        },
                    )
                    .then(
                        if (off) Modifier else Modifier.clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onDay(day)
                        },
                    )
                    .semantics { contentDescription = "$label $full: " + if (on) "ja" else if (off) "gesperrt" else "nein" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    short,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        on || day == highlight -> MaterialTheme.colorScheme.onPrimary
                        off -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
