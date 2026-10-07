package app.tenet.android.feature.sport.run

import app.tenet.android.core.common.PaceAnchor
import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.common.RunPaceMath
import app.tenet.android.core.common.RunPlanMatcher
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.common.RunWorkoutStructure
import app.tenet.android.core.common.RunZone
import app.tenet.android.core.data.RunningOverview
import app.tenet.android.core.database.dao.RunDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Status of a planned run, like Runna's week view. */
enum class UnitStatus { DONE, TODAY, MISSED, PLANNED, SKIPPED }

data class PlanRunUnit(
    val id: String,
    val week: Int,
    val dayIndex: Int,
    val date: LocalDate,
    val title: String,
    val zone: RunZone,
    val race: Boolean,
    val status: UnitStatus,
    val workout: RunWorkoutStructure.Workout,
    val targetPaceSecPerKm: Int?,
    /** The run that completed it (linked or same-day match). */
    val doneRun: RunDao.RunSessionRow?,
)

data class PlanWeekUi(
    val index: Int,
    val start: LocalDate,
    val units: List<PlanRunUnit>,
) {
    val plannedKm: Float get() = units.filter { it.status != UnitStatus.SKIPPED }.sumOf { it.workout.estDistanceM.toDouble() }.toFloat() / 1000f
    val doneKm: Float get() = units.mapNotNull { it.doneRun }.sumOf { it.run.distanceM.toDouble() }.toFloat() / 1000f
    val doneCount: Int get() = units.count { it.status == UnitStatus.DONE }
}

/** Builds the week-by-week plan view (dates, status, structure, matched runs). */
object RunPlanUiBuilder {

    fun build(overview: RunningOverview, method: PaceMethod, today: LocalDate = LocalDate.now()): List<PlanWeekUi> {
        val plan = overview.plan ?: return emptyList()
        val start = plan.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
        val goalDate = plan.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val weeks = goalDate?.let { RunPlanMath.weekCount(start, it) } ?: ((overview.plannedUnits.maxOfOrNull { it.planned.weekIndex ?: 0 } ?: 0) + 1)
        val anchor = overview.detail?.current5kSec?.let { PaceAnchor(5_000, it) }
        val paces = RunWorkoutStructure.Paces(
            easy = anchor?.let { RunPaceMath.targetPaceSecPerKm(RunZone.EASY, it, method) },
            recovery = anchor?.let { RunPaceMath.targetPaceSecPerKm(RunZone.RECOVERY, it, method) },
            // Goal race pace for race-pace finishes: the wish time, else the prediction from the form.
            race = RunPlanMath.raceDistanceM(RunPlanMath.RunGoal.fromName(overview.detail?.goalId))?.let { dist ->
                val time = overview.detail?.targetTimeSec ?: anchor?.let { app.tenet.android.core.common.RacePrediction.riegel(5_000, it.timeSec, dist) }
                time?.let { it * 1000 / dist }
            },
        )
        val zone = ZoneId.systemDefault()
        val units = overview.plannedUnits.mapNotNull { row ->
            val week = row.planned.weekIndex ?: return@mapNotNull null
            val day = row.planned.dayIndex ?: 0
            Triple(row, week, start.plusWeeks(week.toLong()).plusDays(day.toLong()))
        }
        val runs = overview.planRuns
        val matches = RunPlanMatcher.match(
            planned = units.map { (row, _, date) -> RunPlanMatcher.Planned(row.planned.id, date, row.planned.skipped) },
            runs = runs.map { r ->
                RunPlanMatcher.Run(r.session.id, Instant.ofEpochMilli(r.session.startedAt).atZone(zone).toLocalDate(), r.session.plannedWorkoutId)
            },
        )
        val runsById = runs.associateBy { it.session.id }
        val built = units.map { (row, week, date) ->
            val rw = row.runWorkout
            val runZone = rw?.runType?.let { runCatching { RunZone.valueOf(it.name) }.getOrNull() } ?: RunZone.EASY
            val race = row.planned.title.startsWith("Wettkampf")
            val done = matches[row.planned.id]?.let { runsById[it] }
            PlanRunUnit(
                id = row.planned.id,
                week = week,
                dayIndex = row.planned.dayIndex ?: 0,
                date = date,
                title = row.planned.title,
                zone = runZone,
                race = race,
                status = when {
                    done != null -> UnitStatus.DONE
                    row.planned.skipped -> UnitStatus.SKIPPED
                    date == today -> UnitStatus.TODAY
                    date.isBefore(today) -> UnitStatus.MISSED
                    else -> UnitStatus.PLANNED
                },
                workout = RunWorkoutStructure.build(
                    zone = runZone,
                    targetDurationSec = rw?.targetDurationSec,
                    targetDistanceM = rw?.targetDistanceM,
                    targetPaceSecPerKm = rw?.targetPaceSecPerKm,
                    intervalsJson = rw?.intervalsJson,
                    paces = paces,
                    race = race,
                ),
                targetPaceSecPerKm = rw?.targetPaceSecPerKm,
                doneRun = done,
            )
        }
        return (0 until weeks).map { w ->
            PlanWeekUi(w, start.plusWeeks(w.toLong()), built.filter { it.week == w }.sortedBy { it.dayIndex })
        }
    }

    /** Today's unit if there is one, else the next open one. */
    fun nextUnit(weeks: List<PlanWeekUi>, today: LocalDate = LocalDate.now()): PlanRunUnit? {
        val all = weeks.flatMap { it.units }
        return all.firstOrNull { it.date == today && it.status != UnitStatus.SKIPPED }
            ?: all.filter { it.status == UnitStatus.PLANNED }.minByOrNull { it.date }
    }
}
