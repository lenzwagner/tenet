package app.tenet.android.core.common

/**
 * Builds a gym plan from the first-run setup: split and routines for the
 * chosen days, sets/reps for the goal and start weights from the user's
 * current strength (selected 1RM formula from "weight × reps"; unknown lifts are
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
        UPPER_LOWER("2er-Split · Oberkörper / Unterkörper", "Zwei Einheiten im Wechsel · ideal für 4 Tage"),
        PPL("3er-Split · Push / Pull / Beine", "Drücken, Ziehen, Beine · ideal für 3, 5 oder 6 Tage"),
        FOUR_DAY("4er-Split · Brust / Rücken / Beine / Schultern", "Jede Muskelgruppe einmal pro Woche mit viel Volumen · ideal für 4 Tage"),
        CUSTOM("Eigener Split", "Muskelgruppen je Einheit selbst kombinieren"),
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
        BENCH("ex-bankdruecken", "Bankdrücken (LH, flach)", Triple(0.6f, 1.0f, 1.3f), true),
        INCLINE_BENCH("ex-schraegbank-lh", "Schrägbank (LH, 30°) · optional", Triple(0.6f * BenchStrength.INCLINE_30_RATIO, BenchStrength.INCLINE_30_RATIO, 1.3f * BenchStrength.INCLINE_30_RATIO), true),
        INCLINE_DUMBBELL("ex-schraegbank", "Schrägbank (KH, 30°) · optional", Triple(0.6f * BenchStrength.INCLINE_DUMBBELL_RATIO, BenchStrength.INCLINE_DUMBBELL_RATIO, 1.3f * BenchStrength.INCLINE_DUMBBELL_RATIO), true, Load.DUMBBELL),
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
        val formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
        /** The rotating workouts with their muscle groups when [split] is [Split.CUSTOM]. */
        val customUnits: List<GymSplits.SplitUnit> = emptyList(),
        /** Plan name instead of the split's label (e.g. "4er-Split"). */
        val customName: String? = null,
    )

    data class PlannedExercise(
        val exerciseId: String,
        val sets: Int,
        /** Reps, or seconds for timed exercises (plank). */
        val reps: Int,
        val restSec: Int,
        /** Suggested weight for the first session; null = bodyweight / choose yourself. */
        val startWeightKg: Float?,
        /** The rest was set by hand: it is kept instead of the advice per set. */
        val restFixed: Boolean = false,
    )

    data class Routine(val title: String, val exercises: List<PlannedExercise>)

    data class Plan(
        val name: String,
        val routines: List<Routine>,
        /** Estimated 1RM per lift (entered or estimated). */
        val oneRepMax: Map<Lift, Float>,
        /** Lifts whose 1RM was estimated, not entered. */
        val estimated: Set<Lift>,
        val benchEstimate: BenchStrength.Estimate? = null,
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
        val transfer = benchEstimate(input)
        val map = Lift.entries.associateWith { lift ->
            val entered = input.lifts[lift]?.takeIf {
                it.weightKg.isFinite() && it.reps in 1..30 && (it.weightKg > 0f || (lift.load == Load.BODYWEIGHT_PLUS && it.weightKg >= 0f))
            }
            if (entered != null) {
                val load = if (lift.load == Load.BODYWEIGHT_PLUS) bw + entered.weightKg else entered.weightKg
                OneRepMax.oneRepMax(load, entered.reps, input.formula)
            } else if (lift == Lift.BENCH && transfer != null) {
                estimated += lift
                transfer.oneRepMaxKg
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

    /** Entered flat bench always wins; incline is only a fallback, never a record. */
    private fun benchEstimate(input: Input): BenchStrength.Estimate? {
        val own = input.lifts[Lift.BENCH]
        if (own != null && own.weightKg.isFinite() && own.weightKg > 0f && own.reps in 1..30) return null
        return listOf(Lift.INCLINE_BENCH, Lift.INCLINE_DUMBBELL).firstNotNullOfOrNull { lift ->
            val set = input.lifts[lift] ?: return@firstNotNullOfOrNull null
            BenchStrength.estimate(
                BenchStrength.variant(lift.exerciseId)!!, BenchStrength.Variant.FLAT_BARBELL,
                set.weightKg, set.reps, input.formula,
            )
        }
    }

    /**
     * One setup lift in a routine. Bodyweight-plus lifts get the extra weight
     * (null = bodyweight); too weak for bodyweight reps → easier variant
     * (lat pulldown with a weight, bench dips).
     */
    private fun mainExercise(lift: Lift, oneRepMax: Float, bw: Float, goal: Goal, mainSets: Int, accSets: Int, formula: OneRepMaxFormula): PlannedExercise {
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
            return PlannedExercise(lift.exerciseId, sets, reps, rest, workingWeight(oneRepMax, reps, formula = formula))
        }
        val total = OneRepMax.weightForReps(oneRepMax, reps + 2, formula)
        return when {
            total >= bw -> {
                val extra = OverloadMath.round(total - bw, 2.5f).takeIf { total - bw >= 2.5f }
                PlannedExercise(lift.exerciseId, sets, reps, rest, extra)
            }
            lift == Lift.PULLUP -> PlannedExercise(LATZUG.exerciseId, sets, reps, rest, workingWeight(oneRepMax * 0.85f, reps, formula = formula))
            else -> PlannedExercise(BANKDIPS.exerciseId, sets, reps.coerceAtLeast(10), rest, null)
        }
    }

    /** Weight for [reps] with about two reps in reserve (inverse selected 1RM equation). */
    fun workingWeight(oneRepMax: Float, reps: Int, step: Float = stepFor(oneRepMax), formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY): Float {
        val raw = OneRepMax.weightForReps(oneRepMax, reps + 2, formula)
        if (raw <= 0f) return 0f
        return OverloadMath.round(raw, step)
    }

    private fun stepFor(weight: Float): Float = if (weight < 25f) 1f else 2.5f

    /** Accessory: base lift and share of its 1RM (per dumbbell where it applies). */
    private data class Accessory(val exerciseId: String, val base: Lift?, val ratio: Float, val timed: Boolean = false)

    private val SCHRAEGBANK = Accessory("ex-schraegbank", Lift.BENCH, BenchStrength.INCLINE_DUMBBELL_RATIO)
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

    private val BUTTERFLY = Accessory("ex-butterfly", null, 0f)
    private val KABELRUDERN = Accessory("ex-kabelrudern", null, 0f)
    private val REVERSE_FLY = Accessory("ex-reverse-fly", null, 0f)
    private val HAMMER = Accessory("ex-hammercurl", Lift.ROW, 0.2f)
    private val SZ_CURL = Accessory("ex-sz-curl", null, 0f)
    private val FRENCH = Accessory("ex-french-press", null, 0f)
    private val UEBERKOPF = Accessory("ex-ueberkopf-trizeps", null, 0f)
    private val BEINSTRECKER = Accessory("ex-beinstrecker", null, 0f)
    private val CRUNCH = Accessory("ex-kabel-crunch", null, 0f)
    private val BEINHEBEN = Accessory("ex-beinheben", null, 0f)

    private val ACCESSORIES = listOf(
        SCHRAEGBANK, TRIZEPS, BANKDIPS, BIZEPS, LATZUG, FACEPULL, RDL, BEINPRESSE, BEINBEUGER, WADEN, AUSFALL, PLANK,
        BUTTERFLY, KABELRUDERN, REVERSE_FLY, HAMMER, SZ_CURL, FRENCH, UEBERKOPF, BEINSTRECKER, CRUNCH, BEINHEBEN,
    )

    /** Exercises of a muscle group, the heavy basics first. */
    private fun pool(muscle: GymSplits.Muscle): List<Slot> = when (muscle) {
        GymSplits.Muscle.CHEST -> listOf(Main(Lift.BENCH), Acc(SCHRAEGBANK), Main(Lift.DIPS), Acc(BUTTERFLY))
        GymSplits.Muscle.BACK -> listOf(Main(Lift.PULLUP), Main(Lift.ROW), Main(Lift.DEADLIFT), Acc(LATZUG), Acc(KABELRUDERN))
        GymSplits.Muscle.SHOULDERS -> listOf(Main(Lift.OHP), Main(Lift.LATERAL), Acc(FACEPULL), Acc(REVERSE_FLY))
        GymSplits.Muscle.BICEPS -> listOf(Acc(BIZEPS), Acc(HAMMER), Acc(SZ_CURL))
        GymSplits.Muscle.TRICEPS -> listOf(Acc(TRIZEPS), Acc(FRENCH), Acc(UEBERKOPF))
        GymSplits.Muscle.LEGS -> listOf(Main(Lift.SQUAT), Acc(RDL), Acc(BEINPRESSE), Acc(BEINBEUGER), Acc(WADEN), Acc(BEINSTRECKER))
        GymSplits.Muscle.CORE -> listOf(Acc(PLANK), Acc(CRUNCH), Acc(BEINHEBEN))
    }

    /**
     * A unit from its muscle groups: one exercise per group in turn until the unit
     * is full, then ordered group by group. Without groups the title decides
     * ("Push", "Arme"); a title that names none alternates two full-body units.
     */
    private fun customSlots(unit: GymSplits.SplitUnit, index: Int): List<Slot> {
        val muscles = unit.muscles.ifEmpty { GymSplits.musclesOf(unit.title) }.distinct().sortedBy { it.ordinal }
        if (muscles.isEmpty()) {
            return if (index % 2 == 0) {
                listOf(Main(Lift.SQUAT), Main(Lift.BENCH), Main(Lift.ROW), Main(Lift.LATERAL), Acc(PLANK))
            } else {
                listOf(Main(Lift.DEADLIFT), Main(Lift.OHP), Main(Lift.PULLUP), Main(Lift.DIPS), Acc(AUSFALL))
            }
        }
        val target = when (muscles.size) {
            1, 2 -> 5
            in 3..5 -> 6
            else -> 7
        }
        val pools = muscles.map(::pool)
        val picked = mutableListOf<Triple<Int, Int, Slot>>()
        var rank = 0
        while (picked.size < target && pools.any { rank < it.size }) {
            pools.forEachIndexed { m, p -> if (picked.size < target && rank < p.size) picked += Triple(m, rank, p[rank]) }
            rank++
        }
        return picked.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }

    /**
     * Start weight for any exercise the builder knows (setup lift or accessory) at
     * [reps]; null = bodyweight or choose yourself.
     */
    fun startWeight(input: Input, exerciseId: String, reps: Int): Float? {
        val orm = oneRepMaxes(input).first
        Lift.entries.firstOrNull { it.exerciseId == exerciseId }?.let { lift ->
            val max = orm.getValue(lift)
            if (lift.load != Load.BODYWEIGHT_PLUS) return workingWeight(max, reps, formula = input.formula)
            val extra = OneRepMax.weightForReps(max, reps + 2, input.formula) - bodyweight(input)
            return OverloadMath.round(extra, 2.5f).takeIf { extra >= 2.5f }
        }
        val accessory = ACCESSORIES.firstOrNull { it.exerciseId == exerciseId } ?: return null
        return accessory.base?.let { workingWeight(orm.getValue(it) * accessory.ratio, reps, formula = input.formula) }
    }

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
            Split.FOUR_DAY -> listOf(
                "Brust + Trizeps" to listOf(Main(Lift.BENCH), Acc(SCHRAEGBANK), dips, Acc(TRIZEPS)),
                "Rücken + Bizeps" to listOf(Main(Lift.DEADLIFT), pull, Main(Lift.ROW), Acc(FACEPULL), Acc(BIZEPS)),
                "Beine" to listOf(Main(Lift.SQUAT), Acc(RDL), Acc(BEINPRESSE), Acc(BEINBEUGER), Acc(WADEN)),
                "Schultern + Bauch" to listOf(Main(Lift.OHP), lateral, Acc(FACEPULL), Acc(PLANK)),
            )
            Split.CUSTOM -> input.customUnits
                .filter { it.title.isNotBlank() }
                .ifEmpty { listOf(GymSplits.SplitUnit("Training A", emptyList()), GymSplits.SplitUnit("Training B", emptyList())) }
                .mapIndexed { index, unit -> unit.title.trim() to customSlots(unit, index) }
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
                        is Main -> mainExercise(slot.lift, orm.getValue(slot.lift), bw, goal, mainSets, accSets, input.formula)
                        is Acc -> {
                            val a = slot.accessory
                            // Entered incline performance wins over the generic bench-derived accessory load.
                            val accessoryMax = if (a == SCHRAEGBANK) {
                                when {
                                    Lift.INCLINE_DUMBBELL !in estimated -> orm.getValue(Lift.INCLINE_DUMBBELL)
                                    Lift.INCLINE_BENCH !in estimated -> orm.getValue(Lift.INCLINE_BENCH) * BenchStrength.DUMBBELL_PER_HAND_RATIO
                                    else -> orm.getValue(Lift.BENCH) * a.ratio
                                }
                            } else a.base?.let { orm.getValue(it) * a.ratio }
                            PlannedExercise(
                                exerciseId = a.exerciseId,
                                sets = accSets,
                                reps = when {
                                    a.timed -> 45
                                    else -> goal.accReps
                                },
                                restSec = goal.accRestSec,
                                startWeightKg = accessoryMax?.let { workingWeight(it, goal.accReps, formula = input.formula) },
                            )
                        }
                    }
                    // A weak pull-up becomes the lat pulldown, which a unit may already hold.
                }.distinctBy { it.exerciseId },
            )
        }
        return Plan(
            // Every exercise once per routine (e.g. dips fallback = bench dips next to triceps).
            name = "${input.customName?.takeIf { it.isNotBlank() } ?: input.split.label} · ${goal.label}",
            routines = routines,
            oneRepMax = orm,
            estimated = estimated,
            benchEstimate = benchEstimate(input),
        )
    }
}
