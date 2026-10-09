package app.tenet.android.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.tenet.android.MainActivity
import app.tenet.android.R
import app.tenet.android.core.data.health.TodayFeedRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.NumberFormat
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeedWidgetEntryPoint {
    fun feed(): TodayFeedRepository
}

/** Redraws every Tenet widget (app start/resume, morning report). */
object TenetWidgets {
    suspend fun updateAll(context: Context) {
        runCatching {
            TodayFeedWidget().updateAll(context)
            StepsWidget().updateAll(context)
            FormWidget().updateAll(context)
            SleepWidget().updateAll(context)
        }
    }

    internal suspend fun load(context: Context): TodayFeedRepository.Snapshot =
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, FeedWidgetEntryPoint::class.java).feed().snapshot()
        }.onFailure { android.util.Log.w("TenetWidget", "snapshot failed", it) }
            .getOrDefault(TodayFeedRepository.Snapshot())
}

private fun isDark(context: Context) =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** Same colours as the feed on "Heute" (light / dark). */
private data class Pill(val container: Color, val fill: Color, val content: Color, val badge: Color)

private object WidgetColors {
    fun steps(dark: Boolean) = if (dark) Pill(Color(0xFF00796B), Color(0xFF00796B), Color(0xFFB2F5EA), Color(0xFF4FE3CF))
    else Pill(Color(0xFFCFF3EC), Color(0xFFCFF3EC), Color(0xFF00564C), Color(0xFF34C3AE))
    fun form(dark: Boolean) = if (dark) Pill(Color(0xFF0B4F6C), Color(0xFF0E7AA3), Color(0xFFC5ECFF), Color(0xFF7FD0F5))
    else Pill(Color(0xFFD6EEFA), Color(0xFFA9DCF5), Color(0xFF0A4A66), Color(0xFF0E7AA3))
    fun sleep(dark: Boolean) = if (dark) Pill(Color(0xFF6E4FB5), Color(0xFF6E4FB5), Color(0xFFEADDFF), Color(0xFFCDB6FF))
    else Pill(Color(0xFFE8DEFA), Color(0xFFE8DEFA), Color(0xFF3D2A73), Color(0xFF8B6CD9))
    fun surface(dark: Boolean) = if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    fun onSurface(dark: Boolean) = if (dark) Color(0xFFF5F5F7) else Color(0xFF111113)
    fun muted(dark: Boolean) = if (dark) Color(0xFF98989F) else Color(0xFF6E6E73)
    fun accent(dark: Boolean) = if (dark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
    fun track(dark: Boolean) = if (dark) Color(0xFF3A3A3C) else Color(0xFFE3E3E8)
}

/** The week ring as a bitmap (widgets cannot draw arcs). */
private fun ringBitmap(fraction: Float, dark: Boolean, px: Int = 360): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val stroke = px * 0.11f
    val rect = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = WidgetColors.track(dark).toArgbInt()
    c.drawArc(rect, 0f, 360f, false, paint)
    if (fraction > 0f) {
        paint.color = WidgetColors.accent(dark).toArgbInt()
        c.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
    }
    return bmp
}

/** Tenet-Form background: the brighter part fills like a bar. */
private fun fillBitmap(fraction: Float, pill: Pill): Bitmap {
    val bmp = Bitmap.createBitmap(200, 10, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(pill.container.toArgbInt())
    val paint = Paint().apply { color = pill.fill.toArgbInt() }
    c.drawRect(0f, 0f, 200f * fraction.coerceIn(0f, 1f), 10f, paint)
    return bmp
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

@Composable
private fun WeekRing(s: TodayFeedRepository.Snapshot, dark: Boolean, size: androidx.compose.ui.unit.Dp) {
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(ringBitmap(s.weekActiveMin.toFloat() / s.weekGoalMin, dark)),
            contentDescription = "Diese Woche ${s.weekActiveMin} von ${s.weekGoalMin} Minuten aktiv",
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Woche aktiv", style = TextStyle(color = ColorProvider(WidgetColors.muted(dark)), fontSize = (size.value / 10.5).sp))
            Text(
                "${s.weekPercent} %",
                style = TextStyle(color = ColorProvider(WidgetColors.onSurface(dark)), fontSize = (size.value / 4.6).sp, fontWeight = FontWeight.Bold),
            )
            Text(
                "${s.weekActiveMin} von ${s.weekGoalMin} min",
                style = TextStyle(color = ColorProvider(WidgetColors.accent(dark)), fontSize = (size.value / 10.5).sp),
            )
        }
    }
}

@Composable
private fun PillView(
    icon: Int,
    label: String,
    value: String,
    pill: Pill,
    modifier: GlanceModifier,
    fill: Float? = null,
    labelBelow: Boolean = false,
) {
    Box(modifier.cornerRadius(28.dp).background(ColorProvider(pill.container))) {
        if (fill != null) {
            Image(ImageProvider(fillBitmap(fill, pill)), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        }
        Row(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(32.dp).cornerRadius(16.dp).background(ColorProvider(pill.badge)), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(if (pill.container.luminance() < 0.5f) pill.container else pill.content)),
                    modifier = GlanceModifier.size(18.dp),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Column {
                val small = TextStyle(color = ColorProvider(pill.content), fontSize = 12.sp)
                if (!labelBelow) Text(label, style = small, maxLines = 1)
                Text(value, style = TextStyle(color = ColorProvider(pill.content), fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (labelBelow) Text(label, style = small, maxLines = 1)
            }
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun StepsPill(s: TodayFeedRepository.Snapshot, dark: Boolean, modifier: GlanceModifier) = PillView(
    R.drawable.ic_widget_walk, "Schritte",
    s.steps?.let { NumberFormat.getIntegerInstance(Locale.GERMAN).format(it) } ?: "–",
    WidgetColors.steps(dark), modifier,
)

@Composable
private fun FormPill(s: TodayFeedRepository.Snapshot, dark: Boolean, modifier: GlanceModifier) = PillView(
    R.drawable.ic_widget_form, "Tenet-Form", s.form?.toString() ?: "–", WidgetColors.form(dark), modifier,
    fill = s.form?.div(100f),
)

@Composable
private fun SleepPill(s: TodayFeedRepository.Snapshot, dark: Boolean, modifier: GlanceModifier) = PillView(
    R.drawable.ic_widget_sleep,
    listOfNotNull(s.sleepScore?.toString(), s.sleepLabel).joinToString(" · ").ifBlank { "Schlaf" },
    s.sleepText ?: "–",
    WidgetColors.sleep(dark), modifier, labelBelow = s.sleepText != null,
)

private val openApp get() = actionStartActivity<MainActivity>()

/**
 * The "Heute" feed on the home screen: small = week ring only, wide = ring
 * plus steps, readiness and sleep.
 */
class TodayFeedWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = TenetWidgets.load(context)
        val dark = isDark(context)
        provideContent {
            val size = LocalSize.current
            Box(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ColorProvider(WidgetColors.surface(dark)))
                    .padding(10.dp).clickable(openApp),
                contentAlignment = Alignment.Center,
            ) {
                if (size.width < WIDE.width) {
                    WeekRing(s, dark, minOf(size.width, size.height) - 20.dp)
                } else {
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        WeekRing(s, dark, minOf(size.height - 20.dp, size.width * 0.4f))
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.fillMaxHeight().defaultWeight()) {
                            val pill = GlanceModifier.fillMaxWidth().defaultWeight()
                            StepsPill(s, dark, pill)
                            Spacer(GlanceModifier.height(6.dp))
                            FormPill(s, dark, pill)
                            Spacer(GlanceModifier.height(6.dp))
                            SleepPill(s, dark, pill)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
    }
}

/** One feed tile on its own (2 × 1). */
abstract class PillWidget : GlanceAppWidget() {
    @Composable
    abstract fun Content(s: TodayFeedRepository.Snapshot, dark: Boolean, modifier: GlanceModifier)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = TenetWidgets.load(context)
        val dark = isDark(context)
        provideContent { Content(s, dark, GlanceModifier.fillMaxSize().clickable(openApp)) }
    }
}

class StepsWidget : PillWidget() {
    @Composable
    override fun Content(s: TodayFeedRepository.Snapshot, dark: Boolean, modifier: GlanceModifier) = StepsPill(s, dark, modifier)
}

class FormWidget : PillWidget() {
    @Composable
    override fun Content(s: TodayFeedRepository.Snapshot, dark: Boolean, modifier: GlanceModifier) = FormPill(s, dark, modifier)
}

class SleepWidget : PillWidget() {
    @Composable
    override fun Content(s: TodayFeedRepository.Snapshot, dark: Boolean, modifier: GlanceModifier) = SleepPill(s, dark, modifier)
}

class TodayFeedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayFeedWidget()
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}

class FormWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FormWidget()
}

class SleepWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SleepWidget()
}
