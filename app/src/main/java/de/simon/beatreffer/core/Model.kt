package de.simon.beatreffer.core

/**
 * Reine Datenmodelle ohne Android-Abhaengigkeiten.
 * Alles in diesem Paket ist mit normalen JUnit-Tests pruefbar.
 */

/** Taktart mit Betonungs-Gruppierung, z. B. 7/8 als 2+2+3. */
data class TimeSignature(
    val numerator: Int = 4,
    val denominator: Int = 4,
    val grouping: List<Int> = listOf(4),
) {
    init {
        require(numerator in 1..16) { "Zaehler muss 1..16 sein" }
        require(denominator in setOf(2, 4, 8, 16)) { "Nenner muss 2, 4, 8 oder 16 sein" }
        require(grouping.isNotEmpty() && grouping.all { it > 0 } && grouping.sum() == numerator) {
            "Gruppierung muss sich zu $numerator aufsummieren"
        }
    }

    /** Indizes (0-basiert) der Schlaege, auf denen eine Gruppe beginnt. */
    val groupStarts: Set<Int>
        get() = grouping.runningFold(0) { acc, g -> acc + g }.dropLast(1).toSet()

    val label: String get() = "$numerator/$denominator"
    val groupingLabel: String get() = grouping.joinToString("+")

    companion object {
        /** Sinnvolle Standard-Gruppierung fuer eine Taktart. */
        fun defaultGrouping(numerator: Int, denominator: Int): List<Int> {
            if (denominator >= 8 && numerator > 3) {
                if (numerator % 3 == 0) return List(numerator / 3) { 3 }
                // Ungerade Achtel/Sechzehntel: Zweier, am Ende eine Dreiergruppe
                if (numerator % 2 == 1) return List((numerator - 3) / 2) { 2 } + 3
                return List(numerator / 2) { 2 }
            }
            if (numerator == 5) return listOf(3, 2)
            if (numerator == 7) return listOf(4, 3)
            return listOf(numerator)
        }

        fun of(numerator: Int, denominator: Int) =
            TimeSignature(numerator, denominator, defaultGrouping(numerator, denominator))

        /** Parst "2+2+3". Liefert null, wenn ungueltig. */
        fun parseGrouping(text: String, numerator: Int): List<Int>? {
            val parts = text.split('+').map { it.trim() }.filter { it.isNotEmpty() }
            val nums = parts.map { it.toIntOrNull() ?: return null }
            if (nums.isEmpty() || nums.any { it <= 0 } || nums.sum() != numerator) return null
            return nums
        }

        val QUICK = listOf(
            of(2, 4), of(3, 4), of(4, 4), of(5, 4), of(6, 4), of(7, 4),
            of(3, 8), of(5, 8), of(6, 8), of(7, 8), of(9, 8), of(12, 8),
        )
    }
}

enum class AppMode { SIMPLE, ADVANCED }

/** Was nachgetippt wird. */
enum class PracticeType { BEAT, SUBDIVISION, PATTERN }

enum class TargetMode { FIXED, ROTATE }

/**
 * Ausblendstufen, aufsteigend nach Schwierigkeit sortiert.
 * Die Reihenfolge wird fuer Stufen-Vorschlaege genutzt.
 */
enum class MuteMode {
    ALWAYS_ON,          // Ton immer an
    ACCENT_ONLY,        // nur die Eins klickt
    ALTERNATE,          // X Takte an, Y Takte aus
    RANDOM_GAPS,        // zufaellige stille Takte
    COUNT_IN_SILENT,    // vorzaehlen, danach still
    FULL_SILENT;        // kein Ton, nur ein Takt optisch vorzaehlen

    companion object {
        val SIMPLE_MODES = listOf(ALWAYS_ON, COUNT_IN_SILENT, FULL_SILENT)
    }
}

/** Was der Bildschirm in stillen Passagen anzeigt. */
enum class VisualHint { PULSE, TARGET_ONLY, NONE }

enum class Tolerance(val windowMs: Int) {
    EASY(80), MEDIUM(50), HARD(25);

    fun tighter(): Tolerance? = when (this) {
        EASY -> MEDIUM
        MEDIUM -> HARD
        HARD -> null
    }
}

enum class DurationType { MINUTES, BARS }

enum class ClickSound { WOODBLOCK, BEEP, HIHAT }

data class PracticeConfig(
    val appMode: AppMode = AppMode.SIMPLE,
    val bpm: Int = 90,
    val timeSignature: TimeSignature = TimeSignature(),
    val practiceType: PracticeType = PracticeType.BEAT,
    /** 2 = Achtel, 3 = Triolen, 4 = Sechzehntel. Nur bei SUBDIVISION relevant. */
    val subdivision: Int = 2,
    val subdivisionAudible: Boolean = true,
    val patternId: String = Patterns.ALL.first().id,
    val targetMode: TargetMode = TargetMode.FIXED,
    /** 0-basierte Schlag-Indizes. Leer = alle Schlaege. */
    val targetBeats: Set<Int> = emptySet(),
    val muteMode: MuteMode = MuteMode.ALWAYS_ON,
    val alternateOnBars: Int = 2,
    val alternateOffBars: Int = 2,
    val gapPercent: Int = 30,
    val countInBars: Int = 1,
    val durationType: DurationType = DurationType.MINUTES,
    val durationMinutes: Int = 2,
    val durationBars: Int = 32,
    val tolerance: Tolerance = Tolerance.MEDIUM,
    val countExtraTaps: Boolean = false,
    val liveFeedback: Boolean = true,
    val visualHint: VisualHint = VisualHint.TARGET_ONLY,
    val sound: ClickSound = ClickSound.WOODBLOCK,
) {
    companion object {
        const val MIN_BPM = 40
        const val MAX_BPM = 240
    }

    /**
     * Die tatsaechlich wirksame Konfiguration. Im einfachen Modus werden
     * alle erweiterten Optionen auf ihre Standardwerte gesetzt.
     */
    fun effective(): PracticeConfig {
        val base = if (appMode == AppMode.ADVANCED) this else copy(
            timeSignature = TimeSignature(),
            practiceType = PracticeType.BEAT,
            targetMode = TargetMode.FIXED,
            targetBeats = emptySet(),
            muteMode = if (muteMode in MuteMode.SIMPLE_MODES) muteMode else MuteMode.ALWAYS_ON,
            countInBars = 1,
            tolerance = Tolerance.MEDIUM,
            countExtraTaps = false,
            visualHint = VisualHint.TARGET_ONLY,
        )
        val ts = if (base.practiceType == PracticeType.PATTERN) Patterns.byId(base.patternId).timeSignature
        else base.timeSignature
        val validTargets = base.targetBeats.filter { it in 0 until ts.numerator }.toSet()
        return base.copy(
            bpm = base.bpm.coerceIn(MIN_BPM, MAX_BPM),
            timeSignature = ts,
            targetBeats = validTargets,
            countInBars = base.countInBars.coerceIn(1, 4),
        )
    }

    /**
     * Schluessel fuer "gleiche Einstellungen". Dauer, Klang und Anzeige
     * gehoeren nicht dazu, weil sie die Schwierigkeit kaum aendern.
     */
    fun comparisonKey(): String {
        val c = effective()
        val sb = StringBuilder()
        sb.append(c.practiceType.name).append('|')
        when (c.practiceType) {
            PracticeType.PATTERN -> sb.append(c.patternId)
            PracticeType.SUBDIVISION -> sb.append(c.timeSignature.label).append(':')
                .append(c.timeSignature.groupingLabel).append(":s").append(c.subdivision)
            PracticeType.BEAT -> sb.append(c.timeSignature.label).append(':').append(c.timeSignature.groupingLabel)
        }
        sb.append('|').append(c.bpm)
        sb.append('|').append(c.muteMode.name)
        if (c.muteMode == MuteMode.ALTERNATE) sb.append(c.alternateOnBars).append('/').append(c.alternateOffBars)
        if (c.muteMode == MuteMode.RANDOM_GAPS) sb.append(c.gapPercent)
        sb.append('|').append(c.targetMode.name)
        if (c.targetMode == TargetMode.FIXED && c.practiceType != PracticeType.PATTERN) {
            sb.append(c.targetBeats.sorted().joinToString(","))
        }
        sb.append('|').append(c.tolerance.name)
        sb.append('|').append(if (c.countExtraTaps) "x" else "-")
        return sb.toString()
    }
}
