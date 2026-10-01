package de.simon.beatreffer.data

import android.content.Context
import de.simon.beatreffer.core.CsvWriter
import de.simon.beatreffer.core.PracticeConfig
import de.simon.beatreffer.core.SessionSummary
import de.simon.beatreffer.core.TapKind
import de.simon.beatreffer.core.TapRecord
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Speichert alles lokal auf dem Geraet:
 *  - Einstellungen und Kalibrierung in SharedPreferences
 *  - je Uebung eine Zeile JSON in sessions.jsonl
 *  - die einzelnen Tipps je Uebung in taps/<id>.txt (nur fuer den Export)
 */
class Repository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val sessionsFile = File(app.filesDir, "sessions.jsonl")
    private val tapsDir = File(app.filesDir, "taps")

    fun loadConfig(): PracticeConfig = prefs.getString("config", null)?.let {
        runCatching { JsonMapping.configFromJson(JSONObject(it)) }.getOrNull()
    } ?: PracticeConfig()

    fun saveConfig(c: PracticeConfig) {
        prefs.edit().putString("config", JsonMapping.configToJson(c).toString()).apply()
    }

    /** Latenz in ms, wird von jedem Tipp abgezogen. */
    var calibrationMs: Double
        get() = prefs.getFloat("calibrationMs", 0f).toDouble()
        set(v) = prefs.edit().putFloat("calibrationMs", v.toFloat()).apply()

    var isCalibrated: Boolean
        get() = prefs.getBoolean("calibrated", false)
        set(v) = prefs.edit().putBoolean("calibrated", v).apply()

    var exportTaps: Boolean
        get() = prefs.getBoolean("exportTaps", false)
        set(v) = prefs.edit().putBoolean("exportTaps", v).apply()

    @Synchronized
    fun loadSessions(): List<SessionSummary> {
        if (!sessionsFile.exists()) return emptyList()
        return sessionsFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { JsonMapping.summaryFromJson(JSONObject(line)) }.getOrNull() }
    }

    @Synchronized
    fun saveSession(summary: SessionSummary, taps: List<TapRecord>) {
        sessionsFile.appendText(JsonMapping.summaryToJson(summary).toString() + "\n")
        tapsDir.mkdirs()
        val sb = StringBuilder()
        for (t in taps) {
            sb.append(t.timeSec).append(';').append(t.kind.name).append(';')
                .append(t.offsetMs ?: "").append(';').append(t.bar ?: "").append(';').append(t.beat ?: "").append('\n')
        }
        File(tapsDir, "${summary.id}.txt").writeText(sb.toString())
    }

    private fun loadTaps(id: String): List<TapRecord> {
        val f = File(tapsDir, "$id.txt")
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val p = line.split(';')
            if (p.size < 5) return@mapNotNull null
            val kind = TapKind.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
            TapRecord(p[0].toDoubleOrNull() ?: return@mapNotNull null, kind, p[2].toDoubleOrNull(), p[3].toIntOrNull(), p[4].toIntOrNull())
        }
    }

    @Synchronized
    fun deleteAll() {
        sessionsFile.delete()
        tapsDir.deleteRecursively()
    }

    /** Schreibt die CSV-Dateien in den Cache und gibt sie zum Teilen zurueck. */
    fun exportCsv(sessions: List<SessionSummary>, includeTaps: Boolean): List<File> {
        val german = Locale.getDefault().language == "de"
        val writer = CsvWriter(separator = ';', decimalComma = german)
        val dir = File(app.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        val today = LocalDate.now().toString()
        val bom = "﻿" // damit Excel UTF-8 erkennt
        val files = ArrayList<File>()
        File(dir, "beatreffer_sessions_$today.csv").also {
            it.writeText(bom + writer.sessions(sessions))
            files += it
        }
        if (includeTaps) {
            val sb = StringBuilder(bom).append(writer.tapsHeader())
            for (s in sessions.sortedBy { it.timestampMillis }) writer.taps(s.id, s.timestampMillis, loadTaps(s.id), sb)
            File(dir, "beatreffer_taps_$today.csv").also {
                it.writeText(sb.toString())
                files += it
            }
        }
        return files
    }
}
