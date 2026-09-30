package app.tenet.android.core.data.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Journal reminders (App_Konzept.md 5.3 Traumtagebuch): a morning prompt to
 * note last night's dream and optional reality checks during the day for
 * lucid-dream training. Both run as periodic WorkManager jobs.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Daily dream reminder at [minuteOfDay] (0..1439); off when [enabled] is false. */
    fun scheduleDreamReminder(enabled: Boolean, minuteOfDay: Int) {
        scheduleDaily<DreamReminderWorker>(DREAM_WORK, enabled, minuteOfDay)
    }

    /** Morning training reminder on days with a planned unit. */
    fun scheduleTrainingReminder(enabled: Boolean, minuteOfDay: Int) {
        scheduleDaily<TrainingReminderWorker>(TRAINING_WORK, enabled, minuteOfDay)
    }

    private inline fun <reified W : androidx.work.ListenableWorker> scheduleDaily(name: String, enabled: Boolean, minuteOfDay: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val target = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        // Re-enqueue so a changed time applies.
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    /** Reality checks roughly every 3 h between 9:00 and 21:00. */
    fun scheduleRealityChecks(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(REALITY_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<RealityCheckWorker>(3, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(REALITY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        private const val DREAM_WORK = "journal_dream_reminder"
        private const val REALITY_WORK = "journal_reality_check"
        private const val TRAINING_WORK = "sport_training_reminder"

        /** Intent extra MainActivity reads to open the dream editor. */
        const val EXTRA_OPEN = "tenet_open"
        const val OPEN_DREAM = "dream"
        const val OPEN_SPORT = "sport"
        /** Launcher shortcuts (res/xml/shortcuts.xml). */
        const val OPEN_NOTE = "note"
        const val OPEN_MEAL = "meal"
    }
}
