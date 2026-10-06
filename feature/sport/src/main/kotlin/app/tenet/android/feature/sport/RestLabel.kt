package app.tenet.android.feature.sport

import app.tenet.android.core.common.MovementPattern
import app.tenet.android.core.common.RestAdvisor
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.MeasureType

/**
 * Rest between sets as shown in plans: the plan's own value, or with 0
 * ("automatisch") the advised rest for this exercise and rep count, e.g.
 * "Pause 2:30" – instead of a misleading "Pause 0:00".
 */
internal fun planRestLabel(restSec: Int, exercise: Exercise?, reps: Int): String {
    val seconds = if (restSec > 0 || exercise == null) {
        restSec
    } else {
        val timed = exercise.measureType == MeasureType.HOLD || exercise.measureType == MeasureType.DURATION
        RestAdvisor.advise(MovementPattern.fromName(exercise.pattern), exercise.primaryMuscles, if (timed) 0 else reps).seconds
    }
    return "Pause ${seconds / 60}:" + (seconds % 60).toString().padStart(2, '0')
}
