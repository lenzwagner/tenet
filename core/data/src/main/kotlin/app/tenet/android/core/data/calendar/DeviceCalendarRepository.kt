package app.tenet.android.core.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.data.WeekCalendarRepository
import app.tenet.android.core.database.entity.Discipline
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The phone's own calendar (Google Calendar, Outlook … via CalendarContract):
 * appointments are read for the week calendar, planned workouts are written
 * as all-day events into one chosen calendar.
 */
@Singleton
class DeviceCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weekCalendar: WeekCalendarRepository,
) {
    data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean)

    data class Event(val title: String, val start: Instant, val end: Instant, val allDay: Boolean, val color: Int)

    fun canRead() = granted(Manifest.permission.READ_CALENDAR)
    fun canWrite() = canRead() && granted(Manifest.permission.WRITE_CALENDAR)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val out = mutableListOf<DeviceCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.VISIBLE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(5) == 0) continue
                    out += DeviceCalendar(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Kalender",
                        account = c.getString(2).orEmpty(),
                        color = c.getInt(3),
                        writable = c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        }
        out
    }

    /** Appointments from all visible calendars between [from] and [to] (inclusive), Tenet's own left out. */
    suspend fun events(from: LocalDate, to: LocalDate): Map<LocalDate, List<Event>> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyMap()
        val zone = ZoneId.systemDefault()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val out = mutableMapOf<LocalDate, MutableList<Event>>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.VISIBLE,
                ),
                null, null, CalendarContract.Instances.BEGIN,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(6) == 0) continue
                    if (c.getString(5)?.contains(MARKER) == true) continue
                    val allDay = c.getInt(3) == 1
                    val s = Instant.ofEpochMilli(c.getLong(1))
                    val e = Instant.ofEpochMilli(c.getLong(2))
                    // All-day events are stored in UTC.
                    val day = if (allDay) s.atZone(ZoneOffset.UTC).toLocalDate() else s.atZone(zone).toLocalDate()
                    out.getOrPut(day) { mutableListOf() } += Event(c.getString(0) ?: "Termin", s, e, allDay, c.getInt(4))
                }
            }
        }
        out
    }

    /**
     * Writes the planned workouts of the next [days] days as all-day events into
     * [calendarId]; Tenet's earlier events there (marked in the description) are
     * replaced, so changes to the plan follow. Returns the number written.
     */
    suspend fun syncWorkouts(calendarId: Long, days: Int = 14): Int = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext 0
        val today = LocalDate.now()
        val last = today.plusDays(days.toLong() - 1)
        // Planned units are resolved week by week (recurring plans need the week's Monday).
        var monday = WeekMath.weekStart(today)
        val planned = buildList {
            while (!monday.isAfter(last)) {
                addAll(weekCalendar.observePlannedBetween(monday, monday.plusDays(6)).first())
                monday = monday.plusWeeks(1)
            }
        }.filter { !it.skipped }
            .mapNotNull { p -> p.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it to p } }
            .filter { (d, _) -> !d.isBefore(today) && !d.isAfter(last) }
            .distinctBy { (d, p) -> "$d/${p.discipline}/${p.title}" }

        val resolver = context.contentResolver
        val fromUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        runCatching {
            resolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ?",
                arrayOf(calendarId.toString(), "%$MARKER%", fromUtc.toString()),
            )
        }
        var written = 0
        planned.forEach { (day, p) ->
            val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "${p.discipline.emoji()} ${p.discipline.label()}: ${p.title}")
                put(CalendarContract.Events.DESCRIPTION, "Geplantes Training aus Tenet\n$MARKER")
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + 86_400_000L)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            }
            if (runCatching { resolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull() != null) written++
        }
        written
    }

    /** Removes everything Tenet wrote into [calendarId] (switching the export off). */
    suspend fun clearWorkouts(calendarId: Long) = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
                arrayOf(calendarId.toString(), "%$MARKER%"),
            )
        }
    }

    private fun Discipline.label() = when (this) {
        Discipline.GYM -> "Gym"
        Discipline.CALISTHENICS -> "Calisthenics"
        Discipline.RUNNING -> "Laufen"
    }

    private fun Discipline.emoji() = when (this) {
        Discipline.GYM -> "🏋️"
        Discipline.CALISTHENICS -> "🤸"
        Discipline.RUNNING -> "🏃"
    }

    companion object {
        /** In the description of every event Tenet writes, to find (and replace) them again. */
        const val MARKER = "[tenet-training]"
    }
}
