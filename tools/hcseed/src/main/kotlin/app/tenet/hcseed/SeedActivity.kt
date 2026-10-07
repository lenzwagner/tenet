package app.tenet.hcseed

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.lifecycle.lifecycleScope
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Writes three sample runs like a watch would (session, distance, heart
 * rate every 10 s). Opening the app again adds nothing twice (client ids).
 */
class SeedActivity : ComponentActivity() {

    private val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
    )

    private val request = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(permissions)) seed() else show("Berechtigungen fehlen")
    }

    private lateinit var text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply { textSize = 20f; setPadding(48, 160, 48, 48) }
        setContentView(text)
        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(this@SeedActivity)
            if (client.permissionController.getGrantedPermissions().containsAll(permissions)) seed() else request.launch(permissions)
        }
    }

    private data class Sample(val daysAgo: Long, val km: Double, val minutes: Long, val hr: Int, val id: String)

    private fun seed() {
        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(this@SeedActivity)
            val samples = listOf(
                Sample(9, 10.0, 55, 148, "seed-easy-10k"),
                Sample(5, 5.0, 23, 172, "seed-tempo-5k"),
                Sample(2, 15.0, 88, 150, "seed-long-15k"),
            )
            val records = samples.flatMap { s ->
                val start = Instant.now().minus(Duration.ofDays(s.daysAgo)).minus(Duration.ofHours(3))
                val end = start.plus(Duration.ofMinutes(s.minutes))
                val meta = { suffix: String -> Metadata.autoRecorded(Device(type = Device.TYPE_WATCH), clientRecordId = s.id + suffix) }
                listOf(
                    ExerciseSessionRecord(
                        startTime = start, startZoneOffset = ZoneOffset.UTC, endTime = end, endZoneOffset = ZoneOffset.UTC,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, title = "Lauf (Testdaten)",
                        metadata = meta("-session"),
                    ),
                    DistanceRecord(
                        startTime = start, startZoneOffset = ZoneOffset.UTC, endTime = end, endZoneOffset = ZoneOffset.UTC,
                        distance = Length.kilometers(s.km), metadata = meta("-distance"),
                    ),
                    HeartRateRecord(
                        startTime = start, startZoneOffset = ZoneOffset.UTC, endTime = end, endZoneOffset = ZoneOffset.UTC,
                        samples = (0 until s.minutes * 6).map { i ->
                            HeartRateRecord.Sample(start.plusSeconds(i * 10), (s.hr + (i % 5) - 2).toLong())
                        },
                        metadata = meta("-hr"),
                    ),
                )
            }
            // 30 nights: sleep with stages, resting pulse and HRV in the morning.
            // Last night is shorter with a higher pulse and lower HRV (moderate readiness).
            val zone = java.time.ZoneId.systemDefault()
            val nights = (0L..30L).flatMap { daysAgo ->
                val wake = java.time.LocalDate.now().minusDays(daysAgo).atTime(6, 45).atZone(zone).toInstant()
                val hours = if (daysAgo == 0L) 6.2 else 7.2 + (daysAgo % 4) * 0.25
                val start = wake.minusSeconds((hours * 3600).toLong())
                val meta = { suffix: String -> Metadata.autoRecorded(Device(type = Device.TYPE_WATCH), clientRecordId = "seed-night-$daysAgo$suffix") }
                val total = Duration.between(start, wake)
                fun at(f: Double) = start.plusSeconds((total.seconds * f).toLong())
                val stages = listOf(
                    SleepSessionRecord.Stage(at(0.0), at(0.08), SleepSessionRecord.STAGE_TYPE_LIGHT),
                    SleepSessionRecord.Stage(at(0.08), at(0.22), SleepSessionRecord.STAGE_TYPE_DEEP),
                    SleepSessionRecord.Stage(at(0.22), at(0.55), SleepSessionRecord.STAGE_TYPE_LIGHT),
                    SleepSessionRecord.Stage(at(0.55), at(0.72), SleepSessionRecord.STAGE_TYPE_REM),
                    SleepSessionRecord.Stage(at(0.72), at(0.76), SleepSessionRecord.STAGE_TYPE_AWAKE),
                    SleepSessionRecord.Stage(at(0.76), at(0.92), SleepSessionRecord.STAGE_TYPE_LIGHT),
                    SleepSessionRecord.Stage(at(0.92), at(1.0), SleepSessionRecord.STAGE_TYPE_REM),
                )
                listOf(
                    SleepSessionRecord(
                        startTime = start, startZoneOffset = ZoneOffset.UTC, endTime = wake, endZoneOffset = ZoneOffset.UTC,
                        stages = stages, metadata = meta("-sleep"),
                    ),
                    RestingHeartRateRecord(
                        time = wake, zoneOffset = ZoneOffset.UTC,
                        beatsPerMinute = if (daysAgo == 0L) 58 else 52L + daysAgo % 3,
                        metadata = meta("-rhr"),
                    ),
                    HeartRateVariabilityRmssdRecord(
                        time = wake.minusSeconds(1800), zoneOffset = ZoneOffset.UTC,
                        heartRateVariabilityMillis = if (daysAgo == 0L) 44.0 else 58.0 + (daysAgo % 5),
                        metadata = meta("-hrv"),
                    ),
                )
            }
            runCatching { client.insertRecords(nights) }
                .onFailure { show("Fehler Nächte: ${it.message}"); return@launch }
            runCatching { client.insertRecords(records) }
                .onSuccess { show("30 Nächte (Schlaf, Ruhepuls, HRV) +\n3 Testläufe geschrieben:\n10 km 55:00 (Puls 148)\n5 km 23:00 (Puls 172)\n15 km 1:28:00 (Puls 150)") }
                .onFailure { show("Fehler: ${it.message}") }
        }
    }

    private fun show(msg: String) {
        text.text = msg
    }
}
