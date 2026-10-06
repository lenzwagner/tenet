package app.tenet.android.feature.sport.calisthenics

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import app.tenet.android.core.data.SkillRepository
import app.tenet.android.core.data.reminder.ReminderScheduler
import app.tenet.android.core.database.entity.CriterionType
import app.tenet.android.core.database.entity.SetEntry
import app.tenet.android.feature.sport.R
import app.tenet.android.feature.sport.RestTimerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A calisthenics skill session as a live notification, like the gym one:
 * skill and step, set x/y with the target, progress per set (Android 16
 * segments), the running hold as a countdown chip in the status bar, and
 * "Satz fertig" to tick the next set without opening the app.
 */
object SkillLiveNotification {

    private const val NOTIF_ID = 4211
    internal const val ACTION_DONE = "app.tenet.android.skill.SET_DONE"
    internal const val EXTRA_SESSION = "session"

    data class Snapshot(
        val sessionId: String,
        val title: String,
        val isHold: Boolean,
        val target: Int,
        val sets: List<SetEntry>,
        /** System time the running hold ends, or null. */
        val holdEndsAt: Long? = null,
    )

    fun show(context: Context, s: Snapshot) {
        RestTimerService.ensureChannel(context, RestTimerService.CHANNEL_TIMER)
        val done = s.sets.count { it.completed }
        val total = s.sets.size.coerceAtLeast(1)
        val unit = if (s.isHold) "${s.target} s halten" else "${s.target} Wdh"
        val builder = Notification.Builder(context, RestTimerService.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setContentIntent(openIntent(context))
            .setContentTitle(s.title)
        val holding = s.holdEndsAt != null && s.holdEndsAt > System.currentTimeMillis()
        if (done >= s.sets.size && s.sets.isNotEmpty()) {
            builder.setContentText("Alle Sätze geschafft · Session in der App beenden")
        } else {
            builder.setContentText("Satz ${done + 1}/$total · Ziel $unit")
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notif_timer),
                        "Satz fertig",
                        PendingIntent.getBroadcast(
                            context,
                            ACTION_DONE.hashCode(),
                            Intent(context, SkillSetDoneReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_SESSION, s.sessionId),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
                )
        }
        if (holding) {
            builder.setWhen(s.holdEndsAt!!).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            // Live Update chip while the hold counts down, otherwise the set number.
            builder.setRequestPromotedOngoing(holding)
            builder.setShortCriticalText(if (holding) "Halten" else "Satz ${(done + 1).coerceAtMost(total)}")
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_notif_timer))
            repeat(total) { style.addProgressSegment(Notification.ProgressStyle.Segment(1)) }
            builder.setStyle(style.setProgress(done))
        } else {
            builder.setProgress(total, done, false)
        }
        manager(context).notify(NOTIF_ID, builder.build())
    }

    fun cancel(context: Context) = manager(context).cancel(NOTIF_ID)

    private fun manager(context: Context) = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun openIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(ReminderScheduler.EXTRA_OPEN, ReminderScheduler.OPEN_SPORT) ?: return null
        return PendingIntent.getActivity(context, NOTIF_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Snapshot straight from the database (receiver, app not open). */
    internal suspend fun load(repository: SkillRepository, sessionId: String): Snapshot? {
        val info = repository.skillSessionInfo(sessionId) ?: return null
        val sets = repository.observeSets(info.sessionExerciseId).first()
        return Snapshot(
            sessionId = sessionId,
            title = "${info.skill.name} · ${info.step.label}",
            isHold = info.step.criterionType == CriterionType.HOLD,
            target = info.step.criterionValue,
            sets = sets,
        )
    }
}

/** "Satz fertig" from the notification: ticks the next open set with its target value. */
@AndroidEntryPoint
class SkillSetDoneReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: SkillRepository

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(SkillLiveNotification.EXTRA_SESSION) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = SkillLiveNotification.load(repository, sessionId) ?: return@launch
                val next = snapshot.sets.firstOrNull { !it.completed } ?: return@launch
                val done = if (snapshot.isHold) {
                    next.copy(completed = true, durationSec = next.durationSec ?: snapshot.target)
                } else {
                    next.copy(completed = true, reps = if (next.reps > 0) next.reps else snapshot.target)
                }
                repository.upsertSet(done)
                val info = repository.skillSessionInfo(sessionId)
                if (info != null) repository.checkAchievement(sessionId, info.step.exerciseId)
                SkillLiveNotification.load(repository, sessionId)?.let { SkillLiveNotification.show(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
