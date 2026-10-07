package app.tenet.android.core.designsystem.component

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Standard icon button with a plain M3 tooltip carrying [contentDescription]
 * and the expressive press-morph shape.
 */
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

/** Centered M3 Expressive loading indicator (morphing shapes). */
@Composable
fun TenetLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/** List/section label in the M3 "title small, primary" style. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    if (app.tenet.android.core.designsystem.theme.isClearStyle) {
        // iOS grouped-list header: small, grey, set in from the cell edge.
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
        )
        return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * Empty state: an icon inside an expressive [MaterialShapes] container,
 * headline and optional supporting text.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    /** Optional next step, e.g. "Erste Notiz schreiben". */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val clear = app.tenet.android.core.designsystem.theme.isClearStyle
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (clear) 8.dp else 12.dp),
    ) {
        if (clear) {
            // iOS-style: a plain, large grey symbol instead of a shaped badge.
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(52.dp).padding(bottom = 4.dp),
            )
        } else {
            Surface(
                shape = breathingMorphShape(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst, periodMs = 3_200),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }
        }
        Text(
            text = title,
            style = if (clear) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.Button(
                onClick = onAction,
                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(actionLabel) }
        }
    }
}

/** Small expressive shape badge for list leading icons. */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerShape: androidx.compose.ui.graphics.Shape = MaterialShapes.Cookie4Sided.toShape(),
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        shape = containerShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/** M3 switch that shows a check icon in the thumb while on. */
@Composable
fun TenetSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (app.tenet.android.core.designsystem.theme.isClearStyle) {
        // iOS-like: always a full-size white knob, accent track when on, a
        // plain grey track when off – no outline, no check mark.
        val c = MaterialTheme.colorScheme
        val dark = c.background.luminance() < 0.5f
        val offTrack = if (dark) androidx.compose.ui.graphics.Color(0xFF39393D) else androidx.compose.ui.graphics.Color(0xFFE5E5EA)
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            thumbContent = { Box(Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)) },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = c.primary,
                checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedTrackColor = offTrack,
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
        return
    }
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

/**
 * Read-only rows as an M3 Expressive segmented group (rounded outer
 * corners, small gaps) instead of divider lines – for lists inside cards.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedRows(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    onClick: ((index: Int) -> Unit)? = null,
    row: @Composable androidx.compose.foundation.layout.RowScope.(index: Int) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(app.tenet.android.core.designsystem.theme.tenetSegmentedGap)) {
        repeat(count) { index ->
            val shape = app.tenet.android.core.designsystem.theme.tenetSegmentedShapes(index, count).shape
            val content: @Composable () -> Unit = {
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { row(index) }
            }
            if (onClick != null) {
                Surface(onClick = { onClick(index) }, shape = shape, color = containerColor, modifier = Modifier.fillMaxWidth(), content = content)
            } else {
                Surface(shape = shape, color = containerColor, modifier = Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

/**
 * Compact filled number cell for workout tables (kg, Wdh, s): 40 dp high,
 * tinted once the set is done. Shared by gym and calisthenics sessions.
 */
@Composable
fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    done: Boolean,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    selectAllOnFocus: Boolean = false,
    /** Shown dimmed while the field is empty. */
    placeholder: String? = null,
) {
    var editor by remember { mutableStateOf(TextFieldValue(value)) }
    var focused by remember { mutableStateOf(false) }
    val currentEditor = if (editor.text == value) editor else editor.copy(text = value)
    LaunchedEffect(focused, selectAllOnFocus) {
        if (focused && selectAllOnFocus) {
            // Apply after the focus-causing tap has positioned the cursor.
            withFrameNanos { }
            editor = editor.copy(text = value, selection = TextRange(0, value.length))
        }
    }
    val colors = MaterialTheme.colorScheme
    val container = if (done) colors.secondaryContainer else colors.surfaceContainerHighest
    val content = if (done) colors.onSecondaryContainer else colors.onSurface
    androidx.compose.foundation.text.BasicTextField(
        value = currentEditor,
        onValueChange = { updated ->
            editor = updated
            if (updated.text != value) onValueChange(updated.text)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = content, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .height(40.dp)
            .background(container, MaterialTheme.shapes.small),
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (placeholder != null && currentEditor.text.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                }
                if (suffix != null) Text(suffix, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        },
    )
}

/**
 * Card header after Apple Health's summary cards: a small pictogram and the
 * title in the accent color, grey [meta] (time, date, count) and a chevron
 * on the right when the card opens something. [action] takes an icon button
 * or similar in place of the chevron.
 */
@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = app.tenet.android.core.designsystem.theme.LocalCardTint.current ?: MaterialTheme.colorScheme.primary,
    meta: String? = null,
    chevron: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            // The title keeps its width; only the grey info on the right gives way.
            modifier = if (meta == null) Modifier.weight(1f) else Modifier,
        )
        if (meta != null) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
        }
        if (action != null) {
            action()
        } else if (chevron) {
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 2.dp).size(22.dp),
            )
        }
    }
}

/**
 * Bar for multi-select in lists and grids (long-press an item to start):
 * close, "N ausgewählt", select all and delete. Back ends the selection.
 */
@Composable
fun SelectionBar(
    count: Int,
    total: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TooltipIconButton(androidx.compose.material.icons.Icons.Rounded.Close, "Auswahl beenden", onClose)
        Text(
            "$count ausgewählt",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (count < total) {
            androidx.compose.material3.TextButton(onClick = onSelectAll, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
                Text("Alle")
            }
        }
        IconButton(onClick = onDelete, enabled = count > 0, shapes = IconButtonDefaults.shapes()) {
            Icon(
                androidx.compose.material.icons.Icons.Outlined.Delete,
                contentDescription = "Auswahl löschen",
                tint = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Round check mark on a selectable tile: filled accent when [selected], an empty ring otherwise. */
@Composable
fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .size(24.dp)
            .background(if (selected) c.primary else c.surface.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
            .then(
                if (selected) Modifier
                else Modifier.border(1.5.dp, c.outline, androidx.compose.foundation.shape.CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = "Ausgewählt", tint = c.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}
