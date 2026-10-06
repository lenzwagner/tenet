package app.tenet.android.feature.sport.gym

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.material3.Surface
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import app.tenet.android.core.common.BodyMeasurements
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import app.tenet.android.core.designsystem.component.RecordBadge
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import java.util.Locale
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.ProgressMath
import app.tenet.android.core.database.entity.BodyMetric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel

// ---- PR badges -----------------------------------------------------------

/**
 * Recent personal records as badges (App_Konzept.md 5.2.1: "PR-Erkennung
 * mit Badge"). Shown above the classic progress list.
 */
@Composable
fun PrBadgeCard(personalBests: List<ProgressMath.PersonalBest>) {
    if (personalBests.isEmpty()) return
    // Only surface records from the last 14 days; older ones are history.
    val cutoff = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
    val recent = personalBests.filter { it.at >= cutoff }.take(5)
    if (recent.isEmpty()) return

    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordBadge()
                Spacer(Modifier.width(12.dp))
                Text("Neue Bestleistungen", style = MaterialTheme.typography.titleMedium)
            }
            recent.forEach { pr ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${pr.exercise} · ${pr.kind.badgeLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = pr.displayValue,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

private val ProgressMath.PersonalBest.Kind.badgeLabel: String
    get() = when (this) {
        ProgressMath.PersonalBest.Kind.WEIGHT -> "Gewicht"
        ProgressMath.PersonalBest.Kind.REPS -> "Wiederholungen"
        ProgressMath.PersonalBest.Kind.ONE_REP_MAX -> "1RM"
        ProgressMath.PersonalBest.Kind.VOLUME -> "Volumen"
    }

private val ProgressMath.PersonalBest.displayValue: String
    get() = when (kind) {
        ProgressMath.PersonalBest.Kind.REPS -> "${value.toInt()} Wdh"
        else -> "${value.toInt()} kg"
    }

// ---- 1RM history ---------------------------------------------------------

/**
 * Line chart of the estimated 1RM per exercise over time
 * (App_Konzept.md 5.2.1: "Geschätzter 1RM ... als Zeitreihe").
 */
@Composable
fun OneRmHistoryCard(
    sets: List<ProgressMath.SetRecord>,
    exercises: List<String>,
    formula: OneRepMaxFormula,
) {
    if (exercises.isEmpty()) return
    var selected by remember(exercises) { mutableStateOf(exercises.first()) }

    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("1RM-Verlauf (${formula.label})", style = MaterialTheme.typography.titleMedium)
            }

            if (exercises.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    exercises.forEach { exercise ->
                        FilterChip(
                            selected = selected == exercise,
                            onClick = { selected = exercise },
                            label = { Text(exercise) },
                        )
                    }
                }
            }

            val history = remember(sets, selected, formula) {
                ProgressMath.oneRepMaxHistory(sets, selected, formula)
            }
            if (history.size < 2) {
                Text(
                    text = "Noch zu wenig Sessions für einen Verlauf " +
                        "(mind. 2 Einheiten mit „$selected“).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val producer = remember { CartesianChartModelProducer() }
                LaunchedEffect(history) {
                    producer.runTransaction {
                        lineModel {
                            series(
                                history.map { it.date.toEpochDay().toFloat() },
                                history.map { it.value },
                            )
                        }
                    }
                }
                val dateLabels = remember(history) {
                    val formatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
                    history.associate { it.date.toEpochDay().toDouble() to it.date.format(formatter) }
                }
                val shortDate = remember(history) { DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN) }
                // Charts follow the M3 color scheme (primary, tertiary …).
                ProvideVicoTheme(rememberM3VicoTheme()) {
                    CartesianChartHost(
                        rememberCartesianChart(
                            rememberLineCartesianLayer(),
                            startAxis = VerticalAxis.rememberStart(),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = CartesianValueFormatter { _, x, _ ->
                                    dateLabels[x] ?: x.toInt().toString()
                                },
                            ),
                        ),
                        producer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .semantics {
                                contentDescription = "Liniendiagramm 1RM-Verlauf $selected: " +
                                    "${history.size} Einheiten, " +
                                    "von ${history.minOf { it.date }.format(shortDate)} " +
                                    "bis ${history.maxOf { it.date }.format(shortDate)}, " +
                                    "Bestwert ${history.maxOf { it.value }.toInt()} Kilogramm."
                            },
                    )
                }
                Text(
                    text = "Bestwert: " +
                        "${history.maxOf { it.value }.toInt()} kg · " +
                        "${history.size} Einheiten",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Weekly volume -------------------------------------------------------

/**
 * Column chart of the weekly volume (Σ weight × reps) per muscle group
 * (App_Konzept.md 5.2.1: "Volumen pro Muskelgruppe und Woche").
 */
@Composable
fun VolumeHistoryCard(
    sets: List<ProgressMath.SetRecord>,
    muscleGroups: List<String>,
) {
    if (sets.isEmpty()) return
    var selected by remember(muscleGroups) { mutableStateOf<String?>(null) }

    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Volumen pro Woche", style = MaterialTheme.typography.titleMedium)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selected == null,
                    onClick = { selected = null },
                    label = { Text("Alle") },
                )
                muscleGroups.forEach { muscle ->
                    FilterChip(
                        selected = selected == muscle,
                        onClick = { selected = muscle },
                        label = { Text(muscle) },
                    )
                }
            }

            val weeks = remember(sets, selected) {
                ProgressMath.weeklyVolume(sets, muscle = selected, weeksBack = 7)
            }
            val totalVolume = weeks.sumOf { it.volumeKg.toDouble() }.toFloat()
            if (totalVolume <= 0f) {
                Text(
                    text = "Noch keine abgeschlossenen Sätze in diesem Zeitraum.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val producer = remember { CartesianChartModelProducer() }
                LaunchedEffect(weeks) {
                    producer.runTransaction {
                        columnModel { series(weeks.map { it.volumeKg }) }
                    }
                }
                // Column charts use 0-based x indices; label by position.
                val weekLabels = remember(weeks) {
                    val formatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
                    weeks.map { it.weekStart.format(formatter) }
                }
                // Charts follow the M3 color scheme (primary, tertiary …).
                ProvideVicoTheme(rememberM3VicoTheme()) {
                    CartesianChartHost(
                        rememberCartesianChart(
                            rememberColumnCartesianLayer(),
                            startAxis = VerticalAxis.rememberStart(),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = CartesianValueFormatter { _, x, _ ->
                                    weekLabels.getOrElse(x.toInt()) { "" }
                                },
                            ),
                        ),
                        producer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .semantics {
                                contentDescription = "Säulendiagramm Wochenvolumen: " +
                                    "${weeks.size} Wochen" +
                                    (selected?.let { ", Muskelgruppe $it" } ?: "") +
                                    ", insgesamt ${totalVolume.toInt()} Kilogramm."
                            },
                    )
                }
                Text(
                    text = "8 Wochen gesamt: ${totalVolume.toInt()} kg" +
                        (selected?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Body metrics --------------------------------------------------------

/** Weight time series with a quick-add dialog (App_Konzept.md 5.2.1). */
@Composable
fun BodyMetricCard(
    metrics: List<BodyMetric>,
    metricsLoaded: Boolean = true,
    lastPromptDate: String? = null,
    onWeeklyPromptDismiss: () -> Unit = {},
    onSave: (weight: Float?, bodyFat: Float?, measurements: Map<String, Float>) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var weeklyQuestionOpen by remember { mutableStateOf(false) }
    var weeklyQuestionHandled by remember { mutableStateOf(false) }
    val today = java.time.LocalDate.now()
    LaunchedEffect(metricsLoaded, metrics, today) {
        val enteredThisWeek = metrics.lastOrNull()?.date?.parseIso()?.let {
            !it.isBefore(app.tenet.android.core.common.WeekMath.weekStart(today))
        } == true
        if (metricsLoaded && today.dayOfWeek == java.time.DayOfWeek.MONDAY && !enteredThisWeek &&
            lastPromptDate != today.toString() && !weeklyQuestionHandled
        ) {
            weeklyQuestionOpen = true
        }
    }

    if (weeklyQuestionOpen) {
        AlertDialog(
            onDismissRequest = {
                weeklyQuestionOpen = false
                weeklyQuestionHandled = true
                onWeeklyPromptDismiss()
            },
            title = { Text("Gewicht aktualisieren?") },
            text = { Text("Montags kannst du dein aktuelles Gewicht erfassen. Verlauf erscheint hier in Karte „Körper“.") },
            confirmButton = {
                Button(onClick = {
                    weeklyQuestionOpen = false
                    weeklyQuestionHandled = true
                    dialogOpen = true
                }) { Text("Eintragen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    weeklyQuestionOpen = false
                    weeklyQuestionHandled = true
                    onWeeklyPromptDismiss()
                }) { Text("Diese Woche nicht") }
            },
        )
    }

    TenetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.MonitorWeight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Körper", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(shapes = ButtonDefaults.shapes(), onClick = { dialogOpen = true }) { Text("Eintragen") }
            }

            val latest = metrics.lastOrNull()
            val previous = metrics.getOrNull(metrics.size - 2)
            if (latest != null) {
                val delta = previous?.let { latest.weight - it.weight }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${formatWeight(latest.weight)} kg",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (delta != null && kotlin.math.abs(delta) >= 0.1f) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "%+.1f kg".format(delta),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (delta > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                }
                Text(
                    text = latest.date.parseIso().formatDate() + (latest.bodyFat?.let { " · ${formatWeight(it)} % Körperfett" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Latest value per measurement and the change since the first one.
                val series = BodyMeasurements.FIELDS.mapNotNull { (key, label) ->
                    val values = metrics.mapNotNull { BodyMeasurements.decode(it.measurementsJson)[key] }
                    values.lastOrNull()?.let { Triple(label, it, values.last() - values.first()) }
                }
                if (series.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        series.forEach { (label, value, delta) ->
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text(
                                    "$label ${formatWeight(value)} cm" +
                                        if (kotlin.math.abs(delta) >= 0.1f) " (%+.1f)".format(delta).replace('.', ',') else "",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Noch kein Gewicht eingetragen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (metrics.size >= 2) {
                val producer = remember { CartesianChartModelProducer() }
                LaunchedEffect(metrics) {
                    producer.runTransaction {
                        lineModel {
                            series(
                                metrics.map { it.date.parseIso().toEpochDay().toFloat() },
                                metrics.map { it.weight },
                            )
                        }
                    }
                }
                val dateLabels = remember(metrics) {
                    val formatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
                    metrics.associate {
                        it.date.parseIso().toEpochDay().toDouble() to
                            it.date.parseIso().format(formatter)
                    }
                }
                // Charts follow the M3 color scheme (primary, tertiary …).
                ProvideVicoTheme(rememberM3VicoTheme()) {
                    CartesianChartHost(
                        rememberCartesianChart(
                            rememberLineCartesianLayer(),
                            startAxis = VerticalAxis.rememberStart(),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                valueFormatter = CartesianValueFormatter { _, x, _ ->
                                    dateLabels[x] ?: ""
                                },
                            ),
                        ),
                        producer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .semantics {
                                contentDescription = "Liniendiagramm Körpergewicht: " +
                                    "${metrics.size} Einträge, " +
                                    "von ${formatWeight(metrics.minOf { it.weight })} " +
                                    "bis ${formatWeight(metrics.maxOf { it.weight })} Kilogramm."
                            },
                    )
                }
            }
        }
    }

    if (dialogOpen) {
        AddWeightDialog(
            last = metrics.lastOrNull(),
            onDismiss = { dialogOpen = false },
            onSave = { weight, fat, measurements ->
                onSave(weight, fat, measurements)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun AddWeightDialog(
    last: BodyMetric?,
    onDismiss: () -> Unit,
    onSave: (Float?, Float?, Map<String, Float>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val measures = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val lastMeasures = remember(last) { BodyMeasurements.decode(last?.measurementsJson) }
    fun num(s: String) = s.replace(',', '.').toFloatOrNull()
    val parsed = num(text)
    val weightOk = text.isBlank() || (parsed != null && parsed in 20f..400f)
    val values = measures.mapNotNull { (k, v) -> num(v)?.takeIf { it in 10f..250f }?.let { k to it } }.toMap()
    val any = parsed != null || num(fat) != null || values.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Körper erfassen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Gewicht (kg)") },
                        placeholder = { last?.let { Text(formatWeight(it.weight)) } },
                        singleLine = true,
                        isError = !weightOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("Fett (%)") },
                        placeholder = { last?.bodyFat?.let { Text(formatWeight(it)) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Umfänge in cm (optional)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                BodyMeasurements.FIELDS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (key, label) ->
                            OutlinedTextField(
                                value = measures[key].orEmpty(),
                                onValueChange = { measures[key] = it },
                                label = { Text(label) },
                                placeholder = { lastMeasures[key]?.let { Text(formatWeight(it)) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = { onSave(parsed, num(fat)?.takeIf { it in 2f..70f }, values) },
                enabled = weightOk && any && (parsed != null || last != null),
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

private fun formatWeight(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString()
    else value.toString().replace('.', ',')

private fun String.parseIso(): LocalDate =
    runCatching { LocalDate.parse(this) }.getOrDefault(LocalDate.now())

private fun LocalDate.formatDate(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMAN))
