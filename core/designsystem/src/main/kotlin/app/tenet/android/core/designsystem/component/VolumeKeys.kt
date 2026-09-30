package app.tenet.android.core.designsystem.component

import androidx.compose.runtime.getValue
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * Hardware volume keys as a trigger (App_Konzept.md 5.2.2: "Start per Tap
 * oder Lautstärketaste"). The activity forwards key events via
 * [dispatch]; while a handler is registered the keys do not change the
 * volume. The most recently registered handler wins.
 */
object VolumeKeys {
    private val handlers = ArrayDeque<() -> Unit>()

    /** Returns true when the event was consumed. Call from Activity.dispatchKeyEvent. */
    fun dispatch(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        val handler = handlers.lastOrNull() ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler()
        return true
    }

    internal fun register(handler: () -> Unit) = handlers.addLast(handler)
    internal fun unregister(handler: () -> Unit) = handlers.remove(handler)
}

/** Calls [onPress] on a volume key press while [enabled] and in composition. */
@Composable
fun VolumeKeyHandler(enabled: Boolean = true, onPress: () -> Unit) {
    val current by rememberUpdatedState(onPress)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler = { current() }
        VolumeKeys.register(handler)
        onDispose { VolumeKeys.unregister(handler) }
    }
}
