package app.tenet.android.core.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tenet.android.core.common.RunWorkoutStructure
import app.tenet.android.core.common.RunZone
import app.tenet.android.core.common.TrainingReminderText
import app.tenet.android.core.data.RunningRepository
import app.tenet.android.core.data.SportRepository
import app.tenet.android.core.data.WeekCalendarRepository
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TrainingReminderEntryPoint {
    fun weekCalendar(): WeekCalendarRepository
    fun running(): RunningRepository
    fun sport(): SportRepository
    fun sportDao(): SportDao
}

/**
 * Morning reminder on training days, like Runna: "Heute: Intervalle 3 × 1000 m".
 * Silent on rest days, for skipped units and when today's unit is already done.
 */
class TrainingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, TrainingReminderEntryPoint::class.java)
        val units = todaysUnits(deps)
        val (title, text) = TrainingReminderText.notification(units) ?: return Result.success()
        notify(applicationContext, id = 4103, title = title, text = text, open = ReminderScheduler.OPEN_SPORT, channel = TRAINING_CHANNEL)
        return Result.success()
    }

    private suspend fun todaysUnits(deps: TrainingReminderEntryPoint): List<TrainingReminderText.Unit> {
        val today = LocalDate.now()
        val planned = deps.weekCalendar().observePlannedBetween(today, today).first().filter { !it.skipped }
        val doneToday = deps.weekCalendar().observeSessionsBetween(today, today.plusDays(1)).first()
            .filter { it.session.endedAt != null }
            .map { it.session.discipline }
            .toSet()
        return planned
            .filter { it.discipline !in doneToday }
            .distinctBy { if (it.discipline == Discipline.RUNNING) it.id else it.discipline.name }
            .mapNotNull { p ->
                when (p.discipline) {
                    Discipline.RUNNING -> {
                        val run = deps.running().plannedRun(p.id)?.second ?: return@mapNotNull null
                        val intervals = RunWorkoutStructure.parseIntervals(run.intervalsJson)
                        TrainingReminderText.Unit.Run(
                            zone = runCatching { RunZone.valueOf(run.runType.name) }.getOrDefault(RunZone.EASY),
                            distanceM = run.targetDistanceM,
                            durationSec = run.targetDurationSec,
                            intervalReps = intervals?.reps,
                            intervalLengthM = intervals?.lengthM,
                            paceSecPerKm = run.targetPaceSecPerKm,
                        )
                    }
                    Discipline.GYM -> {
                        // Undated gym plans rotate: remind of the workout that is actually next.
                        val next = deps.sport().nextWorkoutOnce(p.planId) ?: p
                        TrainingReminderText.Unit.Gym(next.title, deps.sportDao().routineOnce(next.id).size)
                    }
                    Discipline.CALISTHENICS ->
                        TrainingReminderText.Unit.Cali("", deps.sportDao().routineOnce(p.id).size)
                }
            }
    }
}
