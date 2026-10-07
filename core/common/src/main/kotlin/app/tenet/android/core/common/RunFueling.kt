package app.tenet.android.core.common

import kotlin.math.roundToInt

/**
 * Eating and drinking around a run, after the usual sports-nutrition
 * guidelines (ACSM position stand on hydration, IOC/ISSN carbohydrate
 * recommendations), scaled by duration, intensity and body weight:
 *
 * - Carbohydrate before: not needed for short easy runs; 30–60 g 1–2 h
 *   before medium or hard runs; 1–2 g/kg 2–3 h before long runs and races
 *   (2–3 g/kg before a marathon).
 * - Carbohydrate during: none under ~60 min; 30–60 g/h from 60 min,
 *   60–90 g/h beyond 2.5 h (glucose + fructose), starting after 30–45 min.
 * - Water before: 5–7 ml/kg 2–4 h before, plus a few sips shortly before
 *   longer efforts.
 * - Water during: not needed under ~45 min; 400–800 ml/h for longer runs
 *   (more when hot), in sips every 15–20 min.
 * - Sodium: 300–600 mg/h on runs over 2 h.
 *
 * Guidance only – everyone tolerates different amounts; new fuel is tried
 * in training, never first on race day.
 */
object RunFueling {

    data class Advice(
        /** Grams before the run; null = nothing special needed. */
        val carbsBeforeG: IntRange?,
        val carbsBeforeWhen: String?,
        /** Grams per hour during the run; null = none. */
        val carbsPerHourG: IntRange?,
        /** Total grams for the run (≈ per-hour amount × hours after the first 30 min). */
        val carbsDuringTotalG: IntRange?,
        val waterBeforeMl: IntRange,
        /** Millilitres per hour during the run; null = not needed. */
        val waterPerHourMl: IntRange?,
        val sodium: Boolean,
        /** Short practical notes (timing, examples). */
        val tips: List<String>,
    )

    fun advise(durationSec: Int, zone: RunZone, race: Boolean, weightKg: Float?, marathon: Boolean = false): Advice {
        val kg = (weightKg ?: 75f).coerceIn(40f, 140f)
        val min = durationSec / 60
        val hard = race || zone == RunZone.TEMPO || zone == RunZone.INTERVAL
        val long = race || zone == RunZone.LONG || min >= 75

        val (carbsBefore, carbsWhen) = when {
            race && marathon -> range(2 * kg, 3 * kg) to "am Vortag kohlenhydratreich, am Morgen 2–3 h vor dem Start"
            long -> range(1 * kg, 2 * kg) to "2–3 h vorher, z. B. Haferflocken, Brot mit Honig, Reis"
            hard || min >= 45 -> (30..60) to "1–2 h vorher, z. B. Banane oder Toast mit Marmelade"
            else -> null to null
        }

        val (perHour, total) = when {
            min < 60 -> null to null
            min < 150 -> (30..60).let { it to totalDuring(it, min) }
            else -> (60..90).let { it to totalDuring(it, min) }
        }

        val waterBefore = range(5 * kg, 7 * kg, step = 50)
        val waterPerHour = when {
            min < 45 -> null
            min < 75 && !race -> 300..500
            else -> 400..800
        }
        val sodium = min >= 120

        val tips = buildList {
            if (perHour != null) add("Ab etwa 30–45 Minuten alle 20–30 Minuten etwas: Gel (~25 g), Riegel oder Sportgetränk.")
            if (perHour != null && perHour.last > 60) add("Über 60 g pro Stunde nur mit Glukose-Fruktose-Mischung und vorher im Training geübt.")
            if (waterPerHour != null) add("Lieber kleine Schlucke alle 15–20 Minuten als viel auf einmal; bei Hitze eher am oberen Ende.")
            if (waterPerHour == null) add("Unterwegs musst du nichts trinken – bei Hitze ein paar Schlucke vorher reichen.")
            if (sodium) add("Über 2 Stunden: 300–600 mg Natrium pro Stunde (Elektrolyt-Tabs oder Salz-Kapseln).")
            if (race) add("Im Wettkampf nichts Neues ausprobieren – Frühstück und Gels vorher im langen Lauf testen.")
            if (carbsBefore == null && !race) add("Kurz und locker: eine normale Mahlzeit im Lauf des Tages reicht, nüchtern geht auch.")
        }

        return Advice(
            carbsBeforeG = carbsBefore,
            carbsBeforeWhen = carbsWhen,
            carbsPerHourG = perHour,
            carbsDuringTotalG = total,
            waterBeforeMl = waterBefore,
            waterPerHourMl = waterPerHour,
            sodium = sodium,
            tips = tips,
        )
    }

    /** Fuel counts from ~30 min on. */
    private fun totalDuring(perHour: IntRange, min: Int): IntRange {
        val hours = ((min - 30).coerceAtLeast(0)) / 60.0
        return range((perHour.first * hours).toFloat(), (perHour.last * hours).toFloat())
    }

    private fun range(lo: Float, hi: Float, step: Int = 5): IntRange {
        fun round(v: Float) = ((v / step).roundToInt() * step)
        return round(lo)..round(hi)
    }
}
