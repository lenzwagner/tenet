package app.tenet.android.core.data

import app.tenet.android.core.common.MovementPattern
import app.tenet.android.core.common.MovementPattern.CALF
import app.tenet.android.core.common.MovementPattern.CHEST_FLY
import app.tenet.android.core.common.MovementPattern.CORE_FLEXION
import app.tenet.android.core.common.MovementPattern.CORE_STABILITY
import app.tenet.android.core.common.MovementPattern.ELBOW_EXTENSION
import app.tenet.android.core.common.MovementPattern.ELBOW_FLEXION
import app.tenet.android.core.common.MovementPattern.GLUTE
import app.tenet.android.core.common.MovementPattern.GRIP
import app.tenet.android.core.common.MovementPattern.HINGE
import app.tenet.android.core.common.MovementPattern.HORIZONTAL_PULL
import app.tenet.android.core.common.MovementPattern.HORIZONTAL_PUSH
import app.tenet.android.core.common.MovementPattern.INCLINE_PUSH
import app.tenet.android.core.common.MovementPattern.KNEE_EXTENSION
import app.tenet.android.core.common.MovementPattern.KNEE_FLEXION
import app.tenet.android.core.common.MovementPattern.LATERAL_RAISE
import app.tenet.android.core.common.MovementPattern.LUNGE
import app.tenet.android.core.common.MovementPattern.REAR_DELT
import app.tenet.android.core.common.MovementPattern.SHRUG
import app.tenet.android.core.common.MovementPattern.SQUAT
import app.tenet.android.core.common.MovementPattern.VERTICAL_PULL
import app.tenet.android.core.common.MovementPattern.VERTICAL_PUSH
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.MeasureType

/**
 * Built-in exercise catalog. Ids are stable (plans and history refer to
 * them); new entries reach existing installs through "insert missing".
 * Muscle names: Brust, Rücken, Schulter, Bizeps, Trizeps, Unterarme,
 * Beine (Quadrizeps), Beinbeuger, Gesäß, Waden, Core.
 */
object ExerciseCatalog {

    private const val LH = "Langhantel"
    private const val KH = "Kurzhantel"
    private const val MA = "Maschine"
    private const val KZ = "Kabelzug"
    private const val KG = "Körpergewicht"
    private const val SZ = "SZ-Stange"

    private fun gym(
        id: String,
        name: String,
        primary: String,
        secondary: String,
        equipment: String,
        pattern: MovementPattern,
        measure: MeasureType = MeasureType.REPS,
    ) = Exercise(
        id = id,
        name = name,
        discipline = Discipline.GYM,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = equipment,
        measureType = measure,
        pattern = pattern.name,
    )

    val gym: List<Exercise> = listOf(
        // Brust
        gym("ex-bankdruecken", "Bankdrücken", "Brust", "Trizeps,Schulter", LH, HORIZONTAL_PUSH),
        gym("ex-kh-bank", "Bankdrücken (KH)", "Brust", "Trizeps,Schulter", KH, HORIZONTAL_PUSH),
        gym("ex-brustpresse", "Brustpresse", "Brust", "Trizeps", MA, HORIZONTAL_PUSH),
        gym("ex-smith-bank", "Bankdrücken (Multipresse)", "Brust", "Trizeps,Schulter", MA, HORIZONTAL_PUSH),
        gym("ex-g-liegestuetz", "Liegestütz", "Brust", "Trizeps,Core", KG, HORIZONTAL_PUSH),
        gym("ex-g-dips", "Dips (Brust)", "Brust", "Trizeps,Schulter", KG, HORIZONTAL_PUSH),
        gym("ex-schraegbank", "Schrägbankdrücken (KH)", "Brust", "Schulter,Trizeps", KH, INCLINE_PUSH),
        gym("ex-schraegbank-lh", "Schrägbankdrücken (LH)", "Brust", "Schulter,Trizeps", LH, INCLINE_PUSH),
        gym("ex-schraeg-maschine", "Schrägbankpresse", "Brust", "Schulter,Trizeps", MA, INCLINE_PUSH),
        gym("ex-butterfly", "Butterfly", "Brust", "", MA, CHEST_FLY),
        gym("ex-kabel-fly", "Cable Crossover", "Brust", "Schulter", KZ, CHEST_FLY),
        gym("ex-kh-fly", "Fliegende (KH)", "Brust", "Schulter", KH, CHEST_FLY),
        // Schulter
        gym("ex-schulterdruecken", "Schulterdrücken", "Schulter", "Trizeps", LH, VERTICAL_PUSH),
        gym("ex-kh-schulter", "Schulterdrücken (KH)", "Schulter", "Trizeps", KH, VERTICAL_PUSH),
        gym("ex-arnold", "Arnold Press", "Schulter", "Trizeps", KH, VERTICAL_PUSH),
        gym("ex-schulterpresse", "Schulterpresse", "Schulter", "Trizeps", MA, VERTICAL_PUSH),
        gym("ex-landmine", "Landmine Press", "Schulter", "Brust,Trizeps", LH, VERTICAL_PUSH),
        gym("ex-seitheben", "Seitheben", "Schulter", "", KH, LATERAL_RAISE),
        gym("ex-kabel-seitheben", "Seitheben (Kabel)", "Schulter", "", KZ, LATERAL_RAISE),
        gym("ex-seitheben-maschine", "Seitheben (Maschine)", "Schulter", "", MA, LATERAL_RAISE),
        gym("ex-facepull", "Face Pulls", "Schulter", "Rücken", KZ, REAR_DELT),
        gym("ex-reverse-fly", "Reverse Butterfly", "Schulter", "Rücken", MA, REAR_DELT),
        gym("ex-vorgebeugt-seitheben", "Vorgebeugtes Seitheben", "Schulter", "Rücken", KH, REAR_DELT),
        // Rücken
        gym("ex-rudern", "Rudern (Langhantel)", "Rücken", "Bizeps", LH, HORIZONTAL_PULL),
        gym("ex-kh-rudern", "Einarmiges Rudern (KH)", "Rücken", "Bizeps", KH, HORIZONTAL_PULL),
        gym("ex-kabelrudern", "Rudern am Kabel (sitzend)", "Rücken", "Bizeps", KZ, HORIZONTAL_PULL),
        gym("ex-rudermaschine", "Rudermaschine (brustgestützt)", "Rücken", "Bizeps", MA, HORIZONTAL_PULL),
        gym("ex-tbar", "T-Bar-Rudern", "Rücken", "Bizeps", LH, HORIZONTAL_PULL),
        gym("ex-pendlay", "Pendlay-Rudern", "Rücken", "Bizeps", LH, HORIZONTAL_PULL),
        gym("ex-klimmzuege", "Klimmzüge", "Rücken", "Bizeps", KG, VERTICAL_PULL),
        gym("ex-chinups", "Chin-ups (Untergriff)", "Rücken", "Bizeps", KG, VERTICAL_PULL),
        gym("ex-assist-klimmzug", "Klimmzüge (unterstützt)", "Rücken", "Bizeps", MA, VERTICAL_PULL),
        gym("ex-latzzug", "Lat-Zug", "Rücken", "Bizeps", MA, VERTICAL_PULL),
        gym("ex-latzug-eng", "Lat-Zug (enger Griff)", "Rücken", "Bizeps", KZ, VERTICAL_PULL),
        gym("ex-straight-arm", "Überzüge am Kabel", "Rücken", "", KZ, VERTICAL_PULL),
        gym("ex-shrugs", "Shrugs (KH)", "Rücken", "Unterarme", KH, SHRUG),
        gym("ex-hyperext", "Hyperextensions", "Rücken", "Gesäß,Beinbeuger", MA, HINGE),
        // Arme
        gym("ex-bizepscurl", "Bizeps-Curl", "Bizeps", "Unterarme", KH, ELBOW_FLEXION),
        gym("ex-lh-curl", "Langhantel-Curl", "Bizeps", "Unterarme", LH, ELBOW_FLEXION),
        gym("ex-sz-curl", "SZ-Curl", "Bizeps", "Unterarme", SZ, ELBOW_FLEXION),
        gym("ex-hammercurl", "Hammer-Curl", "Bizeps", "Unterarme", KH, ELBOW_FLEXION),
        gym("ex-kabel-curl", "Curl am Kabel", "Bizeps", "", KZ, ELBOW_FLEXION),
        gym("ex-scott-curl", "Scott-Curl", "Bizeps", "", MA, ELBOW_FLEXION),
        gym("ex-konzentrationscurl", "Konzentrationscurl", "Bizeps", "", KH, ELBOW_FLEXION),
        gym("ex-trizepsdruecken", "Trizeps-Drücken", "Trizeps", "", KZ, ELBOW_EXTENSION),
        gym("ex-french-press", "French Press (SZ)", "Trizeps", "", SZ, ELBOW_EXTENSION),
        gym("ex-ueberkopf-trizeps", "Trizeps über Kopf (Kabel)", "Trizeps", "", KZ, ELBOW_EXTENSION),
        gym("ex-eng-bank", "Enges Bankdrücken", "Trizeps", "Brust", LH, ELBOW_EXTENSION),
        gym("ex-g-bankdips", "Bankdips", "Trizeps", "Brust", KG, ELBOW_EXTENSION),
        gym("ex-kickbacks", "Trizeps-Kickbacks", "Trizeps", "", KH, ELBOW_EXTENSION),
        gym("ex-wristcurl", "Unterarm-Curls", "Unterarme", "", KH, GRIP),
        gym("ex-farmers", "Farmer's Walk", "Unterarme", "Core,Rücken", KH, GRIP, MeasureType.DURATION),
        // Beine
        gym("ex-kniebeugen", "Kniebeugen", "Beine,Gesäß", "Rücken", LH, SQUAT),
        gym("ex-frontkniebeugen", "Frontkniebeugen", "Beine", "Gesäß,Core", LH, SQUAT),
        gym("ex-goblet", "Goblet Squat", "Beine,Gesäß", "Core", KH, SQUAT),
        gym("ex-beinpresse", "Beinpresse", "Beine", "Gesäß", MA, SQUAT),
        gym("ex-hackenschmidt", "Hackenschmidt", "Beine", "Gesäß", MA, SQUAT),
        gym("ex-smith-squat", "Kniebeugen (Multipresse)", "Beine,Gesäß", "", MA, SQUAT),
        gym("ex-kreuzheben", "Kreuzheben", "Rücken,Gesäß", "Beine", LH, HINGE),
        gym("ex-rdl", "Rumänisches Kreuzheben", "Beinbeuger,Gesäß", "Rücken", LH, HINGE),
        gym("ex-kh-rdl", "Rumänisches Kreuzheben (KH)", "Beinbeuger,Gesäß", "Rücken", KH, HINGE),
        gym("ex-sumo", "Sumo-Kreuzheben", "Gesäß,Beine", "Rücken", LH, HINGE),
        gym("ex-trapbar", "Kreuzheben (Trap Bar)", "Beine,Gesäß", "Rücken", "Trap Bar", HINGE),
        gym("ex-goodmorning", "Good Mornings", "Beinbeuger", "Rücken,Gesäß", LH, HINGE),
        gym("ex-ausfallschritte", "Ausfallschritte (KH)", "Beine,Gesäß", "", KH, LUNGE),
        gym("ex-bulgarian", "Bulgarian Split Squat", "Beine,Gesäß", "", KH, LUNGE),
        gym("ex-walking-lunges", "Walking Lunges", "Beine,Gesäß", "", KH, LUNGE),
        gym("ex-stepups", "Step-ups", "Beine,Gesäß", "", KH, LUNGE),
        gym("ex-beinstrecker", "Beinstrecker", "Beine", "", MA, KNEE_EXTENSION),
        gym("ex-beinbeuger", "Beinbeuger", "Beinbeuger", "", MA, KNEE_FLEXION),
        gym("ex-beinbeuger-sitzend", "Beinbeuger (sitzend)", "Beinbeuger", "", MA, KNEE_FLEXION),
        gym("ex-nordic", "Nordic Curls", "Beinbeuger", "", KG, KNEE_FLEXION),
        gym("ex-hipthrust", "Hip Thrust", "Gesäß", "Beinbeuger", LH, GLUTE),
        gym("ex-glute-bridge", "Glute Bridge", "Gesäß", "Beinbeuger", KG, GLUTE),
        gym("ex-abduktor", "Abduktoren-Maschine", "Gesäß", "", MA, GLUTE),
        gym("ex-glute-kickback", "Kickback am Kabel", "Gesäß", "", KZ, GLUTE),
        gym("ex-adduktor", "Adduktoren-Maschine", "Beine", "", MA, GLUTE),
        gym("ex-wadenheben", "Wadenheben", "Waden", "", MA, CALF),
        gym("ex-waden-sitzend", "Wadenheben (sitzend)", "Waden", "", MA, CALF),
        gym("ex-waden-beinpresse", "Wadenheben an der Beinpresse", "Waden", "", MA, CALF),
        // Core
        gym("ex-plank", "Plank", "Core", "", KG, CORE_STABILITY, MeasureType.DURATION),
        gym("ex-seitstuetz", "Seitstütz", "Core", "", KG, CORE_STABILITY, MeasureType.DURATION),
        gym("ex-abwheel", "Ab Wheel", "Core", "Schulter", "Ab Roller", CORE_STABILITY),
        gym("ex-pallof", "Pallof Press", "Core", "", KZ, CORE_STABILITY),
        gym("ex-deadbug", "Dead Bug", "Core", "", KG, CORE_STABILITY),
        gym("ex-crunches", "Crunches", "Core", "", KG, CORE_FLEXION),
        gym("ex-kabel-crunch", "Crunch am Kabel", "Core", "", KZ, CORE_FLEXION),
        gym("ex-beinheben", "Beinheben hängend", "Core", "Unterarme", KG, CORE_FLEXION),
        gym("ex-russian-twist", "Russian Twist", "Core", "", KG, CORE_FLEXION),
    )

    /** Movement pattern per calisthenics exercise id (skill steps and basics). */
    val calisthenicsPatterns: Map<String, MovementPattern> = mapOf(
        "ex-fl-tuck" to HORIZONTAL_PULL, "ex-fl-adv" to HORIZONTAL_PULL, "ex-fl-oneleg" to HORIZONTAL_PULL,
        "ex-fl-straddle" to HORIZONTAL_PULL, "ex-fl-full" to HORIZONTAL_PULL, "ex-cs-row" to HORIZONTAL_PULL,
        "ex-hs-wall1" to VERTICAL_PUSH, "ex-hs-wall2" to VERTICAL_PUSH, "ex-hs-kickup" to VERTICAL_PUSH,
        "ex-hs-free" to VERTICAL_PUSH, "ex-cs-pike" to VERTICAL_PUSH,
        "ex-mu-pullup" to VERTICAL_PULL, "ex-mu-chest" to VERTICAL_PULL, "ex-mu-negative" to VERTICAL_PULL,
        "ex-mu-up" to VERTICAL_PULL, "ex-cs-chinup" to VERTICAL_PULL,
        "ex-ps-squat" to SQUAT, "ex-ps-assisted" to LUNGE, "ex-ps-box" to LUNGE, "ex-ps-full" to LUNGE,
        "ex-cs-lunge" to LUNGE, "ex-cs-nordic" to KNEE_FLEXION, "ex-cs-glute-bridge" to GLUTE,
        "ex-pl-lean" to HORIZONTAL_PUSH, "ex-pl-tuck" to HORIZONTAL_PUSH, "ex-pl-adv" to HORIZONTAL_PUSH,
        "ex-pl-straddle" to HORIZONTAL_PUSH,
        "ex-oap-pushup" to HORIZONTAL_PUSH, "ex-oap-wide" to HORIZONTAL_PUSH, "ex-oap-archer" to HORIZONTAL_PUSH,
        "ex-oap-full" to HORIZONTAL_PUSH, "ex-cs-knee-pushup" to HORIZONTAL_PUSH, "ex-cs-dips" to HORIZONTAL_PUSH,
        "ex-cs-diamond" to ELBOW_EXTENSION, "ex-cs-bench-dips" to ELBOW_EXTENSION,
        "ex-cs-hollow" to CORE_STABILITY, "ex-cs-lsit" to CORE_STABILITY, "ex-cs-plank" to CORE_STABILITY,
        "ex-cs-knee-raise" to CORE_FLEXION, "ex-cs-toes-to-bar" to CORE_FLEXION,
    )

    private fun cali(
        id: String,
        name: String,
        primary: String,
        secondary: String,
        equipment: String = KG,
        measure: MeasureType = MeasureType.REPS,
    ) = Exercise(
        id = id,
        name = name,
        discipline = Discipline.CALISTHENICS,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = equipment,
        measureType = measure,
        pattern = calisthenicsPatterns[id]?.name,
    )

    /** Calisthenics basics beyond the skill trees (for swapping and adding). */
    val calisthenicsExtras: List<Exercise> = listOf(
        cali("ex-cs-pike", "Pike Push-ups", "Schulter", "Trizeps"),
        cali("ex-cs-chinup", "Chin-ups", "Rücken", "Bizeps", equipment = "Stange"),
        cali("ex-cs-diamond", "Diamond Push-ups", "Trizeps", "Brust"),
        cali("ex-cs-lunge", "Ausfallschritte", "Beine", "Gesäß"),
        cali("ex-cs-nordic", "Nordic Curls", "Beinbeuger", ""),
        cali("ex-cs-glute-bridge", "Einbeinige Glute Bridge", "Gesäß", "Beinbeuger"),
        cali("ex-cs-lsit", "L-Sit", "Core", "Trizeps", equipment = "Barren", measure = MeasureType.HOLD),
        cali("ex-cs-plank", "Plank", "Core", "", measure = MeasureType.HOLD),
        cali("ex-cs-knee-raise", "Knieheben hängend", "Core", "Unterarme", equipment = "Stange"),
        cali("ex-cs-toes-to-bar", "Toes to Bar", "Core", "Rücken", equipment = "Stange"),
    )
}
