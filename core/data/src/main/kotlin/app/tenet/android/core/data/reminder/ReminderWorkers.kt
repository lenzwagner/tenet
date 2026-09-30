package app.tenet.android.core.data.reminder

import app.tenet.android.core.data.R
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalTime

private const val CHANNEL_ID = "journal_reminders"
internal const val TRAINING_CHANNEL = "training_reminders"

private val RealityChecks = listOf(
    "Träumst du gerade? Schau zweimal auf eine Uhr oder einen Text.",
    "Reality-Check: Halte dir die Nase zu und versuch zu atmen.",
    "Zähl deine Finger. Sind es wirklich fünf?",
    "Wie bist du hierher gekommen? Erinnerst du dich an den Weg?",
    "Drück einen Finger gegen die Handfläche. Geht er hindurch?",
)

/** Morning prompt: "Hast du heute geträumt?" – opens the dream editor. */
class DreamReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        notify(
            context = applicationContext,
            id = 4101,
            title = "Hast du heute geträumt?",
            text = "Halte deinen Traum fest, bevor er verblasst.",
            open = ReminderScheduler.OPEN_DREAM,
        )
        return Result.success()
    }
}

/** Daytime reality check for lucid-dream training (quiet at night). */
class RealityCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val hour = LocalTime.now().hour
        if (hour in 9..20) {
            notify(
                context = applicationContext,
                id = 4102,
                title = "Reality-Check",
                text = RealityChecks.random(),
                open = null,
            )
        }
        return Result.success()
    }
}

internal fun notify(context: Context, id: Int, title: String, text: String, open: String?, channel: String = CHANNEL_ID) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(channel) == null) {
        val name = if (channel == TRAINING_CHANNEL) "Trainings-Erinnerungen" else "Journal-Erinnerungen"
        manager.createNotificationChannel(NotificationChannel(channel, name, NotificationManager.IMPORTANCE_DEFAULT))
    }
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        // Reach a running instance via onNewIntent instead of just resuming it.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (open != null) putExtra(ReminderScheduler.EXTRA_OPEN, open)
    }
    val pending = launch?.let {
        PendingIntent.getActivity(
            context,
            id,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notif_journal)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
}
