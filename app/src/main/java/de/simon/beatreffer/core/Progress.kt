package de.simon.beatreffer.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Gespeicherte Zusammenfassung einer Uebung (ohne einzelne Tipps). */
data class SessionSummary(
    val id: String,
    val timestampMillis: Long,
    val config: PracticeConfig,
    val configKey: String,
    val completed: Boolean,
    val playedSec: Double,
    val expected: Int,
    val hits: Int,
    val misses: Int,
    val extras: Int,
    val hitRate: Double,
    val score: Double,
    val meanAbsMs: Double,
    val meanSignedMs: Double,
    val stdDevMs: Double,
    val driftMsPerMin: Double?,
    val perBeat: List<Stat>,
    val audible: Stat,
    val silent: Stat,
) {
    companion object {
        fun from(id: String, timestampMillis: Long, config: PracticeConfig, completed: Boolean, r: SessionResult) =
            SessionSummary(
                id, timestampMillis, config.effective(), config.comparisonKey(), completed, r.playedSec,
                r.expected, r.hits, r.misses, r.extras, r.hitRate, r.score, r.meanAbsMs, r.meanSignedMs,
                r.stdDevMs, r.driftMsPerMin, r.perBeat, r.audible, r.silent,
            )
    }
}

data class Averages(val count: Int, val score: Double, val hitRate: Double, val meanAbsMs: Double)

data class Comparison(
    val last7: Averages?,
    val last30: Averages?,
    val previousBest: Double?,
    val isNewBest: Boolean,
)

data class DayPoint(val day: LocalDate, val count: Int, val score: Double, val hitRate: Double, val meanAbsMs: Double)

sealed class Insight {
    data object NotEnoughData : Insight()
    data object AllGood : Insight()
    /** [beat] 1-basiert. */
    data class WeakBeat(val timeSignature: String, val beat: Int, val meanAbsMs: Double, val hitRate: Double,
                        val avgAbsMs: Double, val avgHitRate: Double) : Insight()
    data class SilentWorse(val audibleHitRate: Double, val silentHitRate: Double,
                           val audibleAbsMs: Double, val silentAbsMs: Double) : Insight()
    data class TempoDrop(val fromBpm: Int, val hitRateBelow: Double, val hitRateAbove: Double) : Insight()
    data class Rushing(val meanSignedMs: Double) : Insight()
    data class Dragging(val meanSignedMs: Double) : Insight()
    data class SpeedingUp(val driftMsPerMin: Double) : Insight()
    data class SlowingDown(val driftMsPerMin: Double) : Insight()
}

sealed class Suggestion(val newConfig: PracticeConfig) {
    class TempoUp(newConfig: PracticeConfig, val bpm: Int) : Suggestion(newConfig)
    class MoreSilence(newConfig: PracticeConfig, val muteMode: MuteMode) : Suggestion(newConfig)
    class TighterTolerance(newConfig: PracticeConfig, val tolerance: Tolerance) : Suggestion(newConfig)
}

object Progress {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Nur Uebungen mit genug Daten zaehlen fuer Statistik. */
    fun usable(list: List<SessionSummary>) = list.filter { it.expected >= 4 }

    fun averages(list: List<SessionSummary>): Averages? {
        if (list.isEmpty()) return null
        return Averages(
            list.size,
            list.map { it.score }.average(),
            list.map { it.hitRate }.average(),
            list.filter { it.hits > 0 }.map { it.meanAbsMs }.let { if (it.isEmpty()) 0.0 else it.average() },
        )
    }

    /** Vergleich mit frueheren Uebungen gleicher Einstellung. [history] ohne [current]. */
    fun compare(current: SessionSummary, history: List<SessionSummary>): Comparison {
        val same = usable(history).filter { it.configKey == current.configKey && it.id != current.id }
        val now = current.timestampMillis
        val last7 = averages(same.filter { now - it.timestampMillis <= 7 * DAY_MS })
        val last30 = averages(same.filter { now - it.timestampMillis <= 30 * DAY_MS })
        val best = same.filter { it.completed }.maxOfOrNull { it.score }
        val isNewBest = current.completed && current.expected >= 4 && (best == null || current.score > best)
        return Comparison(last7, last30, best, isNewBest && best != null)
    }

    /** Bestwert pro Einstellung, neueste zuerst. */
    fun bests(history: List<SessionSummary>): List<SessionSummary> =
        usable(history).filter { it.completed }
            .groupBy { it.configKey }
            .map { (_, list) -> list.maxBy { it.score } }
            .sortedByDescending { it.timestampMillis }

    fun daily(list: List<SessionSummary>, zone: ZoneId): List<DayPoint> =
        usable(list).groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
            .map { (day, s) ->
                val a = averages(s)!!
                DayPoint(day, a.count, a.score, a.hitRate, a.meanAbsMs)
            }
            .sortedBy { it.day }

    /** Schwachstellen ueber die uebergebenen Uebungen (typisch: letzte 30 Tage). */
    fun analyze(list: List<SessionSummary>): List<Insight> {
        val s = usable(list)
        if (s.size < 3) return listOf(Insight.NotEnoughData)
        val out = ArrayList<Insight>()

        // 1) Schwache Schlaege pro Taktart (nur ohne Muster, dort sind die Schlaege ungleich belegt)
        s.filter { it.config.practiceType != PracticeType.PATTERN }
            .groupBy { it.config.timeSignature.label }
            .filter { it.value.size >= 3 }
            .forEach { (label, group) ->
                val n = group.first().perBeat.size
                val perBeat = (0 until n).map { b -> group.fold(Stat()) { acc, x -> acc + (x.perBeat.getOrNull(b) ?: Stat()) } }
                val total = perBeat.fold(Stat()) { a, b -> a + b }
                if (total.hits < 20) return@forEach
                val worst = perBeat.withIndex()
                    .filter { it.value.expected >= 8 }
                    .maxByOrNull { it.value.meanAbsMs + (1 - it.value.hitRate) * 100 }
                    ?: return@forEach
                val w = worst.value
                val clearlyWorse = (w.meanAbsMs > total.meanAbsMs * 1.3 && w.meanAbsMs - total.meanAbsMs > 5) ||
                    (total.hitRate - w.hitRate >= 0.10)
                if (clearlyWorse) {
                    out += Insight.WeakBeat(label, worst.index + 1, w.meanAbsMs, w.hitRate, total.meanAbsMs, total.hitRate)
                }
            }

        // 2) Stille Passagen
        val aud = s.fold(Stat()) { a, x -> a + x.audible }
        val sil = s.fold(Stat()) { a, x -> a + x.silent }
        if (aud.expected >= 20 && sil.expected >= 20) {
            if (aud.hitRate - sil.hitRate >= 0.08 || (sil.hits > 5 && sil.meanAbsMs > aud.meanAbsMs * 1.3 && sil.meanAbsMs - aud.meanAbsMs > 5)) {
                out += Insight.SilentWorse(aud.hitRate, sil.hitRate, aud.meanAbsMs, sil.meanAbsMs)
            }
        }

        // 3) Tempo-Grenze: ab welchem Tempo faellt die Trefferquote deutlich ab
        val buckets = listOf(40, 80, 110, 140, 170)
        fun bucketOf(bpm: Int) = buckets.last { bpm >= it }
        val byBucket = s.groupBy { bucketOf(it.config.bpm) }
            .filter { it.value.size >= 2 }
            .mapValues { e -> e.value.map { it.hitRate }.average() }
            .toSortedMap()
        var bestBelow: Double? = null
        for ((bpm, rate) in byBucket) {
            val b = bestBelow
            if (b != null && b - rate >= 0.10) {
                out += Insight.TempoDrop(bpm, b, rate)
                break
            }
            bestBelow = maxOf(b ?: rate, rate)
        }

        // 4) Tendenz eilen / schleppen
        val withHits = s.filter { it.hits >= 5 }
        if (withHits.size >= 3) {
            val totalHits = withHits.sumOf { it.hits }
            val signed = withHits.sumOf { it.meanSignedMs * it.hits } / totalHits
            if (signed <= -8) out += Insight.Rushing(signed)
            if (signed >= 8) out += Insight.Dragging(signed)
        }

        // 5) Tempo-Drift innerhalb einer Uebung
        val drifts = s.mapNotNull { it.driftMsPerMin }
        if (drifts.size >= 3) {
            val d = drifts.average()
            if (d <= -10) out += Insight.SpeedingUp(d)
            if (d >= 10) out += Insight.SlowingDown(d)
        }

        if (out.isEmpty()) out += Insight.AllGood
        return out
    }

    const val SUGGEST_SESSIONS = 3
    const val SUGGEST_SCORE = 85.0
    const val SUGGEST_HIT_RATE = 0.90

    /**
     * Vorschlaege fuer die naechste Stufe, wenn die letzten [SUGGEST_SESSIONS]
     * Uebungen mit dieser Einstellung (inkl. der aktuellen) stabil gut waren.
     */
    fun suggestions(current: SessionSummary, history: List<SessionSummary>): List<Suggestion> {
        val recent = (usable(history).filter { it.configKey == current.configKey && it.id != current.id && it.completed } +
            current)
            .sortedByDescending { it.timestampMillis }
            .take(SUGGEST_SESSIONS)
        if (recent.size < SUGGEST_SESSIONS) return emptyList()
        if (!recent.all { it.completed && it.score >= SUGGEST_SCORE && it.hitRate >= SUGGEST_HIT_RATE }) return emptyList()

        val c = current.config
        val out = ArrayList<Suggestion>()
        if (c.bpm < PracticeConfig.MAX_BPM) {
            val bpm = minOf(c.bpm + 5, PracticeConfig.MAX_BPM)
            out += Suggestion.TempoUp(c.copy(bpm = bpm), bpm)
        }
        nextMuteMode(c)?.let { out += Suggestion.MoreSilence(c.copy(muteMode = it), it) }
        if (c.appMode == AppMode.ADVANCED) {
            c.tolerance.tighter()?.let { out += Suggestion.TighterTolerance(c.copy(tolerance = it), it) }
        }
        return out
    }

    fun nextMuteMode(c: PracticeConfig): MuteMode? {
        val order = if (c.appMode == AppMode.SIMPLE) MuteMode.SIMPLE_MODES else MuteMode.entries
        val i = order.indexOf(c.muteMode)
        return if (i < 0) order.first() else order.getOrNull(i + 1)
    }
}
