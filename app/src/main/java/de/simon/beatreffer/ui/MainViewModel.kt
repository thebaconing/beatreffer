package de.simon.beatreffer.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import de.simon.beatreffer.audio.ClickEngine
import de.simon.beatreffer.core.Calibration
import de.simon.beatreffer.core.Click
import de.simon.beatreffer.core.ClickLevel
import de.simon.beatreffer.core.Comparison
import de.simon.beatreffer.core.PlanBuilder
import de.simon.beatreffer.core.PracticeConfig
import de.simon.beatreffer.core.Progress
import de.simon.beatreffer.core.SessionPlan
import de.simon.beatreffer.core.SessionResult
import de.simon.beatreffer.core.SessionSummary
import de.simon.beatreffer.core.Suggestion
import de.simon.beatreffer.core.TapEvaluator
import de.simon.beatreffer.core.TapOutcome
import de.simon.beatreffer.data.Repository
import java.io.File
import java.util.UUID

enum class Screen { HOME, PRACTICE, RESULT, STATS, SETTINGS, CALIBRATION }

/** Laufende Uebung. */
class PracticeSession(
    val plan: SessionPlan,
    val evaluator: TapEvaluator,
    val startedAtMillis: Long,
)

/** Letzter Tipp fuer das Live-Feedback. [seq] aendert sich bei jedem Tipp. */
data class LiveTap(val outcome: TapOutcome, val windowMs: Double, val seq: Int)

data class ResultState(
    val summary: SessionSummary,
    val result: SessionResult,
    val comparison: Comparison,
    val suggestions: List<Suggestion>,
    val saved: Boolean,
)

class CalibrationState(val beats: List<Double>) {
    val taps = ArrayList<Double>()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val engine = ClickEngine()
    private val main = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(Screen.HOME)
        private set

    var config by mutableStateOf(repo.loadConfig())
        private set

    var sessions by mutableStateOf(repo.loadSessions())
        private set

    var practice by mutableStateOf<PracticeSession?>(null)
        private set

    var liveTap by mutableStateOf<LiveTap?>(null)
        private set

    var result by mutableStateOf<ResultState?>(null)
        private set

    var calibrationMs by mutableStateOf(repo.calibrationMs)
        private set
    var isCalibrated by mutableStateOf(repo.isCalibrated)
        private set
    var exportTaps by mutableStateOf(repo.exportTaps)
        private set

    var calibration by mutableStateOf<CalibrationState?>(null)
        private set
    var calibrationRunning by mutableStateOf(false)
        private set
    /** Ergebnis der letzten Kalibrierung: Wert in ms, oder null wenn misslungen. Nicht gesetzt = noch keins. */
    var calibrationResult by mutableStateOf<Double?>(null)
        private set
    var calibrationFailed by mutableStateOf(false)
        private set
    var calibrationTapCount by mutableStateOf(0)
        private set

    private var tapSeq = 0

    // ---------- Navigation ----------

    fun open(s: Screen) {
        if (screen == Screen.PRACTICE && s != Screen.PRACTICE) stopPractice()
        if (screen == Screen.CALIBRATION && s != Screen.CALIBRATION) cancelCalibration()
        screen = s
    }

    /** Zurueck-Taste. Liefert false, wenn die App geschlossen werden darf. */
    fun back(): Boolean = when (screen) {
        Screen.HOME -> false
        Screen.PRACTICE -> { stopPractice(); true }
        Screen.CALIBRATION -> { open(Screen.SETTINGS); true }
        else -> { open(Screen.HOME); true }
    }

    // ---------- Einstellungen ----------

    fun updateConfig(transform: (PracticeConfig) -> PracticeConfig) {
        val c = runCatching { transform(config) }.getOrNull() ?: return
        config = c
        repo.saveConfig(c)
    }

    fun setExportTaps(v: Boolean) {
        exportTaps = v
        repo.exportTaps = v
    }

    fun resetCalibration() {
        calibrationMs = 0.0
        isCalibrated = false
        repo.calibrationMs = 0.0
        repo.isCalibrated = false
    }

    fun previewSound() {
        val beat = 60.0 / 100
        val clicks = listOf(ClickLevel.ACCENT, ClickLevel.BEAT, ClickLevel.BEAT, ClickLevel.BEAT)
            .mapIndexed { i, l -> Click(i * beat, l) }
        engine.start(clicks, config.sound, totalSec = 4 * beat, leadInSec = 0.15)
    }

    // ---------- Uebung ----------

    fun startPractice() {
        val plan = PlanBuilder.build(config)
        val ev = TapEvaluator(plan)
        practice = PracticeSession(plan, ev, System.currentTimeMillis())
        liveTap = null
        screen = Screen.PRACTICE
        engine.start(plan.clicks, plan.config.sound, plan.totalSec) {
            main.post { finishPractice(completed = true) }
        }
    }

    /** [uptimeMillis] aus dem Touch-Event (gleiche Uhr wie System.nanoTime). */
    fun onTap(uptimeMillis: Long) {
        val p = practice ?: return
        val t = engine.timelineSec(uptimeMillis * 1_000_000L) - calibrationMs / 1000.0
        val outcome = p.evaluator.onTap(t)
        liveTap = LiveTap(outcome, p.evaluator.windowSec * 1000.0, ++tapSeq)
    }

    fun stopPractice() {
        if (practice == null) return
        finishPractice(completed = false)
    }

    private fun finishPractice(completed: Boolean) {
        val p = practice ?: return
        val stoppedAt = if (completed) null else engine.nowSec() - calibrationMs / 1000.0
        engine.stop()
        practice = null
        val r = p.evaluator.finish(stoppedAt)
        val summary = SessionSummary.from(UUID.randomUUID().toString(), p.startedAtMillis, p.plan.config, completed, r)
        val history = sessions
        val save = r.expected >= 4
        if (save) {
            repo.saveSession(summary, r.taps)
            sessions = history + summary
        }
        result = ResultState(
            summary = summary,
            result = r,
            comparison = Progress.compare(summary, history),
            suggestions = if (completed) Progress.suggestions(summary, history) else emptyList(),
            saved = save,
        )
        screen = Screen.RESULT
    }

    fun applySuggestion(s: Suggestion) {
        updateConfig { s.newConfig }
        open(Screen.HOME)
    }

    // ---------- Kalibrierung ----------

    fun openCalibration() {
        calibration = null
        calibrationResult = null
        calibrationFailed = false
        calibrationRunning = false
        calibrationTapCount = 0
        screen = Screen.CALIBRATION
    }

    fun startCalibration() {
        val beats = Calibration.beatTimes()
        val state = CalibrationState(beats)
        calibration = state
        calibrationResult = null
        calibrationFailed = false
        calibrationTapCount = 0
        calibrationRunning = true
        val clicks = beats.mapIndexed { i, t -> Click(t, if (i % 4 == 0) ClickLevel.ACCENT else ClickLevel.BEAT) }
        engine.start(clicks, config.sound, beats.last() + 60.0 / Calibration.BPM) {
            main.post { finishCalibration() }
        }
    }

    fun onCalibrationTap(uptimeMillis: Long) {
        val c = calibration ?: return
        if (!calibrationRunning) return
        c.taps += engine.timelineSec(uptimeMillis * 1_000_000L)
        calibrationTapCount = c.taps.size
    }

    private fun finishCalibration() {
        val c = calibration ?: return
        calibrationRunning = false
        val ms = Calibration.latencyMs(c.beats, c.taps)
        calibrationResult = ms
        calibrationFailed = ms == null
    }

    fun acceptCalibration() {
        val ms = calibrationResult ?: return
        calibrationMs = ms
        isCalibrated = true
        repo.calibrationMs = ms
        repo.isCalibrated = true
        open(Screen.SETTINGS)
    }

    private fun cancelCalibration() {
        if (calibrationRunning) engine.stop()
        calibrationRunning = false
        calibration = null
    }

    // ---------- Daten ----------

    fun exportFiles(): List<File> = repo.exportCsv(sessions, exportTaps)

    fun deleteAllData() {
        repo.deleteAll()
        sessions = emptyList()
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
