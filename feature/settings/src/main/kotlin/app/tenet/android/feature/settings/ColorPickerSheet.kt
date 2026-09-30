package app.tenet.android.feature.settings

import app.tenet.android.core.designsystem.component.animatedMorphShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.tenet.android.core.designsystem.component.TenetSlider
import app.tenet.android.core.designsystem.component.rememberSheetState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.theme.previewColorScheme

/** Curated seeds; each produces a balanced M3 tonal scheme. */
internal val ColorPresets: List<Pair<String, Color>> = listOf(
    "Petrol" to Color(0xFF006A6A),
    "Türkis" to Color(0xFF00897B),
    "Cyan" to Color(0xFF0097A7),
    "Blau" to Color(0xFF1E6BD6),
    "Indigo" to Color(0xFF4C5BD4),
    "Violett" to Color(0xFF7B4FD1),
    "Lila" to Color(0xFF9C27B0),
    "Pink" to Color(0xFFD0457A),
    "Rot" to Color(0xFFC62828),
    "Orange" to Color(0xFFE0701A),
    "Bernstein" to Color(0xFFD9A21B),
    "Oliv" to Color(0xFF7C8B24),
    "Grün" to Color(0xFF2E7D32),
    "Braun" to Color(0xFF795548),
    "Schiefer" to Color(0xFF546E7A),
    "Graphit" to Color(0xFF5F5F5F),
)

internal fun colorName(argb: Int?): String =
    if (argb == null) {
        "Standard"
    } else {
        ColorPresets.firstOrNull { it.second.toArgb() == argb }?.first
            ?: "#%06X".format(argb and 0xFFFFFF)
    }

/**
 * Bottom sheet to pick one app seed color: preset swatches, a free hue
 * slider and a live preview of the resulting M3 color roles.
 *
 * @param current the stored ARGB, or null for the default theme.
 * @param onPick called with the new ARGB, or null to reset to the default.
 */
@Composable
internal fun ColorPickerSheet(
    title: String,
    current: Int?,
    otherPrimary: Int?,
    otherSecondary: Int?,
    editingPrimary: Boolean,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberSheetState(skipPartiallyExpanded = true)
    var picked by remember { mutableStateOf(current?.let { Color(it) }) }
    val hsv = remember(picked) { picked?.toHsv() }
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val preview: ColorScheme = remember(picked, darkTheme) {
        val p = if (editingPrimary) picked else otherPrimary?.let { Color(it) }
        val s = if (editingPrimary) otherSecondary?.let { Color(it) } else picked
        previewColorScheme(p, s, darkTheme)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)

            SchemePreview(preview)

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 8,
            ) {
                ColorPresets.forEach { (name, color) ->
                    Swatch(
                        color = color,
                        name = name,
                        selected = picked?.toArgb() == color.toArgb(),
                        onClick = { picked = color },
                    )
                }
            }

            Column {
                Text(
                    "Eigener Farbton",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val hue = hsv?.get(0) ?: 180f
                TenetSlider(
                    value = hue,
                    onValueChange = { picked = Color.hsv(it, 0.7f, 0.72f) },
                    valueRange = 0f..359f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, 0.7f, 0.72f),
                        activeTrackColor = Color.hsv(hue, 0.7f, 0.72f),
                    ),
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onPick(null) }, shapes = ButtonDefaults.shapes()) {
                    Text("Standard", maxLines = 1)
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Abbrechen") }
                Button(
                    onClick = { onPick(picked?.toArgb()) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Übernehmen") }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    // Selected swatch morphs into an expressive cookie shape.
    Surface(
        onClick = onClick,
        shape = animatedMorphShape(selected, MaterialShapes.Circle, MaterialShapes.Cookie9Sided),
        color = color,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        modifier = Modifier
            .size(40.dp)
            .semantics {
                contentDescription = name
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Mini preview of the key roles the picked seeds generate. */
@Composable
private fun SchemePreview(scheme: ColorScheme) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleChip("Primär", scheme.primary, scheme.onPrimary, Modifier.weight(1f))
                RoleChip("Container", scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleChip("Sekundär", scheme.secondary, scheme.onSecondary, Modifier.weight(1f))
                RoleChip("Container", scheme.secondaryContainer, scheme.onSecondaryContainer, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleChip("Tertiär", scheme.tertiary, scheme.onTertiary, Modifier.weight(1f))
                RoleChip("Fläche", scheme.surfaceContainerHighest, scheme.onSurface, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoleChip(label: String, color: Color, onColor: Color, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = modifier.height(40.dp)) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = onColor)
        }
    }
}

private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return out
}
