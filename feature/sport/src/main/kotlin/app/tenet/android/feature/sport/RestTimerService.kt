package app.tenet.android.feature.sport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import app.tenet.android.core.common.SetInputParser
import app.tenet.android.core.common.WorkoutGuide
import app.tenet.android.core.data.SportRepository
import app.tenet.android.feature.sport.gym.RestUiState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The workout as a live notification (Android 16 "Live Update", promoted
 * ongoing; plain ongoing notification below): rest countdown with a chip
 * in the status bar, the next set with its planned values, progress over
 * all sets, and actions to log the set right there ("Wie geplant" or
 * "Eintragen" with text input such as "80x8"), add rest or skip it.
 *
 * Runs as a foreground service while a gym session is guided; the rest
 * state is exposed as a process-wide flow for the session screen.
 */
@AndroidEntryPoint
class RestTimerService : Service() {

    @Inject lateinit var sport: SportRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickJob: Job? = null
    private var sessionId: String? = null
    private var totalSec = 0
    private var endAt = 0L // elapsedRealtime of the rest end; 0 = no rest running
    private var next: WorkoutGuide.Next? = null
    private var progress: Pair<List<Int>, Int> = emptyList<Int>() to 0 // sets per exercise, done

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started via startForegroundService(): always go foreground first.
        goForeground()
        intent?.getStringExtra(EXTRA_SESSION)?.let { sessionId = it }
        when (intent?.action) {
            ACTION_START -> startRest(intent.getIntExtra(EXTRA_SECONDS, 0))
            ACTION_GUIDE -> refreshGuide()
            ACTION_ADD -> if (endAt > 0) {
                val add = intent.getIntExtra(EXTRA_SECONDS, 30) * 1000L
                endAt += add
                totalSec += add.toInt() / 1000
                publishRest()
                updateNotification()
            }
            ACTION_SKIP -> endRest(alert = false)
            ACTION_LOG_PLANNED -> logNext(input = null)
            ACTION_LOG_INPUT -> logNext(input = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_INPUT)?.toString())
            else -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }
        if (sessionId == null) stopEverything()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        scope.cancel()
        _state.value = null
        super.onDestroy()
    }

    // ---- State machine: resting ↔ ready for the next set ------------------

    private fun startRest(seconds: Int) {
        if (seconds <= 0) return refreshGuide()
        totalSec = seconds
        endAt = SystemClock.elapsedRealtime() + seconds * 1000L
        publishRest()
        refreshGuide()
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (remainingSec() <= 0) {
                    endRest(alert = true)
                    break
                }
                publishRest()
                updateNotification()
            }
        }
    }

    private fun endRest(alert: Boolean) {
        tickJob?.cancel()
        val wasResting = endAt > 0
        endAt = 0
        totalSec = 0
        _state.value = null
        if (alert && wasResting) notifyDone()
        updateNotification()
    }

    private fun remainingSec(): Int = ((endAt - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)

    private fun publishRest() {
        _state.value = if (endAt > 0) RestUiState(remainingSec(), totalSec) else null
    }

    /** Reloads the session: next set, planned values, progress. */
    private fun refreshGuide() {
        val id = sessionId ?: return
        scope.launch {
            val guide = runCatching { sport.guide(id) }.getOrNull() ?: return@launch
            val blocks = guide.map { it.second }
            next = WorkoutGuide.next(blocks)
            progress = blocks.map { it.sets.size } to blocks.sumOf { b -> b.sets.count { it.completed } }
            updateNotification()
        }
    }

    /** Logs the next open set as planned or with the typed values, then starts its rest. */
    private fun logNext(input: String?) {
        val id = sessionId ?: return
        scope.launch {
            val guide = runCatching { sport.guide(id) }.getOrNull() ?: return@launch
            val target = WorkoutGuide.next(guide.map { it.second }) ?: return@launch refreshGuide()
            val set = guide[target.blockIndex].first.sets.firstOrNull { it.id == target.set.id } ?: return@launch
            val parsed = input?.let { SetInputParser.parse(it, target.timed) }
            if (input != null && parsed == null) {
                // Unreadable input: keep the reply field state honest and ask again.
                updateNotification(hint = "„$input“ nicht verstanden – z. B. 80x8")
                return@launch
            }
            val reps = if (target.timed) null else parsed?.reps ?: target.plannedReps
            sport.completeSet(
                set,
                weightKg = if (target.timed) null else parsed?.weightKg ?: target.plannedKg,
                reps = reps,
                seconds = if (target.timed) parsed?.seconds ?: target.plannedSec else null,
            )
            _changes.tryEmit(id)
            // Rest after this set, from what was actually done (more reps → a bit less rest).
            val rest = WorkoutGuide.restAfter(guide.map { it.second }, target.blockIndex, target.set.id, reps ?: 0)
            val remaining = WorkoutGuide.remaining(guide.map { it.second }) - 1
            if (remaining <= 0) {
                endRest(alert = false)
                refreshGuide()
            } else {
                startRest(rest)
            }
        }
    }

    // ---- Notification -------------------------------------------------------

    private fun goForeground() {
        ensureChannel(this, CHANNEL_TIMER)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun updateNotification(hint: String? = null) {
        if (sessionId == null) return
        manager().notify(NOTIF_ID, buildNotification(hint))
    }

    private fun buildNotification(hint: String? = null): Notification {
        val n = next
        val resting = endAt > 0
        val builder = Notification.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setContentIntent(launchIntent())
        when {
            resting -> {
                val left = remainingSec()
                // Time is in the chronometer (and the status-bar chip), not repeated in the title.
                builder.setContentTitle("Pause")
                    .setContentText(n?.let { "Als Nächstes: ${it.name} · Satz ${it.number}/${it.count} · ${it.plannedText}" } ?: "Gleich geht's weiter")
                    .setWhen(System.currentTimeMillis() + left * 1000L)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(action("+30 s", ACTION_ADD))
                    .addAction(action("Überspringen", ACTION_SKIP))
                if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText(clock(left))
            }
            n != null -> {
                builder.setContentTitle(if (n.isDrop) "${n.name} · Dropsatz" else "${n.name} · Satz ${n.number}/${n.count}")
                    .setContentText(
                        hint ?: when {
                            n.weightUnknown -> "Erstes Mal: Gewicht und Wiederholungen eintragen, z. B. 40x${n.plannedReps}"
                            n.isDrop -> "Sofort weiter: ${n.plannedText}"
                            n.dropNext -> "Geplant: ${n.plannedText} · danach sofort Dropsatz"
                            n.supersetSwitch -> "Geplant: ${n.plannedText} · danach direkt Supersatz-Partner"
                            else -> "Geplant: ${n.plannedText} · danach ${clock(n.restSec)} Pause"
                        },
                    )
                // "Wie geplant" only when there is a plan (weight known).
                if (!n.weightUnknown) builder.addAction(action("Wie geplant", ACTION_LOG_PLANNED))
                builder.addAction(inputAction(n))
                if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText("Satz ${n.number}")
            }
            else -> {
                builder.setContentTitle("Alle Sätze geschafft")
                    .setContentText("Training in der App beenden")
                    .setOngoing(false)
            }
        }
        if (Build.VERSION.SDK_INT >= 36) {
            // Live Update (status-bar chip) while resting; Android hides reply fields
            // in promoted notifications, so the "Eintragen" state stays a normal one.
            builder.setRequestPromotedOngoing(resting)
            builder.setStyle(progressStyle())
        }
        return builder.build()
    }

    /** One segment per exercise, progress = sets done (Android 16 ProgressStyle). */
    private fun progressStyle(): Notification.Style {
        val (perExercise, done) = progress
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressTrackerIcon(Icon.createWithResource(this, R.drawable.ic_notif_timer))
        perExercise.filter { it > 0 }.forEach { style.addProgressSegment(Notification.ProgressStyle.Segment(it)) }
        if (perExercise.sum() == 0) style.addProgressSegment(Notification.ProgressStyle.Segment(1))
        return style.setProgress(done)
    }

    private fun inputAction(n: WorkoutGuide.Next): Notification.Action {
        val choices: Array<CharSequence>? = when {
            n.weightUnknown -> null
            n.timed -> arrayOf("${n.plannedSec ?: 30}", "${(n.plannedSec ?: 30) + 15}")
            n.plannedKg > 0f -> {
                val kg = WorkoutGuide.fmt(n.plannedKg)
                arrayOf("${kg}x${n.plannedReps}", "${kg}x${(n.plannedReps - 1).coerceAtLeast(1)}", "${kg}x${n.plannedReps + 1}")
            }
            else -> arrayOf("${n.plannedReps}", "${(n.plannedReps - 1).coerceAtLeast(1)}", "${n.plannedReps + 1}")
        }
        val input = RemoteInput.Builder(KEY_INPUT)
            .setLabel(if (n.timed) "Sekunden, z. B. 45" else "kg × Wdh, z. B. 80x8")
            .apply { if (choices != null) setChoices(choices) }
            .build()
        val pending = PendingIntent.getForegroundService(
            this,
            ACTION_LOG_INPUT.hashCode(),
            Intent(this, RestTimerService::class.java).setAction(ACTION_LOG_INPUT).putExtra(EXTRA_SESSION, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif_timer), "Eintragen", pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun action(title: String, action: String): Notification.Action {
        val pending = PendingIntent.getForegroundService(
            this,
            action.hashCode(),
            Intent(this, RestTimerService::class.java).setAction(action).putExtra(EXTRA_SESSION, sessionId).putExtra(EXTRA_SECONDS, 30),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif_timer), title, pending).build()
    }

    private fun notifyDone() {
        ensureChannel(this, CHANNEL_DONE)
        val n = next
        val notification = Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setContentTitle("Pause vorbei")
            .setContentText(n?.let { "${it.name} · Satz ${it.number}: ${it.plannedText}" } ?: "Nächster Satz!")
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setContentIntent(launchIntent())
            .build()
        manager().notify(NOTIF_DONE_ID, notification)
    }

    private fun stopEverything() {
        tickJob?.cancel()
        endAt = 0
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        _state.value = null
        stopSelf()
    }

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun manager(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun clock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    companion object {
        /** Default importance (shown on top, expanded, with the input field) but silent. */
        private const val CHANNEL_TIMER = "workout_live"
        private const val OLD_CHANNEL_TIMER = "rest_timer"
        private const val CHANNEL_DONE = "rest_timer_done"
        private const val NOTIF_ID = 4711
        private const val NOTIF_DONE_ID = 4712

        private const val ACTION_START = "app.tenet.android.action.REST_START"
        private const val ACTION_GUIDE = "app.tenet.android.action.WORKOUT_GUIDE"
        private const val ACTION_ADD = "app.tenet.android.action.REST_ADD"
        private const val ACTION_SKIP = "app.tenet.android.action.REST_SKIP"
        private const val ACTION_LOG_PLANNED = "app.tenet.android.action.SET_PLANNED"
        private const val ACTION_LOG_INPUT = "app.tenet.android.action.SET_INPUT"
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_SESSION = "session"
        private const val KEY_INPUT = "set_input"

        private val _state = MutableStateFlow<RestUiState?>(null)

        /** Live countdown state; null when no rest is running. */
        val state: StateFlow<RestUiState?> = _state.asStateFlow()

        private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 8)

        /** Session ids whose sets were logged from the notification (the screen reloads). */
        val changes: SharedFlow<String> = _changes.asSharedFlow()

        /** Rest after a completed set; the notification then shows the next set. */
        fun start(context: Context, totalSec: Int, sessionId: String) {
            context.startForegroundService(
                Intent(context, RestTimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SECONDS, totalSec)
                    .putExtra(EXTRA_SESSION, sessionId),
            )
        }

        /** Shows (or refreshes) the live notification with the next set, no rest. */
        fun guide(context: Context, sessionId: String) {
            context.startForegroundService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_GUIDE).putExtra(EXTRA_SESSION, sessionId),
            )
        }

        fun add(context: Context, seconds: Int) {
            if (_state.value == null) return
            context.startForegroundService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_ADD).putExtra(EXTRA_SECONDS, seconds),
            )
        }

        /** Ends the rest early; the notification switches to the next set. */
        fun skip(context: Context) {
            if (_state.value == null) return
            context.startForegroundService(Intent(context, RestTimerService::class.java).setAction(ACTION_SKIP))
        }

        /** Removes the notification (session finished or discarded). */
        fun cancel(context: Context) {
            context.stopService(Intent(context, RestTimerService::class.java))
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIF_ID)
            manager.cancel(NOTIF_DONE_ID)
            _state.value = null
        }

        private fun ensureChannel(context: Context, id: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(id) != null) return
            // The old low-importance channel sat folded under "Lautlos".
            manager.deleteNotificationChannel(OLD_CHANNEL_TIMER)
            val channel = NotificationChannel(
                id,
                if (id == CHANNEL_TIMER) "Training & Pausen" else "Pause vorbei",
                if (id == CHANNEL_TIMER) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH,
            )
            if (id == CHANNEL_TIMER) {
                channel.setSound(null, null)
                channel.enableVibration(false)
                channel.description = "Pausen-Countdown und nächster Satz während des Trainings"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
