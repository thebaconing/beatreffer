package de.thebaconing.beatreffer.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV-Export. Standard ist Semikolon als Trenner und Komma als Dezimalzeichen,
 * damit ein deutsches Excel die Datei direkt richtig oeffnet.
 */
class CsvWriter(
    private val separator: Char = ';',
    private val decimalComma: Boolean = true,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun num(v: Double?, digits: Int = 1): String {
        if (v == null || v.isNaN()) return ""
        val s = String.format(Locale.ROOT, "%.${digits}f", v)
        return if (decimalComma) s.replace('.', ',') else s
    }

    private fun esc(v: String): String =
        if (v.any { it == separator || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun row(vararg cells: Any?): String =
        cells.joinToString(separator.toString()) { esc(it?.toString() ?: "") }

    private fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(dateFmt)
    private fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(timeFmt)

    fun sessions(list: List<SessionSummary>): String {
        val sb = StringBuilder()
        sb.append(row(
            "session_id", "date", "time", "app_mode", "practice_type", "time_signature", "grouping", "subdivision",
            "pattern", "bpm", "mute_mode", "target_mode", "target_beats", "tolerance_ms", "count_extra_taps",
            "completed", "played_sec", "expected", "hits", "misses", "extra_taps", "hit_rate_pct", "score",
            "mean_abs_ms", "mean_signed_ms", "stddev_ms", "drift_ms_per_min", "per_beat_mean_abs_ms",
            "audible_hit_rate_pct", "silent_hit_rate_pct",
        )).append('\n')
        for (s in list.sortedBy { it.timestampMillis }) {
            val c = s.config
            sb.append(row(
                s.id, date(s.timestampMillis), time(s.timestampMillis), c.appMode.name, c.practiceType.name,
                c.timeSignature.label, c.timeSignature.groupingLabel,
                if (c.practiceType == PracticeType.SUBDIVISION) c.subdivision else "",
                if (c.practiceType == PracticeType.PATTERN) c.patternId else "",
                c.bpm, c.muteMode.name, c.targetMode.name,
                if (c.targetBeats.isEmpty()) "all" else c.targetBeats.sorted().joinToString("+") { (it + 1).toString() },
                c.tolerance.windowMs, c.countExtraTaps, s.completed, num(s.playedSec), s.expected, s.hits, s.misses,
                s.extras, num(s.hitRate * 100), num(s.score), num(s.meanAbsMs), num(s.meanSignedMs), num(s.stdDevMs),
                num(s.driftMsPerMin),
                s.perBeat.joinToString("|") { if (it.hits == 0) "-" else num(it.meanAbsMs) },
                if (s.audible.expected == 0) "" else num(s.audible.hitRate * 100),
                if (s.silent.expected == 0) "" else num(s.silent.hitRate * 100),
            )).append('\n')
        }
        return sb.toString()
    }

    fun taps(sessionId: String, timestampMillis: Long, taps: List<TapRecord>, sb: StringBuilder) {
        taps.forEachIndexed { i, t ->
            sb.append(row(
                sessionId, date(timestampMillis), i + 1, num(t.timeSec, 3), t.kind.name,
                t.bar?.plus(1) ?: "", t.beat?.plus(1) ?: "", num(t.offsetMs),
            )).append('\n')
        }
    }

    fun tapsHeader(): String = row("session_id", "date", "tap", "time_sec", "kind", "bar", "beat", "offset_ms") + "\n"
}
