package app.tenet.android.core.designsystem.nutrition

import app.tenet.android.core.designsystem.theme.TenetCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.TenetSlider
import app.tenet.android.core.designsystem.component.rememberSheetState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import app.tenet.android.core.common.ActivityLevel
import app.tenet.android.core.common.BodyProfile
import app.tenet.android.core.common.EnergyMath
import app.tenet.android.core.common.MacroTargets
import app.tenet.android.core.common.Sex
import app.tenet.android.core.common.WeightGoal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * MD3 bottom sheet to set the daily calorie goal and macro split (4/4/9 kcal per gram).
 *
 * Shared by the Today and Nutrition screens; the caller persists the values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    currentKcal: Float,
    currentProtein: Float,
    currentCarbs: Float,
    currentFat: Float,
    onDismiss: () -> Unit,
    onSave: (kcal: Float, protein: Float, carbs: Float, fat: Float) -> Unit,
    profile: BodyProfile? = null,
    onSaveProfile: (BodyProfile) -> Unit = {},
) {
    var calculator by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    var kcal by remember { mutableFloatStateOf(currentKcal.coerceIn(800f, 6000f)) }
    var protein by remember { mutableFloatStateOf(currentProtein.coerceIn(20f, 300f)) }
    var carbs by remember { mutableFloatStateOf(currentCarbs.coerceIn(20f, 500f)) }
    var fat by remember { mutableFloatStateOf(currentFat.coerceIn(10f, 150f)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tagesziel", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Kalorienziel und Makro-Verteilung festlegen – gilt ab heute.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SegmentedSelector(
                segments = listOf(Segment("Manuell"), Segment("Berechnen")),
                selectedIndex = if (calculator) 1 else 0,
                onSelect = { calculator = it == 1 },
            )

            if (calculator) {
                GoalCalculator(
                    initial = profile ?: BodyProfile(),
                    onApply = { targets, body ->
                        kcal = targets.kcal.coerceIn(800f, 6000f)
                        protein = targets.protein.coerceIn(20f, 300f)
                        carbs = targets.carbs.coerceIn(20f, 500f)
                        fat = targets.fat.coerceIn(10f, 150f)
                        onSaveProfile(body)
                        calculator = false
                    },
                )
            } else {
            // Quick presets as M3 suggestion chips.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GoalPresets.forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            kcal = preset.kcal
                            protein = preset.protein
                            carbs = preset.carbs
                            fat = preset.fat
                        },
                        label = { Text(preset.label) },
                        icon = {
                            Icon(
                                preset.icon,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }

            GoalSlider("Kalorien", kcal, 800f..6000f, 50f, "kcal") { kcal = it }
            GoalSlider("Eiweiß", protein, 20f..300f, 5f, "g") { protein = it }
            GoalSlider("Kohlenhydrate", carbs, 20f..500f, 5f, "g") { carbs = it }
            GoalSlider("Fett", fat, 10f..150f, 5f, "g") { fat = it }

            val macroKcal = protein * 4f + carbs * 4f + fat * 9f
            val delta = (macroKcal - kcal).roundToInt()
            val proteinPct = if (kcal > 0f) (protein * 4f / kcal * 100f).roundToInt() else 0
            val carbsPct = if (kcal > 0f) (carbs * 4f / kcal * 100f).roundToInt() else 0
            val fatPct = if (kcal > 0f) (fat * 9f / kcal * 100f).roundToInt() else 0
            val mismatch = abs(delta) > 25

            TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Makro-Verteilung", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Eiweiß $proteinPct % · Kohlenhydrate $carbsPct % · Fett $fatPct %",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = buildString {
                            append("Makros ergeben ${macroKcal.roundToInt()} kcal von ${kcal.roundToInt()} kcal")
                            if (mismatch) {
                                append(if (delta > 0) " (+$delta)" else " ($delta)")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mismatch) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") }
                Button(
                    onClick = { onSave(kcal, protein, carbs, fat) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("Speichern")
                }
            }
            }
        }
    }
}

@Composable
private fun GoalSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${value.roundToInt()} $unit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TenetSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = ((range.endInclusive - range.start) / step).toInt() - 1,
        )
    }
}

private class GoalPreset(
    val label: String,
    val icon: ImageVector,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
)

private val GoalPresets = listOf(
    GoalPreset("Defizit", Icons.AutoMirrored.Outlined.TrendingDown, 1800f, 150f, 160f, 60f),
    GoalPreset("Halten", Icons.Outlined.Balance, 2300f, 140f, 260f, 75f),
    GoalPreset("Aufbau", Icons.AutoMirrored.Outlined.TrendingUp, 2800f, 170f, 330f, 85f),
)

/**
 * Calorie calculator (App_Konzept.md 5.4): Mifflin-St Jeor × activity factor
 * plus goal adjustment; protein and fat per kg body weight, carbs the rest.
 */
@Composable
private fun GoalCalculator(
    initial: BodyProfile,
    onApply: (MacroTargets, BodyProfile) -> Unit,
) {
    var sex by rememberSaveable { mutableStateOf(initial.sex) }
    var age by rememberSaveable { mutableStateOf(initial.age.toString()) }
    var height by rememberSaveable { mutableStateOf(initial.heightCm.roundToInt().toString()) }
    var weight by rememberSaveable { mutableStateOf(initial.weightKg.toString().removeSuffix(".0").replace('.', ',')) }
    var activity by rememberSaveable { mutableStateOf(initial.activity) }
    var goal by rememberSaveable { mutableStateOf(initial.goal) }
    var proteinPerKg by rememberSaveable { mutableFloatStateOf(2f) }
    var fatPerKg by rememberSaveable { mutableFloatStateOf(0.9f) }

    val body = BodyProfile(
        sex = sex,
        age = age.toIntOrNull()?.coerceIn(14, 99) ?: initial.age,
        heightCm = height.replace(',', '.').toFloatOrNull()?.coerceIn(120f, 230f) ?: initial.heightCm,
        weightKg = weight.replace(',', '.').toFloatOrNull()?.coerceIn(35f, 250f) ?: initial.weightKg,
        activity = activity,
        goal = goal,
    )
    val targetKcal = EnergyMath.targetKcal(body)
    val targets = EnergyMath.macrosByBodyWeight(targetKcal, body.weightKg, proteinPerKg, fatPerKg)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedSelector(
            segments = listOf(Segment("Männlich"), Segment("Weiblich")),
            selectedIndex = if (sex == Sex.FEMALE) 1 else 0,
            onSelect = { sex = if (it == 1) Sex.FEMALE else Sex.MALE },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput("Alter", age, { age = it }, "J.", Modifier.weight(1f))
            NumberInput("Größe", height, { height = it }, "cm", Modifier.weight(1f))
            NumberInput("Gewicht", weight, { weight = it }, "kg", Modifier.weight(1f))
        }

        var activityOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = activityOpen, onExpandedChange = { activityOpen = it }) {
            OutlinedTextField(
                value = activity.label + " (×" + activity.factor + ")",
                onValueChange = {},
                readOnly = true,
                label = { Text("Aktivität") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = activityOpen) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = activityOpen, onDismissRequest = { activityOpen = false }) {
                ActivityLevel.entries.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.label + " (×" + level.factor + ")") },
                        onClick = {
                            activity = level
                            activityOpen = false
                        },
                    )
                }
            }
        }

        SegmentedSelector(
            segments = WeightGoal.entries.map { Segment(it.label) },
            selectedIndex = WeightGoal.entries.indexOf(goal),
            onSelect = { goal = WeightGoal.entries[it] },
        )

        LabeledSlider("Eiweiß", proteinPerKg, 1.2f..2.6f, 13, "%.1f g/kg".format(java.util.Locale.GERMAN, proteinPerKg)) { proteinPerKg = it }
        LabeledSlider("Fett", fatPerKg, 0.6f..1.4f, 7, "%.1f g/kg".format(java.util.Locale.GERMAN, fatPerKg)) { fatPerKg = it }

        TenetCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Grundumsatz ${EnergyMath.bmr(body).roundToInt()} kcal · Gesamtumsatz ${EnergyMath.tdee(body).roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${targets.kcal.roundToInt()} kcal", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    "Eiweiß ${targets.protein.roundToInt()} g · Kohlenhydrate ${targets.carbs.roundToInt()} g · Fett ${targets.fat.roundToInt()} g",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(
            onClick = { onApply(targets, body) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Übernehmen") }
    }
}

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit, suffix: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        TenetSlider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
