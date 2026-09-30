package app.tenet.android.feature.nutrition

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.Segment
import app.tenet.android.core.designsystem.component.SegmentedSelector
import app.tenet.android.core.designsystem.component.rememberSheetState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.EnergyMath
import app.tenet.android.core.database.entity.Food
import app.tenet.android.core.database.entity.MealType
import java.time.LocalTime
import kotlin.math.roundToInt

internal val MealType.defaultLabel: String
    get() = when (this) {
        MealType.BREAKFAST -> "Frühstück"
        MealType.LUNCH -> "Mittag"
        MealType.DINNER -> "Abend"
        MealType.SNACK -> "Snacks"
    }

/** Section name honoring the user's renames (App_Konzept.md 5.4 "umbenennbar"). */
internal fun MealType.label(custom: Map<String, String>): String = custom[name] ?: defaultLabel

internal val MealType.icon: ImageVector
    get() = when (this) {
        MealType.BREAKFAST -> Icons.Outlined.FreeBreakfast
        MealType.LUNCH -> Icons.Outlined.LunchDining
        MealType.DINNER -> Icons.Outlined.DinnerDining
        MealType.SNACK -> Icons.Outlined.Cookie
    }

internal fun defaultMealType(): MealType = when (LocalTime.now().hour) {
    in 4..10 -> MealType.BREAKFAST
    in 11..14 -> MealType.LUNCH
    in 15..17 -> MealType.SNACK
    else -> MealType.DINNER
}

/** Accepts both German (1,5) and English (1.5) decimal separators. */
internal fun String.parseAmount(): Float? = trim().replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }

internal fun Float.fmt(): String = if (this >= 10f) roundToInt().toString() else "%.1f".format(java.util.Locale.GERMAN, this).removeSuffix(",0").removeSuffix(".0")

/** Units offered for [food]: grams, millilitres (1:1) and its serving if known. */
internal data class PortionUnit(val label: String, val grams: Float)

internal fun unitsFor(food: Food): List<PortionUnit> = buildList {
    add(PortionUnit("g", 1f))
    add(PortionUnit("ml", 1f))
    food.servingSizeG?.let { add(PortionUnit("Portion (${it.fmt()} g)", it)) }
}

/** Connected single-select meal picker (M3 Expressive button group). */
@Composable
internal fun MealPicker(selected: MealType, names: Map<String, String>, onSelect: (MealType) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val meals = MealType.entries
        meals.forEachIndexed { index, meal ->
            ToggleButton(
                checked = selected == meal,
                onCheckedChange = { onSelect(meal) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    meals.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) { Text(meal.label(names), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/**
 * Portion input (App_Konzept.md 5.4): amount + unit with a live preview of
 * the nutrients, meal selection and the favorite toggle.
 */
@Composable
internal fun PortionSheet(
    food: Food,
    initialMeal: MealType,
    mealNames: Map<String, String>,
    onDismiss: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onAdd: (grams: Float, quantity: Float, unit: String, meal: MealType) -> Unit,
) {
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    val units = unitsFor(food)
    var unitIndex by rememberSaveable { mutableStateOf(if (food.servingSizeG != null) 2 else 0) }
    val unit = units[unitIndex.coerceIn(units.indices)]
    var amount by rememberSaveable { mutableStateOf(if (unit.grams > 1f) "1" else "100") }
    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    var favorite by rememberSaveable { mutableStateOf(food.favorite) }
    val quantity = amount.parseAmount() ?: 0f
    val grams = quantity * unit.grams

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(food.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(food.brand, "${food.kcalPer100.roundToInt()} kcal / 100 g").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconToggleButton(
                    checked = favorite,
                    onCheckedChange = {
                        favorite = it
                        onToggleFavorite(it)
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (favorite) "Aus Favoriten entfernen" else "Zu Favoriten",
                    )
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Menge") },
                suffix = { Text(unit.label.substringBefore(" (")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            SegmentedSelector(
                segments = units.map { Segment(it.label) },
                selectedIndex = unitIndex,
                onSelect = { index ->
                    val u = units[index]
                    // Keep the amount sensible when switching unit families.
                    if ((units[unitIndex].grams > 1f) != (u.grams > 1f)) amount = if (u.grams > 1f) "1" else "100"
                    unitIndex = index
                },
            )

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Nutrient("${EnergyMath.per100(food.kcalPer100, grams).roundToInt()}", "kcal")
                    Nutrient(EnergyMath.per100(food.proteinPer100, grams).fmt(), "Eiweiß g")
                    Nutrient(EnergyMath.per100(food.carbsPer100, grams).fmt(), "Kohlenh. g")
                    Nutrient(EnergyMath.per100(food.fatPer100, grams).fmt(), "Fett g")
                }
            }

            MealPicker(selected = meal, names = mealNames, onSelect = { meal = it })

            Button(
                onClick = { onAdd(grams, quantity, unit.label.substringBefore(" ("), meal) },
                enabled = grams > 0f,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Hinzufügen · ${grams.fmt()} g") }
        }
    }
}

@Composable
internal fun Nutrient(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLargeEmphasized)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
