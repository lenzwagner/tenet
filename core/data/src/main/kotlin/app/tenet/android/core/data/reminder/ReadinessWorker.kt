package app.tenet.android.core.data.reminder

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tenet.android.core.common.Readiness
import app.tenet.android.core.data.health.ReadinessRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReadinessEntryPoint {
    fun readiness(): ReadinessRepository
}

/**
 * Morning report like Google Health / Fitbit: readiness score with resting
 * pulse, HRV and sleep. Once a day; if the watch has not synced last night
 * yet, it tries again every 45 minutes until 11:00.
 */
class ReadinessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("readiness", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST, null) == today) return Result.success()
        val deps = EntryPointAccessors.fromApplication(applicationContext, ReadinessEntryPoint::class.java)
        val now = deps.readiness().today()
        if (now == null || now.input.sleep == null) {
            if (LocalTime.now().isBefore(LocalTime.of(11, 0))) {
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    RETRY_WORK,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ReadinessWorker>().setInitialDelay(45, TimeUnit.MINUTES).build(),
                )
            }
            return Result.success()
        }
        val (title, text) = Readiness.notification(now.result)
        notify(applicationContext, id = 4104, title = title, text = text, open = ReminderScheduler.OPEN_TODAY, channel = READINESS_CHANNEL)
        prefs.edit { putString(KEY_LAST, today) }
        return Result.success()
    }

    companion object {
        private const val KEY_LAST = "last_notified"
        private const val RETRY_WORK = "readiness_retry"
    }
}
