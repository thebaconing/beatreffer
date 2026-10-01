package de.thebaconing.beatreffer.core

import kotlin.math.ceil
import kotlin.random.Random

/** Lautstaerke-/Betonungsstufe eines Klicks. */
enum class ClickLevel { ACCENT, GROUP, BEAT, SUB }

data class Click(val timeSec: Double, val level: ClickLevel)

/**
 * Ein erwarteter Tipp.
 * [bar] ist der Uebungstakt (0-basiert, ohne Vorzaehltakte).
 * [beat] ist der Schlag im Takt (0-basiert), [sub] die Unterteilung im Schlag.
 */
data class Expected(
    val timeSec: Double,
    val bar: Int,
    val beat: Int,
    val sub: Int,
    val audible: Boolean,
)

data class BarInfo(
    /** Absoluter Takt inkl. Vorzaehltakte. */
    val index: Int,
    val startSec: Double,
    val isCountIn: Boolean,
    val audible: Boolean,
    /** Schlaege, die in diesem Takt Ziel sind (fuer die Anzeige). */
    val targets: Set<Int>,
    val visual: VisualHint,
)

/**
 * Startsignal vor dem ersten Tipp.
 * [beatsLeft] > 0: so viele Schläge bis zum ersten Tipp, [phase] läuft innerhalb des Schlags von 0 bis 1.
 * [beatsLeft] == 0: jetzt tippen (kurz nach dem ersten erwarteten Tipp).
 * [listening]: noch weiter als ein Takt entfernt, erst zuhören.
 */
data class StartCue(val beatsLeft: Int, val phase: Double, val listening: Boolean)

/**
 * Kompletter Ablauf einer Uebung. Zeit 0 = Beginn des ersten Vorzaehltakts.
 */
data class SessionPlan(
    val config: PracticeConfig,
    val beatSec: Double,
    val barSec: Double,
    /** Kleinster Abstand im Raster, das getippt werden kann. */
    val pulseSec: Double,
    val countInBars: Int,
    val practiceBars: Int,
    val bars: List<BarInfo>,
    val clicks: List<Click>,
    val expected: List<Expected>,
) {
    val practiceStartSec: Double get() = countInBars * barSec
    val totalSec: Double get() = bars.size * barSec
    val beatsPerBar: Int get() = config.timeSignature.numerator

    /** Zeitpunkt des ersten erwarteten Tipps. */
    val firstTapSec: Double get() = expected.firstOrNull()?.timeSec ?: practiceStartSec

    /** Startsignal zum Zeitpunkt t, oder null, wenn der Einstieg vorbei ist. */
    fun startCue(t: Double): StartCue? {
        val first = firstTapSec
        if (t >= first) return if (t < first + beatSec) StartCue(0, (t - first) / beatSec, false) else null
        val remaining = (first - t) / beatSec
        val beatsLeft = ceil(remaining - 1e-9).toInt().coerceAtLeast(1)
        val phase = 1.0 - (remaining - (beatsLeft - 1))
        return StartCue(beatsLeft, phase.coerceIn(0.0, 1.0), listening = beatsLeft > beatsPerBar)
    }

    /** Takt zum Zeitpunkt t, oder null ausserhalb. */
    fun barAt(t: Double): BarInfo? {
        if (t < 0) return null
        val i = (t / barSec).toInt()
        return bars.getOrNull(i)
    }

    /** Schlag (0-basiert) zum Zeitpunkt t. */
    fun beatAt(t: Double): Int {
        val inBar = t - (t / barSec).toInt() * barSec
        return (inBar / beatSec).toInt().coerceIn(0, beatsPerBar - 1)
    }
}

object PlanBuilder {

    fun practiceBarCount(config: PracticeConfig): Int {
        val c = config.effective()
        val barSec = 60.0 / c.bpm * c.timeSignature.numerator
        return when (c.durationType) {
            DurationType.BARS -> c.durationBars.coerceAtLeast(1)
            DurationType.MINUTES -> ceil(c.durationMinutes * 60.0 / barSec).toInt().coerceAtLeast(1)
        }
    }

    fun build(rawConfig: PracticeConfig, seed: Long = System.nanoTime()): SessionPlan {
        val c = rawConfig.effective()
        val ts = c.timeSignature
        val n = ts.numerator
        val beatSec = 60.0 / c.bpm
        val barSec = beatSec * n
        val pattern = if (c.practiceType == PracticeType.PATTERN) Patterns.byId(c.patternId) else null
        val stepsPerBeat = when (c.practiceType) {
            PracticeType.BEAT -> 1
            PracticeType.SUBDIVISION -> c.subdivision.coerceIn(2, 4)
            PracticeType.PATTERN -> pattern!!.stepsPerBeat
        }
        val pulseSec = beatSec / stepsPerBeat
        val countIn = c.countInBars
        val practiceBars = practiceBarCount(c)
        val random = Random(seed)
        val groupStarts = ts.groupStarts

        val bars = ArrayList<BarInfo>(countIn + practiceBars)
        val clicks = ArrayList<Click>()
        val expected = ArrayList<Expected>()

        for (abs in 0 until countIn + practiceBars) {
            val start = abs * barSec
            val isCountIn = abs < countIn
            val p = abs - countIn

            val audible = when {
                isCountIn -> c.muteMode != MuteMode.FULL_SILENT
                else -> when (c.muteMode) {
                    MuteMode.ALWAYS_ON, MuteMode.ACCENT_ONLY -> true
                    MuteMode.ALTERNATE -> {
                        val on = c.alternateOnBars.coerceAtLeast(1)
                        val cycle = on + c.alternateOffBars.coerceAtLeast(1)
                        p % cycle < on
                    }
                    MuteMode.RANDOM_GAPS -> p == 0 || random.nextInt(100) >= c.gapPercent
                    MuteMode.COUNT_IN_SILENT, MuteMode.FULL_SILENT -> false
                }
            }

            val visual = when {
                isCountIn -> VisualHint.PULSE
                c.muteMode == MuteMode.FULL_SILENT -> VisualHint.NONE
                audible -> VisualHint.PULSE
                else -> c.visualHint
            }

            val targets: Set<Int> = when {
                pattern != null -> pattern.hitsInBar(maxOf(p, 0)).map { it / stepsPerBeat }.toSet()
                isCountIn -> emptySet()
                c.targetMode == TargetMode.ROTATE -> setOf(p % n)
                c.targetBeats.isEmpty() -> (0 until n).toSet()
                else -> c.targetBeats
            }

            bars += BarInfo(abs, start, isCountIn, audible, if (isCountIn) emptySet() else targets, visual)

            // Klicks
            if (audible) {
                val accentOnly = !isCountIn && c.muteMode == MuteMode.ACCENT_ONLY
                for (b in 0 until n) {
                    if (accentOnly && b != 0) continue
                    val level = when {
                        b == 0 -> ClickLevel.ACCENT
                        b in groupStarts -> ClickLevel.GROUP
                        else -> ClickLevel.BEAT
                    }
                    clicks += Click(start + b * beatSec, level)
                    if (!accentOnly && c.practiceType == PracticeType.SUBDIVISION && c.subdivisionAudible) {
                        for (k in 1 until stepsPerBeat) {
                            clicks += Click(start + (b + k.toDouble() / stepsPerBeat) * beatSec, ClickLevel.SUB)
                        }
                    }
                }
            }

            // Erwartete Tipps
            if (!isCountIn) {
                if (pattern != null) {
                    for (step in pattern.hitsInBar(p)) {
                        expected += Expected(start + step * pulseSec, p, step / stepsPerBeat, step % stepsPerBeat, audible)
                    }
                } else {
                    for (b in targets.sorted()) {
                        for (k in 0 until stepsPerBeat) {
                            expected += Expected(start + (b * stepsPerBeat + k) * pulseSec, p, b, k, audible)
                        }
                    }
                }
            }
        }

        return SessionPlan(c, beatSec, barSec, pulseSec, countIn, practiceBars, bars, clicks, expected)
    }
}
