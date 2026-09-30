package app.tenet.android.core.data

import app.tenet.android.core.common.CaliPlanBuilder
import app.tenet.android.core.common.IntervalMath
import app.tenet.android.core.common.SkillMath
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.database.dao.SkillDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.CriterionType
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.FormQuality
import app.tenet.android.core.database.entity.MeasureType
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RoutineExercise
import app.tenet.android.core.database.entity.SessionExercise
import app.tenet.android.core.database.entity.SessionMode
import app.tenet.android.core.database.entity.SetEntry
import app.tenet.android.core.database.entity.SetType
import app.tenet.android.core.database.entity.Skill
import app.tenet.android.core.database.entity.SkillCategory
import app.tenet.android.core.database.entity.SkillProgress
import app.tenet.android.core.database.entity.SkillStep
import app.tenet.android.core.database.entity.SkillStepAchievement
import app.tenet.android.core.database.entity.TrainingPlan
import app.tenet.android.core.database.entity.WorkoutSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Input for the calisthenics setup wizard. */
data class CaliSetupData(
    val skills: List<Skill>,
    val steps: List<SkillStep>,
    val currentSteps: Map<String, String>,
    /** Exercise id → name (calisthenics library). */
    val names: Map<String, String> = emptyMap(),
)

/** Everything the calisthenics page needs, precomputed off the UI thread. */
data class CalisthenicsOverview(
    val skills: List<Skill> = emptyList(),
    val steps: List<SkillStep> = emptyList(),
    val progress: List<SkillProgress> = emptyList(),
    val achievements: List<SkillStepAchievement> = emptyList(),
    val bests: Map<String, SkillDao.ExerciseBest> = emptyMap(),
    val plan: TrainingPlan? = null,
    val mainWorkout: PlannedWorkout? = null,
    val routine: List<RoutineExercise> = emptyList(),
    /** Calisthenics library (routine + step exercises). */
    val exercises: List<Exercise> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val sessions: List<WorkoutSession> = emptyList(),
    val loading: Boolean = true,
)    /** Title + criterion of the skill session currently on screen. */
data class SkillSessionInfo(
    val skill: Skill,
    val step: SkillStep,
    val exercise: Exercise,
    val sessionExerciseId: String,
)

/** One station of a strength session, ready for the interval screen. */
data class StrengthStation(
    val exerciseId: String,
    val sessionExerciseId: String = "",
    val name: String,
    val targetReps: Int,
    val measureType: MeasureType,
)

/** Everything the circuit/EMOM screen needs, loaded once per session. */
data class CsStrengthInfo(
    val session: WorkoutSession,
    val title: String?,
    val stations: List<StrengthStation>,
)

/**
 * Calisthenics skill tree (App_Konzept.md 5.2.2): skills, progression steps,
 * per-session criterion achievements and the confirmed promotion flow, plus
 * the lifecycle of the calisthenics training session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SkillRepository @Inject constructor(
    private val skillDao: SkillDao,
    private val sportDao: SportDao,
    private val healthWriter: app.tenet.android.core.data.health.HealthWriter,
) {
    // ---- Seed -----------------------------------------------------------

    /**
     * Inserts the calisthenics library, the skill trees and a default plan
     * once. Called lazily when the calisthenics tab is first opened.
     */
    suspend fun ensureSeedData() {
        // New library entries (setup variants, catalog extras) reach existing installs too.
        sportDao.insertMissingExercises(seedExercises() + ExerciseCatalog.calisthenicsExtras)
        if (sportDao.exercisesWithoutPattern() > 0) {
            ExerciseCatalog.calisthenicsPatterns.forEach { (id, pattern) -> sportDao.setPatternIfMissing(id, pattern.name) }
        }
        if (skillDao.skillCount() == 0) {
            seedSkills().forEach { skillDao.upsertSkill(it) }
            seedSteps().forEach { skillDao.upsertStep(it) }
        }
        if (sportDao.activePlanOnce(Discipline.CALISTHENICS) == null) {
            sportDao.upsertPlan(
                TrainingPlan(
                    id = PLAN_ID,
                    discipline = Discipline.CALISTHENICS,
                    name = "Calisthenics Basis",
                    active = true,
                ),
            )
            sportDao.upsertWorkout(
                PlannedWorkout(
                    id = WORKOUT_ID,
                    planId = PLAN_ID,
                    discipline = Discipline.CALISTHENICS,
                    title = "Skill + Kraft",
                    sortOrder = 0,
                ),
            )
            sportDao.upsertRoutineExercises(seedRoutine())
        }
    }

    // ---- Setup ----------------------------------------------------------

    /** Skills, their steps and where the user stands, for the setup wizard. */
    suspend fun setupData(): CaliSetupData {
        ensureSeedData()
        val steps = skillDao.observeSteps().first()
        return CaliSetupData(
            skills = skillDao.observeSkills().first(),
            steps = steps,
            currentSteps = skillDao.observeProgress().first().associate { it.skillId to it.currentStepId },
            names = sportDao.observeExercises(Discipline.CALISTHENICS).first().associate { it.id to it.name },
        )
    }

    /** Steps in the shape the pure plan builder expects. */
    fun builderSteps(steps: List<SkillStep>): List<CaliPlanBuilder.Step> = steps.map {
        CaliPlanBuilder.Step(it.id, it.skillId, it.sortOrder, it.exerciseId, it.criterionSets, it.criterionValue)
    }

    /**
     * Applies the setup: new routine for the calisthenics workout, training
     * days (ISO 1–7) and the step every skill currently stands on.
     */
    suspend fun applyCaliSetup(built: CaliPlanBuilder.Plan, trainingDays: List<Int>) {
        ensureSeedData()
        val plan = sportDao.activePlanOnce(Discipline.CALISTHENICS) ?: return
        val workout = sportDao.firstWorkoutOnce(plan.id) ?: return
        sportDao.deleteRoutine(workout.id)
        sportDao.upsertRoutineExercises(
            built.routine.mapIndexed { order, ex ->
                RoutineExercise(
                    plannedWorkoutId = workout.id,
                    exerciseId = ex.exerciseId,
                    sortOrder = order,
                    targetSets = ex.sets,
                    targetReps = ex.target,
                    restSec = ex.restSec,
                )
            },
        )
        sportDao.setTrainingDays(plan.id, trainingDays.sorted().joinToString(",").ifEmpty { null })
        val now = System.currentTimeMillis()
        built.progress.forEach { (skillId, stepId) -> skillDao.upsertProgress(SkillProgress(skillId, stepId, now)) }
    }

    // ---- Skill tree reads ----------------------------------------------

    fun observeCalisthenicsOverview(): Flow<CalisthenicsOverview> =
        combine(
            skillDao.observeSkills(),
            skillDao.observeSteps(),
            skillDao.observeProgress(),
            skillDao.observeAchievements(),
            skillDao.bestByExercise(),
        ) { skills, steps, progress, achievements, bests ->
            TreeState(skills, steps, progress, achievements, bests)
        }.flatMapLatest { tree ->
            combine(
                sportDao.observeActivePlan(Discipline.CALISTHENICS),
                sportDao.observeActiveSession(Discipline.CALISTHENICS),
                sportDao.observeFinishedSessions(Discipline.CALISTHENICS),
                sportDao.observeExercises(Discipline.CALISTHENICS),
            ) { plan, active, finished, exercises ->
                SessionShell(plan, active, finished, exercises)
            }.flatMapLatest { shell ->
                val plan = shell.plan
                if (plan == null) {
                    flowOf(buildOverview(tree, shell, mainWorkout = null, routine = emptyList()))
                } else {
                    sportDao.observeWorkouts(plan.id).flatMapLatest { workouts ->
                        val main = workouts.firstOrNull()
                        if (main == null) {
                            flowOf(buildOverview(tree, shell, mainWorkout = null, routine = emptyList()))
                        } else {
                            sportDao.observeRoutine(main.id)
                                .map { routine -> buildOverview(tree, shell, main, routine) }
                        }
                    }
                }
            }
        }

    private fun buildOverview(
        tree: TreeState,
        shell: SessionShell,
        mainWorkout: PlannedWorkout?,
        routine: List<RoutineExercise>,
    ) = CalisthenicsOverview(
        skills = tree.skills,
        steps = tree.steps,
        progress = tree.progress,
        achievements = tree.achievements,
        bests = tree.bests.associateBy { it.exerciseId },
        plan = shell.plan,
        mainWorkout = mainWorkout,
        routine = routine,
        exercises = shell.exercises,
        activeSession = shell.active,
        sessions = shell.finished,
    )

    /** The step a skill currently stands on (first step when untracked). */
    suspend fun currentStepOnce(skillId: String): SkillStep? {
        val progress = skillDao.progressOnce(skillId)
        if (progress != null) {
            skillDao.stepOnce(progress.currentStepId)?.let { return it }
        }
        return skillDao.stepsOfSkillOnce(skillId).firstOrNull()
    }

    /** Skill + step + exercise behind a running session (for its title). */
    suspend fun skillSessionInfo(sessionId: String): SkillSessionInfo? {
        val sessionExercise = sportDao.sessionExercisesOnce(sessionId).firstOrNull() ?: return null
        val step = skillDao.stepByExercise(sessionExercise.exerciseId) ?: return null
        val skill = skillDao.skillById(step.skillId) ?: return null
        val exercise = sportDao.exerciseById(step.exerciseId) ?: return null
        return SkillSessionInfo(skill, step, exercise, sessionExercise.id)
    }

    /**
     * Records the current step's criterion as met for this session and
     * returns true when it is newly met (drives the "Aufstieg" snackbar).
     */
    suspend fun checkAchievement(sessionId: String, exerciseId: String): Boolean {
        val step = skillDao.stepByExercise(exerciseId) ?: return false
        val attempts = skillDao.setsOfSessionExercise(sessionId, exerciseId).map { set ->
            SkillMath.Attempt(
                completed = set.completed,
                seconds = set.durationSec,
                reps = set.reps,
                sloppy = set.formQuality == FormQuality.SLOPPY,
            )
        }
        val criterion = SkillMath.Criterion(
            isHold = step.criterionType == CriterionType.HOLD,
            sets = step.criterionSets,
            value = step.criterionValue,
        )
        if (!SkillMath.sessionMeets(attempts, criterion)) return false
        val achievement = SkillStepAchievement(
            stepId = step.id,
            sessionId = sessionId,
            achievedAt = System.currentTimeMillis(),
        )
        return skillDao.insertAchievement(achievement) != -1L
    }

    /**
     * Confirms the suggested promotion: advances the skill to its next step
     * (never happens automatically, App_Konzept.md 5.2.2).
     */
    suspend fun confirmPromotion(skillId: String) {
        val progress = skillDao.progressOnce(skillId) ?: return
        val steps = skillDao.stepsOfSkillOnce(skillId)
        val next = steps.getOrNull(steps.indexOfFirst { it.id == progress.currentStepId } + 1)
            ?: return
        skillDao.upsertProgress(
            SkillProgress(
                skillId = skillId,
                currentStepId = next.id,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    // ---- Session lifecycle ---------------------------------------------

    /** Session, workout title and stations for the interval workout screen. */
    suspend fun strengthSessionInfo(sessionId: String): CsStrengthInfo? {
        val session = sportDao.sessionOnce(sessionId) ?: return null
        val sessionExercises = sportDao.sessionExercisesOnce(sessionId)
        if (sessionExercises.isEmpty()) return null
        val exercises = sportDao.exercisesByIds(sessionExercises.map { it.exerciseId })
            .associateBy { it.id }
        val routine = session.plannedWorkoutId
            ?.let { sportDao.routineOnce(it) }
            .orEmpty()
        val title = session.plannedWorkoutId
            ?.let { sportDao.workoutById(it) }
            ?.title
        return CsStrengthInfo(
            session = session,
            title = title,
            stations = sessionExercises.map { se ->
                val exercise = exercises[se.exerciseId]
                StrengthStation(
                    exerciseId = se.exerciseId,
                    sessionExerciseId = se.id,
                    name = exercise?.name ?: se.exerciseId,
                    targetReps = routine.firstOrNull { it.exerciseId == se.exerciseId }
                        ?.targetReps ?: 8,
                    measureType = exercise?.measureType ?: MeasureType.REPS,
                )
            },
        )
    }

    /**
     * Starts a calisthenics strength session from the plan's routine —
     * classic sets ([SessionMode.SETS], shown by the generic session
     * screen) or an interval mode (shown by the interval workout screen).
     * Resumes an active calisthenics session instead of creating a second.
     * Returns null when the plan has no routine yet.
     */
    suspend fun startStrengthSession(
        mode: SessionMode,
        rounds: Int? = null,
        workSec: Int? = null,
        restSec: Int? = null,
        intervalSec: Int? = null,
    ): String? {
        sportDao.activeSessionOnce(Discipline.CALISTHENICS)?.let { return it.id }

        val plan = sportDao.activePlanOnce(Discipline.CALISTHENICS) ?: return null
        val workout = sportDao.firstWorkoutOnce(plan.id) ?: return null
        val routine = sportDao.routineOnce(workout.id).orEmpty()
        if (routine.isEmpty()) return null

        val sessionId = newUuid()
        sportDao.insertSession(
            WorkoutSession(
                id = sessionId,
                discipline = Discipline.CALISTHENICS,
                plannedWorkoutId = workout.id,
                startedAt = System.currentTimeMillis(),
                mode = mode,
                rounds = rounds,
                workSec = workSec,
                restSec = restSec,
                intervalSec = intervalSec,
            ),
        )
        sportDao.insertSessionExercises(
            routine.map { target ->
                SessionExercise(
                    id = newUuid(),
                    sessionId = sessionId,
                    exerciseId = target.exerciseId,
                    sortOrder = target.sortOrder,
                )
            },
        )
        if (mode == SessionMode.SETS) {
            // Classic sets: prefill the target sets so the generic session
            // screen works exactly like the gym one.
            val exercisesByIds = sportDao.exercisesByIds(routine.map { it.exerciseId })
                .associateBy { it.id }
            val sessionExercises = sportDao.sessionExercisesOnce(sessionId)
            val initialSets = mutableListOf<SetEntry>()
            for (se in sessionExercises) {
                val target = routine.firstOrNull { it.exerciseId == se.exerciseId }
                val measure = exercisesByIds[se.exerciseId]?.measureType
                repeat(target?.targetSets ?: 3) { index ->
                    initialSets += SetEntry(
                        id = newUuid(),
                        sessionExerciseId = se.id,
                        sortOrder = index,
                        type = SetType.WORKING,
                        reps = if (measure == MeasureType.HOLD || measure == MeasureType.NEGATIVE) {
                            0
                        } else {
                            target?.targetReps ?: 8
                        },
                        durationSec = if (measure == MeasureType.HOLD || measure == MeasureType.NEGATIVE) {
                            target?.targetReps
                        } else {
                            null
                        },
                    )
                }
            }
            sportDao.insertSets(initialSets)
        }
        // Interval modes log one set per completed work phase/minute;
        // the interval screen syncs them while the timer runs.
        return sessionId
    }

    /**
     * Logs the sets for all completed circuit work phases that are not in
     * the database yet (idempotent — safe to call every tick). [elapsedSec]
     * comes from the session's timer, so pauses are respected.
     */
    suspend fun syncCircuitSets(sessionId: String, elapsedSec: Long) {
        val session = sportDao.sessionOnce(sessionId) ?: return
        if (session.mode != SessionMode.CIRCUIT) return
        val sessionExercises = sportDao.sessionExercisesOnce(sessionId)
        if (sessionExercises.isEmpty()) return
        val target = IntervalMath.completedWorks(
            elapsedSec = elapsedSec,
            stations = sessionExercises.size,
            workSec = session.workSec ?: 40,
            restSec = session.restSec ?: 20,
            rounds = session.rounds ?: 4,
        )
        val existing = skillDao.countSetsOfSession(sessionId)
        insertIntervalSets(sessionId, sessionExercises, session, from = existing, until = target)
    }

    /**
     * Logs the sets for all completed EMOM minutes that are not in the
     * database yet (station rotates by minute index).
     */
    suspend fun syncEmomSets(sessionId: String, elapsedSec: Long) {
        val session = sportDao.sessionOnce(sessionId) ?: return
        if (session.mode != SessionMode.EMOM) return
        val sessionExercises = sportDao.sessionExercisesOnce(sessionId)
        if (sessionExercises.isEmpty()) return
        val target = IntervalMath.completedMinutes(
            elapsedSec = elapsedSec,
            minutes = session.rounds ?: 10,
            intervalSec = session.intervalSec ?: 60,
        )
        val existing = skillDao.countSetsOfSession(sessionId)
        insertIntervalSets(sessionId, sessionExercises, session, from = existing, until = target)
    }

    /** Completed AMRAP rounds (one set per station per round). */
    suspend fun amrapRounds(sessionId: String): Int {
        val stations = sportDao.sessionExercisesOnce(sessionId).size
        if (stations == 0) return 0
        return skillDao.countSetsOfSession(sessionId) / stations
    }

    /** Logs one finished AMRAP round: a completed set for every station. */
    suspend fun logAmrapRound(sessionId: String): Int {
        val session = sportDao.sessionOnce(sessionId) ?: return 0
        if (session.mode != SessionMode.AMRAP) return 0
        val sessionExercises = sportDao.sessionExercisesOnce(sessionId)
        if (sessionExercises.isEmpty()) return 0
        val existing = skillDao.countSetsOfSession(sessionId)
        insertIntervalSets(sessionId, sessionExercises, session, from = existing, until = existing + sessionExercises.size)
        return (existing / sessionExercises.size) + 1
    }

    /** Removes the last logged AMRAP round (mis-tap). */
    suspend fun undoAmrapRound(sessionId: String): Int {
        val stations = sportDao.sessionExercisesOnce(sessionId).size
        if (stations == 0) return 0
        val existing = skillDao.countSetsOfSession(sessionId)
        if (existing < stations) return 0
        skillDao.deleteLastSetsOfSession(sessionId, stations)
        return (existing - stations) / stations
    }

    private suspend fun insertIntervalSets(
        sessionId: String,
        sessionExercises: List<SessionExercise>,
        session: WorkoutSession,
        from: Int,
        until: Int,
    ) {
        if (until <= from) return
        val routine = session.plannedWorkoutId
            ?.let { sportDao.routineOnce(it) }
            .orEmpty()
        val exercisesByIds = sportDao.exercisesByIds(sessionExercises.map { it.exerciseId })
            .associateBy { it.id }
        val sets = (from until until).map { index ->
            val se = sessionExercises[index % sessionExercises.size]
            val target = routine.firstOrNull { it.exerciseId == se.exerciseId }
            val measure = exercisesByIds[se.exerciseId]?.measureType
            val isHold = measure == MeasureType.HOLD || measure == MeasureType.NEGATIVE
            SetEntry(
                id = newUuid(),
                sessionExerciseId = se.id,
                sortOrder = index,
                type = SetType.WORKING,
                reps = if (isHold) 0 else (target?.targetReps ?: 8),
                durationSec = if (isHold) {
                    target?.targetReps ?: session.workSec
                } else {
                    null
                },
                completed = true,
            )
        }
        sportDao.insertSets(sets)
        // Sets are appended with a running sortOrder; the station order
        // follows index % stations, which matches the round order on screen.
    }

    /**
     * Resumes the active calisthenics session or starts a skill session for
     * [skillId] (one SessionExercise for the current step, sets prefilled
     * with the criterion). Returns null when the skill has no step yet.
     */
    suspend fun startSkillSession(skillId: String): String? {
        sportDao.activeSessionOnce(Discipline.CALISTHENICS)?.let { return it.id }
        val step = currentStepOnce(skillId) ?: return null

        val sessionId = newUuid()
        sportDao.insertSession(
            WorkoutSession(
                id = sessionId,
                discipline = Discipline.CALISTHENICS,
                startedAt = System.currentTimeMillis(),
            ),
        )
        val sessionExerciseId = newUuid()
        sportDao.insertSessionExercises(
            listOf(
                SessionExercise(
                    id = sessionExerciseId,
                    sessionId = sessionId,
                    exerciseId = step.exerciseId,
                    sortOrder = 0,
                ),
            ),
        )
        val isHold = step.criterionType == CriterionType.HOLD
        sportDao.insertSets(
            (0 until step.criterionSets).map { index ->
                SetEntry(
                    id = newUuid(),
                    sessionExerciseId = sessionExerciseId,
                    sortOrder = index,
                    type = SetType.WORKING,
                    reps = if (isHold) 0 else step.criterionValue,
                    durationSec = if (isHold) step.criterionValue else null,
                    completed = false,
                )
            },
        )
        return sessionId
    }

    fun observeSession(id: String): Flow<WorkoutSession?> = sportDao.observeSession(id)

    suspend fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> =
        sportDao.observeSessionExercises(sessionId)

    fun observeSets(sessionExerciseId: String): Flow<List<SetEntry>> =
        sportDao.observeSets(sessionExerciseId)

    suspend fun upsertSet(set: SetEntry) = sportDao.upsertSet(set)

    /** Adds the next set (copying the previous set's values). */
    suspend fun addSet(sessionExerciseId: String) {
        val existing = sportDao.setsOnce(sessionExerciseId)
        val previous = existing.lastOrNull()
        sportDao.insertSets(
            listOf(
                SetEntry(
                    id = newUuid(),
                    sessionExerciseId = sessionExerciseId,
                    sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                    type = SetType.WORKING,
                    reps = previous?.reps ?: 0,
                    durationSec = previous?.durationSec,
                    completed = false,
                ),
            ),
        )
    }

    suspend fun endSession(sessionId: String) {
        val session = sportDao.sessionOnce(sessionId)
        val now = System.currentTimeMillis()
        // Left open for hours: estimate ~3 min per ticked set instead of the whole day.
        val end = if (session != null && now - session.startedAt > 4 * 60 * 60_000L) {
            val sets = sportDao.sessionExercisesOnce(sessionId).sumOf { sportDao.setsOnce(it.id).count { s -> s.completed } }
            session.startedAt + (sets * 3 * 60_000L).coerceIn(15 * 60_000L, 2 * 60 * 60_000L)
        } else {
            now
        }
        sportDao.endSession(sessionId, end)
        healthWriter.writeWorkout(sessionId)
    }

    /** Discards the active calisthenics session incl. exercises and sets. */
    suspend fun discardActiveSession() {
        val sessionId = sportDao.activeSessionOnce(Discipline.CALISTHENICS)?.id ?: return
        sportDao.deleteSetsOfSession(sessionId)
        sportDao.deleteSessionExercises(sessionId)
        sportDao.deleteSession(sessionId)
    }

    private data class TreeState(
        val skills: List<Skill>,
        val steps: List<SkillStep>,
        val progress: List<SkillProgress>,
        val achievements: List<SkillStepAchievement>,
        val bests: List<SkillDao.ExerciseBest>,
    )

    private data class SessionShell(
        val plan: TrainingPlan?,
        val active: WorkoutSession?,
        val finished: List<WorkoutSession>,
        val exercises: List<Exercise>,
    )

    // ---- Seed data -------------------------------------------------------

    private fun seedSkills() = listOf(
        Skill("sk-fl", "Front Lever", SkillCategory.PULL, 0),
        Skill("sk-hs", "Handstand", SkillCategory.BALANCE, 1),
        Skill("sk-mu", "Muscle-up", SkillCategory.PULL, 2),
        Skill("sk-pistol", "Pistol Squat", SkillCategory.LEGS, 3),
        Skill("sk-planche", "Planche", SkillCategory.PUSH, 4),
        Skill("sk-oap", "Liegestütz einarmig", SkillCategory.PUSH, 5),
    )

    private fun hold(
        id: String,
        skillId: String,
        label: String,
        sortOrder: Int,
        exerciseId: String,
        sets: Int,
        seconds: Int,
    ) = SkillStep(
        id = id,
        skillId = skillId,
        label = label,
        sortOrder = sortOrder,
        exerciseId = exerciseId,
        criterionType = CriterionType.HOLD,
        criterionSets = sets,
        criterionValue = seconds,
    )

    private fun reps(
        id: String,
        skillId: String,
        label: String,
        sortOrder: Int,
        exerciseId: String,
        sets: Int,
        count: Int,
    ) = SkillStep(
        id = id,
        skillId = skillId,
        label = label,
        sortOrder = sortOrder,
        exerciseId = exerciseId,
        criterionType = CriterionType.REPS,
        criterionSets = sets,
        criterionValue = count,
    )

    private fun seedSteps() = listOf(
        // Front Lever: Tuck -> Advanced Tuck -> One Leg -> Straddle -> Full
        hold("st-fl-1", "sk-fl", "Tuck", 0, "ex-fl-tuck", 3, 10),
        hold("st-fl-2", "sk-fl", "Advanced Tuck", 1, "ex-fl-adv", 3, 10),
        hold("st-fl-3", "sk-fl", "One Leg", 2, "ex-fl-oneleg", 3, 8),
        hold("st-fl-4", "sk-fl", "Straddle", 3, "ex-fl-straddle", 3, 6),
        hold("st-fl-5", "sk-fl", "Full", 4, "ex-fl-full", 3, 5),
        // Handstand: Wand -> Kick-up -> freier Handstand
        hold("st-hs-1", "sk-hs", "Wand, Bauch zur Wand", 0, "ex-hs-wall1", 3, 30),
        hold("st-hs-2", "sk-hs", "Wand, Brust zur Wand", 1, "ex-hs-wall2", 3, 45),
        reps("st-hs-3", "sk-hs", "Kick-up", 2, "ex-hs-kickup", 3, 5),
        hold("st-hs-4", "sk-hs", "Freier Handstand", 3, "ex-hs-free", 3, 10),
        // Muscle-up: Klimmzüge -> zur Brust -> negativ -> Muscle-up
        reps("st-mu-1", "sk-mu", "Klimmzüge", 0, "ex-mu-pullup", 3, 10),
        reps("st-mu-2", "sk-mu", "Klimmzug zur Brust", 1, "ex-mu-chest", 3, 6),
        reps("st-mu-3", "sk-mu", "Negative Muscle-up", 2, "ex-mu-negative", 3, 3),
        reps("st-mu-4", "sk-mu", "Muscle-up", 3, "ex-mu-up", 3, 3),
        // Pistol Squat: Kniebeuge -> assisted -> Box -> Pistol
        reps("st-ps-1", "sk-pistol", "Kniebeuge", 0, "ex-ps-squat", 3, 20),
        reps("st-ps-2", "sk-pistol", "Assisted Pistol", 1, "ex-ps-assisted", 3, 8),
        reps("st-ps-3", "sk-pistol", "Box Pistol", 2, "ex-ps-box", 3, 6),
        reps("st-ps-4", "sk-pistol", "Pistol", 3, "ex-ps-full", 3, 5),
        // Planche: Lean -> Tuck -> Advanced Tuck -> Straddle
        hold("st-pl-1", "sk-planche", "Planche Lean", 0, "ex-pl-lean", 3, 20),
        hold("st-pl-2", "sk-planche", "Tuck Planche", 1, "ex-pl-tuck", 3, 10),
        hold("st-pl-3", "sk-planche", "Advanced Tuck", 2, "ex-pl-adv", 3, 8),
        hold("st-pl-4", "sk-planche", "Straddle", 3, "ex-pl-straddle", 3, 5),
        // One-Arm Push-up: Liegestütze -> Archer -> einarmig
        reps("st-oap-1", "sk-oap", "Liegestütz", 0, "ex-oap-pushup", 3, 20),
        reps("st-oap-2", "sk-oap", "Breite Liegestütz", 1, "ex-oap-wide", 3, 15),
        reps("st-oap-3", "sk-oap", "Archer Push-up", 2, "ex-oap-archer", 3, 8),
        reps("st-oap-4", "sk-oap", "Einarmig", 3, "ex-oap-full", 3, 3),
    )

    private fun ex(
        id: String,
        name: String,
        primary: String,
        secondary: String,
        equipment: String = "Körpergewicht",
        measureType: MeasureType = MeasureType.REPS,
    ) = Exercise(
        id = id,
        name = name,
        discipline = Discipline.CALISTHENICS,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = equipment,
        measureType = measureType,
    )

    private fun seedExercises() = listOf(
        // Front Lever
        ex("ex-fl-tuck", "Tuck Front Lever", "Rücken", "Bauch,Arme", measureType = MeasureType.HOLD),
        ex("ex-fl-adv", "Advanced Tuck Front Lever", "Rücken", "Bauch,Arme", measureType = MeasureType.HOLD),
        ex("ex-fl-oneleg", "One Leg Front Lever", "Rücken", "Bauch,Arme", measureType = MeasureType.HOLD),
        ex("ex-fl-straddle", "Straddle Front Lever", "Rücken", "Bauch,Arme", measureType = MeasureType.HOLD),
        ex("ex-fl-full", "Front Lever", "Rücken", "Bauch,Arme", measureType = MeasureType.HOLD),
        // Handstand
        ex("ex-hs-wall1", "Handstand Wand (Bauch zur Wand)", "Schultern", "Bauch", measureType = MeasureType.HOLD),
        ex("ex-hs-wall2", "Handstand Wand (Brust zur Wand)", "Schultern", "Bauch", measureType = MeasureType.HOLD),
        ex("ex-hs-kickup", "Handstand Kick-up", "Schultern", "Beine"),
        ex("ex-hs-free", "Freier Handstand", "Schultern", "Bauch", measureType = MeasureType.HOLD),
        // Muscle-up
        ex("ex-mu-pullup", "Klimmzüge", "Rücken", "Arme", equipment = "Stange"),
        ex("ex-mu-chest", "Klimmzug zur Brust", "Rücken", "Arme", equipment = "Stange"),
        ex("ex-mu-negative", "Negative Muscle-up", "Rücken", "Arme,Brust", equipment = "Stange", measureType = MeasureType.NEGATIVE),
        ex("ex-mu-up", "Muscle-up", "Rücken", "Arme,Brust", equipment = "Stange"),
        // Pistol Squat
        ex("ex-ps-squat", "Kniebeuge", "Beine", "Bauch"),
        ex("ex-ps-assisted", "Assisted Pistol (Band)", "Beine", "Bauch", equipment = "Band"),
        ex("ex-ps-box", "Box Pistol", "Beine", "Bauch"),
        ex("ex-ps-full", "Pistol Squat", "Beine", "Bauch"),
        // Planche
        ex("ex-pl-lean", "Planche Lean", "Schultern", "Bauch", measureType = MeasureType.HOLD),
        ex("ex-pl-tuck", "Tuck Planche", "Schultern", "Bauch", measureType = MeasureType.HOLD),
        ex("ex-pl-adv", "Advanced Tuck Planche", "Schultern", "Bauch", measureType = MeasureType.HOLD),
        ex("ex-pl-straddle", "Straddle Planche", "Schultern", "Bauch", measureType = MeasureType.HOLD),
        // One-Arm Push-up
        ex("ex-oap-pushup", "Liegestütz", "Brust", "Arme"),
        ex("ex-oap-wide", "Breite Liegestütz", "Brust", "Arme"),
        ex("ex-oap-archer", "Archer Push-up", "Brust", "Arme"),
        ex("ex-oap-full", "Liegestütz einarmig", "Brust", "Arme"),
        // Strength block of the default plan
        ex("ex-cs-dips", "Dips", "Brust", "Schultern", equipment = "Barren"),
        ex("ex-cs-hollow", "Hollow Body Hold", "Bauch", "Beine", measureType = MeasureType.HOLD),
        // Easier variants for the setup (max 0)
        ex("ex-cs-row", "Australian Rows", "Rücken", "Arme", equipment = "Stange/Ringe"),
        ex("ex-cs-knee-pushup", "Knie-Liegestütz", "Brust", "Arme"),
        ex("ex-cs-bench-dips", "Bankdips", "Trizeps", "Brust", equipment = "Bank"),
    )

    private fun seedRoutine() = listOf(
        RoutineExercise(WORKOUT_ID, "ex-mu-pullup", sortOrder = 0, targetSets = 4, targetReps = 8, restSec = 180),
        RoutineExercise(WORKOUT_ID, "ex-cs-dips", sortOrder = 1, targetSets = 4, targetReps = 10, restSec = 120),
        RoutineExercise(WORKOUT_ID, "ex-ps-squat", sortOrder = 2, targetSets = 4, targetReps = 12, restSec = 120),
        RoutineExercise(WORKOUT_ID, "ex-cs-hollow", sortOrder = 3, targetSets = 3, targetReps = 20, restSec = 60),
    )

    private companion object {
        const val PLAN_ID = "cs-plan-default"
        const val WORKOUT_ID = "cs-workout-default"
    }
}
