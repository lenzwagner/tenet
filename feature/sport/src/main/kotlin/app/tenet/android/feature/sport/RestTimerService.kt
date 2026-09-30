package app.tenet.android.feature.sport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.tenet.android.feature.sport.gym.RestUiState
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

/**
 * Foreground service for the rest timer (App_Konzept.md 5.2.1: "Timer
 * läuft als Foreground Service mit Live-Notification, damit er auch bei
 * gesperrtem Display funktioniert").
 *
 * Commands are sent as start intents; the countdown state is exposed as a
 * process-wide flow that [app.tenet.android.feature.sport.gym.ActiveSessionViewModel]
 * collects.
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickJob: Job? = null
    private var totalSec = 0
    private var remainingSec = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                totalSec = intent.getIntExtra(EXTRA_SECONDS, 0).coerceAtLeast(0)
                remainingSec = totalSec
                goForeground()
                startTicking()
            }

            ACTION_ADD -> {
                // startForegroundService() was used: always go foreground first.
                goForeground()
                if (totalSec <= 0) {
                    stopEverything()
                    return START_NOT_STICKY
                }
                remainingSec += intent.getIntExtra(EXTRA_SECONDS, 0)
                totalSec = maxOf(totalSec, remainingSec)
                updateNotification()
                startTicking()
            }

            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        _state.value = null
        super.onDestroy()
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        _state.value = RestUiState(remainingSec, totalSec)
        tickJob = scope.launch {
            while (isActive && remainingSec > 0) {
                delay(1_000)
                remainingSec--
                _state.value = RestUiState(remainingSec, totalSec)
                updateNotification()
            }
            if (remainingSec <= 0) {
                notifyDone()
                stopEverything()
            }
        }
    }

    private fun goForeground() {
        ensureChannel(this, CHANNEL_TIMER)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildTimerNotification(), type)
    }

    private fun updateNotification() {
        if (remainingSec <= 0) return
        manager().notify(NOTIF_ID, buildTimerNotification())
    }

    private fun buildTimerNotification(): Notification {
        val remaining = "%d:%02d".format(remainingSec / 60, remainingSec % 60)
        val total = "%d:%02d".format(totalSec / 60, totalSec % 60)
        return Notification.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setContentTitle("Pausentimer")
            .setContentText("Noch $remaining von $total")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchIntent())
            .build()
    }

    private fun notifyDone() {
        ensureChannel(this, CHANNEL_DONE)
        val notification = Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setContentTitle("Pause vorbei")
            .setContentText("Nächster Satz!")
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .build()
        manager().notify(NOTIF_DONE_ID, notification)
    }

    private fun stopEverything() {
        tickJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        _state.value = null
        stopSelf()
    }

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun manager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_TIMER = "rest_timer"
        private const val CHANNEL_DONE = "rest_timer_done"
        private const val NOTIF_ID = 4711
        private const val NOTIF_DONE_ID = 4712

        private const val ACTION_START = "app.tenet.android.action.REST_START"
        private const val ACTION_ADD = "app.tenet.android.action.REST_ADD"
        private const val ACTION_CANCEL = "app.tenet.android.action.REST_CANCEL"
        private const val EXTRA_SECONDS = "seconds"

        private val _state = MutableStateFlow<RestUiState?>(null)

        /** Live countdown state; null when no timer is running. */
        val state: StateFlow<RestUiState?> = _state.asStateFlow()

        fun start(context: Context, totalSec: Int) {
            val intent = Intent(context, RestTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SECONDS, totalSec)
            context.startForegroundService(intent)
        }

        fun add(context: Context, seconds: Int) {
            val intent = Intent(context, RestTimerService::class.java)
                .setAction(ACTION_ADD)
                .putExtra(EXTRA_SECONDS, seconds)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.stopService(Intent(context, RestTimerService::class.java))
            _state.value = null
        }

        private fun ensureChannel(context: Context, id: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (manager.getNotificationChannel(id) != null) return
            val channel = NotificationChannel(
                id,
                if (id == CHANNEL_TIMER) "Pausentimer" else "Pausentimer-Fertig",
                if (id == CHANNEL_TIMER) {
                    NotificationManager.IMPORTANCE_LOW
                } else {
                    NotificationManager.IMPORTANCE_DEFAULT
                },
            )
            manager.createNotificationChannel(channel)
        }
    }
}
