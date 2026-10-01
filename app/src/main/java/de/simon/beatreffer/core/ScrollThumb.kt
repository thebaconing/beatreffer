package de.simon.beatreffer.core

/** Lage des Scroll-Anzeigers, beides als Anteil der sichtbaren Höhe (0..1). */
data class ScrollThumb(val top: Double, val height: Double)

/**
 * Berechnet den Scroll-Anzeiger aus Scrollposition, maximaler Scrollweite und sichtbarer Höhe (gleiche Einheit, z. B. px).
 * Liefert null, wenn nichts zu scrollen ist. Der Anzeiger ist mindestens [minHeight] hoch, damit er sichtbar bleibt.
 */
fun scrollThumb(value: Double, max: Double, viewport: Double, minHeight: Double = 0.08): ScrollThumb? {
    if (max <= 0.0 || viewport <= 0.0) return null
    val height = (viewport / (viewport + max)).coerceIn(minHeight, 1.0)
    val progress = (value / max).coerceIn(0.0, 1.0)
    return ScrollThumb(top = progress * (1.0 - height), height = height)
}
