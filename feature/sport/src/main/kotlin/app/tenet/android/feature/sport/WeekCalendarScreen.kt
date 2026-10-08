package app.tenet.android.feature.sport

import androidx.compose.foundation.shape.RoundedCornerShape
import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.runtime.getValue
import java.util.Locale
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ButtonDefaults
import app.tenet.android.core.designsystem.component.TooltipIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.database.entity.Discipline
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Cross-discipline week calendar (App_Konzept.md 5.2: "Icon in der Top
 * App Bar … alle Einheiten farbcodiert, damit man Beintag und langen Lauf
 * nicht auf denselben Tag legt"). Works only with sessions and dated
 * planned workouts, so it stays valid for disciplines that don't exist yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekCalendarScreen(
    onBack: () -> Unit,
    viewModel: WeekCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isCurrentWeek = state.weekStart == WeekMath.weekStart(LocalDate.now())

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                colors = app.tenet.android.core.designsystem.header.washTopBarColors(),
                modifier = app.tenet.android.core.designsystem.header.washBar(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TooltipIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", onClick = onBack)
                },
                title = { Text("Wochenkalender") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                WeekSelector(
                    weekStart = state.weekStart,
                    isCurrentWeek = isCurrentWeek,
                    onPrevious = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                    onCurrent = viewModel::goToCurrentWeek,
                )
            }

            val isEmpty = state.days.none { it.sessions.isNotEmpty() || it.planned.isNotEmpty() }
            // Plans without fixed days count as "any day" (like on Heute), so name
            // them instead of claiming the week is empty.
            if (!state.loading && state.flexiblePlans.isNotEmpty()) {
                item {
                    Text(
                        text = state.flexiblePlans.joinToString(" und ") { "${it.discipline.label()} · ${it.title}" } +
                            " ohne feste Tage – an jedem Tag möglich. Feste Trainingstage legst du auf der Sport-Seite fest.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!state.loading && isEmpty) {
                item {
                    Text(
                        text = "Keine Einheiten in dieser Woche. " +
                            "Über die Pfeile wechselst du die Woche.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.days, key = { it.date.toString() }) { day ->
                // Month only on the first day and where a new month starts.
                DayCard(day, showMonth = day == state.days.first() || day.date.dayOfMonth == 1)
            }

            item { Legend() }
        }
    }
}

@Composable
private fun WeekSelector(
    weekStart: LocalDate,
    isCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TooltipIconButton(icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Vorherige Woche", onClick = onPrevious)
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val weekEnd = weekStart.plusDays(6)
            val label = if (weekStart.year == weekEnd.year) {
                "KW ${WeekMath.weekOfYear(weekStart)} · " +
                    "${weekStart.format(dayMonth)} – ${weekEnd.format(dayMonth)} ${weekEnd.year}"
            } else {
                "KW ${WeekMath.weekOfYear(weekStart)} · " +
                    "${weekStart.format(dayMonth)} ${weekStart.year} – " +
                    "${weekEnd.format(dayMonth)} ${weekEnd.year}"
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (!isCurrentWeek) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onCurrent) { Text("Diese Woche") }
            }
        }
        TooltipIconButton(icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Nächste Woche", onClick = onNext)
    }
}

@Composable
private fun DayCard(day: WeekDayUi, showMonth: Boolean = true) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dayLabel = weekdayLabel(day.date)
                if (day.isToday) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "$dayLabel ${day.date.dayOfMonth}.",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Text(
                        text = "$dayLabel ${day.date.dayOfMonth}.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showMonth) {
                    Text(
                        text = day.date.format(monthShort),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (day.sessions.isEmpty() && day.planned.isEmpty() && day.events.isEmpty()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Phone calendar first: they decide when there is time to train.
            day.events.forEach { event -> EventRow(event) }
            day.planned.forEach { planned ->
                PlannedRow(planned)
            }
            day.sessions.forEach { session ->
                SessionRow(session)
            }
        }
    }
}

@Composable
private fun SessionRow(session: WeekSessionUi) {
    val color = disciplineColor(session.discipline)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisciplineDot(color)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = session.workoutTitle ?: session.discipline.label(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = "${session.discipline.label()} · " +
                    timeFormat.format(java.time.Instant.ofEpochMilli(session.startMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (session.running) {
                "läuft …"
            } else {
                "${session.durationMin ?: 0} min"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (session.running) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An appointment from the phone calendar: calendar colour, time, title. */
@Composable
private fun EventRow(event: WeekEventUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).background(Color(event.color), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(11.dp))
        Text(
            text = listOfNotNull(event.time ?: "Ganztägig", event.title).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlannedRow(planned: WeekPlannedUi) {
    val color = disciplineColor(planned.discipline)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .border(2.dp, color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Geplant · ${planned.discipline.label()} · ${planned.title}",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun DisciplineDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Discipline.entries.forEach { discipline ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisciplineDot(disciplineColor(discipline))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = discipline.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val dayMonth = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    .withZone(java.time.ZoneId.systemDefault())

private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "Mo"
    2 -> "Di"
    3 -> "Mi"
    4 -> "Do"
    5 -> "Fr"
    6 -> "Sa"
    else -> "So"
}
