package app.tenet.android.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.RunSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes Tenet's own data to Health Connect: finished workouts (gym,
 * calisthenics, GPS/manual runs) and body weight. Uses client record ids,
 * so writing the same session again updates it instead of duplicating.
 * Silent no-op when Health Connect is off or the write permission is missing.
 */
@Singleton
class HealthWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sportDao: SportDao,
    private val runDao: RunDao,
) {
    private val prefs = context.getSharedPreferences("health_connect", Context.MODE_PRIVATE)
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private suspend fun granted(vararg permissions: String): Boolean =
        prefs.getBoolean("enabled", false) &&
            HealthConnectClient.getSdkStatus(context, HealthConnectRepository.PROVIDER) == HealthConnectClient.SDK_AVAILABLE &&
            runCatching { client.permissionController.getGrantedPermissions().containsAll(permissions.toList()) }.getOrDefault(false)

    /** Mirrors a finished session. Imported Health Connect runs are never written back. */
    suspend fun writeWorkout(sessionId: String) {
        if (sessionId.startsWith("hc-")) return
        runCatching {
            if (!granted(WRITE_EXERCISE)) return
            val session = sportDao.sessionOnce(sessionId) ?: return
            val end = session.endedAt ?: return
            if (end - session.startedAt < 60_000L) return
            val run = if (session.discipline == Discipline.RUNNING) runDao.runSessionOnce(sessionId) else null
            if (run?.source == RunSource.HEALTH_CONNECT) return
            val title = session.plannedWorkoutId?.let { sportDao.workoutById(it)?.title }
                ?: when (session.discipline) {
                    Discipline.GYM -> "Krafttraining"
                    Discipline.CALISTHENICS -> "Calisthenics"
                    Discipline.RUNNING -> "Lauf"
                }
            val start = Instant.ofEpochMilli(session.startedAt)
            val stop = Instant.ofEpochMilli(end)
            val offset = zone.rules.getOffset(start)
            val metadata = if (run?.source == RunSource.GPS) {
                Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE), "tenet-$sessionId", end)
            } else {
                Metadata.manualEntry("tenet-$sessionId", end)
            }
            val records = mutableListOf<Record>(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = stop,
                    endZoneOffset = zone.rules.getOffset(stop),
                    exerciseType = when (session.discipline) {
                        Discipline.GYM -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                        Discipline.CALISTHENICS -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
                        Discipline.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                    },
                    title = title,
                    notes = session.notes.takeIf { it.isNotBlank() },
                    metadata = metadata,
                ),
            )
            if (run != null && run.distanceM > 0f && granted(WRITE_DISTANCE)) {
                records += DistanceRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = stop,
                    endZoneOffset = zone.rules.getOffset(stop),
                    distance = Length.meters(run.distanceM.toDouble()),
                    metadata = Metadata.manualEntry("tenet-dist-$sessionId", end),
                )
            }
            client.insertRecords(records)
        }
    }

    /** Body weight of [date] (one record per day, updated on change). */
    suspend fun writeWeight(date: LocalDate, kg: Float) {
        runCatching {
            if (!granted(WRITE_WEIGHT)) return
            val time = date.atTime(8, 0).atZone(zone).toInstant()
            client.insertRecords(
                listOf(
                    WeightRecord(
                        time = time,
                        zoneOffset = zone.rules.getOffset(time),
                        weight = Mass.kilograms(kg.toDouble()),
                        metadata = Metadata.manualEntry("tenet-weight-$date", System.currentTimeMillis()),
                    ),
                ),
            )
        }
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    companion object {
        val WRITE_EXERCISE = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        val WRITE_DISTANCE = HealthPermission.getWritePermission(DistanceRecord::class)
        val WRITE_WEIGHT = HealthPermission.getWritePermission(WeightRecord::class)
        val READ_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    }
}
