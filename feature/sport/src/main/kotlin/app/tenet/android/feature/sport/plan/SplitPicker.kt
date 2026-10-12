package app.tenet.android.feature.sport.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.dp
import app.tenet.android.core.common.GymSplits
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.designsystem.component.TenetFilterChip

/** Catalogue exercises for [muscles], in the order of the muscle groups. */
internal fun List<Exercise>.matching(muscles: List<GymSplits.Muscle>): List<Exercise> =
    mapNotNull { ex ->
        val own = ex.primaryMuscles.split(',').map { it.trim() }
        muscles.indexOfFirst { m -> own.any { it in m.catalog } }.takeIf { it >= 0 }?.let { it to ex }
    }.sortedBy { it.first }.map { it.second }

internal val SplitChoiceSaver = Saver<GymSplits.Choice, String>(save = { it.encode() }, restore = { GymSplits.Choice.decode(it) })

/**
 * Split length (Ganzkörper, 2er … 5er), then which muscle groups train together:
 * one of the usual combinations or an own one, picked per unit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitPicker(choice: GymSplits.Choice, onChange: (GymSplits.Choice) -> Unit, recommended: GymSplits.Choice? = null) {
    val sizes = GymSplits.sizes
    val size = choice.size.coerceIn(0, sizes.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormLabel("Split")
        Chips(sizes.indices.toList(), size, { sizes[it].label + if (it == recommended?.size) " · empfohlen" else "" }) { onChange(choice.withSize(it)) }
        FormLabel("Muskelgruppen kombinieren")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sizes[size].combos.forEachIndexed { i, c ->
                TenetFilterChip(
                    selected = choice.own == null && choice.combo.coerceIn(0, sizes[size].combos.lastIndex) == i,
                    onClick = { onChange(GymSplits.Choice(size, i)) },
                    label = { Text(c.label) },
                )
            }
            TenetFilterChip(selected = choice.own != null, onClick = { onChange(choice.toOwn()) }, label = { Text("Eigene Kombination") })
        }
        choice.own?.forEachIndexed { i, muscles ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Einheit ${i + 1}" + GymSplits.title(muscles).let { if (it.isEmpty()) "" else " · $it" },
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GymSplits.Muscle.entries.forEach { m ->
                        TenetFilterChip(
                            selected = m in muscles,
                            onClick = {
                                onChange(choice.copy(own = choice.own.orEmpty().mapIndexed { j, set -> if (j != i) set else if (m in set) set - m else set + m }))
                            },
                            label = { Text(m.label) },
                        )
                    }
                }
            }
        }
        if (!choice.valid) {
            Text("Wähle für jede Einheit mindestens eine Muskelgruppe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
