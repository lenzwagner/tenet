package app.tenet.android.feature.sport.run

import app.tenet.android.core.designsystem.component.CardHeader
import app.tenet.android.core.designsystem.theme.TenetCard
import app.tenet.android.core.designsystem.component.RecordBadge
import app.tenet.android.core.designsystem.component.rememberDictation
import app.tenet.android.core.data.ai.AiFiller
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.rememberSheetState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tenet.android.feature.sport.RunningUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Start a GPS run, add one by hand, or see / recover the current one. */
@Composable
internal fun StartRunCard(
    recording: Boolean,
    liveDistanceM: Double,
    liveMovingMs: Long,
    unfinishedStart: Long?,
    onStart: (plannedWorkoutId: String) -> Unit,
    onManual: () -> Unit,
    onImportGpx: () -> Unit = {},
    onSaveUnfinished: () -> Unit,
    onDiscardUnfinished: () -> Unit,
    /** A plan workout is shown above: this starts a run outside the plan. */
    freeRun: Boolean = false,
) {
    TenetCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (recording) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recording) {
                CardHeader(Icons.AutoMirrored.Outlined.DirectionsRun, "Lauf läuft")
                Text(
                    "${formatDuration((liveMovingMs / 1000).toInt())} · ${formatKmDe(liveDistanceM)} km",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Button(onClick = { onStart("") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text("Zur Aufzeichnung")
                }
                return@Column
            }
            if (unfinishedStart != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Unterbrochener Lauf vom ${formatShort(unfinishedStart)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveUnfinished, shapes = ButtonDefaults.shapes()) { Text("Speichern") }
                    TextButton(onClick = onDiscardUnfinished, shapes = ButtonDefaults.shapes()) { Text("Verwerfen") }
                }
            }
            // M3 Expressive split button: start (today's plan if any) | more ways.
            var more by remember { mutableStateOf(false) }
            Box {
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.LeadingButton(
                            onClick = { onStart("") },
                            modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (freeRun) "Freier Lauf" else "Lauf starten")
                        }
                    },
                    trailingButton = {
                        SplitButtonDefaults.TrailingButton(
                            checked = more,
                            onCheckedChange = { more = it },
                            modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                        ) {
                            Icon(
                                Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Weitere Optionen",
                                modifier = Modifier
                                    .size(SplitButtonDefaults.TrailingIconSize)
                                    .graphicsLayer { rotationZ = if (more) 180f else 0f },
                            )
                        }
                    },
                )
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text("Freier Lauf (ohne Plan)") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null) },
                        onClick = {
                            more = false
                            onStart(FREE_RUN)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Lauf nachtragen") },
                        leadingIcon = { Icon(Icons.Outlined.EditCalendar, contentDescription = null) },
                        onClick = {
                            more = false
                            onManual()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("GPX importieren") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                        onClick = {
                            more = false
                            onImportGpx()
                        },
                    )
                }
            }
        }
    }
}

/** Fastest effort per standard distance; tap opens the run. */
@Composable
internal fun RecordsCard(state: RunningUiState, onOpenRun: (String) -> Unit) {
    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardHeader(Icons.Outlined.EmojiEvents, "Bestzeiten")
            state.records.forEach { record ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenRun(record.sessionId) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(distanceLabel(record.distanceM), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        formatShort(record.achievedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(formatDuration(record.durationSec), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Manual run (App_Konzept.md 5.2.3 "Lauf manuell nachtragen (z. B.
 * Laufband) ohne GPS"): date, start time, distance, duration, optional HR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRunSheet(
    onDismiss: () -> Unit,
    /** AI parsing of a dictated run; null hides the mic. */
    aiFill: (suspend (String) -> AiFiller.RunFill?)? = null,
    onSave: (start: LocalDateTime, distanceM: Float, durationSec: Int, avgHr: Int?, notes: String) -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.now().withSecond(0).withNano(0).minusHours(1).format(TIME)) }
    var km by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var seconds by rememberSaveable { mutableStateOf("") }
    var hr by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speech = rememberDictation("Erzähl deinen Lauf, z. B. „Gestern 8 km in 45 Minuten, Puls 150“") { spoken ->
        if (aiFill != null) {
            aiBusy = true
            scope.launch {
                val fill = aiFill(spoken)
                aiBusy = false
                aiFailed = fill == null
                fill?.let { f ->
                    f.date?.let { date = it.toString() }
                    f.time?.let { time = it.format(TIME) }
                    f.distanceKm?.let { km = String.format(Locale.GERMAN, "%.2f", it).trimEnd('0').trimEnd(',') }
                    f.durationSec?.let { sec ->
                        hours = (sec / 3600).takeIf { it > 0 }?.toString().orEmpty()
                        minutes = ((sec % 3600) / 60).toString()
                        seconds = (sec % 60).takeIf { it > 0 }?.toString().orEmpty()
                    }
                    f.avgHr?.let { hr = it.toString() }
                }
            }
        }
    }
    var pickTime by rememberSaveable { mutableStateOf(false) }

    val distanceM = km.replace(',', '.').toFloatOrNull()?.times(1000f)
    val durationSec = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
    val startTime = runCatching { LocalTime.parse(time.trim(), TIME) }.getOrNull()
    val valid = distanceM != null && distanceM >= 100f && durationSec > 0 && startTime != null

    if (pickTime) {
        val timeState = rememberTimePickerState(
            initialHour = startTime?.hour ?: 12,
            initialMinute = startTime?.minute ?: 0,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Startzeit") },
            confirmButton = {
                TextButton(
                    onClick = {
                        time = "%02d:%02d".format(timeState.hour, timeState.minute)
                        pickTime = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
        ) { TimePicker(state = timeState) }
    }

    if (pickDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let {
                            date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().coerceAtMost(LocalDate.now()).toString()
                        }
                        pickDate = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") } },
        ) { DatePicker(state = picker) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lauf nachtragen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (aiFill != null) {
                    if (aiBusy) {
                        LoadingIndicator(Modifier.size(40.dp))
                    } else {
                        FilledTonalButton(
                            onClick = {
                                speech.launch()
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Diktieren")
                        }
                    }
                }
            }
            if (aiFailed) {
                Text(
                    "KI nicht erreichbar – bitte von Hand ausfüllen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.EditCalendar, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)))
                }
                OutlinedButton(
                    onClick = { pickTime = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.width(110.dp),
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(time)
                }
            }
            OutlinedTextField(
                value = km,
                onValueChange = { km = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                label = { Text("Distanz (km)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Dauer", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Std", hours) { v: String -> hours = v },
                    Triple("Min", minutes) { v: String -> minutes = v },
                    Triple("Sek", seconds) { v: String -> seconds = v },
                ).forEach { (label, value, set) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { set(it.filter(Char::isDigit).take(2)) },
                        label = { Text(label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OutlinedTextField(
                value = hr,
                onValueChange = { hr = it.filter(Char::isDigit).take(3) },
                label = { Text("Ø Puls (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notiz (z. B. Laufband, 1 % Steigung)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (distanceM != null && durationSec > 0 && distanceM > 0) {
                Text(
                    "Ø Pace ${formatPaceDe((durationSec / (distanceM / 1000f)).toInt())} /km",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = {
                    onSave(LocalDate.parse(date).atTime(startTime!!), distanceM!!, durationSec, hr.toIntOrNull()?.takeIf { it in 30..250 }, notes.trim())
                },
                enabled = valid,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

private fun formatShort(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
