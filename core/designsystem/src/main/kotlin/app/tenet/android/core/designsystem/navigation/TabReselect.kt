package app.tenet.android.core.designsystem.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import app.tenet.android.core.designsystem.header.HeaderScrollState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Fires whenever the user taps the already selected tab (App_Konzept.md 3.3:
 * "Reselect scrollt nach oben"). Provided per top-level destination by the app.
 */
val LocalTabReselect = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/** Runs [onReselect] each time the current tab is tapped again. */
@Composable
fun ReselectEffect(onReselect: suspend () -> Unit) {
    val events = LocalTabReselect.current
    val action = rememberUpdatedState(onReselect)
    LaunchedEffect(events) { events.collect { action.value() } }
}

/**
 * A [LazyListState] that scrolls back to the top (and re-expands [header])
 * when the tab is reselected.
 */
@Composable
fun rememberReselectListState(header: HeaderScrollState? = null): LazyListState {
    val state = rememberLazyListState()
    ReselectEffect {
        header?.animateExpand()
        state.animateScrollToItem(0)
    }
    return state
}
