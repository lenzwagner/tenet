package app.tenet.android.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide snackbar (one host above the tab bar). Deleting shows
 * "Rückgängig" instead of a confirmation dialog (M3: prefer undo over
 * interrupting dialogs for reversible actions).
 */
class AppSnackbar(val host: SnackbarHostState, private val scope: CoroutineScope) {

    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Short)
        }
    }

    /**
     * [onUndo] runs when "Rückgängig" is tapped, otherwise [onCommit] once
     * the snackbar is gone (timeout, swipe or replaced by the next one).
     */
    fun showUndo(message: String, onUndo: () -> Unit, onCommit: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = "Rückgängig",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo() else onCommit()
        }
    }
}

val LocalAppSnackbar = staticCompositionLocalOf<AppSnackbar?> { null }

@Composable
fun rememberAppSnackbar(): AppSnackbar {
    val scope = rememberCoroutineScope()
    return remember { AppSnackbar(SnackbarHostState(), scope) }
}

/**
 * The app's snackbar look: a floating rounded card with a leading icon chip,
 * "Was · Details" split into a bold line and a quiet second line, and a tonal
 * action – instead of the flat dark bar of the default snackbar.
 */
@Composable
fun TenetSnackbarHost(hostState: SnackbarHostState, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.SnackbarHost(hostState, modifier) { data -> TenetSnackbar(data) }
}

@Composable
private fun TenetSnackbar(data: androidx.compose.material3.SnackbarData) {
    val raw = data.visuals.message.trim()
    val done = raw.startsWith("✓")
    val message = raw.removePrefix("✓").trim()
    // "20 g Gurke · 2 kcal (Abend)": the thing in bold, the details below.
    val title = message.substringBefore(" · ")
    val detail = message.substringAfter(" · ", "").takeIf { it.isNotBlank() }
    val undo = data.visuals.actionLabel != null
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val (chipColor, chipContent, icon) = when {
        done -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, androidx.compose.material.icons.Icons.Rounded.Check)
        undo -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, androidx.compose.material.icons.Icons.Outlined.Delete)
        else -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, androidx.compose.material.icons.Icons.Outlined.Info)
    }
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
        modifier = androidx.compose.ui.Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Row(
            androidx.compose.ui.Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = chipColor, contentColor = chipContent) {
                androidx.compose.material3.Icon(icon, contentDescription = null, modifier = androidx.compose.ui.Modifier.padding(8.dp).size(20.dp))
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(12.dp))
            Column(androidx.compose.ui.Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                detail?.let {
                    androidx.compose.material3.Text(
                        it,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            data.visuals.actionLabel?.let { label ->
                androidx.compose.material3.FilledTonalButton(
                    onClick = { data.performAction() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    modifier = androidx.compose.ui.Modifier.padding(start = 8.dp).height(36.dp),
                ) { androidx.compose.material3.Text(label) }
            }
            if (data.visuals.withDismissAction) {
                androidx.compose.material3.IconButton(onClick = { data.dismiss() }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Close,
                        contentDescription = "Schließen",
                        tint = scheme.onSurfaceVariant,
                        modifier = androidx.compose.ui.Modifier.size(20.dp),
                    )
                }
            } else {
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(8.dp))
            }
        }
    }
}
