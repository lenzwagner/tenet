package app.tenet.android.feature.today

import androidx.compose.material.icons.outlined.MonitorHeart
import app.tenet.android.core.designsystem.header.pageWash
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.designsystem.theme.HealthTint
import app.tenet.android.core.designsystem.theme.cardTint
import app.tenet.android.core.designsystem.component.CardHeader
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import app.tenet.android.core.designsystem.component.animatedMorphShape
import app.tenet.android.core.designsystem.component.FabMenuAction
import app.tenet.android.core.designsystem.component.TenetFabMenu
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.rememberSheetState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tenet.android.core.designsystem.theme.AppArea
import app.tenet.android.core.designsystem.theme.AreaTheme
import app.tenet.android.core.designsystem.theme.areaCardColors
import app.tenet.android.core.designsystem.theme.areaColors
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.designsystem.component.ShapeIcon
import app.tenet.android.core.designsystem.component.TenetSwitch
import app.tenet.android.core.designsystem.component.TooltipIconButton
import app.tenet.android.core.designsystem.dimens.TenetDimens
import app.tenet.android.core.designsystem.header.HeaderImage
import app.tenet.android.core.designsystem.header.PageHeader
import app.tenet.android.core.designsystem.header.collapsingHeight
import app.tenet.android.core.designsystem.header.rememberHeaderScrollState
import app.tenet.android.core.designsystem.navigation.rememberReselectListState
import app.tenet.android.core.designsystem.nutrition.GoalEditorSheet
import app.tenet.android.core.designsystem.nutrition.NutritionRings
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val MoodEmojis = listOf("😞", "🙁", "😐", "🙂", "😊")

/**
 * "Heute" dashboard (App_Konzept.md 5.1): week strip to pick a day, then the
 * module cards (dream, nutrition, sport, journal, streaks) in the user's order.
 */
@Composable
fun TodayScreen(
    onAddFood: (date: String) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenSport: () -> Unit,
    onNewEntry: (type: EntryType, date: String, dictate: Boolean) -> Unit,
    onOpenEntry: (Entry) -> Unit,
    onSearch: () -> Unit = {},
    /** Journal lock active: hide diary/dream text on the dashboard. */
    journalLocked: Boolean = false,
    /** Start the GPS run for a planned running unit. */
    onStartRun: (plannedWorkoutId: String) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val showWeeklyWeightPrompt by viewModel.weeklyWeightPrompt.collectAsStateWithLifecycle()
    var weeklyWeight by rememberSaveable { mutableStateOf("") }
    if (showWeeklyWeightPrompt) {
        val parsedWeight = weeklyWeight.replace(',', '.').toFloatOrNull()
        AlertDialog(
            onDismissRequest = viewModel::dismissWeeklyWeightPrompt,
            title = { Text("Gewicht aktualisieren?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wöchentlicher Eintrag für Gewichtsverlauf. Chart: Sport → Gym → Körper.")
                    OutlinedTextField(
                        value = weeklyWeight,
                        onValueChange = { weeklyWeight = it },
                        label = { Text("Gewicht (kg)") },
                        placeholder = { profile?.let { Text(it.weightKg.toString().replace('.', ',')) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = parsedWeight != null && parsedWeight in 20f..400f,
                    onClick = { parsedWeight?.let(viewModel::saveWeeklyWeight) },
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissWeeklyWeightPrompt) { Text("Diese Woche nicht") }
            },
        )
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }
    val headerState = rememberHeaderScrollState()
    val listState = rememberReselectListState(headerState)
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    // With weather the year goes, so date and weather fit one line.
    val dateText = weather?.let { w ->
        state.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)) + " · " + w.emoji + " " +
            (w.nowC?.let { "${kotlin.math.round(it).toInt()}° " } ?: "${kotlin.math.round(w.maxC).toInt()}°/${kotlin.math.round(w.minC).toInt()}° ") + w.label
    } ?: state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.GERMAN))
    val hour = LocalTime.now().hour
    val title = when {
        !state.isToday -> "Rückblick"
        hour < 11 -> "Guten Morgen"
        hour < 18 -> "Guten Tag"
        else -> "Guten Abend"
    }
    val iso = state.date.toString()

    var goalSheetOpen by remember { mutableStateOf(false) }
    var cardsSheetOpen by remember { mutableStateOf(false) }
    if (goalSheetOpen) {
        GoalEditorSheet(
            currentKcal = state.goal.kcal,
            currentProtein = state.goal.protein,
            currentCarbs = state.goal.carbs,
            currentFat = state.goal.fat,
            onDismiss = { goalSheetOpen = false },
            onSave = { kcal, protein, carbs, fat ->
                viewModel.setGoal(kcal = kcal, protein = protein, carbs = carbs, fat = fat)
                goalSheetOpen = false
            },
            profile = profile,
            onSaveProfile = viewModel::saveProfile,
        )
    }
    if (cardsSheetOpen) {
        CardsSheet(
            order = state.cardOrder,
            hidden = state.hidden,
            onDismiss = { cardsSheetOpen = false },
            onSave = { order, hidden ->
                viewModel.saveCards(order, hidden)
                cardsSheetOpen = false
            },
        )
    }

    // Morning (until 11): the dream card jumps to the top while nothing is noted yet.
    val dreamProminent = state.isToday && hour < 11 && state.dream == null && TodayCard.DREAM in state.cards
    val cards = if (dreamProminent) listOf(TodayCard.DREAM) + (state.cards - TodayCard.DREAM) else state.cards

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        floatingActionButton = {
            QuickCaptureFabMenu(
                onNewEntry = { type -> onNewEntry(type, iso, false) },
                onAddFood = { onAddFood(iso) },
                modifier = Modifier.padding(bottom = TenetDimens.bottomTabBarPadding),
            )
        },
    ) { _ ->
        Column(Modifier.fillMaxSize().pageWash(HeaderImage.TODAY, { headerState.progress })) {
            PageHeader(
                header = HeaderImage.TODAY,
                title = title,
                subtitle = dateText,
                progress = { headerState.progress },
                onSearch = onSearch,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(headerState.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = TenetDimens.bottomFabPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "week") {
                    WeekStrip(
                        selected = state.date,
                        marks = state.marks,
                        onSelect = viewModel::selectDate,
                        onPreviousWeek = { viewModel.shiftWeek(-1) },
                        onNextWeek = { viewModel.shiftWeek(1) },
                    )
                }
                items(cards, key = { it.name }) { card ->
                    Box(Modifier.animateItem()) {
                        when (card) {
                            TodayCard.DREAM -> AreaTheme(AppArea.JOURNAL) { DreamCard(
                                dream = state.dream,
                                prominent = dreamProminent,
                                isToday = state.isToday,
                                onTell = { onNewEntry(EntryType.DREAM, iso, true) },
                                onWrite = { onNewEntry(EntryType.DREAM, iso, false) },
                                onNoDream = { viewModel.markNoDream(iso) },
                                onOpen = onOpenEntry,
                                locked = journalLocked,
                            ) }
                            // Each card in its area's colors (accent buttons included).
                            TodayCard.NUTRITION -> AreaTheme(AppArea.NUTRITION) {
                                NutritionCard(state, onAddFood = { onAddFood(iso) }, onEditGoal = { goalSheetOpen = true })
                            }
                            TodayCard.SPORT -> AreaTheme(AppArea.SPORT) { SportCard(state, onOpenSport, onStartRun) }
                            TodayCard.JOURNAL -> AreaTheme(AppArea.JOURNAL) { JournalCard(
                                state = state,
                                onWrite = { onNewEntry(EntryType.DIARY, iso, false) },
                                onOpen = onOpenEntry,
                                onOpenJournal = onOpenJournal,
                                locked = journalLocked,
                            ) }
                            TodayCard.STREAKS -> StreakCard(state.streaks)
                            TodayCard.READINESS -> readiness?.takeIf { state.isToday }?.let {
                                ReadinessCard(it.result, onClose = viewModel::dismissReadiness.takeIf { _ -> state.cards.last() != TodayCard.READINESS })
                            }
                        }
                    }
                }
                item(key = "customize") {
                    TextButton(
                        onClick = { cardsSheetOpen = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Dashboard, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Karten anpassen")
                    }
                }
            }
        }
    }
}

// ---- Week strip -------------------------------------------------------------

@Composable
private fun WeekStrip(
    selected: LocalDate,
    marks: Map<String, DayMarks>,
    onSelect: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val today = LocalDate.now()
    val haptics = LocalHapticFeedback.current
    val monday = WeekMath.weekStart(selected)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TooltipIconButton(Icons.Outlined.ChevronLeft, "Vorherige Woche", onPreviousWeek)
        Row(Modifier.weight(1f)) {
            (0..6).forEach { offset ->
                val day = monday.plusDays(offset.toLong())
                val isSelected = day == selected
                val future = day.isAfter(today)
                val mark = marks[day.toString()] ?: DayMarks()
                val markLabel = listOfNotNull(
                    "Tagebuch".takeIf { mark.diary },
                    "Training".takeIf { mark.training },
                    "Ernährung".takeIf { mark.nutrition },
                ).joinToString(", ")
                val content = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                // Weekday on top, the number in a square cell (the morph stays
                // undistorted), activity dots below. The whole column is the target.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = !future) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(day)
                        }
                        .padding(vertical = 4.dp)
                        .semantics {
                            contentDescription = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.GERMAN)) +
                                if (markLabel.isEmpty()) "" else ": $markLabel"
                        },
                ) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (future) content else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        // The chosen day morphs from a circle into a cookie.
                        shape = animatedMorphShape(isSelected, MaterialShapes.Circle, MaterialShapes.Cookie9Sided),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = content,
                        border = if (day == today && !isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleSmallEmphasized)
                        }
                    }
                    DayDots(mark, onSelected = false)
                }
            }
        }
        TooltipIconButton(
            Icons.Outlined.ChevronRight,
            "Nächste Woche",
            onNextWeek,
            enabled = WeekMath.weekStart(today).isAfter(monday),
        )
    }
}

/** Three fixed slots (diary · training · nutrition) so dots never shift. */
@Composable
private fun DayDots(mark: DayMarks, onSelected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        listOf(
            mark.diary to colors.tertiary,
            mark.training to colors.primary,
            mark.nutrition to colors.secondary,
        ).forEach { (on, color) ->
            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        when {
                            !on -> androidx.compose.ui.graphics.Color.Transparent
                            onSelected -> colors.onPrimary
                            else -> color
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

// ---- Cards --------------------------------------------------------------------

@Composable
private fun CardTitle(
    icon: ImageVector,
    title: String,
    tint: HealthTint,
    action: (@Composable () -> Unit)? = null,
    meta: String? = null,
    chevron: Boolean = false,
) {
    CardHeader(icon = icon, title = title, color = cardTint(tint), meta = meta, chevron = chevron, action = action)
}

@Composable
private fun DreamCard(
    dream: Entry?,
    prominent: Boolean,
    isToday: Boolean,
    onTell: () -> Unit,
    onWrite: () -> Unit,
    onNoDream: () -> Unit,
    onOpen: (Entry) -> Unit,
    locked: Boolean = false,
) {
    TenetCard(
        colors = CardDefaults.cardColors(
            containerColor = if (prominent) MaterialTheme.colorScheme.tertiaryContainer else areaColors(AppArea.JOURNAL).card,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle(icon = Icons.Outlined.NightsStay, title = "Traum", tint = HealthTint.SLEEP)
            if (dream != null) {
                Surface(
                    onClick = { onOpen(dream) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (locked) "Traum notiert · gesperrt" else dream.title.ifBlank { dream.body }.ifBlank { "Traum notiert" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                Text(
                    if (isToday) "Hast du heute geträumt?" else "Kein Traum notiert.",
                    style = if (prominent) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTell, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Erzählen")
                    }
                    FilledTonalButton(onClick = onWrite, shapes = ButtonDefaults.shapes()) { Text("Schreiben") }
                }
                OutlinedButton(
                    onClick = onNoDream,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Kein Traum")
                }
            }
        }
    }
}

@Composable
private fun NutritionCard(state: TodayUiState, onAddFood: () -> Unit, onEditGoal: () -> Unit) {
    TenetCard(Modifier.fillMaxWidth(), colors = areaCardColors(AppArea.NUTRITION)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(Icons.Outlined.Restaurant, "Ernährung", HealthTint.NUTRITION, action = {
                TooltipIconButton(icon = Icons.Outlined.Edit, contentDescription = "Tagesziel bearbeiten", onClick = onEditGoal)
            })
            NutritionRings(
                kcal = state.totals.kcal,
                goalKcal = state.goal.kcal,
                protein = state.totals.protein,
                goalProtein = state.goal.protein,
                carbs = state.totals.carbs,
                goalCarbs = state.goal.carbs,
                fat = state.totals.fat,
                goalFat = state.goal.fat,
                loading = state.loading,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = onAddFood, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Mahlzeit loggen")
            }
        }
    }
}

private val Discipline.icon: ImageVector
    get() = when (this) {
        Discipline.GYM -> Icons.Outlined.FitnessCenter
        Discipline.CALISTHENICS -> Icons.Outlined.SelfImprovement
        Discipline.RUNNING -> Icons.AutoMirrored.Outlined.DirectionsRun
    }

private val Discipline.label: String
    get() = when (this) {
        Discipline.GYM -> "Gym"
        Discipline.CALISTHENICS -> "Calisthenics"
        Discipline.RUNNING -> "Laufen"
    }

/** Done sessions and what is still planned (any discipline), else the next unit. */
@Composable
private fun SportCard(state: TodayUiState, onOpenSport: () -> Unit, onStartRun: (String) -> Unit) {
    TenetCard(onClick = onOpenSport, modifier = Modifier.fillMaxWidth(), colors = areaCardColors(AppArea.SPORT)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle(Icons.Outlined.FitnessCenter, "Sport", HealthTint.ACTIVITY, chevron = true)
            val rows = state.sessions.map { s ->
                Triple(
                    s.discipline,
                    listOfNotNull(s.discipline.label, s.title).joinToString(" · "),
                    when {
                        s.running -> "läuft gerade"
                        s.minutes != null -> "erledigt · ${s.minutes} Min"
                        else -> "erledigt"
                    },
                )
            } + state.planned.map { p ->
                Triple(p.discipline, "${p.discipline.label} · ${p.title}", if (state.isToday) "heute geplant" else "geplant")
            }
            // A run planned for today can be started right here.
            val runToday = state.planned.firstOrNull { it.discipline == Discipline.RUNNING }?.takeIf { state.isToday }
            if (rows.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                    rows.forEachIndexed { index, (discipline, title, status) ->
                        SegmentedListItem(
                            shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, rows.size),
                            leadingContent = { Icon(discipline.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            supportingContent = { Text(status) },
                        ) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (runToday != null) {
                    Button(
                        onClick = { onStartRun(runToday.id) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("${runToday.title} starten", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                val next = state.nextPlanned
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        next?.discipline?.icon ?: Icons.Outlined.EventAvailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    if (next != null) {
                        val day = next.date?.let { LocalDate.parse(it) }
                        val whenText = when (day) {
                            null -> "demnächst"
                            LocalDate.now().plusDays(1) -> "morgen"
                            else -> day.format(DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN))
                        }
                        Column {
                            Text(
                                if (state.isToday) "Ruhetag · nächste Einheit $whenText" else "Nächste Einheit $whenText",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("${next.discipline.label} · ${next.title}", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Text(
                            if (state.isToday) "Nichts geplant – Ruhetag." else "An diesem Tag kein Training.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalCard(
    state: TodayUiState,
    onWrite: () -> Unit,
    onOpen: (Entry) -> Unit,
    onOpenJournal: () -> Unit,
    locked: Boolean = false,
) {
    TenetCard(Modifier.fillMaxWidth(), colors = areaCardColors(AppArea.JOURNAL)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(
                Icons.Outlined.AutoStories,
                "Journal",
                HealthTint.MIND,
                action = {
                    state.mood?.let { mood ->
                        Text(
                            MoodEmojis[(mood - 1).coerceIn(0, 4)],
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { contentDescription = "Stimmung $mood von 5" },
                        )
                    }
                },
            )
            val diary = state.diary
            if (diary != null) {
                Text(
                    text = if (locked) "Eintrag vorhanden · gesperrt" else diary.body.ifBlank { diary.title }.ifBlank { "Eintrag vorhanden ✓" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(onClick = { onOpen(diary) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text("Eintrag ansehen")
                }
            } else {
                Text(
                    if (state.isToday) "Wie war dein Tag?" else "Für diesen Tag gibt es keinen Eintrag.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onWrite, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isToday) "Tag festhalten" else "Nachtragen")
                }
            }
            val others = state.entries.count { it.type == EntryType.NOTE }
            if (others > 0) {
                TextButton(onClick = onOpenJournal, shapes = ButtonDefaults.shapes()) {
                    Text("$others ${if (others == 1) "Notiz" else "Notizen"} an diesem Tag")
                }
            }
        }
    }
}

/**
 * Morning readiness like Google Health / Fitbit: score ring, what it means
 * for today's training and the parts behind it (HRV, resting pulse, sleep, load).
 */
@Composable
private fun ReadinessCard(r: app.tenet.android.core.common.Readiness.Result, onClose: (() -> Unit)? = null) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val levelColor = readinessColor(r.level, dark)
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(
                Icons.Outlined.MonitorHeart,
                "Bereitschaft",
                HealthTint.BODY,
                meta = "Heute",
                // Close: the card moves to the end of the page for today.
                action = onClose?.let { close ->
                    { TooltipIconButton(icon = Icons.Rounded.Close, contentDescription = "Nach unten schieben", onClick = close) }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReadinessRing(r.score, levelColor, Modifier.size(88.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.headline, style = MaterialTheme.typography.titleLarge)
                    Text(r.advice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Parts in a 2-column grid, like Health's metric tiles.
            r.contributors.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { c ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(c.kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(c.value, style = MaterialTheme.typography.titleMedium)
                                Text(c.detail, style = MaterialTheme.typography.labelSmall, color = readinessColor(partLevel(c.score), dark))
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun partLevel(score: Int) = when {
    score >= 80 -> app.tenet.android.core.common.Readiness.Level.HIGH
    score >= 60 -> app.tenet.android.core.common.Readiness.Level.GOOD
    score >= 40 -> app.tenet.android.core.common.Readiness.Level.MODERATE
    else -> app.tenet.android.core.common.Readiness.Level.LOW
}

private fun readinessColor(level: app.tenet.android.core.common.Readiness.Level, dark: Boolean) = androidx.compose.ui.graphics.Color(
    when (level) {
        app.tenet.android.core.common.Readiness.Level.HIGH -> if (dark) 0xFF30D158 else 0xFF1FA34A
        app.tenet.android.core.common.Readiness.Level.GOOD -> if (dark) 0xFF64D2FF else 0xFF1E88D9
        app.tenet.android.core.common.Readiness.Level.MODERATE -> if (dark) 0xFFFFB340 else 0xFFE08600
        app.tenet.android.core.common.Readiness.Level.LOW -> if (dark) 0xFFFF6961 else 0xFFD93A30
    },
)

/** 270° gauge with the score in the middle. */
@Composable
private fun ReadinessRing(score: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val sweep by androidx.compose.animation.core.animateFloatAsState(270f * score / 100f, label = "readiness")
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = "Bereitschaft $score von 100" }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = stroke)
            drawArc(color, 135f, sweep, false, topLeft, arcSize, style = stroke)
        }
        Text("$score", style = MaterialTheme.typography.headlineMediumEmphasized)
    }
}

/** Diary, tracking and training streaks (App_Konzept.md 5.1). */
@Composable
private fun StreakCard(streaks: Streaks) {
    // Same lightly tinted surface and text color as the area cards above it
    // (the default card was a darker block with greyish text).
    val c = MaterialTheme.colorScheme
    val dark = c.surface.luminance() < 0.5f
    TenetCard(
        Modifier.fillMaxWidth(),
        colors = if (app.tenet.android.core.designsystem.theme.isClearStyle) app.tenet.android.core.designsystem.theme.tenetCardColors() else CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.lerp(c.surfaceContainerLow, c.secondaryContainer, if (dark) 0.22f else 0.30f),
            contentColor = c.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(Icons.Outlined.LocalFireDepartment, "Serien", HealthTint.STREAK)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StreakStat(streaks.diaryDays, if (streaks.diaryDays == 1) "Tag" else "Tage", "Tagebuch", Icons.Outlined.AutoStories, AppArea.JOURNAL)
                StreakStat(streaks.trackingDays, if (streaks.trackingDays == 1) "Tag" else "Tage", "Ernährung", Icons.Outlined.RestaurantMenu, AppArea.NUTRITION)
                StreakStat(streaks.trainingWeeks, if (streaks.trainingWeeks == 1) "Woche" else "Wochen", "Training", Icons.Outlined.FitnessCenter, AppArea.SPORT)
            }
        }
    }
}

@Composable
private fun StreakStat(value: Int, unit: String, label: String, icon: ImageVector, area: AppArea) {
    val colors = areaColors(area)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        ShapeIcon(icon = icon, containerShape = MaterialShapes.Cookie6Sided.toShape(), containerColor = colors.container, contentColor = colors.onContainer)
        Spacer(Modifier.height(4.dp))
        Text("$value", style = MaterialTheme.typography.headlineMediumEmphasized, color = colors.accent)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---- Customize sheet ------------------------------------------------------------

@Composable
private fun CardsSheet(
    order: List<TodayCard>,
    hidden: Set<TodayCard>,
    onDismiss: () -> Unit,
    onSave: (List<TodayCard>, Set<TodayCard>) -> Unit,
) {
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    val items = remember { mutableStateListOf<TodayCard>().apply { addAll(order) } }
    val off = remember { mutableStateListOf<TodayCard>().apply { addAll(hidden) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Karten anpassen", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Reihenfolge und Sichtbarkeit auf „Heute“. Die Traumkarte rückt morgens bis 11 Uhr automatisch nach oben.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
                items.forEachIndexed { index, card ->
                    val visible = card !in off
                    SegmentedListItem(
                        shapes = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, items.size),
                        leadingContent = {
                            TenetSwitch(checked = visible, onCheckedChange = { if (it) off.remove(card) else off.add(card) })
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { items.add(index - 1, items.removeAt(index)) },
                                    enabled = index > 0,
                                    shapes = IconButtonDefaults.shapes(),
                                ) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "${card.label} nach oben") }
                                IconButton(
                                    onClick = { items.add(index + 1, items.removeAt(index)) },
                                    enabled = index < items.lastIndex,
                                    shapes = IconButtonDefaults.shapes(),
                                ) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "${card.label} nach unten") }
                            }
                        },
                    ) { Text(card.label) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(
                    onClick = {
                        items.clear(); items.addAll(DefaultCardOrder); off.clear()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Zurücksetzen") }
                Button(onClick = { onSave(items.toList(), off.toSet()) }, shapes = ButtonDefaults.shapes()) { Text("Speichern") }
            }
        }
    }
}

// ---- Quick capture ----------------------------------------------------------------

@Composable
private fun QuickCaptureFabMenu(
    onNewEntry: (EntryType) -> Unit,
    onAddFood: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TenetFabMenu(
        actions = listOf(
            FabMenuAction("Mahlzeit", Icons.Outlined.RestaurantMenu, onAddFood),
            FabMenuAction("Traum", Icons.Outlined.NightsStay) { onNewEntry(EntryType.DREAM) },
            FabMenuAction("Tagebuch", Icons.Outlined.AutoStories) { onNewEntry(EntryType.DIARY) },
            FabMenuAction("Notiz", Icons.AutoMirrored.Outlined.Notes) { onNewEntry(EntryType.NOTE) },
        ),
        contentDescription = "Schnellerfassung",
        modifier = modifier,
    )
}
