package app.tenet.android.core.data

import app.tenet.android.core.common.Gpx

import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.time.LocalDateTime
import app.tenet.android.core.database.entity.WorkoutSession
import app.tenet.android.core.database.entity.RunTrackPoint
import app.tenet.android.core.database.entity.RunSplit
import app.tenet.android.core.database.entity.RunSource
import app.tenet.android.core.database.entity.RunSession
import app.tenet.android.core.database.entity.PersonalBest
import app.tenet.android.core.common.RunAnalysis
import app.tenet.android.core.common.RunPlanMatcher
import app.tenet.android.core.common.PaceAnchor
import app.tenet.android.core.common.PaceMethod
import app.tenet.android.core.common.RunPaceMath
import app.tenet.android.core.common.RunPlanMath
import app.tenet.android.core.common.RunZone
import app.tenet.android.core.common.RunVolumeMath
import java.time.Instant
import java.time.ZoneId
import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RunPlanDetail
import app.tenet.android.core.database.entity.RunPlanWorkout
import app.tenet.android.core.database.entity.RunType
import app.tenet.android.core.database.entity.TrainingPlan
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** One unit of a running plan with its resolved date. */
data class PlanUnitUi(
    val id: String,
    val date: LocalDate,
    val weekIndex: Int,
    val title: String,
    val runType: RunType?,
    val targetDistanceM: Int?,
    val targetDurationSec: Int?,
    val targetPaceSecPerKm: Int?,
    val skipped: Boolean = false,
) {
    val isRace: Boolean get() = title.startsWith("Wettkampf")
}

/** Raw data of the running plan detail screen. */
data class RunPlanData(
    val plan: TrainingPlan,
    val detail: RunPlanDetail?,
    val units: List<PlanUnitUi>,
    /** Finished runs since the plan start, newest first. */
    val runs: List<RunDao.RunSessionRow>,
    /** Best efforts of all runs (for the prognosis). */
    val efforts: List<PersonalBest>,
)

/** Run detail: result row, splits, this run's best efforts, record holders. */
data class RunDetail(
    val row: RunDao.RunSessionRow,
    val splits: List<RunSplit>,
    val efforts: List<PersonalBest>,
    /** distance → session id of the current record. */
    val recordSessionIds: Map<Int, String>,
)

/** Everything the running page needs, precomputed off the UI thread. */
data class RunningOverview(
    val plan: TrainingPlan? = null,
    val detail: RunPlanDetail? = null,
    /** Recurring units of the active plan (weekIndex + dayIndex based). */
    val plannedUnits: List<SportDao.RecurringPlanned> = emptyList(),
    /** Recent recorded runs (empty until GPS tracking lands). */
    val runs: List<RunDao.RunSessionRow> = emptyList(),
    /** All finished runs since the plan started (for plan progress). */
    val planRuns: List<RunDao.RunSessionRow> = emptyList(),
)

/**
 * Running plan structure (App_Konzept.md 5.2.3): goal templates, weekly
 * run-type structure with moderate progression, target paces from the
 * chosen method. The active plan is anchored at its start Monday; the week
 * calendar resolves weekIndex/dayIndex against that anchor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RunningRepository @Inject constructor(
    private val runDao: RunDao,
    private val sportDao: SportDao,
    private val weekCalendarRepository: WeekCalendarRepository,
    private val healthWriter: app.tenet.android.core.data.health.HealthWriter,
) {
    // ---- Plan creation ----------------------------------------------------

    /**
     * Replaces the active running plan with a generated one. Returns the
     * new plan id, or null when the range is degenerate.
     */
    suspend fun createPlan(
        goal: RunPlanMath.RunGoal,
        goalDate: LocalDate,
        runsPerWeek: Int,
        current5kSec: Int?,
        paceMethod: PaceMethod,
        taper: Boolean = true,
        targetTimeSec: Int? = null,
        /** Chosen weekdays 0 (Mo) … 6 (So), one per run; null = default days. */
        days: List<Int>? = null,
        volume: Float = 1f,
    ): String {
        sportDao.deactivatePlans(Discipline.RUNNING)

        val planStart = WeekMath.weekStart(LocalDate.now())
        val planId = newUuid()
        sportDao.upsertPlan(
            TrainingPlan(
                id = planId,
                discipline = Discipline.RUNNING,
                name = goal.label,
                goal = goal.name,
                startDate = planStart.toString(),
                endDate = goalDate.toString(),
                active = true,
            ),
        )
        runDao.upsertPlanDetail(
            RunPlanDetail(
                planId = planId,
                goalId = goal.name,
                runsPerWeek = runsPerWeek.coerceIn(2, 6),
                current5kSec = current5kSec,
                paceMethodId = paceMethod.id,
                targetTimeSec = targetTimeSec,
                taper = taper,
            ),
        )

        val weeks = RunPlanMath.weekCount(planStart, goalDate)
        val template = RunPlanMath.weeklyTemplate(runsPerWeek)
        val anchor = current5kSec?.let { PaceAnchor(distanceM = 5000, timeSec = it) }
        val goalIsMarathon = goal == RunPlanMath.RunGoal.MARATHON

        val planned = mutableListOf<PlannedWorkout>()
        val details = mutableListOf<RunPlanWorkout>()
        for (week in 0 until weeks) {
            val units = RunPlanMath.planWeek(goal, week, runsPerWeek, totalWeeks = weeks, taper = taper, days = days, volume = volume)
            template.forEachIndexed { order, _ ->
                val unit = units[order]
                val plannedId = newUuid()
                planned += PlannedWorkout(
                    id = plannedId,
                    planId = planId,
                    discipline = Discipline.RUNNING,
                    date = null,
                    weekIndex = week,
                    dayIndex = unit.dayIndex,
                    title = unit.title,
                    sortOrder = order,
                )
                details += RunPlanWorkout(
                    plannedWorkoutId = plannedId,
                    runType = unit.zone.toRunType(),
                    targetDistanceM = unit.targetDistanceM,
                    targetDurationSec = unit.targetDurationSec,
                    targetPaceSecPerKm = anchor?.let {
                        RunPaceMath.targetPaceSecPerKm(
                            zone = unit.zone,
                            anchor = it,
                            method = paceMethod,
                            goalIsMarathon = goalIsMarathon,
                        )
                    },
                    intervalsJson = unit.intervalsJson,
                )
            }
        }
        // Race day: the goal race replaces that day's unit in the final week.
        RunPlanMath.raceDistanceM(goal)?.let { distance ->
            val raceWeek = weeks - 1
            val raceDay = goalDate.dayOfWeek.value - 1
            val raceIndex = planned.indexOfFirst { it.weekIndex == raceWeek && it.dayIndex == raceDay }
            val raceId = if (raceIndex >= 0) planned[raceIndex].id else newUuid()
            val race = PlannedWorkout(
                id = raceId,
                planId = planId,
                discipline = Discipline.RUNNING,
                date = null,
                weekIndex = raceWeek,
                dayIndex = raceDay,
                title = "Wettkampf · ${goal.label}",
                sortOrder = 99,
            )
            val detail = RunPlanWorkout(
                plannedWorkoutId = raceId,
                runType = RunType.TEMPO,
                targetDistanceM = distance,
                targetPaceSecPerKm = targetTimeSec?.let { (it * 1000.0 / distance).roundToInt() },
            )
            if (raceIndex >= 0) {
                planned[raceIndex] = race
                details.removeAll { it.plannedWorkoutId == raceId }
            } else {
                planned += race
            }
            details += detail
        }
        planned.forEach { sportDao.upsertWorkout(it) }
        runDao.upsertRunPlanWorkouts(details)
        return planId
    }

    // ---- Plan adjustments (Runna-style) ---------------------------------------

    /**
     * Re-anchors the remaining plan on the current form: new 5 km anchor,
     * optional new goal time, and recomputed target paces for every unit
     * from today on (past units keep their paces for comparison).
     */
    suspend fun adjustPlanToForm(planId: String, form5kSec: Int, newTargetSec: Int?) {
        val detail = runDao.planDetailOnce(planId) ?: return
        val plan = sportDao.activePlanOnce(Discipline.RUNNING)?.takeIf { it.id == planId } ?: return
        val target = newTargetSec ?: detail.targetTimeSec
        runDao.upsertPlanDetail(detail.copy(current5kSec = form5kSec, targetTimeSec = target))

        val start = LocalDate.parse(plan.startDate)
        val today = LocalDate.now()
        val anchor = PaceAnchor(distanceM = 5000, timeSec = form5kSec)
        val method = PaceMethod.fromId(detail.paceMethodId)
        val goal = runCatching { RunPlanMath.RunGoal.valueOf(detail.goalId) }.getOrNull()
        val raceDistance = goal?.let { RunPlanMath.raceDistanceM(it) }
        val updated = sportDao.workoutsOnce(planId).mapNotNull { w ->
            val date = w.date?.let(LocalDate::parse) ?: start.plusDays((w.weekIndex ?: 0) * 7L + (w.dayIndex ?: 0))
            if (date.isBefore(today)) return@mapNotNull null
            val rw = runDao.runPlanWorkoutOnce(w.id) ?: return@mapNotNull null
            val pace = when {
                w.title.startsWith("Wettkampf") && raceDistance != null ->
                    target?.let { (it * 1000.0 / raceDistance).roundToInt() }
                else -> rw.runType?.let {
                    RunPaceMath.targetPaceSecPerKm(
                        zone = RunZone.valueOf(it.name),
                        anchor = anchor,
                        method = method,
                        goalIsMarathon = goal == RunPlanMath.RunGoal.MARATHON,
                    )
                }
            }
            rw.copy(targetPaceSecPerKm = pace ?: rw.targetPaceSecPerKm)
        }
        runDao.upsertRunPlanWorkouts(updated)
    }


    suspend fun movePlannedRun(plannedId: String, dayIndex: Int) = sportDao.movePlannedDay(plannedId, dayIndex.coerceIn(0, 6))

    suspend fun setPlannedRunSkipped(plannedId: String, skipped: Boolean) = sportDao.setPlannedSkipped(plannedId, skipped)

    // ---- Reads --------------------------------------------------------------

    /** Active running plan with detail and (recurring) planned units. */
    fun observeOverview(): Flow<RunningOverview> =
        combine(
            sportDao.observeActivePlan(Discipline.RUNNING),
            sportDao.observeRecurringPlanned(),
            runDao.observeRecentRuns(),
        ) { plan, recurring, runs -> PlanShell(plan, recurring, runs) }
            .flatMapLatest { shell ->
                val plan = shell.plan
                if (plan == null) {
                    flowOf(RunningOverview(runs = shell.runs))
                } else {
                    val from = plan.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
                    combine(runDao.observePlanDetail(plan.id), runDao.observeRunsSince(from)) { detail, planRuns ->
                        RunningOverview(
                            plan = plan,
                            detail = detail,
                            plannedUnits = shell.recurring
                                .filter { it.planned.planId == plan.id },
                            runs = shell.runs,
                            planRuns = planRuns,
                        )
                    }
                }
            }

    /** Finished runs of roughly the last half year (enough for 8 weeks / 6 months). */
    fun observeVolumeRuns(today: LocalDate = LocalDate.now()): Flow<List<RunVolumeMath.Run>> {
        val zone = ZoneId.systemDefault()
        val from = today.withDayOfMonth(1).minusMonths(6).atStartOfDay(zone).toInstant().toEpochMilli()
        return runDao.observeRunVolume(from).map { rows ->
            rows.map {
                RunVolumeMath.Run(
                    date = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate(),
                    distanceM = it.distanceM,
                    durationSec = it.durationSec,
                )
            }
        }
    }

    // ---- Recorded runs -------------------------------------------------------

    /** Everything the running plan detail screen needs. */
    fun observeRunPlan(planId: String): Flow<RunPlanData?> =
        sportDao.observePlan(planId).flatMapLatest { plan ->
            if (plan == null) return@flatMapLatest flowOf(null)
            val start = plan.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            val fromMs = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            combine(
                runDao.observePlanDetail(planId),
                sportDao.observePlanUnits(planId),
                runDao.observeRunsSince(fromMs),
                runDao.observePersonalBests(),
            ) { detail, units, runs, efforts ->
                RunPlanData(
                    plan = plan,
                    detail = detail,
                    units = units.mapNotNull { row ->
                        val w = row.planned.weekIndex ?: return@mapNotNull null
                        val d = row.planned.dayIndex ?: return@mapNotNull null
                        PlanUnitUi(
                            id = row.planned.id,
                            date = WeekMath.weekStart(start).plusWeeks(w.toLong()).plusDays(d.toLong()),
                            weekIndex = w,
                            title = row.planned.title,
                            runType = row.runWorkout?.runType,
                            targetDistanceM = row.runWorkout?.targetDistanceM,
                            targetDurationSec = row.runWorkout?.targetDurationSec,
                            targetPaceSecPerKm = row.runWorkout?.targetPaceSecPerKm,
                            skipped = row.planned.skipped,
                        )
                    }.sortedBy { it.date },
                    runs = runs,
                    efforts = efforts,
                )
            }
        }

    /** Records per standard distance (fastest effort of all runs). */
    fun observeRecords(): Flow<List<PersonalBest>> = runDao.observeRecords()

    /** Everything the run detail screen shows except the (large) track. */
    fun observeRunDetail(sessionId: String): Flow<RunDetail?> = combine(
        runDao.observeRun(sessionId),
        runDao.observeSplits(sessionId),
        runDao.observePersonalBestsOfSession(sessionId),
        runDao.observeRecords(),
    ) { row, splits, efforts, records ->
        row?.let {
            RunDetail(
                row = it,
                splits = splits,
                efforts = efforts,
                recordSessionIds = records.associate { r -> r.distanceM to r.sessionId },
            )
        }
    }

    suspend fun trackOnce(sessionId: String): List<RunAnalysis.Point> =
        runDao.trackPointsOnce(sessionId).map { it.toPoint() }

    fun observeTrack(sessionId: String): Flow<List<RunAnalysis.Point>> =
        runDao.observeTrackPoints(sessionId).map { list -> list.map { it.toPoint() } }

    /**
     * Manual run (treadmill, forgotten watch): finished session + result,
     * linked to the planned unit of that day, best efforts if the distance
     * hits a standard one. Returns the session id.
     */
    suspend fun addManualRun(
        start: LocalDateTime,
        distanceM: Float,
        durationSec: Int,
        avgHr: Int?,
        notes: String,
    ): String {
        val id = newUuid()
        val startMs = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        sportDao.insertSession(
            WorkoutSession(
                id = id,
                discipline = Discipline.RUNNING,
                plannedWorkoutId = matchPlannedRun(startMs, catchUp = true),
                startedAt = startMs,
                endedAt = startMs + durationSec * 1000L,
                notes = notes,
            ),
        )
        runDao.upsertRunSession(
            RunSession(
                sessionId = id,
                distanceM = distanceM,
                durationSec = durationSec,
                avgPaceSecPerKm = paceOf(distanceM, durationSec),
                avgHr = avgHr,
                source = RunSource.MANUAL,
            ),
        )
        saveEfforts(id, RunAnalysis.wholeRunEfforts(distanceM, durationSec), startMs)
        healthWriter.writeWorkout(id)
        return id
    }

    /**
     * Imports a GPX track (Strava, Garmin …) as a finished run with splits and
     * best efforts. Null when the file has no timed track or the run is too short.
     */
    suspend fun importGpx(xml: String): String? {
        val track = Gpx.parse(xml)
        val points = track.points
        if (points.size < 2) return null
        val summary = RunAnalysis.summary(points)
        if (summary.distanceM < 100f || summary.durationSec <= 0) return null
        val startMs = points.first().time
        // Same start as an existing run: return that one instead of a duplicate.
        sportDao.observeSessionsBetween(startMs - 60_000L, startMs + 60_000L).first()
            .firstOrNull { it.session.discipline == Discipline.RUNNING }
            ?.let { return it.session.id }
        val id = newUuid()
        sportDao.insertSession(
            WorkoutSession(
                id = id,
                discipline = Discipline.RUNNING,
                plannedWorkoutId = matchPlannedRun(startMs, catchUp = true),
                startedAt = startMs,
                endedAt = points.last().time,
                notes = track.name.orEmpty(),
            ),
        )
        runDao.insertTrackPoints(
            points.map { RunTrackPoint(newUuid(), id, it.time, it.lat, it.lon, it.altitude, it.hr) },
        )
        runDao.upsertRunSession(
            RunSession(
                sessionId = id,
                distanceM = summary.distanceM,
                durationSec = summary.durationSec,
                avgPaceSecPerKm = paceOf(summary.distanceM, summary.durationSec),
                avgHr = summary.avgHr,
                elevationGainM = summary.elevationGainM,
                source = RunSource.GPX,
            ),
        )
        analyzeTrack(id, points)
        healthWriter.writeWorkout(id)
        return id
    }

    /** GPX file content of a run with a track; null without GPS points. */
    suspend fun exportGpx(sessionId: String): String? {
        val points = trackOnce(sessionId)
        if (points.size < 2) return null
        val session = sportDao.sessionOnce(sessionId)
        val title = session?.plannedWorkoutId?.let { sportDao.workoutById(it)?.title }
            ?: session?.notes?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() && it.length < 60 }
            ?: "Lauf"
        return Gpx.write(title, points)
    }

    /** Starts a GPS-recorded run session (unfinished until [finishRecordedRun]). */
    suspend fun startRecordedRun(plannedWorkoutId: String?): String {
        val id = newUuid()
        val now = System.currentTimeMillis()
        sportDao.insertSession(
            WorkoutSession(
                id = id,
                discipline = Discipline.RUNNING,
                plannedWorkoutId = plannedWorkoutId ?: matchPlannedRun(now),
                startedAt = now,
            ),
        )
        return id
    }

    suspend fun appendTrackPoints(points: List<RunTrackPoint>) {
        if (points.isNotEmpty()) runDao.insertTrackPoints(points)
    }

    /**
     * Ends a GPS run: aggregates, splits and best efforts from the track.
     * Runs shorter than 100 m are discarded. Returns false when discarded.
     */
    suspend fun finishRecordedRun(sessionId: String, movingSec: Int? = null): Boolean {
        val points = trackOnce(sessionId)
        val summary = RunAnalysis.summary(points)
        if (summary.distanceM < 100f) {
            deleteRun(sessionId)
            return false
        }
        val duration = movingSec ?: summary.durationSec
        sportDao.endSession(sessionId, points.last().time)
        runDao.upsertRunSession(
            RunSession(
                sessionId = sessionId,
                distanceM = summary.distanceM,
                durationSec = duration,
                avgPaceSecPerKm = paceOf(summary.distanceM, duration),
                avgHr = summary.avgHr,
                elevationGainM = summary.elevationGainM,
                source = RunSource.GPS,
            ),
        )
        analyzeTrack(sessionId, points)
        healthWriter.writeWorkout(sessionId)
        return true
    }

    /** Splits + best efforts from the stored track (GPS runs, imported routes). */
    suspend fun analyzeTrack(sessionId: String, points: List<RunAnalysis.Point>? = null) {
        val track = points ?: trackOnce(sessionId)
        if (track.size < 2) return
        runDao.deleteSplits(sessionId)
        runDao.upsertSplits(
            RunAnalysis.splits(track).map {
                RunSplit(sessionId = sessionId, sortOrder = it.index - 1, distanceM = it.distanceM, durationSec = it.durationSec)
            },
        )
        saveEfforts(sessionId, RunAnalysis.bestEfforts(track), track.first().time)
    }

    /** Stores this run's best efforts (replacing earlier ones of the run). */
    suspend fun saveEfforts(sessionId: String, efforts: Map<Int, Int>, achievedAt: Long) {
        runDao.deletePersonalBestsOfSession(sessionId)
        runDao.upsertPersonalBests(
            efforts.map { (distance, sec) ->
                PersonalBest(id = "$sessionId-$distance", distanceM = distance, durationSec = sec, sessionId = sessionId, achievedAt = achievedAt)
            },
        )
    }

    suspend fun deleteRun(sessionId: String) {
        runDao.deleteTrackPoints(sessionId)
        runDao.deleteSplits(sessionId)
        runDao.deletePersonalBestsOfSession(sessionId)
        runDao.deleteRunSession(sessionId)
        sportDao.deleteSession(sessionId)
    }

    /**
     * First open running unit planned for that day, so it counts as done.
     * With [catchUp] (runs added afterwards) a missed unit of the previous
     * [RunPlanMatcher.CATCH_UP_DAYS] days also counts when that day has none.
     */
    suspend fun matchPlannedRun(startMs: Long, catchUp: Boolean = false): String? {
        val day = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val from = if (catchUp) day.minusDays(RunPlanMatcher.CATCH_UP_DAYS) else day
        val planned = weekCalendarRepository.observePlannedBetween(from, day).first()
            .filter { it.discipline == Discipline.RUNNING && !it.skipped }
        if (planned.isEmpty()) return null
        val taken = weekCalendarRepository.observeSessionsBetween(from, day.plusDays(1)).first()
            .mapNotNull { it.session.plannedWorkoutId }
            .toSet()
        val open = planned.filter { it.id !in taken }
        return open.filter { it.date == day.toString() }.minByOrNull { it.sortOrder }?.id
            ?: open.filter { it.date != day.toString() }.maxWithOrNull(compareBy({ it.date }, { -it.sortOrder }))?.id
    }

    /** Today's open planned run with its detail (for "Geplanten Lauf starten"). */
    suspend fun todaysPlannedRun(): Pair<PlannedWorkout, RunPlanWorkout?>? {
        val id = matchPlannedRun(System.currentTimeMillis()) ?: return null
        val workout = sportDao.workoutById(id) ?: return null
        return workout to runDao.runPlanWorkoutOnce(id)
    }

    suspend fun plannedRun(id: String): Pair<PlannedWorkout, RunPlanWorkout?>? {
        val workout = sportDao.workoutById(id) ?: return null
        return workout to runDao.runPlanWorkoutOnce(id)
    }

    /** A GPS run left unfinished (app killed while recording). */
    suspend fun unfinishedRecordedRun(): WorkoutSession? =
        sportDao.activeSessionOnce(Discipline.RUNNING)

    suspend fun updateRunNotes(sessionId: String, notes: String) {
        val session = sportDao.sessionOnce(sessionId) ?: return
        sportDao.updateSessionSummary(sessionId, notes, session.perceivedEffort)
    }

    private fun paceOf(distanceM: Float, durationSec: Int) =
        if (distanceM <= 0f) 0 else (durationSec / (distanceM / 1000f)).roundToInt()

    private fun RunTrackPoint.toPoint() = RunAnalysis.Point(timestamp, lat, lon, altitude, hr)

    suspend fun planDetailOnce(planId: String): RunPlanDetail? = runDao.planDetailOnce(planId)

    private fun RunZone.toRunType(): RunType = RunType.valueOf(name)

    private data class PlanShell(
        val plan: TrainingPlan?,
        val recurring: List<SportDao.RecurringPlanned>,
        val runs: List<RunDao.RunSessionRow>,
    )
}
