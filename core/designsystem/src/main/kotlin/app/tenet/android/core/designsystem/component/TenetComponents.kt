package app.tenet.android.core.designsystem.component

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
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
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(androidx.compose.material3.ListItemDefaults.SegmentedGap)) {
        repeat(count) { index ->
            val shape = androidx.compose.material3.ListItemDefaults.segmentedShapes(index, count).shape
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
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { inner() }
                if (suffix != null) Text(suffix, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        },
    )
}
