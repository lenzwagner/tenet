package app.tenet.android.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/** One entry of a [TenetFabMenu]. */
data class FabMenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private const val MENU_ANIM_MS = 260

/**
 * FAB that opens a speed-dial menu like Google Keep: the whole screen (tab
 * bar included) is dimmed, the actions appear as large pills stacked above
 * the button and the button turns into a dark close button. Closed, it is a
 * plain 56 dp FAB, so it lines up with the other tabs' FABs.
 * [actions] are listed top to bottom; the last one sits next to the button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TenetFabMenu(
    actions: List<FabMenuAction>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptics = LocalHapticFeedback.current

    FloatingActionButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            open = true
        },
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            // The open menu draws its own button at the same spot.
            .alpha(if (open) 0f else 1f)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = "Geschlossen"
            },
    ) { Icon(Icons.Rounded.Add, contentDescription = null) }

    if (open && bounds != Rect.Zero) {
        FabMenuOverlay(
            actions = actions,
            anchor = bounds,
            onClosed = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FabMenuOverlay(actions: List<FabMenuAction>, anchor: Rect, onClosed: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { expanded = true }
    // Keep the overlay until the collapse animation has played.
    LaunchedEffect(closing) {
        if (closing) {
            delay(MENU_ANIM_MS.toLong())
            onClosed()
        }
    }
    fun close() {
        expanded = false
        closing = true
    }
    // An action leaves right away (it usually opens a new screen).
    fun choose(action: () -> Unit) {
        onClosed()
        action()
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Full-screen, no system dim: the scrim below fades with the menu.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        val colors = MaterialTheme.colorScheme
        val scrim by animateFloatAsState(if (expanded) 0.5f else 0f, tween(MENU_ANIM_MS), label = "scrim")
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        ) {
            // FloatingActionButtonMenu adds a 16 dp margin around its button;
            // place it so the button lands exactly on the closed FAB.
            val margin = 16.dp
            FloatingActionButtonMenu(
                expanded = expanded,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val right = anchor.right + margin.toPx()
                        val bottom = anchor.bottom + margin.toPx()
                        placeable.place((right - placeable.width).toInt(), (bottom - placeable.height).toInt())
                    }
                },
                button = {
                    ToggleFloatingActionButton(
                        checked = expanded,
                        onCheckedChange = { close() },
                        containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                            initialColor = colors.primaryContainer,
                            finalColor = colors.inverseSurface,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Menü schließen"
                            stateDescription = if (expanded) "Geöffnet" else "Geschlossen"
                        },
                    ) {
                        val icon = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = ToggleFloatingActionButtonDefaults.iconColor(
                                initialColor = colors.onPrimaryContainer,
                                finalColor = colors.inverseOnSurface,
                            )(checkedProgress),
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            ) {
                actions.forEach { action ->
                    FloatingActionButtonMenuItem(
                        onClick = { choose(action.onClick) },
                        icon = { Icon(action.icon, contentDescription = null) },
                        text = { Text(action.label, style = MaterialTheme.typography.titleMedium) },
                        containerColor = colors.inverseSurface,
                        contentColor = colors.inverseOnSurface,
                    )
                }
            }
        }
    }
}
