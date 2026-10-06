package app.tenet.android.core.designsystem.header

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

/** Header photo per top-level page. */
enum class HeaderImage(@DrawableRes internal val res: Int) {
    TODAY(R.drawable.header_today),
    SPORT(R.drawable.header_sport),
    NUTRITION(R.drawable.header_naehrung),
    JOURNAL(R.drawable.header_journal),
    SETTINGS(R.drawable.header_settings),
}

/**
 * Decoded header photos, kept for the whole process: `painterResource`
 * decoded the 1080 × 720 JPEG on the main thread every time a tab was
 * composed again. [prewarm] decodes all five in the background at start.
 */
object HeaderImages {
    private val cache = java.util.concurrent.ConcurrentHashMap<HeaderImage, androidx.compose.ui.graphics.ImageBitmap>()

    fun get(context: android.content.Context, header: HeaderImage): androidx.compose.ui.graphics.ImageBitmap =
        cache.getOrPut(header) { decode(context, header) }

    /** Call off the main thread, e.g. right after launch. */
    fun prewarm(context: android.content.Context) {
        HeaderImage.entries.forEach { runCatching { get(context, it) } }
    }

    private fun decode(context: android.content.Context, header: HeaderImage): androidx.compose.ui.graphics.ImageBitmap {
        val options = android.graphics.BitmapFactory.Options().apply {
            // Opaque photos: GPU-backed bitmap, uploaded once instead of every draw.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) inPreferredConfig = android.graphics.Bitmap.Config.HARDWARE
        }
        val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, header.res, options)
        return bitmap.asImageBitmap()
    }
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

/** Height of the "Klar" large-title header (status bar excluded). */
val ClearHeaderHeight: Dp = 112.dp

@Composable
fun rememberHeaderScrollState(
    headerHeight: Dp = if (app.tenet.android.core.designsystem.theme.isClearStyle) ClearHeaderHeight else 184.dp,
): HeaderScrollState {
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
@Composable
fun PageHeader(
    header: HeaderImage,
    title: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = if (app.tenet.android.core.designsystem.theme.isClearStyle) ClearHeaderHeight else 184.dp,
    subtitle: String? = null,
    onSearch: (() -> Unit)? = null,
) {
    if (app.tenet.android.core.designsystem.theme.isClearStyle) {
        LargeTitleHeader(title, progress, modifier, height, subtitle, onSearch)
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .collapsingHeight(height) { 1f - progress() }
            .clipToBounds(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .graphicsLayer {
                    val p = progress()
                    val scale = 1f - 0.04f * p
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - p * p
                },
        ) {
            Box {
                val context = androidx.compose.ui.platform.LocalContext.current
                val bitmap = androidx.compose.runtime.remember(header) { HeaderImages.get(context, header) }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress()
                            val scale = 1f + 0.08f * p
                            scaleX = scale
                            scaleY = scale
                            // Blur via RenderEffect (API 31+) in the draw phase;
                            // skipped entirely while the header is fully expanded.
                            renderEffect = if (p > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val radius = (16.dp * p).toPx()
                                BlurEffect(radius, radius, TileMode.Clamp)
                            } else {
                                null
                            }
                        },
                )
                // Bottom scrim carries the title.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(HeaderScrim),
                )
                if (onSearch != null) {
                    // Global search entry (App_Konzept.md 6), glassy on the photo.
                    FilledTonalIconButton(
                        onClick = onSearch,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = "Suchen")
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                ) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.87f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * "Klar" header like an iOS large title: bold page name on the plain
 * background, optional small line above it, search as a plain accent icon.
 * Collapses with the same [progress] as the photo header and fades out.
 */
@Composable
private fun LargeTitleHeader(
    title: String,
    progress: () -> Float,
    modifier: Modifier,
    height: Dp,
    subtitle: String?,
    onSearch: (() -> Unit)?,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .collapsingHeight(height) { 1f - progress() }
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress()
                    alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                    val scale = 1f - 0.06f * p
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                },
        ) {
            if (onSearch != null) {
                androidx.compose.material3.IconButton(
                    onClick = onSearch,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 8.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Suchen", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            ) {
                if (subtitle != null) {
                    Text(
                        text = subtitle.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
