package app.tenet.android.core.common

/**
 * Pure math behind the nutrition progress UI (calorie ring and segmented
 * macro donut). Kept free of Compose so it can be unit tested in core:common.
 */
object NutritionMath {

    /** One segment of the macro donut: its place on the circle plus fill state. */
    data class MacroSegment(
        /** Start angle in degrees (0° = 3 o'clock, -90° = 12 o'clock). */
        val startAngle: Float,
        /** Angular size of the whole segment (track), degrees. */
        val sweep: Float,
        /** Fill of the segment: value / goal, may exceed 1f. */
        val progress: Float,
        val over: Boolean,
    ) {
        /** The actually drawn arc length of this segment, degrees (clamped). */
        val drawnSweep: Float get() = sweep * progress.coerceIn(0f, 1f)
    }

    /** Fraction of [goal] reached by [value], clamped to 0..1; 0 when no goal is set. */
    fun fraction(value: Float, goal: Float): Float =
        if (goal <= 0f) 0f else (value / goal).coerceIn(0f, 1f)

    /**
     * Lays out the macro donut.
     *
     * Each segment's angular size is that macro's share of the consumed
     * macros (evenly split when nothing is consumed yet), while [MacroSegment.progress]
     * reflects the fill towards the per-macro goal.
     *
     * @param consumed grams consumed per macro (e.g. protein, carbs, fat)
     * @param goals gram goals in the same order
     * @param gapDegrees gap drawn between two segments
     * @param startAngle angle of the ring's origin, -90f starts at 12 o'clock
     */
    fun macroSegments(
        consumed: List<Float>,
        goals: List<Float>,
        gapDegrees: Float = 4f,
        startAngle: Float = -90f,
    ): List<MacroSegment> {
        require(consumed.size == goals.size) { "consumed and goals must have the same size" }
        if (consumed.isEmpty()) return emptyList()

        val safe = consumed.map { it.coerceAtLeast(0f) }
        val total = safe.sum()
        val shares = if (total <= 0f) {
            List(safe.size) { 1f / safe.size }
        } else {
            safe.map { it / total }
        }
        val sweepBase = 360f - gapDegrees * safe.size

        var angle = startAngle + gapDegrees / 2f
        return safe.mapIndexed { index, value ->
            val sweep = sweepBase * shares[index]
            val progress = if (goals[index] <= 0f) 0f else value / goals[index]
            val segment = MacroSegment(
                startAngle = angle,
                sweep = sweep,
                progress = progress,
                over = progress > 1f,
            )
            angle += sweep + gapDegrees
            segment
        }
    }

    /** Grams still available until [goal] is reached; negative when over goal. */
    fun remainingGrams(consumed: Float, goal: Float): Float = goal - consumed
}
