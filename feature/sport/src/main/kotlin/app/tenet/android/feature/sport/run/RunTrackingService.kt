package app.tenet.android.feature.sport.run

import androidx.compose.runtime.getValue
import app.tenet.android.feature.sport.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.ServiceCompat
import app.tenet.android.core.common.RunGuidance
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.database.entity.RunTrackPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RunStatus { STARTING, RUNNING, PAUSED, AUTO_PAUSED, SAVING, FINISHED }

/** Current interval phase for the live screen. */
data class IntervalLive(
    val label: String,
    /** "320 m" / "0:45" left in the phase. */
    val remaining: String,
    val isRest: Boolean,
    val done: Boolean = false,
)

/** Live state of the recorded run, shared with the UI. */
data class RunLiveState(
    val sessionId: String,
    val status: RunStatus = RunStatus.STARTING,
    val movingMs: Long = 0L,
    val distanceM: Double = 0.0,
    val currentPaceSecPerKm: Int? = null,
    val gpsAccuracyM: Float? = null,
    val route: List<Pair<Double, Double>> = emptyList(),
    val interval: IntervalLive? = null,
    val autoPause: Boolean = true,
    val voice: Boolean = true,
    /** Set after saving; null when the run was too short and discarded. */
    val savedSessionId: String? = null,
) {
    val avgPaceSecPerKm: Int?
        get() = if (distanceM < 50) null else ((movingMs / 1000.0) / (distanceM / 1000.0)).toInt()
}

/**
 * GPS run recording as a location foreground service (App_Konzept.md
 * 5.2.3: "Foreground Service (Typ location), damit der Lauf bei gesperrtem
 * Display weiterläuft. Live-Notification mit Zeit, Distanz, Pace",
 * "Geführte Intervalle … TextToSpeech und Vibration", "Auto-Pause bei
 * Stillstand, Kilometer-Ansagen").
 *
 * Track points are buffered and written in batches; no points are stored
 * while paused, so the analysis sees the pause as a gap.
 */
@AndroidEntryPoint
class RunTrackingService : Service(), TextToSpeech.OnInitListener {

    @Inject lateinit var repository: RunningRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var tickJob: Job? = null
    private var lastTickAt = 0L
    /** Anchor for distance (null after a pause). */
    private var lastLocation: Location? = null
    /** Last accurate fix, for speed only. */
    private var lastSeen: Location? = null
    private val buffer = mutableListOf<RunTrackPoint>()
    private val paceWindow = ArrayDeque<Pair<Long, Double>>()
    private val autoPause = RunGuidance.AutoPause()
    private var phases: List<RunGuidance.Phase> = emptyList()
    private var intervalState = RunGuidance.IntervalState()
    private var nextKm = 1
    private var lastKmMovingMs = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_STOP -> finish()
            ACTION_VOICE -> _state.value = _state.value?.let { it.copy(voice = !it.voice) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.GERMAN
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
    }

    // ---- Lifecycle ------------------------------------------------------------

    @SuppressLint("MissingPermission") // checked by the screen before starting
    private fun start(intent: Intent) {
        if (_state.value?.status?.let { it != RunStatus.FINISHED } == true) return
        val planned = intent.getStringExtra(EXTRA_PLANNED)
        val pace = intent.getIntExtra(EXTRA_PACE, 0).takeIf { it > 0 }
        val warmup = app.tenet.android.core.common.RunWorkoutStructure.WARMUP_SEC
        // Like Runna: 10 min easy warm-up, then the reps (or the tempo block) start on their own.
        phases = RunGuidance.parseIntervals(intent.getStringExtra(EXTRA_INTERVALS), pace, warmupSec = warmup)
            .ifEmpty { RunGuidance.tempoPhases(intent.getIntExtra(EXTRA_TEMPO, 0), pace, warmup) }
        // Easy/long runs: no phases, but a pace target and distance to announce against.
        steadyPace = if (phases.isEmpty()) pace else null
        steadyDistanceM = intent.getIntExtra(EXTRA_DISTANCE, 0).takeIf { it > 0 && steadyPace != null }
        halfwaySaid = false
        goalSaid = false
        val voice = intent.getBooleanExtra(EXTRA_VOICE, true)
        val auto = intent.getBooleanExtra(EXTRA_AUTO_PAUSE, true)
        _state.value = RunLiveState(sessionId = "", voice = voice, autoPause = auto)
        goForeground()
        tts = TextToSpeech(this, this) // also when muted: voice can be switched on mid-run

        scope.launch {
            val id = repository.startRecordedRun(planned)
            _state.value = _state.value?.copy(sessionId = id, status = RunStatus.RUNNING, interval = intervalLive(0.0, 0L))
            lastTickAt = System.currentTimeMillis()
            fused.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                    .setMinUpdateIntervalMillis(1_000L)
                    .setWaitForAccurateLocation(false)
                    .build(),
                callback,
                Looper.getMainLooper(),
            )
            startTicking()
            phases.firstOrNull()?.let { say(RunGuidance.phaseAnnouncement(it)); vibrate() }
            steadyPace?.let { say(RunGuidance.steadyStartAnnouncement(it, steadyDistanceM)) }
        }
    }

    private fun setPaused(paused: Boolean) {
        val before = _state.value ?: return
        if (before.status == RunStatus.SAVING || before.status == RunStatus.FINISHED) return
        tickClock() // count the running time up to now before switching
        val s = _state.value!!
        lastLocation = null // no distance across a pause
        autoPause.reset()
        _state.value = s.copy(status = if (paused) RunStatus.PAUSED else RunStatus.RUNNING)
        updateNotification()
        if (paused) say("Lauf pausiert.") else say("Weiter geht's.")
    }

    private fun finish() {
        val before = _state.value ?: return stopSelf()
        if (before.status == RunStatus.SAVING || before.status == RunStatus.FINISHED) return
        tickClock()
        val s = _state.value!!
        fused.removeLocationUpdates(callback)
        tickJob?.cancel()
        _state.value = s.copy(status = RunStatus.SAVING)
        scope.launch {
            flush()
            val saved = s.sessionId.isNotEmpty() &&
                repository.finishRecordedRun(s.sessionId, (s.movingMs / 1000).toInt())
            _state.value = _state.value?.copy(status = RunStatus.FINISHED, savedSessionId = s.sessionId.takeIf { saved })
            if (saved) say("Lauf gespeichert. ${spokenKm(s.distanceM)} in ${RunGuidance.spokenDuration((s.movingMs / 1000).toInt())}.")
            delay(if (saved) 4_000 else 0) // let the summary finish speaking
            ServiceCompat.stopForeground(this@RunTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---- Clock & GPS ------------------------------------------------------------

    private fun startTicking() {
        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                tickClock()
                checkIntervals()
                updateNotification()
            }
        }
    }

    /** Moving time only advances while running. */
    private fun tickClock() {
        val now = System.currentTimeMillis()
        val s = _state.value ?: return
        if (s.status == RunStatus.RUNNING) {
            _state.value = s.copy(movingMs = s.movingMs + (now - lastTickAt))
        }
        lastTickAt = now
    }

    private fun onLocation(location: Location) {
        val s = _state.value ?: return
        if (s.sessionId.isEmpty() || s.status == RunStatus.SAVING || s.status == RunStatus.FINISHED) return
        _state.value = s.copy(gpsAccuracyM = location.accuracy)
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return

        // Speed from the last fix (also while paused, so auto-pause can resume);
        // the reported speed can be 0 on some devices/emulators.
        val seen = lastSeen
        lastSeen = location
        val seenSpeed = seen?.let {
            val sec = (location.time - it.time).coerceAtLeast(1) / 1000.0
            (it.distanceTo(location) / sec).toFloat()
        } ?: 0f
        val speed = maxOf(if (location.hasSpeed()) location.speed else 0f, seenSpeed)

        val previous = lastLocation
        val step = previous?.distanceTo(location)?.toDouble() ?: 0.0
        val dt = previous?.let { (location.time - it.time).coerceAtLeast(1) } ?: 1_000L

        // Auto-pause / resume on standstill.
        if (s.autoPause && (s.status == RunStatus.RUNNING || s.status == RunStatus.AUTO_PAUSED)) {
            val paused = autoPause.update(speed, location.time, s.status == RunStatus.AUTO_PAUSED)
            if (paused != (s.status == RunStatus.AUTO_PAUSED)) {
                tickClock()
                _state.value = _state.value!!.copy(status = if (paused) RunStatus.AUTO_PAUSED else RunStatus.RUNNING)
                if (paused) {
                    lastLocation = null
                    say("Auto-Pause.")
                } else {
                    say("Weiter.")
                }
                updateNotification()
            }
        }
        val current = _state.value!!
        if (current.status != RunStatus.RUNNING) return

        // Ignore GPS jumps (> 12 m/s is not running).
        val add = if (previous != null && step / (dt / 1000.0) < 12.0) step else 0.0
        lastLocation = location
        val distance = current.distanceM + add
        buffer += RunTrackPoint(
            id = newUuid(),
            sessionId = current.sessionId,
            timestamp = location.time,
            lat = location.latitude,
            lon = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
        )
        if (buffer.size >= 15) scope.launch { flush() }

        paceWindow.addLast(current.movingMs to distance)
        while (paceWindow.size > 2 && current.movingMs - paceWindow.first().first > 30_000) paceWindow.removeFirst()
        val (t0, d0) = paceWindow.first()
        val pace = if (distance - d0 > 20) (((current.movingMs - t0) / 1000.0) / ((distance - d0) / 1000.0)).toInt() else null

        _state.value = current.copy(
            distanceM = distance,
            currentPaceSecPerKm = pace,
            route = current.route + (location.latitude to location.longitude),
        )
        announceKilometer(distance, current.movingMs)
        checkIntervals()
    }

    private suspend fun flush() {
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        buffer.clear()
        repository.appendTrackPoints(batch)
    }

    // ---- Guidance -------------------------------------------------------------

    private var steadyPace: Int? = null
    private var steadyDistanceM: Int? = null
    private var halfwaySaid = false
    private var goalSaid = false

    private fun announceKilometer(distance: Double, movingMs: Long) {
        steadyDistanceM?.let { goal ->
            if (!halfwaySaid && goal >= 4000 && distance >= goal / 2.0) {
                halfwaySaid = true
                say(RunGuidance.steadyHalfway(goal))
            }
            if (!goalSaid && distance >= goal) {
                goalSaid = true
                say(RunGuidance.steadyGoalReached(goal))
            }
        }
        if (distance < nextKm * 1000.0) return
        val kmSec = ((movingMs - lastKmMovingMs) / 1000).toInt()
        val hint = steadyPace?.let { " " + RunGuidance.steadyPaceHint(kmSec, it) }.orEmpty()
        say(RunGuidance.kilometerAnnouncement(nextKm, (movingMs / 1000).toInt(), kmSec) + hint)
        lastKmMovingMs = movingMs
        nextKm++
    }

    private fun checkIntervals() {
        if (phases.isEmpty()) return
        val s = _state.value ?: return
        if (s.status != RunStatus.RUNNING) return
        val (next, event) = RunGuidance.advance(phases, intervalState, s.distanceM, s.movingMs)
        intervalState = next
        when (event) {
            is RunGuidance.IntervalEvent.PhaseStarted -> {
                say(RunGuidance.phaseAnnouncement(event.phase))
                vibrate()
            }
            is RunGuidance.IntervalEvent.AlmostDone -> say(RunGuidance.almostDoneAnnouncement(event.phase))
            RunGuidance.IntervalEvent.Finished -> {
                say(RunGuidance.FINISHED_ANNOUNCEMENT)
                vibrate()
            }
            null -> Unit
        }
        _state.value = _state.value?.copy(interval = intervalLive(s.distanceM, s.movingMs))
    }

    private fun intervalLive(distance: Double, movingMs: Long): IntervalLive? {
        if (phases.isEmpty()) return null
        val phase = phases.getOrNull(intervalState.index)
            ?: return IntervalLive("Intervalle geschafft", "auslaufen", isRest = true, done = true)
        val left = RunGuidance.remaining(phases, intervalState, distance, movingMs) ?: 0
        return when (phase) {
            is RunGuidance.Phase.Work -> IntervalLive(
                label = "Intervall ${phase.rep}/${phase.reps} · ${phase.distanceM} m",
                remaining = "$left m",
                isRest = false,
            )
            is RunGuidance.Phase.Rest -> IntervalLive(
                label = "Pause ${phase.rep}/${phase.reps}",
                remaining = "%d:%02d".format(left / 60, left % 60),
                isRest = true,
            )
            is RunGuidance.Phase.Warmup -> IntervalLive(
                label = "Einlaufen · locker",
                remaining = "%d:%02d".format(left / 60, left % 60),
                isRest = true,
            )
            is RunGuidance.Phase.Tempo -> IntervalLive(
                label = "Tempo" + (phase.paceSecPerKm?.let { " · %d:%02d /km".format(it / 60, it % 60) } ?: ""),
                remaining = "%d:%02d".format(left / 60, left % 60),
                isRest = false,
            )
        }
    }

    private fun say(text: String) {
        if (_state.value?.voice != true || !ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, newUuid())
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)) }
    }

    // ---- Notification -----------------------------------------------------------

    private fun goForeground() {
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        val s = _state.value ?: return
        if (s.status == RunStatus.SAVING || s.status == RunStatus.FINISHED) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val paused = s?.status == RunStatus.PAUSED || s?.status == RunStatus.AUTO_PAUSED
        val text = if (s == null) {
            "GPS wird gesucht …"
        } else {
            listOfNotNull(
                formatDuration((s.movingMs / 1000).toInt()),
                formatKmDe(s.distanceM) + " km",
                s.avgPaceSecPerKm?.let { "Ø " + formatPaceDe(it) + " /km" },
            ).joinToString(" · ")
        }
        val toggle = if (paused) {
            Notification.Action.Builder(null, "Weiter", servicePending(ACTION_RESUME, 1)).build()
        } else {
            Notification.Action.Builder(null, "Pause", servicePending(ACTION_PAUSE, 2)).build()
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_run)
            .setContentTitle(
                when (s?.status) {
                    RunStatus.PAUSED -> "Lauf pausiert"
                    RunStatus.AUTO_PAUSED -> "Auto-Pause"
                    else -> "Lauf läuft"
                },
            )
            .setContentText(s?.interval?.let { "${it.label} · noch ${it.remaining} — $text" } ?: text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setContentIntent(launchIntent())
            .addAction(toggle)
            .build()
    }

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, RunTrackingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?.putExtra(EXTRA_OPEN_RUN, true)
            ?: return null
        return PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Laufaufzeichnung", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL = "run_tracking"
        private const val NOTIF_ID = 4801
        private const val MAX_ACCURACY_M = 30f

        private const val ACTION_START = "app.tenet.android.action.RUN_START"
        private const val ACTION_PAUSE = "app.tenet.android.action.RUN_PAUSE"
        private const val ACTION_RESUME = "app.tenet.android.action.RUN_RESUME"
        private const val ACTION_STOP = "app.tenet.android.action.RUN_STOP"
        private const val ACTION_VOICE = "app.tenet.android.action.RUN_VOICE"
        private const val EXTRA_PLANNED = "planned"
        private const val EXTRA_INTERVALS = "intervals"
        private const val EXTRA_PACE = "pace"
        private const val EXTRA_TEMPO = "tempo"
private const val EXTRA_DISTANCE = "distance"
        private const val EXTRA_VOICE = "voice"
        private const val EXTRA_AUTO_PAUSE = "autoPause"

        /** Set on the launch intent from the notification: open the live run. */
        const val EXTRA_OPEN_RUN = "app.tenet.android.extra.OPEN_RUN"

        private val _state = MutableStateFlow<RunLiveState?>(null)

        /** Live run; null when nothing is recorded. */
        val state: StateFlow<RunLiveState?> = _state.asStateFlow()

        fun start(
            context: Context,
            plannedWorkoutId: String?,
            intervalsJson: String?,
            targetPaceSecPerKm: Int?,
            voice: Boolean,
            autoPause: Boolean,
            tempoSec: Int = 0,
            targetDistanceM: Int? = null,
        ) {
            context.startForegroundService(
                Intent(context, RunTrackingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_PLANNED, plannedWorkoutId)
                    .putExtra(EXTRA_INTERVALS, intervalsJson)
                    .putExtra(EXTRA_PACE, targetPaceSecPerKm ?: 0)
                    .putExtra(EXTRA_TEMPO, tempoSec)
                    .putExtra(EXTRA_DISTANCE, targetDistanceM ?: 0)
                    .putExtra(EXTRA_VOICE, voice)
                    .putExtra(EXTRA_AUTO_PAUSE, autoPause),
            )
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun toggleVoice(context: Context) = send(context, ACTION_VOICE)

        /** UI consumed the finished run. */
        fun clear() {
            if (_state.value?.status == RunStatus.FINISHED) _state.value = null
        }

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, RunTrackingService::class.java).setAction(action))
        }
    }
}

internal fun formatKmDe(meters: Double): String = String.format(Locale.GERMAN, "%.2f", meters / 1000.0)

internal fun formatPaceDe(secPerKm: Int): String = "%d:%02d".format(secPerKm / 60, secPerKm % 60)

internal fun formatDuration(sec: Int): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

private fun spokenKm(meters: Double): String = String.format(Locale.GERMAN, "%.1f Kilometer", meters / 1000.0)
