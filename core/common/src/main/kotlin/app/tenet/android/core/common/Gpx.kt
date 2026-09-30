package app.tenet.android.core.common

import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/**
 * GPX 1.1 track import/export (Strava, Garmin, Komoot …). Heart rate uses the
 * Garmin TrackPointExtension, which every major platform reads.
 */
object Gpx {

    data class Track(val name: String?, val points: List<RunAnalysis.Point>)

    fun write(name: String, points: List<RunAnalysis.Point>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append(
            """<gpx version="1.1" creator="Tenet" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">""",
        ).append('\n')
        append("  <metadata><time>").append(points.firstOrNull()?.let { Instant.ofEpochMilli(it.time) } ?: Instant.EPOCH).append("</time></metadata>\n")
        append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <type>running</type>\n    <trkseg>\n")
        points.forEach { p ->
            append("      <trkpt lat=\"").append(coord(p.lat)).append("\" lon=\"").append(coord(p.lon)).append("\">")
            p.altitude?.let { append("<ele>").append(String.format(Locale.US, "%.1f", it)).append("</ele>") }
            append("<time>").append(Instant.ofEpochMilli(p.time)).append("</time>")
            p.hr?.let {
                append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>").append(it)
                    .append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
            }
            append("</trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    private val TRKPT = Regex("""<trkpt\b([^>]*)>(.*?)</trkpt>|<trkpt\b([^>]*)/>""", RegexOption.DOT_MATCHES_ALL)
    private val LAT = Regex("""\blat\s*=\s*["']([-0-9.eE+]+)["']""")
    private val LON = Regex("""\blon\s*=\s*["']([-0-9.eE+]+)["']""")
    private val ELE = Regex("""<ele>\s*([-0-9.eE+]+)\s*</ele>""")
    private val TIME = Regex("""<time>\s*([^<]+?)\s*</time>""")
    private val HR = Regex("""<(?:\w+:)?hr>\s*(\d+)\s*</(?:\w+:)?hr>""")
    private val NAME = Regex("""<trk>.*?<name>\s*(.*?)\s*</name>""", RegexOption.DOT_MATCHES_ALL)

    /** Track points with timestamps, in time order; points without time are dropped. */
    fun parse(xml: String): Track {
        val points = TRKPT.findAll(xml).mapNotNull { m ->
            val attrs = m.groupValues[1].ifEmpty { m.groupValues[3] }
            val body = m.groupValues[2]
            val lat = LAT.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = LON.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val time = TIME.find(body)?.groupValues?.get(1)?.let(::parseTime) ?: return@mapNotNull null
            RunAnalysis.Point(
                time = time,
                lat = lat,
                lon = lon,
                altitude = ELE.find(body)?.groupValues?.get(1)?.toDoubleOrNull(),
                hr = HR.find(body)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 25..250 },
            )
        }.sortedBy { it.time }.toList()
        val name = NAME.find(xml)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.isNotBlank() }
        return Track(name, points)
    }

    private fun parseTime(s: String): Long? =
        runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()

    private fun coord(v: Double) = String.format(Locale.US, "%.7f", v)

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun unescape(s: String) = s.replace("<![CDATA[", "").replace("]]>", "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
}
