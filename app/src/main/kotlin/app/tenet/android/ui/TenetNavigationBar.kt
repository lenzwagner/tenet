package app.tenet.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeProgressive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tenet.android.navigation.TopLevelTab
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Floating, pill-shaped tab bar (App_Konzept.md 3) built from M3 Expressive
 * parts: a fully rounded, elevated surface container holding
 * [ShortNavigationBarItem]s with the icon at the start. Only the active tab
 * shows its label, so the pill morphs as the selection moves. With [glass]
 * the container turns translucent and blurs the content scrolling underneath.
 */
@Composable
fun TenetNavigationBar(
    tabs: List<TopLevelTab>,
    selected: TopLevelTab?,
    onSelect: (TopLevelTab) -> Unit,
    visible: Boolean,
    glass: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    /** Tabs with a live activity (dot badge), e.g. Sport while a run is recorded. */
    badges: Map<TopLevelTab, String> = emptyMap(),
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier.fillMaxWidth(),
    ) {
        val container = MaterialTheme.colorScheme.surfaceContainer
        val surface = MaterialTheme.colorScheme.surface
        Box(Modifier.fillMaxWidth()) {
            // Soft backdrop behind the pill down to the screen edge: blur (or,
            // without glass, a tint) that fades out from bottom to top.
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        if (glass) {
                            Modifier.hazeBlur(
                                input = HazeInput.Backdrop(hazeState),
                                style = HazeBlurStyle {
                                    backgroundColor(surface)
                                    blurRadius(12.dp)
                                    colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = 0.35f))))
                                    progressive(
                                        HazeProgressive.verticalGradient(
                                            startIntensity = 0f,
                                            endIntensity = 1f,
                                        ),
                                    )
                                },
                            )
                        } else {
                            Modifier.background(
                                Brush.verticalGradient(
                                    0f to surface.copy(alpha = 0f),
                                    1f to surface.copy(alpha = 0.85f),
                                ),
                            )
                        },
                    ),
            )
            TabPill(
                tabs = tabs,
                selected = selected,
                onSelect = onSelect,
                glass = glass,
                hazeState = hazeState,
                container = container,
                badges = badges,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun TabPill(
    tabs: List<TopLevelTab>,
    selected: TopLevelTab?,
    onSelect: (TopLevelTab) -> Unit,
    glass: Boolean,
    hazeState: HazeState,
    container: Color,
    modifier: Modifier = Modifier,
    badges: Map<TopLevelTab, String> = emptyMap(),
) {
    Surface(
        shape = CircleShape,
        color = if (glass) Color.Transparent else container,
        // A shadow would shine through the translucent glass body and grey
        // it out, so only the opaque pill casts one.
        shadowElevation = if (glass) 0.dp else 6.dp,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .then(
                if (glass) {
                    Modifier.hazeBlur(
                        input = HazeInput.Backdrop(hazeState),
                        style = HazeBlurStyle {
                            backgroundColor(container)
                            colorEffects(listOf(HazeColorEffect.tint(container.copy(alpha = 0.72f))))
                            blurRadius(24.dp)
                        },
                        // Blur on a downscaled backdrop: under a 72 % tint the
                        // difference is invisible, the GPU work per frame much lower.
                        performanceMode = dev.chrisbanes.haze.HazePerformanceMode.Performance,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                ShortNavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    iconPosition = NavigationItemIconPosition.Start,
                    icon = {
                        BadgedBox(badge = { if (tab in badges) Badge() }) {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = null,
                            )
                        }
                    },
                    label = if (isSelected) {
                        {
                            Text(
                                text = tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics {
                        contentDescription = badges[tab]?.let { "${tab.label} · $it" } ?: tab.label
                    },
                )
            }
        }
    }
}
