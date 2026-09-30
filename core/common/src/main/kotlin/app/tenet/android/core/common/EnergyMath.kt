package app.tenet.android.core.common

import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

/** PAL multipliers for the Mifflin-St Jeor BMR (App_Konzept.md 5.4 "Ziele"). */
enum class ActivityLevel(val factor: Float, val label: String) {
    SEDENTARY(1.2f, "Sitzend"),
    LIGHT(1.375f, "Leicht aktiv"),
    MODERATE(1.55f, "Mäßig aktiv"),
    ACTIVE(1.725f, "Sehr aktiv"),
    ATHLETE(1.9f, "Extrem aktiv"),
}

enum class WeightGoal(val kcalDelta: Int, val label: String) {
    LOSE(-500, "Abnehmen"),
    MAINTAIN(0, "Halten"),
    GAIN(300, "Aufbauen"),
}

/** Body data used to compute energy needs; stored in the settings. */
data class BodyProfile(
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightCm: Float = 178f,
    val weightKg: Float = 75f,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val goal: WeightGoal = WeightGoal.MAINTAIN,
)

data class MacroTargets(val kcal: Float, val protein: Float, val carbs: Float, val fat: Float)

object EnergyMath {

    /** Basal metabolic rate after Mifflin-St Jeor (kcal/day). */
    fun bmr(profile: BodyProfile): Float {
        val base = 10f * profile.weightKg + 6.25f * profile.heightCm - 5f * profile.age
        return base + if (profile.sex == Sex.MALE) 5f else -161f
    }

    /** Total daily energy expenditure: BMR × activity factor. */
    fun tdee(profile: BodyProfile): Float = bmr(profile) * profile.activity.factor

    /** Target kcal: TDEE plus the goal's deficit/surplus, never below 1200. */
    fun targetKcal(profile: BodyProfile): Float =
        (tdee(profile) + profile.goal.kcalDelta).coerceAtLeast(1200f).roundTo(10)

    /**
     * Macro split with protein and fat in g per kg body weight; carbs fill the
     * remaining energy (4/4/9 kcal per gram). Carbs never go negative.
     */
    fun macrosByBodyWeight(
        kcal: Float,
        weightKg: Float,
        proteinPerKg: Float,
        fatPerKg: Float,
    ): MacroTargets {
        val protein = (weightKg * proteinPerKg).roundTo(5)
        val fat = (weightKg * fatPerKg).roundTo(5)
        val carbs = ((kcal - protein * 4f - fat * 9f) / 4f).coerceAtLeast(0f).roundTo(5)
        return MacroTargets(kcal, protein, carbs, fat)
    }

    /** Macro split by energy share in percent (should add up to 100). */
    fun macrosByPercent(kcal: Float, proteinPct: Int, carbsPct: Int, fatPct: Int): MacroTargets =
        MacroTargets(
            kcal = kcal,
            protein = (kcal * proteinPct / 100f / 4f).roundTo(5),
            carbs = (kcal * carbsPct / 100f / 4f).roundTo(5),
            fat = (kcal * fatPct / 100f / 9f).roundTo(5),
        )

    /** Scales a per-100 g value to [grams]. */
    fun per100(valuePer100: Float, grams: Float): Float = valuePer100 * grams / 100f

    private fun Float.roundTo(step: Int): Float = ((this / step).roundToInt() * step).toFloat()
}
