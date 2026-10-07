package app.tenet.android.feature.journal

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.component.TooltipIconButton

/** One checklist row; [id] keeps focus stable while rows are added or removed. */
private class CheckItem(val id: Long, checked: Boolean, text: String, val heading: Boolean = false) {
    var checked by mutableStateOf(checked)
    var field by mutableStateOf(TextFieldValue(text, TextRange(text.length)))
}

private val CheckLine = Regex("""^\s*[-*] \[( |x|X)] ?(.*)$""")
private val Marker = Regex("""^\s*([-*•] \[[ xX]] ?|[-*•] |\d+[.)] |#{1,3} |> )""")

private val Bullet = Regex("""^\s*([-*•]|\d+[.)]) """)

private data class ParsedLine(val heading: Boolean, val checked: Boolean, val text: String)

/**
 * Body → rows: checklist lines and bullets become items, other lines stay
 * text as headings between them ("Für Satay Chicken · 2 Portionen").
 */
private fun parseItems(body: String): List<ParsedLine> =
    body.lines().filter { it.isNotBlank() }.map { line ->
        CheckLine.find(line)?.let { ParsedLine(false, it.groupValues[1].isNotBlank(), it.groupValues[2]) }
            ?: if (Bullet.containsMatchIn(line)) {
                ParsedLine(false, false, line.replace(Marker, "").trim())
            } else {
                ParsedLine(true, false, line.replace(Marker, "").trim())
            }
    }

private fun serialize(items: List<CheckItem>): String =
    items.joinToString("\n") { if (it.heading) it.field.text else "- [${if (it.checked) "x" else " "}] ${it.field.text}" }

/** Opens as checklist: at least two checklist lines and they are the majority. */
internal fun isChecklist(body: String): Boolean {
    val lines = body.lines().filter { it.isNotBlank() }
    val checks = lines.count { CheckLine.matches(it) }
    return checks >= 1 && (checks == lines.size || (checks >= 2 && checks * 2 >= lines.size))
}

/**
 * Interactive checklist editor (note mode "Checkliste"): each item is a row
 * with checkbox and text. Enter adds the next item, backspace on an empty
 * item removes it. The body is stored as Markdown checklist lines.
 */
@Composable
internal fun ChecklistEditor(body: String, onBody: (String) -> Unit) {
    var nextId by remember { mutableStateOf(0L) }
    // Initial rows straight from the body; an empty note starts with one row.
    val items = remember {
        mutableStateListOf<CheckItem>().apply {
            parseItems(body).forEach { p -> add(CheckItem(nextId++, p.checked, p.text, p.heading)) }
            if (isEmpty()) add(CheckItem(nextId++, false, ""))
        }
    }
    val focus = remember { mutableMapOf<Long, FocusRequester>() }
    val haptics = LocalHapticFeedback.current
    var focusTarget by remember { mutableStateOf<Long?>(null) }

    fun newItem(checked: Boolean, text: String) = CheckItem(nextId++, checked, text)

    // Bodies this editor produced; the state flow can hand back older ones,
    // which must not rebuild the rows (that would drop typed characters).
    val sent = remember { ArrayDeque<String>().apply { add(body) } }
    fun publish() {
        val text = serialize(items)
        if (sent.lastOrNull() != text) {
            sent.addLast(text)
            if (sent.size > 64) sent.removeFirst()
        }
        onBody(text)
    }

    // (Re)build rows when the body changes from outside (e.g. dictation).
    LaunchedEffect(body) {
        if (body.isNotBlank() && body != serialize(items) && body !in sent) {
            sent.clear()
            sent.addLast(body)
            items.clear()
            parseItems(body).forEach { p -> items.add(CheckItem(nextId++, p.checked, p.text, p.heading)) }
            if (items.isEmpty()) items.add(newItem(false, ""))
            // Switching from text: store the converted lines right away.
            if (body.isNotBlank() && serialize(items) != body) publish()
        }
    }
    LaunchedEffect(focusTarget) {
        focusTarget?.let { id -> runCatching { focus[id]?.requestFocus() } }
        focusTarget = null
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            items.forEachIndexed { index, item ->
                val requester = focus.getOrPut(item.id) { FocusRequester() }
                // The row being typed in stays just above the keyboard; rows above scroll away.
                val inView = remember(item.id) { BringIntoViewRequester() }
                var focused by remember(item.id) { mutableStateOf(false) }
                LaunchedEffect(focused, item.field.text.length, items.size) {
                    if (focused) {
                        withFrameNanos { }
                        inView.bringIntoView()
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp).bringIntoViewRequester(inView),
                ) {
                    if (item.heading) Spacer(Modifier.width(16.dp)) else Checkbox(
                        checked = item.checked,
                        onCheckedChange = {
                            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            item.checked = it
                            publish()
                        },
                    )
                    BasicTextField(
                        value = item.field,
                        onValueChange = { value ->
                            // Long items wrap; Enter (a line break) starts the next item instead.
                            val cut = value.text.indexOf('\n')
                            if (cut < 0) {
                                item.field = value
                            } else {
                                val rest = value.text.substring(cut + 1).replace("\n", " ").replaceFirstChar { it.titlecase(java.util.Locale.GERMAN) }
                                item.field = TextFieldValue(value.text.substring(0, cut), TextRange(cut))
                                val added = newItem(false, rest)
                                items.add(index + 1, added)
                                focusTarget = added.id
                            }
                            publish()
                        },
                        singleLine = false,
                        maxLines = Int.MAX_VALUE,
                        textStyle = (if (item.heading) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.bodyLarge).copy(
                            color = if (item.heading) {
                                MaterialTheme.colorScheme.primary
                            } else if (item.checked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                val added = newItem(false, "")
                                items.add(index + 1, added)
                                publish()
                                focusTarget = added.id
                            },
                        ),
                        decorationBox = { inner ->
                            if (item.field.text.isEmpty()) {
                                Text(
                                    "Punkt",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            inner()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(requester)
                            .onFocusChanged { focused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                val removeEmpty = event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Backspace &&
                                    item.field.text.isEmpty() &&
                                    items.size > 1
                                if (removeEmpty) {
                                    items.removeAt(index)
                                    focus.remove(item.id)
                                    publish()
                                    focusTarget = items[(index - 1).coerceAtLeast(0)].id
                                }
                                removeEmpty
                            },
                    )
                    TooltipIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Punkt entfernen",
                        onClick = {
                            items.remove(item)
                            focus.remove(item.id)
                            if (items.isEmpty()) items.add(newItem(false, ""))
                            publish()
                        },
                    )
                }
            }
            TextButton(
                onClick = {
                    val added = newItem(false, "")
                    items.add(added)
                    publish()
                    focusTarget = added.id
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Punkt hinzufügen")
            }
            val done = items.count { it.checked }
            val total = items.count { !it.heading }
            if (total > 1 || done > 0) {
                Text(
                    "$done von $total erledigt",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
                )
            }
        }
    }
}
