package app.tenet.android.feature.sport.run

import androidx.compose.material.icons.outlined.Restaurant
import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.common.RunWorkoutStructure.Kind
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.datastore.UserSettingsRepository
import app.tenet.android.core.designsystem.component.SegmentedRows
import app.tenet.android.core.designsystem.component.TenetLoading
import app.tenet.android.core.designsystem.component.TooltipIconButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RunWorkoutViewModel @Inject constructor(
    private val repository: RunningRepository,
    settings: UserSettingsRepository,
) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)

    val unit: StateFlow<PlanRunUnit?> = combine(repository.observeOverview(), settings.settings, id) { overview, s, plannedId ->
        val method = overview.detail?.paceMethodId?.let { PaceMethod.fromId(it) } ?: s.paceMethod
        RunPlanUiBuilder.build(overview, method).flatMap { it.units }.firstOrNull { it.id == plannedId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Carbs and water before / during this run, scaled by the profile's weight. */
    val fueling: StateFlow<app.tenet.android.core.common.RunFueling.Advice?> =
        combine(unit, repository.observeOverview(), settings.settings) { u, overview, s ->
            u ?: return@combine null
            app.tenet.android.core.common.RunFueling.advise(
                durationSec = u.workout.estDurationSec,
                zone = u.zone,
                race = u.race,
                weightKg = s.profile?.weightKg,
                marathon = overview.detail?.goalId == app.tenet.android.core.common.RunPlanMath.RunGoal.MARATHON.name,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(plannedId: String) {
        id.value = plannedId
    }

    fun move(dayIndex: Int) {
        val plannedId = id.value ?: return
        viewModelScope.launch { repository.movePlannedRun(plannedId, dayIndex) }
    }

    fun setSkipped(skipped: Boolean) {
        val plannedId = id.value ?: return
        viewModelScope.launch { repository.setPlannedRunSkipped(plannedId, skipped) }
    }
}

/** "Verpflegung": carbs and water before and during the run. */
@Composable
private fun FuelCard(a: app.tenet.android.core.common.RunFueling.Advice) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Verpflegung", style = MaterialTheme.typography.titleMedium)
            }
            FuelRow(
                "Kohlenhydrate vorher",
                a.carbsBeforeG?.let { "${it.first}–${it.last} g" } ?: "nicht nötig",
                a.carbsBeforeWhen,
            )
            FuelRow(
                "Kohlenhydrate unterwegs",
                a.carbsPerHourG?.let { "${it.first}–${it.last} g pro Stunde" } ?: "keine",
                a.carbsDuringTotalG?.let { "insgesamt etwa ${it.first}–${it.last} g, ab ca. 30–45 min" },
            )
            FuelRow("Wasser vorher", "${a.waterBeforeMl.first}–${a.waterBeforeMl.last} ml", "2–4 h vor dem Start, kurz davor ein paar Schlucke")
            FuelRow(
                "Wasser unterwegs",
                a.waterPerHourMl?.let { "${it.first}–${it.last} ml pro Stunde" } ?: "nicht nötig",
                a.waterPerHourMl?.let { "alle 15–20 min ein paar Schlucke" + if (a.sodium) " · mit Elektrolyten" else "" },
            )
            a.tips.forEach { tip ->
                Text("• $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Richtwerte nach Sporternährungs-Empfehlungen, an dein Gewicht aus dem Profil angepasst.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FuelRow(label: String, value: String, note: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private val DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

/** Runna-style workout page: metrics, structure step by step, purpose, actions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunWorkoutScreen(
    plannedId: String,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    viewModel: RunWorkoutViewModel = hiltViewModel(),
) {
    LaunchedEffect(plannedId) { viewModel.load(plannedId) }
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val fueling by viewModel.fueling.collectAsStateWithLifecycle()
    var moveDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                navigationIcon = { TooltipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack) },
                title = { Text(unit?.title ?: "Training") },
                subtitle = { unit?.let { Text("${it.date.format(DATE)} · Woche ${it.week + 1}") } },
            )
        },
        bottomBar = {
            val u = unit ?: return@Scaffold
            if (u.status == UnitStatus.DONE) return@Scaffold
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = { moveDialog = true }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.EventRepeat, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Verschieben")
                    }
                    Button(onClick = { onStart(u.id) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Training starten")
                    }
                }
            }
        },
    ) { padding ->
        val u = unit ?: return@Scaffold TenetLoading(Modifier.padding(padding))
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                TenetCard(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (u.status) {
                                    UnitStatus.DONE -> "Erledigt"
                                    UnitStatus.TODAY -> "Heute"
                                    UnitStatus.MISSED -> "Verpasst"
                                    UnitStatus.SKIPPED -> "Übersprungen"
                                    UnitStatus.PLANNED -> "Geplant"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            StatusIcon(u.status)
                        }
                        StructureBar(u.workout, height = 56.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            HeroMetric("≈ ${kmText(u.workout.estDistanceM)}", "Distanz")
                            HeroMetric("≈ ${minText(u.workout.estDurationSec)}", "Dauer")
                            u.targetPaceSecPerKm?.let { HeroMetric(paceText(it), "Ziel-Pace") }
                        }
                    }
                }
            }
            u.doneRun?.let { r ->
                item(key = "done") {
                    TenetCard(onClick = { onOpenRun(r.session.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dein Lauf", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                SmallMetric(kmText(r.run.distanceM.roundToInt()), "gelaufen", "geplant ≈ ${kmText(u.workout.estDistanceM)}")
                                SmallMetric(minText(r.run.durationSec), "Dauer", "geplant ≈ ${minText(u.workout.estDurationSec)}")
                                SmallMetric(paceText(r.run.avgPaceSecPerKm), "Ø Pace", u.targetPaceSecPerKm?.let { "Ziel ${paceText(it)}" } ?: "")
                            }
                            Text("Tippen für Karte, Splits und Bestzeiten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item(key = "steps-h") { SectionTitle(Icons.Outlined.Timeline, "Ablauf") }
            item(key = "steps") {
                val segs = u.workout.segments
                SegmentedRows(count = segs.size, containerColor = MaterialTheme.colorScheme.surfaceContainer) { i ->
                    val s = segs[i]
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when (s.kind) {
                            Kind.WORK, Kind.RACE, Kind.STEADY -> zoneColor(u.zone, u.race)
                            Kind.RECOVERY -> MaterialTheme.colorScheme.surfaceContainerHighest
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.size(width = 6.dp, height = 36.dp),
                    ) {}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.kind.label + (s.rep?.let { r -> if (s.kind == Kind.WORK) " $r" else "" } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            listOfNotNull(
                                s.durationSec?.let { minOrSec(it) },
                                s.distanceM?.let { if (it >= 1000) kmText(it) else "$it m" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    s.paceSecPerKm?.let { Text(paceText(it), style = MaterialTheme.typography.labelLarge) }
                        ?: Text(if (s.kind == Kind.RECOVERY) "locker traben" else "nach Gefühl", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Planned pace kilometre by kilometre (like a pace band).
            val splits = app.tenet.android.core.common.RunWorkoutStructure.kmSplits(u.workout)
            // Interval sessions already list each rep with its pace; mixed km averages would only confuse.
            val continuous = u.workout.segments.none { it.kind == app.tenet.android.core.common.RunWorkoutStructure.Kind.RECOVERY }
            if (splits.size >= 2 && !u.race && continuous) {
                item(key = "splits-h") { SectionTitle(Icons.Outlined.Timeline, "Pace je Kilometer") }
                item(key = "splits") {
                    SegmentedRows(count = splits.size, containerColor = MaterialTheme.colorScheme.surfaceContainer) { i ->
                        val sp = splits[i]
                        Text(
                            if (sp.distanceM >= 1000) "km ${sp.km}" else "km ${sp.km} · ${sp.distanceM} m",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            sp.kind.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(
                            paceText(sp.paceSecPerKm),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (sp.kind == app.tenet.android.core.common.RunWorkoutStructure.Kind.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            item(key = "fuel") { fueling?.let { FuelCard(it) } }
            item(key = "why") {
                TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Warum dieses Training?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Text(u.workout.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(u.workout.feel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            if (u.status != UnitStatus.DONE) {
                item(key = "skip") {
                    TextButton(
                        onClick = { viewModel.setSkipped(u.status != UnitStatus.SKIPPED) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (u.status == UnitStatus.SKIPPED) "Wieder einplanen" else "Training überspringen") }
                }
            }
        }
    }

    if (moveDialog) {
        val u = unit
        AlertDialog(
            onDismissRequest = { moveDialog = false },
            icon = { Icon(Icons.Outlined.EventRepeat, contentDescription = null) },
            title = { Text("Auf welchen Tag?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WEEKDAYS.forEachIndexed { i, name ->
                        TextButton(
                            onClick = {
                                moveDialog = false
                                viewModel.move(i)
                            },
                            enabled = u?.dayIndex != i,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun HeroMetric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
private fun SmallMetric(value: String, label: String, sub: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private fun minOrSec(sec: Int) = if (sec < 60 || sec % 60 != 0) "${sec} s".let { if (sec >= 60) "${sec / 60}:${"%02d".format(sec % 60)} min" else it } else "${sec / 60} min"
