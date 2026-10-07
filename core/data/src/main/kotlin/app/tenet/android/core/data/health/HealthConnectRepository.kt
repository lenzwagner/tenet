package app.tenet.android.core.data.health

import android.content.Context
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.SleepSessionRecord
import app.tenet.android.core.common.SleepMath
import app.tenet.android.core.common.SleepNight
import app.tenet.android.core.database.entity.BodyMetric
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.common.RunAnalysis
import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.RunSession
import app.tenet.android.core.database.entity.RunSource
import app.tenet.android.core.database.entity.RunTrackPoint
import app.tenet.android.core.database.entity.WorkoutSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Imports runs from Health Connect (Pixel Watch, Fitbit, Samsung Health,
 * Garmin, Strava, …) as finished running sessions. Imported sessions get
 * the id `hc-<record id>`, so a re-sync updates instead of duplicating, and
 * runs deleted at the source disappear here too.
 */
@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sportDao: SportDao,
    private val runDao: RunDao,
    private val runningRepository: RunningRepository,
) {
    enum class Availability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

    data class Status(
        val enabled: Boolean = false,
        val lastSync: Long? = null,
        /** Imported runs in the last sync window. */
        val runCount: Int = 0,
        val syncing: Boolean = false,
        val error: String? = null,
    )

    private val prefs = context.getSharedPreferences("health_connect", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val mutex = Mutex()

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    fun availability(): Availability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UPDATE_REQUIRED
            else -> Availability.UNAVAILABLE
        }

    suspend fun hasRequiredPermissions(): Boolean =
        availability() == Availability.AVAILABLE &&
            runCatching { client.permissionController.getGrantedPermissions().containsAll(REQUIRED) }
                .getOrDefault(false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _status.value = _status.value.copy(enabled = enabled, error = null)
    }

    /** Syncs when enabled and the last sync is older than [minAgeMs]. */
    suspend fun syncIfDue(minAgeMs: Long = 15 * 60_000L) {
        val s = _status.value
        if (!s.enabled) return
        if (s.lastSync != null && System.currentTimeMillis() - s.lastSync < minAgeMs) return
        sync()
    }

    /**
     * Reads running sessions of the last 30 days (a year on the first sync)
     * and mirrors them into the local database. Returns the number of runs.
     */
    suspend fun sync(): Result<Int> = mutex.withLock {
        if (availability() != Availability.AVAILABLE) {
            return fail("Health Connect ist nicht verfügbar.")
        }
        if (!hasRequiredPermissions()) return fail("Keine Berechtigung für Health Connect.")
        _status.value = _status.value.copy(syncing = true, error = null)

        val firstSync = _status.value.lastSync == null
        val from = Instant.now().minus(if (firstSync) 365 else 30, ChronoUnit.DAYS)
        val result = runCatching {
            val records = readRunRecords(from)
            val seen = mutableSetOf<String>()
            records.forEach { record ->
                val id = importRun(record) ?: return@forEach
                seen += id
            }
            // Runs deleted (or no longer running) at the source.
            sportDao.importedSessionIds(ID_PREFIX, from.toEpochMilli())
                .filter { it !in seen }
                .forEach { removeImported(it) }
            importWeights(from)
            seen.size
        }
        result.onSuccess { count ->
            val now = System.currentTimeMillis()
            prefs.edit {
                putLong(KEY_LAST_SYNC, now)
                putInt(KEY_COUNT, count)
            }
            _status.value = _status.value.copy(lastSync = now, runCount = count, syncing = false, error = null)
        }.onFailure {
            _status.value = _status.value.copy(syncing = false, error = it.message ?: "Synchronisierung fehlgeschlagen.")
        }
        result
    }

    /**
     * Sleep from Health Connect (watch, Fitbit, Samsung Health …) per wake-up
     * day, for the dream journal. Empty when off or not permitted.
     */
    suspend fun sleepNights(from: Instant): Map<java.time.LocalDate, SleepNight> {
        if (!_status.value.enabled || availability() != Availability.AVAILABLE) return emptyMap()
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (READ_SLEEP !in granted) return emptyMap()
        val sessions = runCatching { readAll(SleepSessionRecord::class, TimeRangeFilter.after(from), emptySet()) }.getOrDefault(emptyList())
        return SleepMath.nights(
            sessions.map { r ->
                fun stage(vararg types: Int) = r.stages.filter { it.stage in types }
                    .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                val total = java.time.Duration.between(r.startTime, r.endTime).toMinutes().toInt()
                val hasStages = r.stages.isNotEmpty()
                val awake = if (hasStages) stage(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED) else null
                SleepNight(
                    date = java.time.LocalDate.MIN,
                    start = r.startTime,
                    end = r.endTime,
                    asleepMin = total - (awake ?: 0),
                    deepMin = if (hasStages) stage(SleepSessionRecord.STAGE_TYPE_DEEP) else null,
                    remMin = if (hasStages) stage(SleepSessionRecord.STAGE_TYPE_REM) else null,
                    lightMin = if (hasStages) stage(SleepSessionRecord.STAGE_TYPE_LIGHT) else null,
                    awakeMin = awake,
                )
            },
        )
    }

    /** Per wake-up day: resting heart rate and night HRV, for the readiness score. */
    data class HeartDay(val restingHr: Int?, val hrvMs: Double?)

    /**
     * Resting heart rate and HRV (RMSSD) per day since [from]. Resting HR from
     * the watch's own daily value, else the low end of the night's heart rate;
     * HRV averaged over the day's samples (mostly taken during sleep).
     */
    suspend fun heartDays(from: Instant, nights: Map<java.time.LocalDate, SleepNight>): Map<java.time.LocalDate, HeartDay> {
        if (!_status.value.enabled || availability() != Availability.AVAILABLE) return emptyMap()
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val zone = java.time.ZoneId.systemDefault()
        val range = TimeRangeFilter.after(from)
        val resting: Map<java.time.LocalDate, Int> = if (READ_RESTING_HR in granted) {
            runCatching { readAll(RestingHeartRateRecord::class, range, emptySet()) }.getOrDefault(emptyList())
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, list) -> list.minOf { it.beatsPerMinute.toInt() } }
        } else {
            emptyMap()
        }
        // Fallback: 5th percentile of the heart rate while asleep.
        val nightHr: Map<java.time.LocalDate, Int> = if (READ_HR in granted && nights.isNotEmpty()) {
            val samples = runCatching { readAll(HeartRateRecord::class, range, emptySet()) }.getOrDefault(emptyList())
                .flatMap { it.samples }
            nights.mapNotNull { (day, night) ->
                val bpm = samples.filter { it.time >= night.start && it.time <= night.end }.map { it.beatsPerMinute.toInt() }.sorted()
                if (bpm.size < 20) null else day to bpm[bpm.size / 20]
            }.toMap()
        } else {
            emptyMap()
        }
        val hrv: Map<java.time.LocalDate, Double> = if (READ_HRV in granted) {
            runCatching { readAll(HeartRateVariabilityRmssdRecord::class, range, emptySet()) }.getOrDefault(emptyList())
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, list) -> list.map { it.heartRateVariabilityMillis }.average() }
        } else {
            emptyMap()
        }
        return (resting.keys + nightHr.keys + hrv.keys).associateWith { day ->
            HeartDay(resting[day] ?: nightHr[day], hrv[day])
        }
    }

    private suspend fun readRunRecords(from: Instant): List<ExerciseSessionRecord> {
        val out = mutableListOf<ExerciseSessionRecord>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(from),
                    pageToken = token,
                ),
            )
            out += response.records.filter { it.exerciseType in RUN_TYPES }
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    /** Upserts one run; returns its session id or null when unusable. */
    private suspend fun importRun(record: ExerciseSessionRecord): String? {
        val start = record.startTime
        val end = record.endTime
        val durationSec = ((end.toEpochMilli() - start.toEpochMilli()) / 1000).toInt()
        if (durationSec < 60) return null
        val origin = setOf(record.metadata.dataOrigin)
        val totals = readTotals(start, end, origin)
        val distanceM = totals.distanceM.toFloat()
        if (distanceM < 100f) return null
        val avgHr = totals.avgHr
        val elevation = totals.elevationM?.roundToInt()

        val id = ID_PREFIX + record.metadata.id
        val existing = sportDao.sessionOnce(id)
        // Same run already recorded in Tenet (phone GPS or entered by hand)?
        // Then the watch copy is skipped instead of counting the run twice.
        if (existing == null && overlapsOwnRun(start.toEpochMilli(), end.toEpochMilli())) return null
        val startMs = start.toEpochMilli()
        sportDao.upsertSession(
            WorkoutSession(
                id = id,
                discipline = Discipline.RUNNING,
                plannedWorkoutId = existing?.plannedWorkoutId ?: runningRepository.matchPlannedRun(startMs, catchUp = true),
                startedAt = startMs,
                endedAt = end.toEpochMilli(),
                notes = existing?.notes?.takeIf { it.isNotBlank() } ?: defaultNote(record),
                perceivedEffort = existing?.perceivedEffort,
            ),
        )
        runDao.upsertRunSession(
            RunSession(
                sessionId = id,
                distanceM = distanceM,
                durationSec = durationSec,
                avgPaceSecPerKm = (durationSec / (distanceM / 1000f)).roundToInt(),
                avgHr = avgHr,
                elevationGainM = elevation,
                source = RunSource.HEALTH_CONNECT,
            ),
        )
        if (importRoute(id, record)) {
            runningRepository.analyzeTrack(id)
        } else {
            runningRepository.saveEfforts(id, RunAnalysis.wholeRunEfforts(distanceM, durationSec), startMs)
        }
        return id
    }

    /** GPS track, only when the source shares it without extra consent. */
    /** Returns true when the run has a route (stored or already present). */
    private suspend fun importRoute(sessionId: String, record: ExerciseSessionRecord): Boolean {
        val route = (record.exerciseRouteResult as? ExerciseRouteResult.Data)?.exerciseRoute
            ?: return runDao.trackPointCount(sessionId) > 1
        if (runDao.trackPointCount(sessionId) == route.route.size) return true
        runDao.deleteTrackPoints(sessionId)
        runDao.insertTrackPoints(
            route.route.mapIndexed { index, point ->
                RunTrackPoint(
                    id = "$sessionId-$index",
                    sessionId = sessionId,
                    timestamp = point.time.toEpochMilli(),
                    lat = point.latitude,
                    lon = point.longitude,
                    altitude = point.altitude?.inMeters,
                )
            },
        )
        return true
    }

    private suspend fun overlapsOwnRun(startMs: Long, endMs: Long): Boolean =
        sportDao.observeSessionsBetween(startMs - 3 * 3_600_000L, endMs).first().any { row ->
            val s = row.session
            if (s.discipline != Discipline.RUNNING || s.id.startsWith(ID_PREFIX)) return@any false
            val sEnd = s.endedAt ?: return@any false
            val overlap = minOf(sEnd, endMs) - maxOf(s.startedAt, startMs)
            overlap > 0.5 * minOf(sEnd - s.startedAt, endMs - startMs)
        }

    private suspend fun removeImported(sessionId: String) = runningRepository.deleteRun(sessionId)

    private data class RunTotals(val distanceM: Double, val avgHr: Int?, val elevationM: Double?)

    /**
     * Distance, heart rate and elevation of one run, summed from the raw
     * records of the same source app. (Aggregates would skip apps that are
     * not in the user's Health Connect priority list.) Heart rate and
     * elevation are optional permissions and simply stay null when denied.
     */
    private suspend fun readTotals(start: Instant, end: Instant, origin: Set<DataOrigin>): RunTotals {
        val range = TimeRangeFilter.between(start, end)
        val distance = readAll(DistanceRecord::class, range, origin).sumOf { it.distance.inMeters }
        val hrSamples = runCatching {
            readAll(HeartRateRecord::class, range, origin).flatMap { it.samples }
                .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
        }.getOrDefault(emptyList())
        val elevation = runCatching {
            readAll(ElevationGainedRecord::class, range, origin).sumOf { it.elevation.inMeters }
        }.getOrNull()?.takeIf { it > 0 }
        return RunTotals(
            distanceM = distance,
            avgHr = hrSamples.takeIf { it.isNotEmpty() }?.map { it.beatsPerMinute }?.average()?.roundToInt(),
            elevationM = elevation,
        )
    }

    /**
     * Body weight from other apps (scale, Fitbit …) for days without an own
     * entry; Tenet's own weights are written back by [HealthWriter].
     */
    private suspend fun importWeights(from: Instant) {
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (HealthWriter.READ_WEIGHT !in granted) return
        val own = sportDao.observeBodyMetrics().first().associateBy { it.date }
        val zone = java.time.ZoneId.systemDefault()
        readAll(WeightRecord::class, TimeRangeFilter.after(from), emptySet())
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
            .groupBy { it.time.atZone(zone).toLocalDate().toString() }
            .forEach { (date, records) ->
                if (date in own) return@forEach
                val kg = records.maxBy { it.time }.weight.inKilograms.toFloat()
                sportDao.upsertBodyMetric(BodyMetric(id = "hc-weight-$date", date = date, weight = (kg * 10).roundToInt() / 10f))
            }
    }

    private suspend fun <T : Record> readAll(
        type: KClass<T>,
        range: TimeRangeFilter,
        origin: Set<DataOrigin>,
    ): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = range,
                    dataOriginFilter = origin,
                    pageToken = token,
                ),
            )
            out += response.records
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    private fun defaultNote(record: ExerciseSessionRecord): String {
        val app = sourceLabel(record.metadata.dataOrigin.packageName)
        val title = record.title?.takeIf { it.isNotBlank() }
        return listOfNotNull(title, "Importiert aus $app").joinToString(" · ")
    }

    private fun sourceLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun fail(message: String): Result<Int> {
        _status.value = _status.value.copy(syncing = false, error = message)
        return Result.failure(IllegalStateException(message))
    }

    private fun readStatus() = Status(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        lastSync = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
        runCount = prefs.getInt(KEY_COUNT, 0),
    )

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        private const val ID_PREFIX = "hc-"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_COUNT = "run_count"

        private val RUN_TYPES = setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        )

        val READ_SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val READ_HR: String = HealthPermission.getReadPermission(HeartRateRecord::class)
        val READ_RESTING_HR: String = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val READ_HRV: String = HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)

        /** Without these, nothing can be imported. */
        val REQUIRED: Set<String> = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
        )

        /** Everything Tenet asks for (optional ones improve the import). */
        val PERMISSIONS: Set<String> = REQUIRED + setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class),
            "android.permission.health.READ_EXERCISE_ROUTES",
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
            HealthWriter.READ_WEIGHT,
            READ_SLEEP,
            READ_RESTING_HR,
            READ_HRV,
            HealthWriter.WRITE_WEIGHT,
            HealthWriter.WRITE_EXERCISE,
            HealthWriter.WRITE_DISTANCE,
        )
    }
}
