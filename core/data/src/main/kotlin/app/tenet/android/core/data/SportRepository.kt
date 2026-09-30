package app.tenet.android.core.data

import app.tenet.android.core.common.BodyMeasurements

import app.tenet.android.core.common.MovementPattern
import app.tenet.android.core.common.ExerciseAlternatives
import kotlinx.coroutines.flow.map
import app.tenet.android.core.common.GymPlanStats
import app.tenet.android.core.common.GymPlanBuilder
import app.tenet.android.core.common.OverloadMath
import app.tenet.android.core.common.OneRepMax
import app.tenet.android.core.common.OneRepMaxFormula
import app.tenet.android.core.common.ProgressMath
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.BodyMetric
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.MeasureType
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RoutineExercise
import app.tenet.android.core.database.entity.SessionExercise
import app.tenet.android.core.database.entity.SetEntry
import app.tenet.android.core.database.entity.SetType
import app.tenet.android.core.database.entity.TrainingPlan
import app.tenet.android.core.database.entity.WorkoutSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** One exercise block of an active session, ready for the UI. */
data class SessionBlock(
    val exercise: Exercise,
    val sessionExercise: SessionExercise,
    val target: RoutineExercise?,
    /** Set index -> "weight x reps" of the previous session. */
    val previousBySortOrder: Map<Int, String>,
    val sets: List<SetEntry>,
    /** Progressive overload proposal for this session. */
    val suggestion: OverloadMath.Suggestion? = null,
    /** Estimated 1RM of the last session / best ever (strength at a glance). */
    val lastE1rm: Float? = null,
    val bestE1rm: Float? = null,
    /** Note on this exercise from the last session that had one (Hevy). */
    val previousNote: String? = null,
)

/** Raw data of the gym plan detail screen. */
data class GymPlanData(
    val plan: TrainingPlan,
    val sets: List<GymPlanStats.SetRow>,
    /** Exercise name → target reps / primary muscles of the plan routine (for the next proposal). */
    val routine: Map<String, Pair<Int, String>>,
    /** Exercise name → its overload rule. */
    val rules: Map<String, OverloadMath.Rule> = emptyMap(),
)

/** Overload rule of a routine entry (defaults when null). */
fun RoutineExercise?.rule(): OverloadMath.Rule =
    if (this == null) OverloadMath.Rule() else OverloadMath.Rule(repMax, stepKg, deloadPercent)

data class BestLift(val name: String, val oneRepMax: Float)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SportRepository @Inject constructor(
    private val dao: SportDao,
    private val healthWriter: app.tenet.android.core.data.health.HealthWriter,
) {
    // ---- Seed ----------------------------------------------------------

    /**
     * Inserts the base library and a default full-body plan once.
     * Called lazily when the sport tab is first opened.
     */
    suspend fun ensureSeedData() {
        // New library entries (e.g. for the setup templates) reach existing installs too.
        dao.insertMissingExercises(seedExercises())
        // Installs from before v13 get the movement pattern of built-in exercises.
        if (dao.exercisesWithoutPattern() > 0) {
            seedExercises().forEach { ex -> ex.pattern?.let { dao.setPatternIfMissing(ex.id, it) } }
        }
        if (dao.activePlanOnce(Discipline.GYM) == null) {
            val plan = TrainingPlan(
                id = PLAN_ID,
                discipline = Discipline.GYM,
                name = "Ganzkörper A",
                goal = "Kraft aufbauen",
                active = true,
            )
            dao.upsertPlan(plan)
            dao.upsertWorkout(
                PlannedWorkout(
                    id = WORKOUT_ID,
                    planId = PLAN_ID,
                    discipline = Discipline.GYM,
                    title = "Ganzkörper A",
                    sortOrder = 0,
                ),
            )
            dao.upsertRoutineExercises(defaultRoutine())
        }
    }

    private fun seedExercises(): List<Exercise> = ExerciseCatalog.gym

    private fun defaultRoutine(): List<RoutineExercise> = listOf(
        routine("ex-bankdruecken", 0, targetSets = 3, targetReps = 8, restSec = 120),
        routine("ex-kniebeugen", 1, targetSets = 3, targetReps = 8, restSec = 180),
        routine("ex-rudern", 2, targetSets = 3, targetReps = 10, restSec = 120),
        routine("ex-schulterdruecken", 3, targetSets = 3, targetReps = 8, restSec = 120),
        routine("ex-bizepscurl", 4, targetSets = 2, targetReps = 12, restSec = 60),
    )

    private fun routine(
        exerciseId: String,
        order: Int,
        targetSets: Int,
        targetReps: Int,
        restSec: Int,
    ) = RoutineExercise(
        plannedWorkoutId = WORKOUT_ID,
        exerciseId = exerciseId,
        sortOrder = order,
        targetSets = targetSets,
        targetReps = targetReps,
        restSec = restSec,
    )

    // ---- Reads ---------------------------------------------------------

    fun observeExercises(discipline: Discipline): Flow<List<Exercise>> =
        dao.observeExercises(discipline)

    suspend fun observeExercisesOnce(discipline: Discipline): List<Exercise> =
        dao.observeExercises(discipline).first()

    suspend fun activePlanOnce(): TrainingPlan? = dao.activePlanOnce(Discipline.GYM)

    suspend fun firstWorkoutOnce(planId: String): PlannedWorkout? =
        dao.firstWorkoutOnce(planId)

    suspend fun workoutsOnce(planId: String): List<PlannedWorkout> = dao.workoutsOnce(planId)

    /** Next workout of a rotating split (A/B, Push/Pull/Beine …). */
    suspend fun nextWorkoutOnce(planId: String): PlannedWorkout? =
        nextWorkout(dao.workoutsOnce(planId), dao.lastWorkoutIdOnce(planId))

    /** The workout after the one trained last; the first one to start with. */
    private fun nextWorkout(workouts: List<PlannedWorkout>, lastId: String?): PlannedWorkout? {
        if (workouts.isEmpty()) return null
        val last = workouts.indexOfFirst { it.id == lastId }
        return if (last < 0) workouts.first() else workouts[(last + 1) % workouts.size]
    }

    suspend fun routineOnce(workoutId: String): List<RoutineExercise> =
        dao.routineOnce(workoutId)

    fun observeActivePlan(): Flow<TrainingPlan?> = dao.observeActivePlan(Discipline.GYM)

    fun observeWorkouts(planId: String): Flow<List<PlannedWorkout>> = dao.observeWorkouts(planId)

    fun observeRoutine(workoutId: String): Flow<List<RoutineExercise>> =
        dao.observeRoutine(workoutId)

    fun observeActiveSession(): Flow<WorkoutSession?> = dao.observeActiveSession(Discipline.GYM)

    fun observeFinishedSessions(): Flow<List<WorkoutSession>> =
        dao.observeFinishedSessions(Discipline.GYM)

    fun observeSession(id: String): Flow<WorkoutSession?> = dao.observeSession(id)

    fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> =
        dao.observeSessionExercises(sessionId)

    /** Plan + main workout + routine + sessions + progress in one state flow graph. */
    fun observeGymOverview(
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): Flow<GymOverview> =
        combine(
            dao.observeActivePlan(Discipline.GYM),
            dao.observeActiveSession(Discipline.GYM),
            dao.observeFinishedSessions(Discipline.GYM),
        ) { plan, active, finished -> Triple(plan, active, finished) }
            .flatMapLatest { (plan, active, finished) ->
                if (plan == null) {
                    flowOf(GymOverview(plan = null, activeSession = active, sessions = finished))
                } else {
                    combine(dao.observeWorkouts(plan.id), dao.observeLastWorkoutId(plan.id)) { w, last -> w to last }
                        .flatMapLatest { (workouts, lastId) ->
                        val main = nextWorkout(workouts, lastId)
                        if (main == null) {
                            flowOf(
                                GymOverview(
                                    plan = plan,
                                    mainWorkout = main,
                                    workouts = workouts,
                                    activeSession = active,
                                    sessions = finished,
                                ),
                            )
                        } else {
                            combine(
                                dao.observeRoutine(main.id),
                                dao.observeExercises(Discipline.GYM),
                                flow { emit(dao.sessionVolumes(Discipline.GYM)) },
                                flow { emit(bestLifts(formula)) },
                            ) { routine, exercises, volumes, bests ->
                                GymOverview(
                                    plan = plan,
                                    mainWorkout = main,
                                    workouts = workouts,
                                    routine = routine,
                                    exercises = exercises,
                                    sessions = finished,
                                    activeSession = active,
                                    volumesBySession = volumes.associate { it.sessionId to it.volume },
                                    bestLifts = bests,
                                )
                            }
                        }
                    }
                }
            }

    suspend fun bestLifts(formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY): List<BestLift> =
        dao.completedSetsWithExercise(Discipline.GYM)
            .groupBy { it.name }
            .map { (name, sets) ->
                BestLift(name, OneRepMax.best(sets.asSequence().map { it.weight to it.reps }, formula))
            }
            .filter { it.oneRepMax > 0f }
            .sortedByDescending { it.oneRepMax }
            .take(5)

    // ---- Setup ---------------------------------------------------------

    /**
     * Replaces the active gym plan with the one built in the setup: one
     * workout per routine (rotating), start weights per exercise and the
     * chosen training days (ISO 1–7).
     */
    suspend fun applyGymSetup(built: GymPlanBuilder.Plan, trainingDays: List<Int>): String {
        dao.insertMissingExercises(seedExercises())
        dao.deactivatePlans(Discipline.GYM)
        val planId = newUuid()
        dao.upsertPlan(
            TrainingPlan(
                id = planId,
                discipline = Discipline.GYM,
                name = built.name,
                goal = built.name,
                active = true,
                trainingDays = trainingDays.sorted().joinToString(",").ifEmpty { null },
            ),
        )
        built.routines.forEachIndexed { index, routine ->
            val workoutId = newUuid()
            dao.upsertWorkout(
                PlannedWorkout(
                    id = workoutId,
                    planId = planId,
                    discipline = Discipline.GYM,
                    title = routine.title,
                    sortOrder = index,
                ),
            )
            dao.upsertRoutineExercises(
                routine.exercises.mapIndexed { order, ex ->
                    RoutineExercise(
                        plannedWorkoutId = workoutId,
                        exerciseId = ex.exerciseId,
                        sortOrder = order,
                        targetSets = ex.sets,
                        targetReps = ex.reps,
                        restSec = ex.restSec,
                        startWeightKg = ex.startWeightKg,
                    )
                },
            )
        }
        return planId
    }

    /** Exercise names for the setup summary. */
    suspend fun exerciseNames(): Map<String, String> {
        dao.insertMissingExercises(seedExercises())
        return dao.observeExercises(Discipline.GYM).first().associate { it.id to it.name }
    }

    // ---- Progress analytics -------------------------------------------

    /** Flattened completed sets for charts and PR detection. */
    suspend fun progressSets(): List<ProgressMath.SetRecord> =
        dao.progressSets(Discipline.GYM).map { row ->
            ProgressMath.SetRecord(
                exercise = row.name,
                primaryMuscles = row.primaryMuscles,
                weight = row.weight,
                reps = row.reps,
                startedAt = row.startedAt,
            )
        }

    suspend fun personalBests(formula: OneRepMaxFormula): List<ProgressMath.PersonalBest> =
        ProgressMath.personalBests(progressSets(), formula)

    // ---- Session lifecycle --------------------------------------------

    suspend fun sessionTitle(sessionId: String): String? {
        val session = dao.sessionOnce(sessionId) ?: return null
        return session.plannedWorkoutId?.let { dao.workoutById(it) }?.title
    }

    /**
     * Resumes the active session or creates one from the active plan.
     * Returns the session id.
     */
    suspend fun startOrResumeSession(): String {
        dao.activeSessionOnce(Discipline.GYM)?.let { return it.id }

        val plan = dao.activePlanOnce(Discipline.GYM)
        val workout = plan?.let { nextWorkoutOnce(it.id) }
        val routine = workout?.let { dao.routineOnce(it.id) }.orEmpty()

        val sessionId = newUuid()
        dao.insertSession(
            WorkoutSession(
                id = sessionId,
                discipline = Discipline.GYM,
                plannedWorkoutId = workout?.id,
                startedAt = System.currentTimeMillis(),
            ),
        )
        if (routine.isNotEmpty()) {
            val exercisesByIds = dao.exercisesByIds(routine.map { it.exerciseId })
                .associateBy { it.id }
            dao.insertSessionExercises(
                routine.map { target ->
                    SessionExercise(
                        id = newUuid(),
                        sessionId = sessionId,
                        exerciseId = target.exerciseId,
                        sortOrder = target.sortOrder,
                    )
                },
            )
            val sessionExercises = dao.sessionExercisesOnce(sessionId)
            val initialSets = mutableListOf<SetEntry>()
            for (se in sessionExercises) {
                val target = routine.firstOrNull { it.exerciseId == se.exerciseId }
                val exercise = exercisesByIds[se.exerciseId]
                val measure = exercise?.measureType
                // Progressive overload: start with the proposed weight.
                val proposal = OverloadMath.suggest(
                    history(se.exerciseId, sessionId),
                    target?.targetReps ?: 8,
                    exercise?.primaryMuscles.orEmpty(),
                    target.rule(),
                )
                // First time: the start weight from the setup, if any.
                val weight = if (proposal.decision == OverloadMath.Decision.FIRST_TIME) {
                    target?.startWeightKg ?: proposal.weightKg
                } else {
                    proposal.weightKg
                }
                repeat(target?.targetSets ?: 3) { index ->
                    initialSets += SetEntry(
                        id = newUuid(),
                        sessionExerciseId = se.id,
                        sortOrder = index,
                        type = SetType.WORKING,
                        weight = weight,
                        reps = if (measure == MeasureType.DURATION) 0 else (target?.targetReps ?: 8),
                    )
                }
            }
            dao.insertSets(initialSets)
        }
        return sessionId
    }

    suspend fun endSession(sessionId: String) {
        val session = dao.sessionOnce(sessionId)
        val now = System.currentTimeMillis()
        // Left open for hours (forgot to finish): estimate a realistic duration
        // from the logged sets (~3 min each) instead of counting the whole night.
        val end = if (session != null && now - session.startedAt > STALE_MS) {
            val sets = dao.sessionExercisesOnce(sessionId).sumOf { dao.setsOnce(it.id).count { s -> s.completed } }
            session.startedAt + (sets * 3 * 60_000L).coerceIn(20 * 60_000L, 2 * 60 * 60_000L)
        } else {
            now
        }
        dao.endSession(sessionId, end)
        healthWriter.writeWorkout(sessionId)
    }

    /** True when a running session was started more than 4 h ago. */
    fun isStale(session: WorkoutSession, now: Long = System.currentTimeMillis()) = now - session.startedAt > STALE_MS

    suspend fun updateSessionSummary(sessionId: String, notes: String, perceivedEffort: Int?) =
        dao.updateSessionSummary(sessionId, notes, perceivedEffort)

    /** Discards the active session incl. its exercises and sets. */
    suspend fun discardActiveSession() {
        val sessionId = dao.activeSessionOnce(Discipline.GYM)?.id ?: return
        dao.deleteSetsOfSession(sessionId)
        dao.deleteSessionExercises(sessionId)
        dao.deleteSession(sessionId)
    }

    /** Session blocks incl. "Vorher" values from the previous session. */
    suspend fun loadBlocks(sessionId: String): List<SessionBlock> {
        val sessionExercises = dao.sessionExercisesOnce(sessionId)
        val workout = dao.sessionOnce(sessionId)?.plannedWorkoutId
            ?.let { dao.workoutById(it) }
        val routine = workout?.let { dao.routineOnce(it.id) }.orEmpty()
        val exercises = dao.exercisesByIds(sessionExercises.map { it.exerciseId })
            .associateBy { it.id }

        return sessionExercises.map { se ->
            val previousRows = dao.previousSets(se.exerciseId, sessionId)
            val previousSessionId = previousRows.firstOrNull()?.sessionId
            val history = workSets(previousRows)
            val target = routine.firstOrNull { it.exerciseId == se.exerciseId }
            val e1rms = history.map { s -> s.maxOfOrNull { OneRepMax.oneRepMax(it.weightKg, it.reps) } ?: 0f }
            SessionBlock(
                // Custom exercises can be deleted between sessions; fall back
                // to a placeholder instead of crashing the session screen.
                exercise = exercises[se.exerciseId] ?: Exercise(
                    id = se.exerciseId,
                    name = "Gelöschte Übung",
                    discipline = Discipline.GYM,
                    primaryMuscles = "",
                    secondaryMuscles = "",
                    equipment = "",
                    measureType = MeasureType.REPS,
                ),
                sessionExercise = se,
                previousNote = dao.lastExerciseNote(se.exerciseId, sessionId),
                target = routine.firstOrNull { it.exerciseId == se.exerciseId },
                previousBySortOrder = previousRows
                    .takeWhile { it.sessionId == previousSessionId }
                    .associate { row ->
                        val w = row.set.weight
                        row.set.sortOrder to "${if (w % 1f == 0f) w.toInt().toString() else w.toString().replace('.', ',')}×${row.set.reps}"
                    },
                sets = dao.setsOnce(se.id),
                suggestion = OverloadMath.suggest(
                    history,
                    target?.targetReps ?: 8,
                    exercises[se.exerciseId]?.primaryMuscles.orEmpty(),
                    target.rule(),
                ).let { s ->
                    // No history yet: show the start weight from the setup.
                    val start = target?.startWeightKg
                    if (s.decision == OverloadMath.Decision.FIRST_TIME && start != null) s.copy(weightKg = start) else s
                },
                lastE1rm = e1rms.firstOrNull()?.takeIf { it > 0f },
                bestE1rm = e1rms.maxOrNull()?.takeIf { it > 0f },
            )
        }
    }

    /** Completed working sets per earlier session of an exercise, newest first. */
    private suspend fun history(exerciseId: String, currentSessionId: String): List<List<OverloadMath.WorkSet>> =
        workSets(dao.previousSets(exerciseId, currentSessionId))

    /**
     * Working sets per past session, newest first. In sessions where nothing
     * at all was ticked off, every set with a weight counts (the user logs
     * without ticking); otherwise unticked sets were skipped.
     */
    private fun workSets(rows: List<SportDao.PreviousSetRow>): List<List<OverloadMath.WorkSet>> =
        rows.groupBy { it.sessionId }.values.map { session ->
            session
                .filter { it.set.type != SetType.WARMUP && it.set.reps > 0 }
                .filter { if (it.sessionHasCompleted) it.set.completed else it.set.weight > 0f }
                .map { OverloadMath.WorkSet(it.set.weight, it.set.reps) }
        }

    /** Gym plan detail: all completed sets of the plan plus routine targets. */
    fun observeGymPlan(planId: String): Flow<GymPlanData?> =
        dao.observePlan(planId).flatMapLatest { plan ->
            if (plan == null) return@flatMapLatest flowOf(null)
            dao.observePlanSets(planId).map { rows ->
                val zone = java.time.ZoneId.systemDefault()
                val routine = dao.workoutsOnce(planId).flatMap { dao.routineOnce(it.id) }.distinctBy { it.exerciseId }
                val exercises = dao.exercisesByIds(routine.map { it.exerciseId }).associateBy { it.id }
                GymPlanData(
                    plan = plan,
                    // Same rule as the overload history: unticked sessions count with filled-in sets.
                    sets = rows.groupBy { it.sessionId }.values.flatMap { session ->
                        session.filter { it.completed }.ifEmpty { session.filter { it.weight > 0f } }
                    }.map {
                        GymPlanStats.SetRow(
                            sessionId = it.sessionId,
                            date = java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate(),
                            exercise = it.exercise,
                            weightKg = it.weight,
                            reps = it.reps,
                        )
                    },
                    routine = routine.mapNotNull { r ->
                        exercises[r.exerciseId]?.let { it.name to (r.targetReps to it.primaryMuscles) }
                    }.toMap(),
                    rules = routine.mapNotNull { r -> exercises[r.exerciseId]?.let { it.name to r.rule() } }.toMap(),
                )
            }
        }

    suspend fun upsertSet(set: SetEntry) = dao.upsertSet(set)

    suspend fun addSet(sessionExerciseId: String): SetEntry {
        val existing = dao.setsOnce(sessionExerciseId)
        val last = existing.maxByOrNull { it.sortOrder }
        val new = SetEntry(
            id = newUuid(),
            sessionExerciseId = sessionExerciseId,
            sortOrder = (last?.sortOrder ?: -1) + 1,
            type = SetType.WORKING,
            weight = last?.weight ?: 0f,
            reps = last?.reps ?: 0,
        )
        dao.upsertSet(new)
        return new
    }

    /** Writes notes and perceived effort when the session is finished. */
    suspend fun finishSession(sessionId: String, notes: String, perceivedEffort: Int?) {
        val session = dao.sessionOnce(sessionId) ?: return
        if (session.endedAt == null) {
            dao.endSession(sessionId, System.currentTimeMillis())
        }
        dao.updateSessionSummary(sessionId, notes, perceivedEffort)
    }

    // ---- Exercise library ----------------------------------------------

    suspend fun exerciseById(id: String): Exercise? = dao.exerciseById(id)

    suspend fun upsertExercise(exercise: Exercise) = dao.upsertExercise(exercise)

    /**
     * Deletes a custom exercise. Returns false when a routine still
     * references it (the UI then offers to remove it from the plan first).
     */
    suspend fun deleteCustomExercise(id: String): Boolean {
        if (dao.routineUsageCount(id) > 0) return false
        dao.deleteCustomExercise(id)
        return true
    }

    suspend fun isExerciseInRoutine(exerciseId: String): Boolean =
        dao.routineUsageCount(exerciseId) > 0

    // ---- Exercise catalog & session editing -------------------------------

    /** All exercises of a discipline (catalog + own ones). */
    suspend fun catalog(discipline: Discipline): List<Exercise> = dao.observeExercises(discipline).first()

    /** Replacement candidates: same movement pattern first, then same main muscle. */
    suspend fun alternatives(exerciseId: String): List<Exercise> {
        val target = dao.exercisesByIds(listOf(exerciseId)).firstOrNull() ?: return emptyList()
        val pool = catalog(target.discipline)
        val ranked = ExerciseAlternatives.rank(target.candidate(), pool.map { it.candidate() })
        val byId = pool.associateBy { it.id }
        return ranked.mapNotNull { byId[it] }
    }

    private fun Exercise.candidate() =
        ExerciseAlternatives.Candidate(id, MovementPattern.fromName(pattern), primaryMuscles)

    /**
     * Swaps an exercise of a running session. Sets already ticked off stay
     * with the old exercise (history stays correct); the open ones move to
     * the new exercise with a fresh weight suggestion. [permanent] also
     * replaces it in the plan's routine.
     */
    suspend fun setExerciseNote(sessionExerciseId: String, notes: String) =
        dao.setSessionExerciseNotes(sessionExerciseId, notes.take(500))

    suspend fun swapSessionExercise(sessionExerciseId: String, newExerciseId: String, permanent: Boolean) {
        val se = dao.sessionExerciseOnce(sessionExerciseId) ?: return
        val newExercise = dao.exercisesByIds(listOf(newExerciseId)).firstOrNull() ?: return
        val sets = dao.setsOnce(sessionExerciseId)
        val open = sets.filter { !it.completed }
        val workoutId = dao.sessionOnce(se.sessionId)?.plannedWorkoutId
        val target = workoutId?.let { id -> dao.routineOnce(id).firstOrNull { it.exerciseId == se.exerciseId } }
        val reps = target?.targetReps ?: open.firstOrNull()?.reps?.takeIf { it > 0 } ?: 8
        if (sets.none { it.completed }) {
            dao.setSessionExerciseExercise(sessionExerciseId, newExerciseId)
            dao.deleteSetsOfSessionExercise(sessionExerciseId)
            dao.insertSets(newSets(sessionExerciseId, se.sessionId, newExercise, sets.size.coerceAtLeast(1), reps))
        } else {
            dao.deleteOpenSetsOfSessionExercise(sessionExerciseId)
            dao.shiftSessionExercises(se.sessionId, se.sortOrder)
            val added = SessionExercise(newUuid(), se.sessionId, newExerciseId, se.sortOrder + 1)
            dao.insertSessionExercises(listOf(added))
            dao.insertSets(newSets(added.id, se.sessionId, newExercise, open.size.coerceAtLeast(1), reps))
        }
        if (permanent && workoutId != null) swapRoutineExercise(workoutId, se.exerciseId, newExerciseId)
    }

    /** Adds an exercise at the end of a running session (and of the routine if [permanent]). */
    suspend fun addSessionExercise(sessionId: String, exerciseId: String, permanent: Boolean) {
        val exercise = dao.exercisesByIds(listOf(exerciseId)).firstOrNull() ?: return
        val order = (dao.sessionExercisesOnce(sessionId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        val added = SessionExercise(newUuid(), sessionId, exerciseId, order)
        dao.insertSessionExercises(listOf(added))
        dao.insertSets(newSets(added.id, sessionId, exercise, 3, 10))
        val workoutId = dao.sessionOnce(sessionId)?.plannedWorkoutId
        if (permanent && workoutId != null && dao.routineOnce(workoutId).none { it.exerciseId == exerciseId }) {
            addRoutineExercise(workoutId, exerciseId)
        }
    }

    /** Removes an exercise from a running session (and from the routine if [permanent]). */
    suspend fun removeSessionExercise(sessionExerciseId: String, permanent: Boolean) {
        val se = dao.sessionExerciseOnce(sessionExerciseId) ?: return
        dao.deleteSetsOfSessionExercise(sessionExerciseId)
        dao.deleteSessionExercise(sessionExerciseId)
        val workoutId = dao.sessionOnce(se.sessionId)?.plannedWorkoutId
        if (permanent && workoutId != null) deleteRoutineExercise(workoutId, se.exerciseId)
    }

    /**
     * Adds three warm-up sets (50/70/85 % × 10/5/3) before the working sets
     * of a session exercise. False when not useful (light or already done).
     */
    suspend fun addWarmupSets(sessionExerciseId: String): Boolean {
        val sets = dao.setsOnce(sessionExerciseId)
        if (sets.any { it.type == SetType.WARMUP }) return false
        val work = sets.firstOrNull { it.type != SetType.WARMUP }?.weight ?: return false
        if (work < 25f) return false
        // The empty barbell (20 kg) is the lower limit only for barbell lifts.
        val se = dao.sessionExerciseOnce(sessionExerciseId)
        val barbell = se?.let { dao.exercisesByIds(listOf(it.exerciseId)).firstOrNull()?.equipment } == "Langhantel"
        val floor = if (barbell) 20f else 2.5f
        sets.forEach { dao.upsertSet(it.copy(sortOrder = it.sortOrder + 3)) }
        dao.insertSets(
            listOf(0.5f to 10, 0.7f to 5, 0.85f to 3).mapIndexed { i, (f, reps) ->
                SetEntry(
                    id = newUuid(),
                    sessionExerciseId = sessionExerciseId,
                    sortOrder = i,
                    type = SetType.WARMUP,
                    weight = OverloadMath.round(work * f, if (work * f < 20f) 1f else 2.5f).coerceAtLeast(floor),
                    reps = reps,
                )
            },
        )
        return true
    }

    /** Whether session changes can also be written to a plan. */
    suspend fun sessionHasPlan(sessionId: String): Boolean = dao.sessionOnce(sessionId)?.plannedWorkoutId != null

    /** Replaces [oldId] by [newId] in a routine, keeping position and targets. */
    suspend fun swapRoutineExercise(workoutId: String, oldId: String, newId: String) {
        val routine = dao.routineOnce(workoutId)
        if (routine.any { it.exerciseId == newId }) {
            deleteRoutineExercise(workoutId, oldId)
            return
        }
        replaceRoutine(
            workoutId,
            routine.map { if (it.exerciseId == oldId) it.copy(exerciseId = newId, startWeightKg = null) else it },
        )
    }

    private suspend fun newSets(
        sessionExerciseId: String,
        sessionId: String,
        exercise: Exercise,
        count: Int,
        reps: Int,
    ): List<SetEntry> {
        val weight = OverloadMath.suggest(history(exercise.id, sessionId), reps, exercise.primaryMuscles).weightKg
        val timed = exercise.measureType == MeasureType.DURATION || exercise.measureType == MeasureType.HOLD
        return List(count) { index ->
            SetEntry(
                id = newUuid(),
                sessionExerciseId = sessionExerciseId,
                sortOrder = index,
                type = SetType.WORKING,
                weight = weight,
                reps = if (timed) 0 else reps,
            )
        }
    }

    // ---- Routine editing ------------------------------------------------

    /** Replaces the routine of [workoutId] with [routine] (order = index). */
    suspend fun replaceRoutine(workoutId: String, routine: List<RoutineExercise>) {
        dao.clearRoutine(workoutId)
        dao.upsertRoutineExercises(routine.mapIndexed { index, target ->
            target.copy(sortOrder = index, plannedWorkoutId = workoutId)
        })
    }

    suspend fun deleteRoutineExercise(workoutId: String, exerciseId: String) {
        dao.deleteRoutineExercise(workoutId, exerciseId)
        // Re-number the remaining targets so the order stays stable.
        val remaining = dao.routineOnce(workoutId)
        dao.upsertRoutineExercises(remaining.mapIndexed { index, target -> target.copy(sortOrder = index) })
    }

    suspend fun addRoutineExercise(workoutId: String, exerciseId: String) {
        val existing = dao.routineOnce(workoutId)
        dao.upsertRoutineExercises(
            listOf(
                RoutineExercise(
                    plannedWorkoutId = workoutId,
                    exerciseId = exerciseId,
                    sortOrder = existing.size,
                    targetSets = 3,
                    targetReps = 8,
                    restSec = 120,
                ),
            ),
        )
    }

    suspend fun updateRoutineExercise(target: RoutineExercise) = dao.upsertRoutineExercises(listOf(target))

    /** Moves a routine entry one position up or down. */
    suspend fun moveRoutineExercise(workoutId: String, exerciseId: String, up: Boolean) {
        val routine = dao.routineOnce(workoutId).toMutableList()
        val index = routine.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) return
        val other = if (up) index - 1 else index + 1
        if (other !in routine.indices) return
        val moved = routine.removeAt(index)
        routine.add(other, moved)
        dao.upsertRoutineExercises(routine.mapIndexed { i, target -> target.copy(sortOrder = i) })
    }

    // ---- Body metrics ---------------------------------------------------

    fun observeBodyMetrics(): Flow<List<BodyMetric>> = dao.observeBodyMetrics()

    suspend fun latestBodyMetric(): BodyMetric? = dao.latestBodyMetric()

    /** Upserts today's entry; the date is the identity for daily measurements. */
    suspend fun saveBodyMetric(
        date: String,
        weight: Float?,
        bodyFat: Float? = null,
        measurements: Map<String, Float> = emptyMap(),
    ) {
        val all = dao.observeBodyMetrics().firstOrNull().orEmpty()
        val existing = all.firstOrNull { it.date == date }
        // Measurements without a new weight keep the last known one.
        val w = weight ?: existing?.weight ?: all.lastOrNull()?.weight ?: return
        val merged = BodyMeasurements.decode(existing?.measurementsJson) + measurements.filterValues { it > 0f }
        dao.upsertBodyMetric(
            BodyMetric(
                id = existing?.id ?: newUuid(),
                date = date,
                weight = w,
                bodyFat = bodyFat ?: existing?.bodyFat,
                measurementsJson = BodyMeasurements.encode(merged),
            ),
        )
        if (weight != null) runCatching { healthWriter.writeWeight(java.time.LocalDate.parse(date), weight) }
    }

    companion object {
        const val PLAN_ID = "default-ganzkoerper-a"
        const val WORKOUT_ID = "default-gk-a-workout"
        private const val STALE_MS = 4 * 60 * 60_000L
    }
}

data class GymOverview(
    val plan: TrainingPlan? = null,
    /** The workout that is due next (rotating split). */
    val mainWorkout: app.tenet.android.core.database.entity.PlannedWorkout? = null,
    /** All workouts of the plan in rotation order. */
    val workouts: List<app.tenet.android.core.database.entity.PlannedWorkout> = emptyList(),
    val routine: List<RoutineExercise> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val sessions: List<WorkoutSession> = emptyList(),
    val volumesBySession: Map<String, Float> = emptyMap(),
    val bestLifts: List<BestLift> = emptyList(),
)
