package app.tenet.android.core.designsystem.header

import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.os.Build
import androidx.compose.animation.core.animate
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tenet.android.core.designsystem.R

/**
 * Top-level page. Each gets a soft colour wash at the top (like Apple
 * Health) in its own hues: start (top left), middle and end (top right).
 */
enum class HeaderImage(internal val wash: List<Long>) {
    TODAY(listOf(0xFFFFB38A, 0xFFF59BC0, 0xFFB7A6F5)),
    SPORT(listOf(0xFF8FE3B8, 0xFF7FD3E8, 0xFF9DB6F7)),
    NUTRITION(listOf(0xFFFFCF7A, 0xFFFFA98C, 0xFFF59BB4)),
    JOURNAL(listOf(0xFFB9A4F7, 0xFF9DB0F7, 0xFF8FD0EE)),
    SETTINGS(listOf(0xFFB8C2D9, 0xFFA9B9E8, 0xFFC4B6E8)),
}

/**
 * Scroll-driven 0..1 progress for [PageHeader]: attach
 * [nestedScrollConnection] to the scrolling container (or an ancestor of it)
 * and pass [progress] to the header. Scrolling down grows the progress,
 * scrolling up shrinks it again.
 */
@Stable
class HeaderScrollState internal constructor(private val rangePx: Float) {
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /** Smoothly re-expands the header (e.g. on tab reselect). */
    suspend fun animateExpand() {
        animate(progress, 0f) { value, _ -> progress = value }
    }

    /**
     * Collapses the header before the list scrolls and expands it only once
     * the list is back at its top. The header consumes exactly what it moves,
     * so content follows the finger 1:1 instead of moving twice as fast.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || progress >= 1f) return Offset.Zero
            return Offset(0f, -move(-available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || progress <= 0f) return Offset.Zero
            return Offset(0f, -move(-available.y))
        }

        /** Moves the header by [deltaPx] (positive = collapse); returns the px actually used. */
        private fun move(deltaPx: Float): Float {
            val before = progress
            progress = (progress + deltaPx / rangePx).coerceIn(0f, 1f)
            return (progress - before) * rangePx
        }
    }
}

@Composable
fun rememberHeaderScrollState(headerHeight: Dp = LargeTitleHeight): HeaderScrollState {
    val rangePx = with(LocalDensity.current) { headerHeight.toPx() }
    return remember(rangePx) { HeaderScrollState(rangePx) }
}

/**
 * M3 hero header: the page photo sits in an extra-large rounded card inset
 * from the screen edges, below the status bar on the surface color, with the
 * title set in the emphasized headline style.
 *
 * As [progress] grows the card collapses, shrinks slightly, blurs and fades,
 * so the content below rises to the status bar like a collapsing top app bar.
 * The status-bar inset itself stays, so pinned rows never slide under it.
 *
 * [progress] is a lambda and is only read in the layout / draw phases, so
 * scrolling never recomposes the header or the screen around it.
 */
/** Height of the large title area that scrolls away. */
val LargeTitleHeight: Dp = 68.dp

/** Compact bar with the small centred title (always there, below the status bar). */
private val CompactBarHeight: Dp = 44.dp

/** How far the colour wash reaches down, behind the first cards. */
private val WashHeight: Dp = 420.dp

/**
 * Apple Health's page background: a soft diagonal colour wash in the
 * page's hues at the top, fading into the grey page background and
 * reaching behind the first cards. It moves up with the large title as
 * the page scrolls ([progress] 0 → 1) and stays visible at the top. Put it on the page's
 * root (behind header, tabs and list).
 */
fun Modifier.pageWash(header: HeaderImage, progress: () -> Float): Modifier = composed {
    val bg = MaterialTheme.colorScheme.background
    val dark = bg.luminance() < 0.5f
    val colors = header.wash.map { Color(it).copy(alpha = if (dark) 0.40f else 0.78f) }
    val density = LocalDensity.current
    val washPx = with(density) { WashHeight.toPx() }
    val shiftPx = with(density) { LargeTitleHeight.toPx() }
    drawBehind {
        val p = progress().coerceIn(0f, 1f)
        // The colour stays at the top while scrolling (behind the compact bar and the
        // section tabs); it only moves up with the large title and gets a bit calmer.
        val alpha = 1f - 0.25f * p
        translate(top = -p * shiftPx) {
            val area = Size(size.width, washPx)
            // The colour stops where the overlay is already solid page colour, so no
            // half-covered pixel row can show at the bottom edge.
            drawRect(
                Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, washPx * 0.55f)),
                size = Size(size.width, washPx * 0.88f),
                alpha = alpha,
            )
            // Soft fade into the page colour, so there is no edge.
            drawRect(
                // Fully the page colour well before the wash ends: no visible edge.
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.3f to bg.copy(alpha = 0.2f),
                    0.55f to bg.copy(alpha = 0.6f),
                    0.75f to bg.copy(alpha = 0.92f),
                    0.85f to bg,
                    endY = washPx,
                ),
                size = area,
                // Always fully opaque: only the colour gets calmer, the fade into the
                // page must stay complete or the colour's edge shows as a line.
            )
        }
    }
}

/**
 * Large-title header like iOS: a compact bar (search, small centred title
 * that fades in) and the large title below it, which scrolls away as the
 * page scrolls. Transparent – the colour wash comes from [pageWash] on
 * the page root.
 */
@Composable
fun PageHeader(
    header: HeaderImage,
    title: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = LargeTitleHeight,
    subtitle: String? = null,
    onSearch: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(CompactBarHeight)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 64.dp)
                    .graphicsLayer { alpha = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f) },
            )
            if (onSearch != null) {
                androidx.compose.material3.IconButton(
                    onClick = onSearch,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Suchen", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .collapsingHeight(height) { 1f - progress() }
                .clipToBounds(),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                    .graphicsLayer {
                        val p = progress()
                        alpha = (1f - p * 1.4f).coerceIn(0f, 1f)
                        translationY = -p * 12.dp.toPx()
                    },
            ) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val HeaderScrim = Brush.verticalGradient(
    0.35f to Color.Black.copy(alpha = 0f),
    1f to Color.Black.copy(alpha = 0.6f),
)

/**
 * Layout-phase height: measures the content at `fullHeight * fraction()`
 * without recomposing when [fraction] changes.
 */
fun Modifier.collapsingHeight(fullHeight: Dp, fraction: () -> Float): Modifier =
    layout { measurable, constraints ->
        val h = (fullHeight.roundToPx() * fraction().coerceIn(0f, 1f)).roundToInt()
        val placeable = measurable.measure(
            constraints.copy(minHeight = h, maxHeight = h),
        )
        layout(placeable.width, h) { placeable.place(0, 0) }
    }

/**
 * Background of a sub page: the page colour with its area's colour wash at
 * the top (like the main pages, without the scroll coupling). Sub-page
 * Scaffolds and top bars are transparent, so the wash shows behind them.
 */
@Composable
fun SubPageWash(header: HeaderImage, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pageWash(header) { 0f },
    ) { content() }
}

/** Transparent top bar over the wash; once content scrolls under it, it gets the page colour. */
@Composable
fun washTopBarColors(): androidx.compose.material3.TopAppBarColors =
    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
    )
