package de.thebaconing.beatreffer.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

enum class TapKind {
    /** Im Toleranzfenster eines Zielschlags. */
    HIT,
    /** Naeher an einem Zielschlag als ein halbes Raster, aber ausserhalb des Fensters. */
    OUTSIDE,
    /** Zielschlag schon getroffen, zweiter Tipp. */
    DOUBLE,
    /** Kein Zielschlag in der Naehe, z. B. ein Schlag, der gerade nicht Ziel ist. */
    EXTRA,
    /** Im Vorzaehlen oder nach dem Ende. Zaehlt nicht. */
    IGNORED,
}

data class TapOutcome(
    val kind: TapKind,
    /** Abweichung zum naechsten Zielschlag in ms. Negativ = zu frueh. */
    val offsetMs: Double?,
    val expectedIndex: Int?,
)

data class TapRecord(
    val timeSec: Double,
    val kind: TapKind,
    val offsetMs: Double?,
    val bar: Int?,
    val beat: Int?,
)

/** Zaehler fuer eine Teilmenge der erwarteten Tipps. */
data class Stat(
    val expected: Int = 0,
    val hits: Int = 0,
    val sumAbsMs: Double = 0.0,
    val sumSignedMs: Double = 0.0,
) {
    val hitRate: Double get() = if (expected == 0) 0.0 else hits.toDouble() / expected
    val meanAbsMs: Double get() = if (hits == 0) 0.0 else sumAbsMs / hits
    val meanSignedMs: Double get() = if (hits == 0) 0.0 else sumSignedMs / hits

    fun addExpected() = copy(expected = expected + 1)
    fun addHit(offsetMs: Double) = copy(hits = hits + 1, sumAbsMs = sumAbsMs + abs(offsetMs), sumSignedMs = sumSignedMs + offsetMs)
    operator fun plus(o: Stat) = Stat(expected + o.expected, hits + o.hits, sumAbsMs + o.sumAbsMs, sumSignedMs + o.sumSignedMs)
}

data class SessionResult(
    val expected: Int,
    val hits: Int,
    val misses: Int,
    val extras: Int,
    val hitRate: Double,
    /** 0..100, beruecksichtigt Treffer und Genauigkeit. */
    val score: Double,
    val meanAbsMs: Double,
    val meanSignedMs: Double,
    val stdDevMs: Double,
    /** Veraenderung der Abweichung in ms pro Minute. Positiv = wird langsamer (schleppt). */
    val driftMsPerMin: Double?,
    val perBeat: List<Stat>,
    val audible: Stat,
    val silent: Stat,
    val taps: List<TapRecord>,
    val playedSec: Double,
)

/**
 * Bewertet Tipps waehrend der Uebung (live) und liefert am Ende das Ergebnis.
 * Tipp-Zeiten sind in Sekunden auf der Zeitachse des [SessionPlan],
 * bereits um die Latenz-Kalibrierung korrigiert.
 */
class TapEvaluator(private val plan: SessionPlan) {
    private val config = plan.config
    private val expected = plan.expected
    private val times = DoubleArray(expected.size) { expected[it].timeSec }
    private val offsets = arrayOfNulls<Double>(expected.size)
    private val taps = ArrayList<TapRecord>()

    /** Wirksames Fenster: nie groesser als knapp ein halbes Raster, sonst waeren Nachbarn mehrdeutig. */
    val windowSec: Double = min(config.tolerance.windowMs / 1000.0, plan.pulseSec * 0.49)
    private val halfPulse = plan.pulseSec * 0.5
    private val endSec = (expected.lastOrNull()?.timeSec ?: plan.totalSec) + halfPulse

    val tapRecords: List<TapRecord> get() = taps

    fun onTap(t: Double): TapOutcome {
        if (expected.isEmpty() || t < plan.practiceStartSec - halfPulse || t > endSec) {
            taps += TapRecord(t, TapKind.IGNORED, null, null, null)
            return TapOutcome(TapKind.IGNORED, null, null)
        }
        val i = nearestIndex(t)
        val d = t - times[i]
        val offMs = d * 1000.0
        val e = expected[i]
        val kind = when {
            abs(d) <= windowSec && offsets[i] == null -> TapKind.HIT
            abs(d) <= windowSec -> TapKind.DOUBLE
            abs(d) <= halfPulse -> TapKind.OUTSIDE
            else -> TapKind.EXTRA
        }
        if (kind == TapKind.HIT) offsets[i] = offMs
        val shownOffset = if (kind == TapKind.EXTRA) null else offMs
        taps += TapRecord(t, kind, shownOffset, e.bar, e.beat)
        return TapOutcome(kind, shownOffset, i)
    }

    private fun nearestIndex(t: Double): Int {
        var lo = 0
        var hi = times.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < t) lo = mid + 1 else hi = mid
        }
        // lo = erstes Element >= t
        if (lo > 0 && (lo == times.size || abs(times[lo - 1] - t) <= abs(times[lo] - t))) return lo - 1
        return lo.coerceAtMost(times.size - 1)
    }

    /**
     * @param stoppedAtSec wenn die Uebung vorzeitig abgebrochen wurde, zaehlen nur
     * Zielschlaege bis zu diesem Zeitpunkt.
     */
    fun finish(stoppedAtSec: Double? = null): SessionResult {
        val limit = stoppedAtSec ?: Double.MAX_VALUE
        val n = plan.beatsPerBar
        val perBeat = MutableList(n) { Stat() }
        var audible = Stat()
        var silent = Stat()
        var count = 0
        var hits = 0
        var points = 0.0
        val hitOffsets = ArrayList<Double>()
        val hitTimes = ArrayList<Double>()

        for (i in expected.indices) {
            val e = expected[i]
            if (e.timeSec > limit) break
            count++
            perBeat[e.beat] = perBeat[e.beat].addExpected()
            if (e.audible) audible = audible.addExpected() else silent = silent.addExpected()
            val off = offsets[i] ?: continue
            hits++
            perBeat[e.beat] = perBeat[e.beat].addHit(off)
            if (e.audible) audible = audible.addHit(off) else silent = silent.addHit(off)
            points += 1.0 - 0.5 * (abs(off) / (windowSec * 1000.0)).coerceAtMost(1.0)
            hitOffsets += off
            hitTimes += e.timeSec
        }

        val extras = taps.count {
            it.timeSec <= limit && (it.kind == TapKind.OUTSIDE || it.kind == TapKind.DOUBLE || it.kind == TapKind.EXTRA)
        }
        val denominator = count + if (config.countExtraTaps) extras else 0
        val score = if (denominator == 0) 0.0 else 100.0 * points / denominator

        val meanSigned = if (hits == 0) 0.0 else hitOffsets.sum() / hits
        val meanAbs = if (hits == 0) 0.0 else hitOffsets.sumOf { abs(it) } / hits
        val std = if (hits < 2) 0.0 else sqrt(hitOffsets.sumOf { (it - meanSigned) * (it - meanSigned) } / (hits - 1))
        val played = min(limit, plan.totalSec) - plan.practiceStartSec

        return SessionResult(
            expected = count,
            hits = hits,
            misses = count - hits,
            extras = extras,
            hitRate = if (count == 0) 0.0 else hits.toDouble() / count,
            score = score,
            meanAbsMs = meanAbs,
            meanSignedMs = meanSigned,
            stdDevMs = std,
            driftMsPerMin = drift(hitTimes, hitOffsets),
            perBeat = perBeat,
            audible = audible,
            silent = silent,
            taps = taps.toList(),
            playedSec = played.coerceAtLeast(0.0),
        )
    }

    /** Steigung der Abweichung ueber die Zeit (lineare Regression), in ms pro Minute. */
    private fun drift(t: List<Double>, off: List<Double>): Double? {
        if (t.size < 8) return null
        val span = t.last() - t.first()
        if (span < 10.0) return null
        val mt = t.average()
        val mo = off.average()
        var num = 0.0
        var den = 0.0
        for (i in t.indices) {
            num += (t[i] - mt) * (off[i] - mo)
            den += (t[i] - mt) * (t[i] - mt)
        }
        if (den == 0.0) return null
        return num / den * 60.0
    }
}
