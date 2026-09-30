package app.tenet.android.core.common

/**
 * Builds a gym plan from the first-run setup: split and routines for the
 * chosen days, sets/reps for the goal and start weights from the user's
 * current strength (Epley 1RM from "weight × reps"; unknown lifts are
 * estimated from bodyweight and training level). Pure, unit tested.
 */
object GymPlanBuilder {

    enum class Goal(val label: String, val description: String, val mainReps: Int, val accReps: Int, val mainRestSec: Int, val accRestSec: Int) {
        STRENGTH("Kraft", "Schwer, wenige Wiederholungen (3–6)", 5, 8, 180, 120),
        HYPERTROPHY("Muskelaufbau", "Mittlere Last, 8–12 Wiederholungen", 8, 12, 150, 90),
        FITNESS("Fit & gesund", "Leichter, mehr Wiederholungen", 12, 15, 90, 60),
    }

    enum class Level(val label: String, val description: String) {
        BEGINNER("Einsteiger", "Unter 6 Monate regelmäßig"),
        INTERMEDIATE("Fortgeschritten", "6 Monate bis 2 Jahre"),
        ADVANCED("Erfahren", "Mehr als 2 Jahre"),
    }

    enum class Split(val label: String, val description: String) {
        FULL_BODY("Ganzkörper", "Jede Einheit trainiert alles · ideal für 2–3 Tage"),
        UPPER_LOWER("Oberkörper / Unterkörper", "Im Wechsel · ideal für 4 Tage"),
        PPL("Push / Pull / Beine", "Drücken, Ziehen, Beine · ideal für 3, 5 oder 6 Tage"),
    }

    /** How a lift is loaded; decides what the weight field means. */
    enum class Load {
        BARBELL,
        /** Weight per dumbbell (per hand). */
        DUMBBELL,
        /** Body weight plus a belt/vest: the weight field is the extra weight, 0 = bodyweight. */
        BODYWEIGHT_PLUS,
    }

    /**
     * Lifts asked in the setup, with typical 1RM relative to bodyweight per
     * level (used when the user leaves a lift empty). Dumbbell ratios are per
     * hand; bodyweight-plus ratios are the total load (body + extra).
     */
    enum class Lift(
        val exerciseId: String,
        val label: String,
        val ratios: Triple<Float, Float, Float>,
        val upper: Boolean,
        val load: Load = Load.BARBELL,
        /** Accessory: trained with the goal's accessory reps. */
        val accessory: Boolean = false,
    ) {
        BENCH("ex-bankdruecken", "Bankdrücken", Triple(0.6f, 1.0f, 1.3f), true),
        SQUAT("ex-kniebeugen", "Kniebeugen", Triple(0.8f, 1.3f, 1.7f), false),
        DEADLIFT("ex-kreuzheben", "Kreuzheben", Triple(1.0f, 1.6f, 2.0f), false),
        OHP("ex-kh-schulter", "Schulterdrücken (Kurzhantel)", Triple(0.16f, 0.26f, 0.34f), true, Load.DUMBBELL),
        ROW("ex-rudern", "Rudern (Langhantel)", Triple(0.5f, 0.85f, 1.1f), true),
        PULLUP("ex-klimmzuege", "Klimmzüge", Triple(1.0f, 1.35f, 1.6f), true, Load.BODYWEIGHT_PLUS),
        DIPS("ex-g-dips", "Dips", Triple(1.0f, 1.45f, 1.75f), true, Load.BODYWEIGHT_PLUS),
        LATERAL("ex-seitheben", "Seitheben", Triple(0.08f, 0.13f, 0.17f), true, Load.DUMBBELL, accessory = true),
    }

    data class LiftInput(val weightKg: Float, val reps: Int)

    data class Input(
        val goal: Goal,
        val level: Level,
        val split: Split,
        /** ISO weekdays 1 (Mo) … 7 (So). */
        val days: List<Int>,
        val bodyweightKg: Float?,
        val female: Boolean = false,
        val lifts: Map<Lift, LiftInput> = emptyMap(),
    )

    data class PlannedExercise(
        val exerciseId: String,
        val sets: Int,
        /** Reps, or seconds for timed exercises (plank). */
        val reps: Int,
        val restSec: Int,
        /** Suggested weight for the first session; null = bodyweight / choose yourself. */
        val startWeightKg: Float?,
    )

    data class Routine(val title: String, val exercises: List<PlannedExercise>)

    data class Plan(
        val name: String,
        val routines: List<Routine>,
        /** Estimated 1RM per lift (entered or estimated). */
        val oneRepMax: Map<Lift, Float>,
        /** Lifts whose 1RM was estimated, not entered. */
        val estimated: Set<Lift>,
    )

    fun recommendedSplit(days: Int): Split = when {
        days <= 3 -> Split.FULL_BODY
        days == 4 -> Split.UPPER_LOWER
        else -> Split.PPL
    }

    private const val DEFAULT_BODYWEIGHT = 75f
    private const val DEFAULT_BODYWEIGHT_FEMALE = 62f

    private fun bodyweight(input: Input) = input.bodyweightKg ?: if (input.female) DEFAULT_BODYWEIGHT_FEMALE else DEFAULT_BODYWEIGHT

    /** 1RM per lift; for [Load.BODYWEIGHT_PLUS] it is the total load (body + extra). */
    fun oneRepMaxes(input: Input): Pair<Map<Lift, Float>, Set<Lift>> {
        val bw = bodyweight(input)
        val estimated = mutableSetOf<Lift>()
        val map = Lift.entries.associateWith { lift ->
            val entered = input.lifts[lift]?.takeIf {
                it.reps in 1..30 && (it.weightKg > 0f || (lift.load == Load.BODYWEIGHT_PLUS && it.weightKg >= 0f))
            }
            if (entered != null) {
                val load = if (lift.load == Load.BODYWEIGHT_PLUS) bw + entered.weightKg else entered.weightKg
                OneRepMax.oneRepMax(load, entered.reps)
            } else {
                estimated += lift
                val ratio = when (input.level) {
                    Level.BEGINNER -> lift.ratios.first
                    Level.INTERMEDIATE -> lift.ratios.second
                    Level.ADVANCED -> lift.ratios.third
                }
                val sexFactor = if (input.female) (if (lift.upper) 0.6f else 0.75f) else 1f
                bw * ratio * sexFactor
            }
        }
        return map to estimated
    }

    /**
     * One setup lift in a routine. Bodyweight-plus lifts get the extra weight
     * (null = bodyweight); too weak for bodyweight reps → easier variant
     * (lat pulldown with a weight, bench dips).
     */
    private fun mainExercise(lift: Lift, oneRepMax: Float, bw: Float, goal: Goal, mainSets: Int, accSets: Int): PlannedExercise {
        val reps = when {
            lift.accessory -> goal.accReps
            lift.load == Load.BODYWEIGHT_PLUS -> goal.mainReps.coerceIn(5, 10)
            else -> goal.mainReps
        }
        val sets = when {
            lift.accessory -> accSets
            lift == Lift.DEADLIFT -> (mainSets - 1).coerceAtLeast(2) // heavy: one set less
            lift.load == Load.BODYWEIGHT_PLUS -> 3
            else -> mainSets
        }
        val rest = if (lift.accessory) goal.accRestSec else goal.mainRestSec
        if (lift.load != Load.BODYWEIGHT_PLUS) {
            return PlannedExercise(lift.exerciseId, sets, reps, rest, workingWeight(oneRepMax, reps))
        }
        val total = oneRepMax / (1f + (reps + 2) / 30f)
        return when {
            total >= bw -> {
                val extra = OverloadMath.round(total - bw, 2.5f).takeIf { total - bw >= 2.5f }
                PlannedExercise(lift.exerciseId, sets, reps, rest, extra)
            }
            lift == Lift.PULLUP -> PlannedExercise(LATZUG.exerciseId, sets, reps, rest, workingWeight(oneRepMax * 0.85f, reps))
            else -> PlannedExercise(BANKDIPS.exerciseId, sets, reps.coerceAtLeast(10), rest, null)
        }
    }

    /** Weight for [reps] with about two reps in reserve (inverse Epley). */
    fun workingWeight(oneRepMax: Float, reps: Int, step: Float = stepFor(oneRepMax)): Float {
        val raw = oneRepMax / (1f + (reps + 2) / 30f)
        return OverloadMath.round(raw, step)
    }

    private fun stepFor(weight: Float): Float = if (weight < 25f) 1f else 2.5f

    /** Accessory: base lift and share of its 1RM (per dumbbell where it applies). */
    private data class Accessory(val exerciseId: String, val base: Lift?, val ratio: Float, val timed: Boolean = false)

    private val SCHRAEGBANK = Accessory("ex-schraegbank", Lift.BENCH, 0.32f)
    private val TRIZEPS = Accessory("ex-trizepsdruecken", Lift.BENCH, 0.35f)
    private val BANKDIPS = Accessory("ex-g-bankdips", null, 0f)
    private val BIZEPS = Accessory("ex-bizepscurl", Lift.ROW, 0.2f)
    private val LATZUG = Accessory("ex-latzzug", Lift.ROW, 0.9f)
    private val FACEPULL = Accessory("ex-facepull", Lift.ROW, 0.3f)
    private val RDL = Accessory("ex-rdl", Lift.DEADLIFT, 0.65f)
    private val BEINPRESSE = Accessory("ex-beinpresse", Lift.SQUAT, 1.6f)
    private val BEINBEUGER = Accessory("ex-beinbeuger", Lift.SQUAT, 0.35f)
    private val WADEN = Accessory("ex-wadenheben", Lift.SQUAT, 0.7f)
    private val AUSFALL = Accessory("ex-ausfallschritte", Lift.SQUAT, 0.2f)
    private val PLANK = Accessory("ex-plank", null, 0f, timed = true)

    private sealed interface Slot
    private data class Main(val lift: Lift) : Slot
    private data class Acc(val accessory: Accessory) : Slot

    fun build(input: Input): Plan {
        val (orm, estimated) = oneRepMaxes(input)
        val pull = Main(Lift.PULLUP)
        val dips = Main(Lift.DIPS)
        val lateral = Main(Lift.LATERAL)
        val templates: List<Pair<String, List<Slot>>> = when (input.split) {
            Split.FULL_BODY -> listOf(
                "Ganzkörper A" to listOf(Main(Lift.SQUAT), Main(Lift.BENCH), Main(Lift.ROW), lateral, Acc(PLANK)),
                "Ganzkörper B" to listOf(Main(Lift.DEADLIFT), Main(Lift.OHP), pull, dips, Acc(AUSFALL)),
            )
            Split.UPPER_LOWER -> listOf(
                "Oberkörper" to listOf(Main(Lift.BENCH), pull, Main(Lift.OHP), Main(Lift.ROW), dips, lateral),
                "Unterkörper" to listOf(Main(Lift.SQUAT), Acc(RDL), Acc(BEINPRESSE), Acc(BEINBEUGER), Acc(WADEN), Acc(PLANK)),
            )
            Split.PPL -> listOf(
                "Push" to listOf(Main(Lift.BENCH), Main(Lift.OHP), Acc(SCHRAEGBANK), dips, lateral, Acc(TRIZEPS)),
                "Pull" to listOf(Main(Lift.DEADLIFT), pull, Main(Lift.ROW), Acc(FACEPULL), Acc(BIZEPS)),
                "Beine" to listOf(Main(Lift.SQUAT), Acc(RDL), Acc(BEINPRESSE), Acc(BEINBEUGER), Acc(WADEN)),
            )
        }
        val bw = bodyweight(input)
        val goal = input.goal
        val mainSets = if (input.level == Level.BEGINNER) 3 else 4
        val accSets = 3
        val routines = templates.map { (title, slots) ->
            Routine(
                title = title,
                exercises = slots.map { slot ->
                    when (slot) {
                        is Main -> mainExercise(slot.lift, orm.getValue(slot.lift), bw, goal, mainSets, accSets)
                        is Acc -> {
                            val a = slot.accessory
                            PlannedExercise(
                                exerciseId = a.exerciseId,
                                sets = accSets,
                                reps = when {
                                    a.timed -> 45
                                    else -> goal.accReps
                                },
                                restSec = goal.accRestSec,
                                startWeightKg = a.base?.let { workingWeight(orm.getValue(it) * a.ratio, goal.accReps) },
                            )
                        }
                    }
                },
            )
        }
        return Plan(
            // Every exercise once per routine (e.g. dips fallback = bench dips next to triceps).
            name = "${input.split.label} · ${goal.label}",
            routines = routines,
            oneRepMax = orm,
            estimated = estimated,
        )
    }
}
