package app.tenet.android.core.data

import app.tenet.android.core.common.ProgressionMath
import app.tenet.android.core.common.RunVolumeMath
import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Progression series of all three disciplines (Sport → Fortschritt). */
data class ProgressionData(
    val gym: List<ProgressionMath.Series>,
    val calisthenics: List<ProgressionMath.Series>,
    val running: List<ProgressionMath.Series>,
)

@Singleton
class ProgressionRepository @Inject constructor(
    private val sportDao: SportDao,
    private val runDao: RunDao,
) {
    fun observe(): Flow<ProgressionData> = combine(
        sportDao.observeProgressionSets(Discipline.GYM),
        sportDao.observeProgressionSets(Discipline.CALISTHENICS),
        runDao.observeRunVolume(0L),
        sportDao.observeBodyMetrics(),
    ) { gym, cali, runs, metrics ->
        val zone = ZoneId.systemDefault()
        val weights = metrics.mapNotNull { m ->
            runCatching { ProgressionMath.Point(java.time.LocalDate.parse(m.date), m.weight) }.getOrNull()
        }
        val strength = ProgressionMath.strength(done(gym))
        ProgressionData(
            // Lifts first, then body weight and strength relative to it.
            gym = strength + listOfNotNull(ProgressionMath.bodyweight(weights)) +
                ProgressionMath.relative(strength, weights).take(4),
            calisthenics = ProgressionMath.calisthenics(done(cali)),
            running = ProgressionMath.running(
                runs.map {
                    RunVolumeMath.Run(
                        date = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate(),
                        distanceM = it.distanceM,
                        durationSec = it.durationSec,
                    )
                },
            ),
        )
    }

    /**
     * Same rule as the overload history: in sessions where something was
     * ticked off only ticked sets count, otherwise every filled-in set.
     */
    private fun done(rows: List<SportDao.ProgressionSetRow>): List<ProgressionMath.SetRecord> =
        rows.filter { row ->
            val filled = row.reps > 0 || (row.durationSec ?: 0) > 0
            filled && (row.completed || !row.sessionHasCompleted)
        }.map {
            ProgressionMath.SetRecord(it.name, it.measureType, it.weight, it.reps, it.durationSec, it.startedAt)
        }
}
