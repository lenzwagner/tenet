package app.tenet.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.theme.AppleSpring
import app.tenet.android.core.designsystem.theme.isClearStyle

/*
 * The app's standard controls in both looks: "Klar" draws them the way Apple
 * does (grey filled fields without an outline, capsule chips, a thin progress
 * bar, the activity spinner, round check marks, a slim slider with a white
 * knob); Expressive keeps the Material 3 originals. Screens use these instead
 * of the M3 components, so every screen follows the look.
 */

/** Grey fill of fields, chips and tracks: sits on white cards and on the grey ground alike. */
val tenetFill: Color
    @Composable @ReadOnlyComposable get() {
        val c = MaterialTheme.colorScheme
        return c.onSurface.copy(alpha = if (c.background.luminance() < 0.5f) 0.12f else 0.06f)
    }

@Composable
private fun appleFieldColors() = tenetFill.let { fill ->
    TextFieldDefaults.colors(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        disabledContainerColor = fill,
        errorContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
}

private val AppleFieldShape = RoundedCornerShape(12.dp)

/** Text field: "Klar" = filled, rounded, no outline (like iOS); Expressive = M3 outlined. */
@Composable
fun TenetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape? = null,
) {
    if (isClearStyle) {
        TextField(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            suffix = suffix, supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines,
            minLines = minLines, shape = shape ?: AppleFieldShape, colors = appleFieldColors(),
        )
    } else {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            suffix = suffix, supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines,
            minLines = minLines, shape = shape ?: OutlinedTextFieldDefaults.shape,
        )
    }
}

/** [TenetTextField] for a [TextFieldValue] (selection kept by the caller). */
@Composable
fun TenetTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape? = null,
) {
    if (isClearStyle) {
        TextField(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            suffix = suffix, supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines,
            minLines = minLines, shape = shape ?: AppleFieldShape, colors = appleFieldColors(),
        )
    } else {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            suffix = suffix, supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines,
            minLines = minLines, shape = shape ?: OutlinedTextFieldDefaults.shape,
        )
    }
}

/** Filter chip: "Klar" = capsule without an outline, selected in the accent's tint. */
@Composable
fun TenetFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    if (isClearStyle) {
        val c = MaterialTheme.colorScheme
        FilterChip(
            selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            shape = CircleShape,
            border = null,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = tenetFill,
                labelColor = c.onSurface,
                iconColor = c.onSurfaceVariant,
                selectedContainerColor = c.primary.copy(alpha = 0.16f),
                selectedLabelColor = c.primary,
                selectedLeadingIconColor = c.primary,
                selectedTrailingIconColor = c.primary,
            ),
        )
    } else {
        FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon)
    }
}

/** Action chip: "Klar" = grey capsule without an outline. */
@Composable
fun TenetAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    if (isClearStyle) {
        AssistChip(
            onClick = onClick, label = label, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            shape = CircleShape,
            border = null,
            colors = AssistChipDefaults.assistChipColors(containerColor = tenetFill),
        )
    } else {
        AssistChip(onClick = onClick, label = label, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon)
    }
}

/** Progress bar: "Klar" = a thin, plain capsule; Expressive = the wavy one. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TenetProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color? = null,
) {
    if (isClearStyle) {
        LinearProgressIndicator(
            progress = progress,
            modifier = modifier.height(5.dp),
            color = color,
            trackColor = trackColor ?: tenetFill,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else if (trackColor != null) {
        LinearWavyProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor)
    } else {
        LinearWavyProgressIndicator(progress = progress, modifier = modifier, color = color)
    }
}

/** Running bar without a known end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TenetProgress(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (isClearStyle) {
        LinearProgressIndicator(modifier = modifier.height(4.dp), color = color, trackColor = tenetFill, strokeCap = StrokeCap.Round, gapSize = 0.dp)
    } else {
        LinearWavyProgressIndicator(modifier = modifier, color = color)
    }
}

/** Progress ring: "Klar" = plain round stroke. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TenetCircularProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    if (isClearStyle) {
        CircularProgressIndicator(progress = progress, modifier = modifier, trackColor = tenetFill, strokeCap = StrokeCap.Round, gapSize = 0.dp)
    } else {
        CircularWavyProgressIndicator(progress = progress, modifier = modifier)
    }
}

/**
 * "Working": "Klar" = Apple's activity indicator (eight ticks, the bright one
 * running round); Expressive = M3's morphing shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TenetSpinner(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (!isClearStyle) {
        LoadingIndicator(modifier = modifier, color = color)
        return
    }
    val turn = rememberInfiniteTransition(label = "spinner")
    val step by turn.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "tick",
    )
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2
            val stroke = r * 0.22f
            val head = step.toInt()
            repeat(8) { i ->
                // The tick behind the head is the brightest, fading round the circle.
                val age = ((head - i) % 8 + 8) % 8
                rotate(i * 45f) {
                    drawLine(
                        color = color.copy(alpha = color.alpha * (1f - age / 8f).coerceAtLeast(0.18f)),
                        start = Offset(center.x, center.y - r * 0.45f),
                        end = Offset(center.x, center.y - r + stroke / 2),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** Check mark: "Klar" = a circle that fills with the accent and a white tick (like Reminders). */
@Composable
fun TenetCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!isClearStyle) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }
    val c = MaterialTheme.colorScheme
    val fill by animateFloatAsState(if (checked) 1f else 0f, AppleSpring.smooth(0.25f), label = "check")
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onValueChange = onCheckedChange,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(c.primary.copy(alpha = fill * alpha))
                .border(1.5.dp, c.outline.copy(alpha = (1f - fill) * alpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = c.onPrimary.copy(alpha = fill),
                modifier = Modifier.size(16.dp).scale(0.6f + 0.4f * fill),
            )
        }
    }
}

/** Slider knob in "Klar": a white disc with a soft shadow. */
@Composable
internal fun AppleSliderThumb() {
    Box(
        Modifier
            .size(26.dp)
            .shadow(3.dp, CircleShape)
            .background(Color.White, CircleShape),
    )
}

/** Slider track in "Klar": a slim line, filled up to [fraction]. */
@Composable
internal fun AppleSliderTrack(fraction: Float, enabled: Boolean) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(tenetFill)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(c.primary.copy(alpha = if (enabled) 1f else 0.4f)),
        )
    }
}

/** Shape of floating action buttons: round in "Klar", M3's rounded square otherwise. */
val tenetFabShape: Shape
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable get() = if (isClearStyle) CircleShape else FloatingActionButtonDefaults.shape
