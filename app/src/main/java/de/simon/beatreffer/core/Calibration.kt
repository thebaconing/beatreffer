package de.simon.beatreffer.core

import kotlin.math.abs

/**
 * Latenz-Kalibrierung: Der Nutzer tippt zu hoerbaren Klicks.
 * Die typische Verzoegerung (Median) ist die Summe aus Touchscreen-,
 * Audio- und Wahrnehmungs-Latenz und wird spaeter von jedem Tipp abgezogen.
 */
object Calibration {
    const val BPM = 100
    const val BEATS = 20
    const val SKIP_FIRST = 4
    const val MIN_SAMPLES = 8
    const val MAX_ABS_MS = 250.0

    fun beatTimes(startSec: Double = 0.0): List<Double> = List(BEATS) { startSec + it * 60.0 / BPM }

    /** Liefert die Latenz in ms oder null, wenn zu wenig brauchbare Tipps da sind. */
    fun latencyMs(beats: List<Double>, taps: List<Double>): Double? {
        val usable = beats.drop(SKIP_FIRST)
        if (usable.isEmpty()) return null
        val diffs = taps.mapNotNull { t ->
            val nearest = usable.minByOrNull { abs(it - t) } ?: return@mapNotNull null
            val d = (t - nearest) * 1000.0
            if (abs(d) <= MAX_ABS_MS) d else null
        }
        if (diffs.size < MIN_SAMPLES) return null
        val sorted = diffs.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
