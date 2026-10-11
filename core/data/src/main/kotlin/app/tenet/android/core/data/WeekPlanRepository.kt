package app.tenet.android.core.data

import app.tenet.android.core.common.WeekMath
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.TrainingPlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The week across all sports: which weekdays the active gym, calisthenics and
 * running plans train on – and keeping them in step with the shared rest days
 * and the rules between them ("calisthenics only on days without a run").
 * It works on whatever plan is active, however it was made (setup, AI coach,
 * built by hand): the days live in the plans, the rules are applied to them.
 */
@Singleton
class WeekPlanRepository @Inject constructor(
    private val sportDao: SportDao,
) {
    enum class Rule(val label: String, val detail: String) {
        CALI_NOT_RUN("Calisthenics nur an lauffreien Tagen", "An Lauftagen kein Calisthenics"),
        GYM_NOT_RUN("Gym nur an lauffreien Tagen", "An Lauftagen kein Gym"),
        GYM_NOT_CALI("Gym und Calisthenics nicht am selben Tag", "Gym hat Vorrang, Calisthenics weicht aus"),
    }

    /** Weekdays as ISO numbers 1 (Mo) … 7 (So). [gym]/[cali] null = no active plan; empty = no fixed days. */
    data class Week(
        val gym: Set<Int>?,
        val cali: Set<Int>?,
        /** Running days of the current plan week; null = no running plan. */
        val run: Set<Int>?,
        val gymPlan: TrainingPlan? = null,
        val caliPlan: TrainingPlan? = null,
        val runPlan: TrainingPlan? = null,
    )

    private fun days(plan: TrainingPlan?): Set<Int>? =
        plan?.let { p -> p.trainingDays.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet() }

    /** Plan week (0-based) that [today] falls into; before the start = 0. */
    private fun planWeek(plan: TrainingPlan, today: LocalDate): Int {
        val start = plan.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 0
        return (ChronoUnit.DAYS.between(WeekMath.weekStart(start), today) / 7).toInt().coerceAtLeast(0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<Week> = combine(
        sportDao.observeActivePlan(Discipline.GYM),
        sportDao.observeActivePlan(Discipline.CALISTHENICS),
        sportDao.observeActivePlan(Discipline.RUNNING),
    ) { gym, cali, run -> Triple(gym, cali, run) }.flatMapLatest { (gym, cali, run) ->
        val units = if (run == null) flowOf(emptyList()) else sportDao.observeWorkoutsOfPlan(run.id)
        units.combine(flowOf(Unit)) { list, _ ->
            val runDays = run?.let { plan ->
                val today = LocalDate.now()
                val week = planWeek(plan, today)
                val normal = list.filter { it.sortOrder != 99 && it.weekIndex != null && it.dayIndex != null }
                // The coming full week shows the pattern (this week may be half over).
                val probe = normal.filter { it.weekIndex == week + 1 }.ifEmpty { normal.filter { it.weekIndex == week } }
                probe.map { it.dayIndex!! + 1 }.toSet()
            }
            Week(days(gym), days(cali), runDays, gym, cali, run)
        }
    }

    /** Training days of the active gym or calisthenics plan. */
    suspend fun setDays(discipline: Discipline, isoDays: Set<Int>) {
        val plan = sportDao.activePlanOnce(discipline) ?: return
        sportDao.setTrainingDays(plan.id, isoDays.filter { it in 1..7 }.sorted().joinToString(",").ifEmpty { null })
    }

    /** Moves the runs of the active running plan from one weekday to another (rest of the plan). */
    suspend fun moveRunDay(fromIso: Int, toIso: Int) {
        val plan = sportDao.activePlanOnce(Discipline.RUNNING) ?: return
        remapRunDays(plan, mapOf(fromIso to toIso))
    }

    private suspend fun remapRunDays(plan: TrainingPlan, moves: Map<Int, Int>) {
        val today = LocalDate.now()
        val week = planWeek(plan, today)
        val todayIndex = today.dayOfWeek.value - 1
        // Two steps over a parking value, so swaps (Di → Do while Do → Di) do not collide.
        moves.forEach { (from, _) -> sportDao.movePlanDay(plan.id, from - 1, from - 1 + PARK, week, todayIndex) }
        moves.forEach { (from, to) -> sportDao.movePlanDay(plan.id, from - 1 + PARK, to - 1, week, -1) }
    }

    /**
     * Brings the active plans in line with [rest] days and [rules]; returns true when
     * something changed. Running goes first (its runs leave rest days for the nearest
     * free day), then gym, then calisthenics, which gives way to both.
     */
    suspend fun enforce(rest: Set<Int>, rules: Set<Rule>): Boolean {
        val week = observe().first()
        var changed = false

        // Running: no run on a rest day.
        var run = week.run
        if (run != null && week.runPlan != null) {
            val moves = mutableMapOf<Int, Int>()
            val taken = run.toMutableSet()
            run.filter { it in rest }.sorted().forEach { day ->
                val free = (1..7).filter { it !in rest && it !in taken }.minByOrNull { kotlin.math.abs(it - day) } ?: return@forEach
                moves[day] = free
                taken -= day
                taken += free
            }
            if (moves.isNotEmpty()) {
                remapRunDays(week.runPlan, moves)
                run = taken
                changed = true
            }
        }
        val runDays = run.orEmpty()

        fun allowed(blocked: Set<Int>) = (1..7).filter { it !in blocked }.toSet()

        /** New days for a plan, or null when it already fits. Empty stored days mean "every day". */
        fun fit(current: Set<Int>, blocked: Set<Int>): Set<Int>? {
            if (blocked.isEmpty()) return null
            val open = allowed(blocked)
            if (open.isEmpty()) return null
            val base = current.ifEmpty { (1..7).toSet() }
            val next = (base - blocked).ifEmpty { open }
            return next.takeIf { it != current }
        }

        var gym = week.gym
        if (gym != null) {
            val blocked = rest + if (Rule.GYM_NOT_RUN in rules) runDays else emptySet()
            fit(gym, blocked)?.let {
                setDays(Discipline.GYM, it)
                gym = it
                changed = true
            }
        }
        val cali = week.cali
        if (cali != null) {
            val blocked = rest +
                (if (Rule.CALI_NOT_RUN in rules) runDays else emptySet()) +
                // "Every day" gym would leave calisthenics no day at all: then gym does not block.
                (if (Rule.GYM_NOT_CALI in rules) gym.orEmpty() else emptySet())
            fit(cali, blocked)?.let {
                setDays(Discipline.CALISTHENICS, it)
                changed = true
            }
        }
        return changed
    }

    /** Days a sport may not train on under [rest] and [rules] (for greying them out). */
    fun blocked(discipline: Discipline, week: Week, rest: Set<Int>, rules: Set<Rule>): Set<Int> = when (discipline) {
        Discipline.RUNNING -> rest
        Discipline.GYM -> rest + if (Rule.GYM_NOT_RUN in rules) week.run.orEmpty() else emptySet()
        Discipline.CALISTHENICS -> rest +
            (if (Rule.CALI_NOT_RUN in rules) week.run.orEmpty() else emptySet()) +
            (if (Rule.GYM_NOT_CALI in rules) week.gym.orEmpty() else emptySet())
    }

    private companion object {
        const val PARK = 100
    }
}
