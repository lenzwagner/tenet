package app.tenet.android.core.common

import java.util.Locale

/** Body measurements in cm, stored as a flat JSON object in BodyMetric.measurementsJson. */
object BodyMeasurements {

    /** Key → German label, in display order. */
    val FIELDS = listOf(
        "waist" to "Taille",
        "chest" to "Brust",
        "hips" to "Hüfte",
        "arm" to "Oberarm",
        "thigh" to "Oberschenkel",
        "calf" to "Wade",
    )

    fun label(key: String): String = FIELDS.firstOrNull { it.first == key }?.second ?: key

    fun encode(values: Map<String, Float>): String? =
        values.filterValues { it > 0f }.takeIf { it.isNotEmpty() }
            ?.entries?.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${String.format(Locale.US, "%.1f", v)}" }

    private val ENTRY = Regex("\"(\\w+)\"\\s*:\\s*([0-9.]+)")

    fun decode(json: String?): Map<String, Float> =
        json?.let { ENTRY.findAll(it).mapNotNull { m -> m.groupValues[2].toFloatOrNull()?.let { v -> m.groupValues[1] to v } }.toMap() }
            .orEmpty()
}
