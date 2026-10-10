package app.tenet.android.core.data

import app.tenet.android.core.common.newUuid
import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.PlannedWorkout
import app.tenet.android.core.database.entity.RoutineExercise
import app.tenet.android.core.database.entity.TrainingPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The plans of a discipline: list them with what is in them, switch, rename,
 * duplicate, delete, start an empty one or store a draft (from the AI chat or
 * a template). A new plan only deactivates the old one, so earlier plans can
 * be picked again. Statistics never depend on the plan – they read every
 * session of the discipline.
 */
@Singleton
class PlanRepository @Inject constructor(
    private val sportDao: SportDao,
    private val runDao: RunDao,
) {
    /** One exercise of a draft: catalogue id, sets × reps (or seconds for holds), rest. */
    data class DraftExercise(val exerciseId: String, val sets: Int, val reps: Int, val restSec: Int)

    data class DraftWorkout(val title: String, val exercises: List<DraftExercise>)

    /** A plan not stored yet (AI proposal, template). [days] = ISO weekdays 1–7. */
    data class Draft(val name: String, val days: List<Int>, val workouts: List<DraftWorkout>)

    /** A workout with its exercises by name, for overview and editor. */
    data class WorkoutSummary(val workout: PlannedWorkout, val routine: List<RoutineExercise>, val names: List<String>)

    data class PlanSummary(val plan: TrainingPlan, val workouts: List<WorkoutSummary>, val datedUnits: Int)

    fun observePlans(discipline: Discipline): Flow<List<TrainingPlan>> = sportDao.observePlans(discipline)

    suspend fun activate(discipline: Discipline, planId: String) = sportDao.activatePlan(discipline, planId)

    /** Every plan of [discipline] with its workouts and exercise names (active plan first). */
    fun observeSummaries(discipline: Discipline): Flow<List<PlanSummary>> =
        combine(
            sportDao.observePlans(discipline),
            sportDao.observeTemplateWorkouts(discipline),
            sportDao.observeRoutines(discipline),
            sportDao.observeExercises(discipline),
        ) { plans, workouts, routines, exercises ->
            val names = exercises.associate { it.id to it.name }
            plans.map { plan ->
                PlanSummary(
                    plan = plan,
                    workouts = workouts.filter { it.planId == plan.id }.map { w ->
                        val routine = routines.filter { it.plannedWorkoutId == w.id }.sortedBy { it.sortOrder }
                        WorkoutSummary(w, routine, routine.map { names[it.exerciseId] ?: "Übung" })
                    },
                    datedUnits = 0,
                )
            }
        }.flatMapLatest { list ->
            // Running plans consist of dated units instead of template workouts.
            if (discipline != Discipline.RUNNING || list.isEmpty()) flowOf(list)
            else combine(list.map { s -> sportDao.observeWorkoutCount(s.plan.id) }) { counts ->
                list.mapIndexed { i, s -> s.copy(datedUnits = counts[i]) }
            }
        }

    /** One plan with its workouts, live (for the editor). */
    fun observePlan(planId: String): Flow<PlanSummary?> =
        sportDao.observePlan(planId).flatMapLatest { plan ->
            if (plan == null) flowOf(null)
            else combine(
                sportDao.observeWorkoutsOfPlan(planId),
                sportDao.observeRoutines(plan.discipline),
                sportDao.observeExercises(plan.discipline),
            ) { workouts, routines, exercises ->
                val names = exercises.associate { it.id to it.name }
                PlanSummary(
                    plan,
                    workouts.map { w ->
                        val routine = routines.filter { it.plannedWorkoutId == w.id }.sortedBy { it.sortOrder }
                        WorkoutSummary(w, routine, routine.map { names[it.exerciseId] ?: "Übung" })
                    },
                    0,
                )
            }
        }

    suspend fun catalog(discipline: Discipline): List<Exercise> = sportDao.observeExercises(discipline).first()

    suspend fun rename(planId: String, name: String) {
        if (name.isNotBlank()) sportDao.renamePlan(planId, name.trim())
    }

    suspend fun setDays(planId: String, days: List<Int>) =
        sportDao.setTrainingDays(planId, days.distinct().sorted().joinToString(",").ifEmpty { null })

    /**
     * Deletes a plan with its workouts and routines. Finished sessions stay (their
     * statistics do not need the plan). When it was the active one, the newest
     * remaining plan takes over.
     */
    suspend fun delete(planId: String) {
        val plan = sportDao.planOnce(planId) ?: return
        if (plan.discipline == Discipline.RUNNING) {
            runDao.deletePlanWorkoutDetails(planId)
            runDao.deletePlanDetail(planId)
        }
        sportDao.deleteRoutinesOfPlan(planId)
        sportDao.deleteWorkoutsOfPlan(planId)
        sportDao.deletePlan(planId)
        if (plan.active) {
            sportDao.observePlans(plan.discipline).first().firstOrNull()?.let { sportDao.activatePlan(plan.discipline, it.id) }
        }
    }

    /** Copy of a gym or calisthenics plan (inactive), to vary it without losing the original. */
    suspend fun duplicate(planId: String): String? {
        val plan = sportDao.planOnce(planId) ?: return null
        if (plan.discipline == Discipline.RUNNING) return null
        val copyId = newUuid()
        sportDao.upsertPlan(plan.copy(id = copyId, name = plan.name + " (Kopie)", active = false))
        sportDao.workoutsOnce(planId).forEach { w ->
            val workoutId = newUuid()
            sportDao.upsertWorkout(w.copy(id = workoutId, planId = copyId))
            sportDao.upsertRoutineExercises(sportDao.routineOnce(w.id).map { it.copy(plannedWorkoutId = workoutId) })
        }
        return copyId
    }

    /** Stores [draft] as a new plan; [activate] makes it the plan in use. Returns its id. */
    suspend fun create(discipline: Discipline, draft: Draft, activate: Boolean): String {
        val first = sportDao.activePlanOnce(discipline) == null
        if (activate) sportDao.deactivatePlans(discipline)
        val planId = newUuid()
        sportDao.upsertPlan(
            TrainingPlan(
                id = planId,
                discipline = discipline,
                name = draft.name.trim().ifBlank { "Mein Plan" },
                goal = draft.name.trim().ifBlank { null },
                startDate = java.time.LocalDate.now().toString(),
                active = activate || first,
                trainingDays = draft.days.distinct().sorted().joinToString(",").ifEmpty { null },
            ),
        )
        draft.workouts.ifEmpty { listOf(DraftWorkout("Einheit A", emptyList())) }.forEachIndexed { index, w ->
            val workoutId = newUuid()
            sportDao.upsertWorkout(PlannedWorkout(id = workoutId, planId = planId, discipline = discipline, title = w.title.ifBlank { "Einheit ${index + 1}" }, sortOrder = index))
            sportDao.upsertRoutineExercises(
                w.exercises.distinctBy { it.exerciseId }.mapIndexed { order, e ->
                    RoutineExercise(
                        plannedWorkoutId = workoutId,
                        exerciseId = e.exerciseId,
                        sortOrder = order,
                        targetSets = e.sets.coerceIn(1, 10),
                        targetReps = e.reps.coerceIn(1, 600),
                        restSec = e.restSec.coerceIn(15, 600),
                    )
                },
            )
        }
        return planId
    }

    // ---- Editing a stored plan ----------------------------------------------------

    suspend fun addWorkout(planId: String, title: String): String? {
        val plan = sportDao.planOnce(planId) ?: return null
        val order = (sportDao.workoutsOnce(planId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = newUuid()
        sportDao.upsertWorkout(PlannedWorkout(id = id, planId = planId, discipline = plan.discipline, title = title.ifBlank { "Einheit ${order + 1}" }, sortOrder = order))
        return id
    }

    suspend fun renameWorkout(workoutId: String, title: String) {
        if (title.isNotBlank()) sportDao.renameWorkout(workoutId, title.trim())
    }

    /** A plan keeps at least one workout. */
    suspend fun deleteWorkout(planId: String, workoutId: String) {
        if (sportDao.workoutsOnce(planId).size <= 1) return
        sportDao.deleteRoutine(workoutId)
        sportDao.deleteWorkout(workoutId)
    }

    suspend fun addExercise(workoutId: String, exercise: Exercise, sets: Int = 3, reps: Int = 10, restSec: Int = 120) {
        val routine = sportDao.routineOnce(workoutId)
        if (routine.any { it.exerciseId == exercise.id }) return
        sportDao.upsertRoutineExercises(
            listOf(RoutineExercise(workoutId, exercise.id, (routine.maxOfOrNull { it.sortOrder } ?: -1) + 1, sets, reps, restSec)),
        )
    }

    suspend fun updateExercise(entry: RoutineExercise) = sportDao.upsertRoutineExercises(listOf(entry))

    suspend fun removeExercise(workoutId: String, exerciseId: String) = sportDao.deleteRoutineExercise(workoutId, exerciseId)

    /** Moves an exercise one place up or down inside its workout. */
    suspend fun moveExercise(workoutId: String, exerciseId: String, up: Boolean) {
        val routine = sportDao.routineOnce(workoutId).sortedBy { it.sortOrder }.toMutableList()
        val i = routine.indexOfFirst { it.exerciseId == exerciseId }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in routine.indices) return
        routine.add(j, routine.removeAt(i))
        sportDao.upsertRoutineExercises(routine.mapIndexed { order, e -> e.copy(sortOrder = order) })
    }
}
