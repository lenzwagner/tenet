package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpxTest {
    private val points = listOf(
        RunAnalysis.Point(1_700_000_000_000, 48.1371, 11.5754, 520.0, 140),
        RunAnalysis.Point(1_700_000_005_000, 48.1372, 11.5756, 521.5, null),
    )

    @Test fun roundTrip() {
        val track = Gpx.parse(Gpx.write("Morgenlauf & mehr", points))
        assertEquals("Morgenlauf & mehr", track.name)
        assertEquals(2, track.points.size)
        assertEquals(points[0].time, track.points[0].time)
        assertEquals(48.1371, track.points[0].lat, 1e-7)
        assertEquals(140, track.points[0].hr)
        assertNull(track.points[1].hr)
        assertEquals(521.5, track.points[1].altitude!!, 0.01)
    }

    @Test fun stravaStyle() {
        val xml = """
            <gpx><trk><name><![CDATA[Lunch Run]]></name><trkseg>
            <trkpt lat='48.0' lon='11.0'><ele>500</ele><time>2024-05-01T10:00:00+02:00</time>
            <extensions><ns3:TrackPointExtension><ns3:hr>150</ns3:hr></ns3:TrackPointExtension></extensions></trkpt>
            <trkpt lat="48.001" lon="11.0"><time>2024-05-01T08:00:10Z</time></trkpt>
            <trkpt lat="48.002" lon="11.0"></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()
        val t = Gpx.parse(xml)
        assertEquals("Lunch Run", t.name)
        assertEquals(2, t.points.size)
        assertEquals(150, t.points[0].hr)
        assertEquals(10_000L, t.points[1].time - t.points[0].time)
    }
}
