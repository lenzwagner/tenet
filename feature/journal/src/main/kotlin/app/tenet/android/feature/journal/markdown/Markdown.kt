package app.tenet.android.feature.journal.markdown

import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/*
 * Small Markdown dialect for journal entries (App_Konzept.md 5.3 Notizen):
 * headings, bullet / numbered lists, checklists, quotes, fenced code, rules,
 * **bold**, *italic*, `code` and [[Note title]] links.
 */

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: String, val text: String) : MdBlock
    data class Check(val checked: Boolean, val text: String, val line: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
}

private val CheckRegex = Regex("""^\s*[-*] \[( |x|X)] (.*)$""")
private val BulletRegex = Regex("""^\s*[-*•] (.*)$""")
private val NumberRegex = Regex("""^\s*(\d+)[.)] (.*)$""")
private val HeadingRegex = Regex("""^(#{1,3}) (.*)$""")
private val LinkRegex = Regex("""\[\[([^\[\]]+)]]""")

fun parseMarkdown(body: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = body.split('\n')
    var i = 0
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) out += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                out += MdBlock.Code(code.toString().trimEnd())
            }
            line.isBlank() -> flush()
            line.trim() == "---" || line.trim() == "***" -> { flush(); out += MdBlock.Rule }
            HeadingRegex.matches(line) -> {
                flush()
                val m = HeadingRegex.find(line)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            CheckRegex.matches(line) -> {
                flush()
                val m = CheckRegex.find(line)!!
                out += MdBlock.Check(m.groupValues[1].isNotBlank(), m.groupValues[2], i)
            }
            BulletRegex.matches(line) -> { flush(); out += MdBlock.Bullet(BulletRegex.find(line)!!.groupValues[1]) }
            NumberRegex.matches(line) -> {
                flush()
                val m = NumberRegex.find(line)!!
                out += MdBlock.Numbered(m.groupValues[1], m.groupValues[2])
            }
            line.startsWith("> ") -> { flush(); out += MdBlock.Quote(line.removePrefix("> ")) }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        i++
    }
    flush()
    return out
}

/** Flips the checkbox on [line] of [body]. */
fun toggleCheck(body: String, line: Int): String {
    val lines = body.split('\n').toMutableList()
    val l = lines.getOrNull(line) ?: return body
    lines[line] = when {
        l.contains("[ ]") -> l.replaceFirst("[ ]", "[x]")
        l.contains("[x]") -> l.replaceFirst("[x]", "[ ]")
        l.contains("[X]") -> l.replaceFirst("[X]", "[ ]")
        else -> l
    }
    return lines.joinToString("\n")
}

/** done / total of checklist items, or null without a checklist. */
fun checklistProgress(body: String): Pair<Int, Int>? {
    // Empty items (the new line after the last Enter) don't count.
    val checks = body.lineSequence().mapNotNull { CheckRegex.find(it) }.filter { it.groupValues[2].isNotBlank() }.toList()
    if (checks.isEmpty()) return null
    return checks.count { it.groupValues[1].isNotBlank() } to checks.size
}

/** All `[[title]]` targets in [body]. */
fun extractLinks(body: String): List<String> =
    LinkRegex.findAll(body).map { it.groupValues[1].trim() }.distinct().toList()

/** Body without Markdown syntax, for one-line previews. */
fun markdownPlain(body: String): String =
    body.lineSequence()
        .map { line ->
            line.replace(Regex("""^#{1,3} """), "")
                .replace(Regex("""^\s*[-*] \[( |x|X)] """), "")
                .replace(Regex("""^\s*[-*•] """), "• ")
                .replace(Regex("""^> """), "")
                .replace("```", "")
        }
        .joinToString(" ")
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""(?<![*\w])[*_](.+?)[*_](?![*\w])"""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(LinkRegex, "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()

// ---- Inline -------------------------------------------------------------

private val InlineRegex = Regex("""\*\*(.+?)\*\*|(?<![*\w])[*_](.+?)[*_](?![*\w])|`([^`]+)`|\[\[([^\[\]]+)]]""")

@Composable
private fun inline(text: String, onLink: (String) -> Unit): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(text, colors) {
        buildAnnotatedString {
            var last = 0
            InlineRegex.findAll(text).forEach { m ->
                append(text.substring(last, m.range.first))
                val (bold, italic, code, link) = m.destructured
                when {
                    bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                    italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                    code.isNotEmpty() -> withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHighest),
                    ) { append(code) }
                    else -> withLink(
                        LinkAnnotation.Clickable(
                            tag = link,
                            styles = TextLinkStyles(
                                SpanStyle(
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ) { onLink(link.trim()) },
                    ) { append(link) }
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

/**
 * Renders [body] as Markdown. Checkboxes are tappable ([onToggleCheck] gets
 * the source line), `[[links]]` call [onLink] with the linked title.
 */
@Composable
fun MarkdownView(
    body: String,
    onToggleCheck: (line: Int) -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    maxBlocks: Int = Int.MAX_VALUE,
    maxLines: Int = Int.MAX_VALUE,
) {
    val all = remember(body) { parseMarkdown(body) }
    val blocks = all.take(maxBlocks)
    // Card preview cut off (blocks left out or a line ellipsized): fade out
    // the bottom edge so it reads as "continues", like Google Keep.
    var overflowed by remember(body, maxBlocks, maxLines) { mutableStateOf(false) }
    val onLayout: (TextLayoutResult) -> Unit = { if (it.hasVisualOverflow) overflowed = true }
    val cut = compact && (all.size > blocks.size || overflowed)
    val t = MaterialTheme.typography
    // Compact = card preview: one size step smaller everywhere.
    // Previews a touch smaller still (13 sp), so the tile's title stands out like in Keep.
    val body1 = if (compact) t.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp) else t.bodyLarge
    val h1 = if (compact) t.titleMediumEmphasized else t.headlineSmallEmphasized
    val h2 = if (compact) t.titleSmallEmphasized else t.titleLargeEmphasized
    val h3 = if (compact) t.labelLargeEmphasized else t.titleMediumEmphasized
    Column(
        modifier.then(if (cut) Modifier.fadeBottom() else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, onLink),
                    style = when (block.level) {
                        1 -> h1
                        2 -> h2
                        else -> h3
                    },
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = onLayout,
                    modifier = Modifier.padding(top = if (compact) 2.dp else 6.dp),
                )
                is MdBlock.Paragraph -> Text(
                    inline(block.text, onLink),
                    style = body1,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = onLayout,
                )
                is MdBlock.Bullet -> ListLine("•", compact) {
                    Text(inline(block.text, onLink), style = body1, maxLines = maxLines, overflow = TextOverflow.Ellipsis, onTextLayout = onLayout)
                }
                is MdBlock.Numbered -> ListLine("${block.number}.", compact) {
                    Text(inline(block.text, onLink), style = body1, maxLines = maxLines, overflow = TextOverflow.Ellipsis, onTextLayout = onLayout)
                }
                is MdBlock.Check -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (compact) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Checkbox(
                                checked = block.checked,
                                onCheckedChange = { onToggleCheck(block.line) },
                                modifier = Modifier.size(28.dp).scale(0.9f),
                            )
                        }
                    } else {
                        Checkbox(checked = block.checked, onCheckedChange = { onToggleCheck(block.line) })
                    }
                    Text(
                        inline(block.text, onLink),
                        style = body1.copy(
                            textDecoration = if (block.checked) TextDecoration.LineThrough else null,
                            color = if (block.checked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Color.Unspecified
                            },
                        ),
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                    onTextLayout = onLayout,
                    )
                }
                is MdBlock.Quote -> Row {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .width(4.dp)
                            .padding(vertical = 2.dp),
                    ) { Box(Modifier.padding(vertical = 10.dp)) }
                    Text(
                        inline(block.text, onLink),
                        style = body1.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                    onTextLayout = onLayout,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                is MdBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        block.text,
                        style = (if (compact) t.bodySmall else t.bodyMedium).copy(fontFamily = FontFamily.Monospace),
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                    onTextLayout = onLayout,
                        modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
                    )
                }
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ListLine(marker: String, compact: Boolean = false, content: @Composable () -> Unit) {
    Row {
        Text(
            marker,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(if (compact) 20.dp else 24.dp),
        )
        content()
    }
}

// ---- Editing helpers ----------------------------------------------------

/** Wraps the selection in [open]/[close] (e.g. "**"), or inserts an empty pair. */
fun TextFieldValue.wrapSelection(open: String, close: String = open): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val newText = text.substring(0, start) + open + text.substring(start, end) + close + text.substring(end)
    val cursor = if (start == end) start + open.length else end + open.length + close.length
    return copy(text = newText, selection = TextRange(cursor))
}

/** Toggles [prefix] (e.g. "- [ ] ") at the start of every selected line. */
fun TextFieldValue.togglePrefix(prefix: String): TextFieldValue {
    val start = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0)).let { if (selection.min == 0) 0 else it + 1 }
    val endLine = text.indexOf('\n', selection.max).let { if (it == -1) text.length else it }
    val lines = text.substring(start, endLine).split('\n')
    val allHave = lines.all { it.startsWith(prefix) }
    val changed = lines.joinToString("\n") { if (allHave) it.removePrefix(prefix) else prefix + it }
    val newText = text.substring(0, start) + changed + text.substring(endLine)
    return copy(text = newText, selection = TextRange(start + changed.length))
}

/** Inserts [snippet] at the cursor. */
fun TextFieldValue.insert(snippet: String): TextFieldValue {
    val start = selection.min
    val newText = text.substring(0, start) + snippet + text.substring(selection.max)
    return copy(text = newText, selection = TextRange(start + snippet.length))
}

private val ListPrefix = Regex("""^(\s*)([-*•] \[[ xX]] |[-*•] |(\d+)([.)]) |> )""")

/**
 * Markdown list continuation: when [this] differs from [previous] by a single
 * newline typed at the cursor and the line before is a list item, the new line
 * starts with the same marker (checklists unchecked, numbers incremented).
 * Enter on an empty item ends the list instead.
 */
fun TextFieldValue.continueList(previous: TextFieldValue): TextFieldValue {
    val pos = selection.start
    if (!selection.collapsed || text.length != previous.text.length + 1 || pos == 0) return this
    if (text[pos - 1] != '\n' || text.removeRange(pos - 1, pos) != previous.text) return this

    val lineStart = text.lastIndexOf('\n', pos - 2) + 1
    val line = text.substring(lineStart, pos - 1)
    val match = ListPrefix.find(line) ?: return this
    val marker = match.value

    if (line.length == marker.length) {
        // Empty item: drop the marker and the new line break, ending the list.
        val newText = text.substring(0, lineStart) + text.substring(pos)
        return copy(text = newText, selection = TextRange(lineStart))
    }
    val indent = match.groupValues[1]
    val next = when {
        match.groupValues[3].isNotEmpty() ->
            "$indent${match.groupValues[3].toInt() + 1}${match.groupValues[4]} "
        marker.contains('[') -> marker.replace(Regex("""\[[xX]]"""), "[ ]")
        else -> marker
    }
    val newText = text.substring(0, pos) + next + text.substring(pos)
    return copy(text = newText, selection = TextRange(pos + next.length))
}

/** Fades the last ~40 dp to transparent (alpha mask, works on any card color). */
private fun Modifier.fadeBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 40.dp.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                1f to Color.Transparent,
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
