package app.tenet.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.ActivityLevel
import app.tenet.android.core.common.BodyProfile
import app.tenet.android.core.common.EnergyMath
import app.tenet.android.core.common.Sex
import app.tenet.android.core.common.WeightGoal
import app.tenet.android.core.datastore.AppModule
import app.tenet.android.core.designsystem.component.ShapeIcon
import kotlin.math.roundToInt

/** Shared frame of the setup steps: progress, icon, title, text, content, bottom buttons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SetupStep(
    step: Int,
    steps: Int,
    icon: ImageVector,
    title: String,
    text: String,
    primary: String = "Weiter",
    onPrimary: () -> Unit,
    onSkip: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        LinearWavyProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            ShapeIcon(icon, containerShape = MaterialShapes.Cookie9Sided.toShape(), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        ) { Text(primary, style = MaterialTheme.typography.titleMedium) }
        if (onSkip != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onSkip, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Überspringen") }
        }
    }
}

private fun AppModule.why(): String = when (this) {
    AppModule.TODAY -> "Dein Tag auf einen Blick"
    AppModule.SPORT -> "Gym, Calisthenics und Laufpläne"
    AppModule.JOURNAL -> "Notizen, Tagebuch und Träume"
    AppModule.NUTRITION -> "Kalorien, Wasser und Rezepte"
}

@Composable
internal fun ModulesStep(step: Int, steps: Int, enabled: Set<AppModule>, onToggle: (AppModule, Boolean) -> Unit, onNext: () -> Unit) {
    SetupStep(
        step, steps, Icons.Outlined.Apps,
        "Was möchtest du nutzen?",
        "Nicht gebrauchte Bereiche verschwinden aus der Leiste. Du kannst sie jederzeit wieder einschalten.",
        onPrimary = onNext,
        primaryEnabled = enabled.isNotEmpty(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            AppModule.entries.forEachIndexed { i, m ->
                val on = m in enabled
                SegmentedListItem(
                    onClick = { onToggle(m, !on) },
                    shapes = ListItemDefaults.segmentedShapes(i, AppModule.entries.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = { SettingsIcon(m.icon) },
                    supportingContent = { Text(m.why()) },
                    trailingContent = { Switch(checked = on, onCheckedChange = { onToggle(m, it) }) },
                ) { Text(m.label) }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(text, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun <T> Segments(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(o), maxLines = 1) }
        }
    }
}

/** Body data for the calorie goal and bodyweight exercises. */
@Composable
internal fun ProfileStep(step: Int, steps: Int, initial: BodyProfile?, onSave: (BodyProfile) -> Unit, onSkip: () -> Unit) {
    val base = initial ?: BodyProfile()
    var sex by rememberSaveable { mutableStateOf(base.sex) }
    var age by rememberSaveable { mutableIntStateOf(base.age) }
    var height by rememberSaveable { mutableFloatStateOf(base.heightCm) }
    var weight by rememberSaveable { mutableFloatStateOf(base.weightKg) }
    var activity by rememberSaveable { mutableStateOf(base.activity) }
    SetupStep(
        step, steps, Icons.Outlined.Person,
        "Ein bisschen über dich",
        "Für deinen Kalorienbedarf und die relative Kraft bei Körpergewichtsübungen.",
        onPrimary = { onSave(base.copy(sex = sex, age = age, heightCm = height, weightKg = weight, activity = activity)) },
        onSkip = onSkip,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segments(Sex.entries, sex, { if (it == Sex.MALE) "Männlich" else "Weiblich" }) { sex = it }
            LabeledSlider("Alter", age.toFloat(), 14f..90f, "$age Jahre") { age = it.roundToInt() }
            LabeledSlider("Größe", height, 140f..210f, "${height.roundToInt()} cm") { height = it.roundToInt().toFloat() }
            LabeledSlider("Gewicht", weight, 40f..150f, "%.1f kg".format(java.util.Locale.GERMAN, weight)) {
                weight = (it * 2).roundToInt() / 2f
            }
            Text("Wie aktiv bist du im Alltag?", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ActivityLevel.entries.forEachIndexed { i, a ->
                    SegmentedListItem(
                        selected = a == activity,
                        onClick = { activity = a },
                        shapes = ListItemDefaults.segmentedShapes(i, ActivityLevel.entries.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) { Text(a.label) }
                }
            }
        }
    }
}

/** Weight goal → calorie target preview, plus the daily water goal. */
@Composable
internal fun GoalsStep(
    step: Int,
    steps: Int,
    profile: BodyProfile?,
    nutrition: Boolean,
    waterMl: Int,
    onSave: (WeightGoal, Int) -> Unit,
) {
    var goal by rememberSaveable { mutableStateOf(profile?.goal ?: WeightGoal.MAINTAIN) }
    var water by rememberSaveable { mutableIntStateOf(waterMl) }
    SetupStep(
        step, steps, Icons.Outlined.Flag,
        "Deine Ziele",
        "Daraus rechnet Tenet deine Tagesziele. Feinjustieren kannst du sie später in Ernährung.",
        onPrimary = { onSave(goal, water) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Segments(WeightGoal.entries, goal, { it.label }) { goal = it }
            if (profile != null && nutrition) {
                val p = profile.copy(goal = goal)
                val kcal = EnergyMath.targetKcal(p)
                val m = EnergyMath.macrosByBodyWeight(kcal, p.weightKg, 2f, 0.9f)
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Tagesziel", style = MaterialTheme.typography.labelLarge)
                        Text("${kcal.roundToInt()} kcal", style = MaterialTheme.typography.displaySmallEmphasized)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Macro("Eiweiß", m.protein)
                            Macro("Kohlenhydrate", m.carbs)
                            Macro("Fett", m.fat)
                        }
                    }
                }
            } else if (nutrition) {
                Text(
                    "Ohne Profil nutzt Tenet ein Standardziel – du kannst es jederzeit in Ernährung anpassen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    LabeledSlider("Wasser pro Tag", water.toFloat(), 1000f..4500f, "%.2f l".format(java.util.Locale.GERMAN, water / 1000f)) {
                        water = (it / 250f).roundToInt() * 250
                    }
                }
            }
        }
    }
}

@Composable
private fun Macro(label: String, grams: Float) {
    Column {
        Text("${grams.roundToInt()} g", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
