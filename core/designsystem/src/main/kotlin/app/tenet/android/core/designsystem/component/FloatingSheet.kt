package app.tenet.android.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A page that floats as a sheet over the page it was opened from (for a
 * `dialog<…>` destination): [heightFraction] of the screen high, rounded
 * top, drag handle, the app behind blurred in step with the sheet
 * ([LocalBackdropBlur]). [content] gets a `close` function that slides the
 * sheet down and then calls [onClosed]; back, a tap beside the sheet and
 * pulling it down close it as well.
 */
@Composable
fun FloatingSheet(
    onClosed: () -> Unit,
    heightFraction: Float = 0.92f,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    // 1 = hidden below the screen, 0 = fully open.
    val offset = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val backdrop = LocalBackdropBlur.current
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val closing = remember { booleanArrayOf(false) }
    val close: () -> Unit = {
        if (!closing[0]) {
            closing[0] = true
            scope.launch {
                offset.animateTo(1f, tween(180, easing = FastOutLinearInEasing))
                onClosed()
            }
        }
    }
    SideEffect {
        window?.setDimAmount(0f)
        window?.setWindowAnimations(0)
    }
    LaunchedEffect(Unit) { offset.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) }
    LaunchedEffect(Unit) { snapshotFlow { 1f - offset.value }.collect { backdrop?.floatValue = it } }
    DisposableEffect(Unit) { onDispose { backdrop?.floatValue = 0f } }
    BackHandler { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * heightFraction
        val sheetPx = constraints.maxHeight * heightFraction
        val progress = 1f - offset.value
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f * progress))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        )
        val drag = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta / sheetPx).coerceIn(0f, 1f)) }
        }
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, (offset.value * sheetPx).roundToInt()) },
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = drag,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (offset.value > 0.25f || velocity > 1800f) close()
                                else offset.animateTo(0f, spring(dampingRatio = 0.9f))
                            },
                        )
                        .padding(vertical = 10.dp)
                        .semantics { contentDescription = "Zum Schließen nach unten ziehen" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 5.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), CircleShape),
                    )
                }
                Box(Modifier.weight(1f)) { content(close) }
            }
        }
    }
}
