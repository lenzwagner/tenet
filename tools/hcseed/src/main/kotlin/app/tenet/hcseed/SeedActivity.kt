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
            runCatching { client.insertRecords(records) }
                .onSuccess { show("3 Testläufe geschrieben:\n10 km 55:00 (Puls 148)\n5 km 23:00 (Puls 172)\n15 km 1:28:00 (Puls 150)") }
                .onFailure { show("Fehler: ${it.message}") }
        }
    }

    private fun show(msg: String) {
        text.text = msg
    }
}
