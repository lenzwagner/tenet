package app.tenet.android.feature.sport

import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.feature.sport.run.PlanWeekCard
import app.tenet.android.feature.sport.run.NextRunCard
import app.tenet.android.feature.sport.setup.SportSetupViewModel
import app.tenet.android.feature.sport.setup.SetupIntroCard
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.designsystem.component.TenetSwitch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.rememberGrowIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GpsFixed
import app.tenet.android.core.database.entity.RunSource
import app.tenet.android.feature.sport.run.RecordsCard
import app.tenet.android.feature.sport.run.StartRunCard
import app.tenet.android.feature.sport.run.ManualRunSheet
import app.tenet.android.feature.sport.run.RunTrackingService
import app.tenet.android.feature.sport.run.RunStatus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.common.RunVolumeMath
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Surface
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import java.util.Locale
import app.tenet.android.core.designsystem.navigation.rememberReselectListState
import androidx.compose.material3.ButtonDefaults
import app.tenet.android.core.designsystem.component.TooltipIconButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.PaceAnchor
import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.common.RunPaceMath
import app.tenet.android.core.common.RunPlanMath.RunGoal
import app.tenet.android.core.common.RunZone
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.core.designsystem.component.LocalAppSnackbar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.FileOpen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Running discipline page (App_Konzept.md 5.2.3): goal-based plan setup
 * and the generated weekly structure. GPS tracking and run history details
 * arrive with the active-run step.
 */
@Composable
fun RunningPage(
    onStartRun: (plannedWorkoutId: String) -> Unit = {},
    onOpenRun: (sessionId: String) -> Unit = {},
    onOpenPlan: (planId: String) -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onOpenWorkout: (plannedId: String) -> Unit = {},
    viewModel: RunningViewModel = hiltViewModel(),
    setupViewModel: SportSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setupDone by setupViewModel.done.collectAsStateWithLifecycle()
    val live by RunTrackingService.state.collectAsStateWithLifecycle()
    val unfinished by viewModel.unfinished.collectAsStateWithLifecycle()
    var showManualSheet by rememberSaveable { mutableStateOf(false) }
    val recording = live != null && live?.status != RunStatus.FINISHED
    LaunchedEffect(recording) { viewModel.checkUnfinished(recording) }

    if (showManualSheet) {
        ManualRunSheet(
            aiFill = if (viewModel.aiAvailable) viewModel::aiRun else null,
            onDismiss = { showManualSheet = false },
            onSave = { start, distanceM, durationSec, hr, notes ->
                showManualSheet = false
                viewModel.addManualRun(start, distanceM, durationSec, hr, notes, onOpenRun)
            },
        )
    }

    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val gpxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val xml = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (xml == null) {
            snackbar?.show("Datei konnte nicht gelesen werden.")
            return@rememberLauncherForActivityResult
        }
        viewModel.importGpx(xml) { id ->
            if (id != null) onOpenRun(id) else snackbar?.show("Keine Laufstrecke mit Zeitstempeln in der GPX-Datei.")
        }
    }

    val listState = rememberReselectListState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh { snackbar?.show(it) } },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = TenetDimens.bottomTabBarPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val showSetup = setupDone?.contains("RUNNING") == false && state.planStart == null
        if (showSetup) {
            item(key = "setup") {
                SetupIntroCard(
                    discipline = app.tenet.android.core.database.entity.Discipline.RUNNING,
                    icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                    title = "Richte deinen Laufplan ein",
                    body = "Ziel, Termin, dein aktuelles Niveau und deine Lauftage – daraus erstellt Tenet " +
                        "einen Plan mit Tempi, Umfang und auf Wunsch Tapering.",
                    onSetup = onOpenSetup,
                    onSkip = { setupViewModel.skip("RUNNING") },
                )
            }
        }
        // Runna-style: today's (or the next) planned run first.
        state.nextUnit?.let { next ->
            item(key = "next-run") {
                NextRunCard(unit = next, onStart = { onStartRun(next.id) }, onOpen = { onOpenWorkout(next.id) })
            }
        }
        item {
            StartRunCard(
                recording = recording,
                liveDistanceM = live?.distanceM ?: 0.0,
                liveMovingMs = live?.movingMs ?: 0L,
                unfinishedStart = unfinished?.startedAt,
                onStart = onStartRun,
                onManual = { showManualSheet = true },
                onImportGpx = { gpxPicker.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) },
                onSaveUnfinished = { viewModel.saveUnfinished(onOpenRun) },
                onDiscardUnfinished = viewModel::discardUnfinished,
                freeRun = state.nextUnit != null,
            )
        }
        // Without a plan the setup card above already asks for one: no second "Plan erstellen".
        if (!(showSetup && state.overview.plan == null)) {
            item {
                val plans by viewModel.plans.collectAsStateWithLifecycle()
                PlanCard(
                    state = state,
                    onCreatePlan = onOpenSetup,
                    onOpenPlan = onOpenPlan,
                    plans = plans,
                    onSwitchPlan = viewModel::switchPlan,
                )
            }
        }
        if (state.planWeeks.isNotEmpty()) {
            item(key = "plan-week") { PlanWeekCard(state.planWeeks, state.currentWeek, onOpenWorkout) }
        }
        item { VolumeCard(state) }
        state.form?.let { form -> item(key = "form") { app.tenet.android.feature.sport.run.FormCard(form) } }
        if (state.records.isNotEmpty()) item { RecordsCard(state, onOpenRun) }
        item { RecentRunsCard(state, onOpenRun) }
    }
    }
}

// ---- Volume ----------------------------------------------------------------

/** Weekly/monthly running volume as bars plus the "sudden jump" hint. */
@Composable
private fun VolumeCard(state: RunningUiState) {
    var monthly by rememberSaveable { mutableStateOf(false) }
    val periods = if (monthly) state.monthlyVolume else state.weeklyVolume
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(Icons.Outlined.BarChart, "Umfang", action = {
                SegmentedSelector(
                    segments = listOf(Segment("Woche"), Segment("Monat")),
                    selectedIndex = if (monthly) 1 else 0,
                    onSelect = { monthly = it == 1 },
                    modifier = Modifier.width(168.dp),
                )
            })
            val current = periods.lastOrNull()
            if (current == null || periods.all { it.runs == 0 }) {
                Text(
                    "Noch keine Läufe im Zeitraum.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatKm(current.distanceM),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    (if (monthly) "km diesen Monat" else "km diese Woche") +
                        " · ${current.runs} ${if (current.runs == 1) "Lauf" else "Läufe"}" +
                        " · ${formatHours(current.durationSec)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            val avg = periods.dropLast(1).map { it.distanceM }.average().toFloat()
            VolumeBars(periods, monthly)
            if (avg > 0f) {
                Text(
                    "Ø ${formatKm(avg)} km pro ${if (monthly) "Monat" else "Woche"} (vorherige ${periods.size - 1})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.jumpWarning?.takeIf { !monthly }?.let { warning ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Umfang +${warning.increasePercent} % gegenüber dem Schnitt der letzten 3 Wochen " +
                                "(${formatKm(warning.currentM)} statt ${formatKm(warning.baselineM)} km). " +
                                "Faustregel: langsam steigern, sonst drohen Überlastungen.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeBars(periods: List<RunVolumeMath.Period>, monthly: Boolean) {
    val grow = rememberGrowIn(periods to monthly)
    val max = maxOf(periods.maxOf { it.distanceM }, 1f)
    val bar = MaterialTheme.colorScheme.primary
    val past = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM", Locale.GERMAN) }
    fun label(p: RunVolumeMath.Period): String =
        if (monthly) p.start.format(monthFmt).removeSuffix(".") else "KW ${WeekMath.weekOfYear(p.start)}"
    val description = periods.joinToString { "${label(it)}: ${formatKm(it.distanceM)} km" }
    Column(Modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            val slot = size.width / periods.size
            val w = slot * 0.55f
            periods.forEachIndexed { i, p ->
                val x = i * slot + (slot - w) / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                val h = size.height * (p.distanceM / max) * grow
                if (h > 0f) {
                    drawRoundRect(
                        if (i == periods.lastIndex) bar else past,
                        Offset(x, size.height - h),
                        Size(w, h),
                        CornerRadius(w / 2),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            periods.forEach { p ->
                Text(
                    label(p),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun formatKm(meters: Float): String = String.format(Locale.GERMAN, "%.1f", meters / 1000f)

private fun formatHours(sec: Int): String = "%d:%02d h".format(sec / 3600, (sec % 3600) / 60)

@Composable
private fun PlanCard(
    state: RunningUiState,
    onCreatePlan: () -> Unit,
    onOpenPlan: (String) -> Unit,
    plans: List<app.tenet.android.core.database.entity.TrainingPlan> = emptyList(),
    onSwitchPlan: (String) -> Unit = {},
) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.AutoMirrored.Outlined.DirectionsRun, "Trainingsplan", action = { PlanSwitchButton(app.tenet.android.core.database.entity.Discipline.RUNNING) })

            val plan = state.overview.plan
            if (plan == null) {
                Text(
                    text = if (state.loading) "Plan wird geladen …" else {
                        "Noch kein Plan. Lege ein Ziel an – die App baut daraus " +
                            "deine Wochenstruktur mit Entlastungswochen."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.loading) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = onCreatePlan, modifier = Modifier.fillMaxWidth()) {
                        Text("Plan erstellen")
                    }
                }
                return@Column
            }

            val goal = RunGoal.fromName(state.overview.detail?.goalId)
            Text(goal.label, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Ziel am ${state.goalDate?.format(dateFmt) ?: "–"} · " +
                    "${state.overview.detail?.runsPerWeek ?: "–"} Einheiten/Woche · " +
                    "${state.weeks} Wochen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detail = state.overview.detail
            val form5k = detail?.current5kSec
            Text(
                text = if (form5k == null) {
                    "Keine Formzeit hinterlegt – Zielpaces sind deaktiviert."
                } else {
                    "Form 5 km: ${formatTime(form5k)} · " +
                        "Paces: ${state.paceMethod.label}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (form5k == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            // Live prognosis from the runs so far, projected to race day.
            RunPlanMath.raceDistanceM(goal)?.let { distance ->
                val now = state.form?.time(distance)
                    ?: form5k?.let { app.tenet.android.core.common.RacePrediction.riegel(5_000, it, distance) }
                if (now != null) {
                    val raceDay = RunPlanMath.expectedRaceDaySec(now, goal, (state.weeks - state.currentWeek).coerceAtLeast(0), detail?.taper == true)
                    val target = detail?.targetTimeSec
                    Text(
                        "Prognose heute ${formatTime(now)} · am Wettkampftag ≈ ${formatTime(raceDay)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (target != null) {
                        val diff = raceDay - target
                        Text(
                            "Wunschzeit ${formatTime(target)} · " + if (diff <= 0) "auf Kurs" else "noch ${formatTime(diff)} schneller nötig",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diff <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = ButtonDefaults.shapes(), onClick = { onOpenPlan(plan.id) }, modifier = Modifier.weight(1f)) {
                    Text("Plan & Prognose")
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onCreatePlan, modifier = Modifier.weight(1f)) {
                    Text("Neu erstellen")
                }
            }
        }
    }
}

@Composable
private fun RecentRunsCard(state: RunningUiState, onOpenRun: (String) -> Unit) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(Icons.Outlined.History, "Letzte Läufe")
            val runs = state.overview.runs
            if (runs.isEmpty()) {
                Text(
                    text = "Noch keine Läufe. Starte eine Aufzeichnung, trag einen Lauf nach " +
                        "oder verbinde Health Connect in den Einstellungen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            // Fixed length keeps the page compact; the rest on demand.
            var showAll by rememberSaveable { mutableStateOf(false) }
            val visible = if (showAll) runs else runs.take(RECENT_RUNS)
            visible.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenRun(row.session.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (row.run.source) {
                            RunSource.GPS -> Icons.Outlined.GpsFixed
                            RunSource.MANUAL -> Icons.Outlined.EditNote
                            RunSource.HEALTH_CONNECT -> Icons.Outlined.Watch
                            RunSource.GPX -> Icons.Outlined.FileOpen
                        },
                        contentDescription = when (row.run.source) {
                            RunSource.GPS -> "GPS-Aufzeichnung"
                            RunSource.MANUAL -> "Nachgetragen"
                            RunSource.HEALTH_CONNECT -> "Aus Health Connect"
                            RunSource.GPX -> "GPX-Import"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = row.session.startedAt.let { formatDateTime(it) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "%.1f km · %s/km".format(
                            Locale.GERMAN,
                            row.run.distanceM / 1000f,
                            formatPace(row.run.avgPaceSecPerKm),
                        ) + (row.run.avgHr?.let { " · $it bpm" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (runs.size > RECENT_RUNS) {
                TextButton(
                    onClick = { showAll = !showAll },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (showAll) "Weniger anzeigen" else "Alle ${runs.size} Läufe anzeigen")
                }
            }
        }
    }
}

// ---- Plan setup sheet -----------------------------------------------------

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TooltipIconButton(icon = Icons.Outlined.Remove, contentDescription = "$label verringern", onClick = { onChange((value - step).coerceIn(range)) })
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        TooltipIconButton(icon = Icons.Outlined.Add, contentDescription = "$label erhöhen", onClick = { onChange((value + step).coerceIn(range)) })
    }
}

// ---- Helpers ----------------------------------------------------------------

private val weekdayLabels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

private fun formatPace(secPerKm: Int): String =
    "%d:%02d".format(secPerKm / 60, secPerKm % 60)

/** "25:06", or with hours "1:55:28". */
private fun formatTime(totalSec: Int): String =
    if (totalSec >= 3600) "%d:%02d:%02d".format(totalSec / 3600, totalSec % 3600 / 60, totalSec % 60)
    else "%d:%02d".format(totalSec / 60, totalSec % 60)

private fun formatDateTime(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN))
}.getOrDefault("")

/** Runs shown before "Alle anzeigen". */
private const val RECENT_RUNS = 5
