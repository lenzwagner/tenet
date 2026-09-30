package app.tenet.android.core.common

/**
 * Movement pattern of an exercise. Exercises with the same pattern can
 * replace each other in a plan ("Übung tauschen"); the catalog is grouped
 * by it.
 */
enum class MovementPattern(val label: String) {
    HORIZONTAL_PUSH("Drücken · Brust"),
    INCLINE_PUSH("Schrägdrücken"),
    CHEST_FLY("Fliegende"),
    VERTICAL_PUSH("Drücken über Kopf"),
    LATERAL_RAISE("Seitheben"),
    REAR_DELT("Hintere Schulter"),
    HORIZONTAL_PULL("Rudern"),
    VERTICAL_PULL("Ziehen von oben"),
    SHRUG("Nacken"),
    ELBOW_FLEXION("Bizeps"),
    ELBOW_EXTENSION("Trizeps"),
    SQUAT("Kniebeuge"),
    HINGE("Hüftstrecken"),
    LUNGE("Einbeinig"),
    KNEE_EXTENSION("Beinstrecken"),
    KNEE_FLEXION("Beinbeugen"),
    GLUTE("Gesäß"),
    CALF("Waden"),
    CORE_STABILITY("Rumpf · Stabilität"),
    CORE_FLEXION("Bauch"),
    GRIP("Griffkraft"),
    ;

    companion object {
        fun fromName(name: String?): MovementPattern? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Ranking of replacement candidates for an exercise: same movement pattern
 * first, then same main muscle; within a group the order of [candidates]
 * is kept. The exercise itself is never suggested.
 */
object ExerciseAlternatives {

    data class Candidate(val id: String, val pattern: MovementPattern?, val primaryMuscles: String)

    fun rank(target: Candidate, candidates: List<Candidate>, limit: Int = 8): List<String> {
        val mainMuscle = target.primaryMuscles.split(',').firstOrNull()?.trim().orEmpty()
        val others = candidates.filter { it.id != target.id }
        val samePattern = if (target.pattern == null) emptyList() else others.filter { it.pattern == target.pattern }
        val sameMuscle = others.filter { c ->
            c !in samePattern && mainMuscle.isNotEmpty() &&
                c.primaryMuscles.split(',').any { it.trim().equals(mainMuscle, ignoreCase = true) }
        }
        return (samePattern + sameMuscle).map { it.id }.take(limit)
    }
}
