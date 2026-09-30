package app.tenet.android.feature.journal

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tenet.android.core.database.dao.TagCount
import app.tenet.android.core.designsystem.component.TooltipIconButton
import java.time.LocalDate

/** Note colors (App_Konzept.md 5.3 "Farben aus der Tonpalette"); null = none. */
internal val NoteColors: List<Int?> = listOf(
    null,
    0xFFE5736F.toInt(),
    0xFFF2A65A.toInt(),
    0xFFE8C547.toInt(),
    0xFF7DBE7A.toInt(),
    0xFF5BB5D9.toInt(),
    0xFF8C8FE0.toInt(),
    0xFFC07DC9.toInt(),
)

/** Card container for a note color, blended into the surface so text stays readable. */
@Composable
internal fun noteContainer(color: Int?): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    if (color == null) return base
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return lerp(base, Color(color), if (dark) 0.28f else 0.38f)
}

internal val DreamEmotions = listOf(
    "Freude", "Angst", "Trauer", "Wut", "Überraschung", "Ruhe",
    "Liebe", "Verwirrung", "Scham", "Ekel", "Neugier", "Freiheit",
)

/** Rotating diary prompts (App_Konzept.md 5.3 "Prompts"). */
internal val DiaryPrompts = listOf(
    "Wofür bist du heute dankbar?",
    "Was hat dich heute gefordert?",
    "Worauf bist du heute stolz?",
    "Was hat dich heute zum Lachen gebracht?",
    "Was würdest du heute anders machen?",
    "Welcher Moment soll dir von heute bleiben?",
    "Was hast du heute gelernt?",
    "Wem möchtest du heute danken?",
    "Was hat dir heute Energie gegeben, was hat sie gekostet?",
    "Worauf freust du dich morgen?",
)

internal fun promptOfDay(offset: Int = 0): String {
    val index = (LocalDate.now().dayOfYear + offset).mod(DiaryPrompts.size)
    return DiaryPrompts[index]
}

internal val MoodEmojis = listOf("😞", "🙁", "😐", "🙂", "😊")

/**
 * Chip-based tag editor: current values as input chips (tap to remove), a
 * text field to add new ones and the most used existing values as suggestions.
 */
@Composable
internal fun TagEditor(
    label: String,
    values: List<String>,
    suggestions: List<TagCount>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    fun commit() {
        if (input.isNotBlank()) onAdd(input)
        input = ""
    }
    // Chips first; "+ Tag" opens a small inline field (suggestions only then).
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEach { value ->
                InputChip(
                    selected = true,
                    onClick = { onRemove(value) },
                    label = { Text(value) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "$value entfernen",
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                )
            }
            if (!adding) {
                androidx.compose.material3.AssistChip(
                    onClick = { adding = true },
                    label = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(androidx.compose.material3.AssistChipDefaults.IconSize)) },
                )
            }
        }
        if (adding) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                        modifier = Modifier.weight(1f).padding(vertical = 14.dp).focusRequester(focus),
                    )
                    if (input.isNotBlank()) {
                        TooltipIconButton(icon = Icons.Outlined.Add, contentDescription = "Hinzufügen", onClick = ::commit)
                    }
                    TooltipIconButton(icon = Icons.Outlined.Close, contentDescription = "Fertig", onClick = { commit(); adding = false })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions
                    .filter { s -> values.none { it.equals(s.name, ignoreCase = true) } }
                    .filter { input.isBlank() || it.name.contains(input.trim(), ignoreCase = true) }
                    .take(8)
                    .forEach { s ->
                        SuggestionChip(onClick = { onAdd(s.name); input = "" }, label = { Text("${s.name} · ${s.count}") })
                    }
            }
        }
    }
}
