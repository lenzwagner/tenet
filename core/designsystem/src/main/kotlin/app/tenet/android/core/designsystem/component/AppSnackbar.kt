package app.tenet.android.core.designsystem.component

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
