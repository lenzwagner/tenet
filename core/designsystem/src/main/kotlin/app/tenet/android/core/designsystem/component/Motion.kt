package app.tenet.android.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * 0 → 1 with the theme's expressive spatial spring, restarted when [key]
 * changes. Charts multiply bar heights/widths with it so they grow in
 * (M3 Expressive motion) instead of popping up.
 */
@Composable
fun rememberGrowIn(key: Any?): Float {
    val progress = remember(key) { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(key) { progress.animateTo(1f, spec) }
    return progress.value
}
