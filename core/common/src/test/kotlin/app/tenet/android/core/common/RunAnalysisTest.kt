package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunAnalysisTest {

    /** Straight line north; one point per second at [speed] m/s. */
    private fun track(seconds: Int, speed: Double, startMs: Long = 0L, startLat: Double = 48.0): List<RunAnalysis.Point> {
        val degPerM = 1.0 / 111_195.0
        return (0..seconds).map { s ->
            RunAnalysis.Point(time = startMs + s * 1000L, lat = startLat + s * speed * degPerM, lon = 11.0)
        }
    }

    @Test
    fun haversine_oneDegreeLatitude() {
        assertEquals(111_195.0, RunAnalysis.haversine(0.0, 0.0, 1.0, 0.0), 5.0)
    }

    @Test
    fun summary_distanceAndMovingTime() {
        val s = RunAnalysis.summary(track(600, 3.0)) // 1800 m in 10 min
        assertEquals(1800f, s.distanceM, 5f)
        assertEquals(600, s.durationSec)
        assertEquals(333.0, s.avgPaceSecPerKm.toDouble(), 2.0)
    }

    @Test
    fun pauseGap_isExcluded() {
        val first = track(300, 3.0)
        val last = first.last()
        // 2 minutes standing (no points), then run on from the same spot.
        val second = track(300, 3.0, startMs = last.time + 120_000L, startLat = last.lat)
        val s = RunAnalysis.summary(first + second)
        assertEquals(600, s.durationSec)
        assertEquals(1800f, s.distanceM, 5f)
    }

    @Test
    fun splits_perKilometer() {
        val splits = RunAnalysis.splits(track(1000, 2.5)) // 2500 m
        assertEquals(3, splits.size)
        assertEquals(400.0, splits[0].durationSec.toDouble(), 1.0)
        assertEquals(400.0, splits[0].paceSecPerKm.toDouble(), 2.0)
        assertEquals(500f, splits[2].distanceM, 5f)
    }

    @Test
    fun bestEfforts_findsFastestWindow() {
        // 1 km slow (2.5 m/s = 400 s), then 1 km fast (5 m/s = 200 s).
        val slow = track(400, 2.5)
        val fast = track(200, 5.0, startMs = slow.last().time, startLat = slow.last().lat).drop(1)
        val best = RunAnalysis.bestEfforts(slow + fast)
        assertEquals(200.0, best[1_000]!!.toDouble(), 2.0)
        assertNull(best[5_000])
    }

    @Test
    fun elevationGain_ignoresNoise() {
        val noisy = listOf(100.0, 101.0, 99.5, 100.5, 104.0, 108.0, 107.0, 111.0)
        assertEquals(11, RunAnalysis.elevationGain(noisy))
    }

    @Test
    fun hrZones_countSeconds() {
        val pts = track(10, 3.0).mapIndexed { i, p -> p.copy(hr = if (i < 5) 120 else 180) }
        val zones = RunAnalysis.hrZoneSeconds(pts, maxHr = 190)
        assertEquals(4, zones[1]) // 120/190 = 63 % → Z2
        assertEquals(6, zones[4]) // 180/190 = 95 % → Z5
    }

    @Test
    fun guidance_intervalsExpandAndAdvance() {
        val phases = RunGuidance.parseIntervals("""[{"reps":2,"lengthM":400,"restSec":60}]""", 270)
        assertEquals(3, phases.size) // work, rest, work
        var state = RunGuidance.IntervalState()
        val (s, e) = RunGuidance.advance(phases, state, 310.0, 60_000)
        assertTrue(e is RunGuidance.IntervalEvent.AlmostDone)
        state = s
        val r = RunGuidance.advance(phases, state, 400.0, 80_000)
        assertTrue(r.second is RunGuidance.IntervalEvent.PhaseStarted)
        state = r.first
        assertTrue(phases[state.index] is RunGuidance.Phase.Rest)
        val r2 = RunGuidance.advance(phases, state, 450.0, 140_000)
        assertTrue(r2.second is RunGuidance.IntervalEvent.PhaseStarted)
        val r3 = RunGuidance.advance(phases, r2.first, 850.0, 240_000)
        assertEquals(RunGuidance.IntervalEvent.Finished, r3.second)
    }

    @Test
    fun guidance_autoPause() {
        val ap = RunGuidance.AutoPause()
        assertFalse(ap.update(0.2f, 0, paused = false))
        assertFalse(ap.update(0.2f, 5_000, paused = false))
        assertTrue(ap.update(0.2f, 9_000, paused = false))
        assertTrue(ap.update(1.0f, 10_000, paused = true))
        assertFalse(ap.update(2.5f, 11_000, paused = true))
    }

    @Test
    fun guidance_spoken() {
        assertEquals("5 Minuten 30", RunGuidance.spokenPace(330))
        assertEquals("eine Stunde 2 Minuten", RunGuidance.spokenDuration(3720))
    }

    @Test
    fun wholeRunEfforts_onlyNearStandardDistances() {
        assertEquals(mapOf(5_000 to 1500), RunAnalysis.wholeRunEfforts(5_050f, 1515))
        assertTrue(RunAnalysis.wholeRunEfforts(6_000f, 1800).isEmpty())
    }

    @Test
    fun gpsJump_addsNoDistance() {
        val pts = track(100, 3.0).toMutableList()
        // One fix 500 m off, then back on track.
        pts[50] = pts[50].copy(lat = pts[50].lat + 500 / 111_195.0)
        val s = RunAnalysis.summary(pts)
        assertTrue("was ${s.distanceM}", s.distanceM < 310f)
    }
}
