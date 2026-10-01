package de.simon.beatreffer.audio

import de.simon.beatreffer.core.ClickLevel
import de.simon.beatreffer.core.ClickSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Erzeugt die Klickgeraeusche synthetisch, damit keine Audiodateien noetig sind.
 * Ergebnis: Mono-Samples im Bereich -1..1.
 */
object ClickSounds {

    private fun gain(level: ClickLevel) = when (level) {
        ClickLevel.ACCENT -> 1.0f
        ClickLevel.GROUP -> 0.8f
        ClickLevel.BEAT -> 0.6f
        ClickLevel.SUB -> 0.35f
    }

    fun render(sound: ClickSound, level: ClickLevel, sampleRate: Int): FloatArray = when (sound) {
        ClickSound.WOODBLOCK -> woodblock(level, sampleRate)
        ClickSound.BEEP -> beep(level, sampleRate)
        ClickSound.HIHAT -> hihat(level, sampleRate)
    }

    private fun woodblock(level: ClickLevel, sr: Int): FloatArray {
        val freq = when (level) {
            ClickLevel.ACCENT -> 1750.0
            ClickLevel.GROUP -> 1400.0
            ClickLevel.BEAT -> 1150.0
            ClickLevel.SUB -> 950.0
        }
        val len = (sr * 0.06).toInt()
        val g = gain(level)
        val rnd = Random(1)
        return FloatArray(len) { i ->
            val t = i.toDouble() / sr
            val env = exp(-t * 90.0)
            val body = sin(2 * PI * freq * t) * 0.8 + sin(2 * PI * freq * 2.7 * t) * 0.2
            val transient = if (i < sr / 1000) (rnd.nextDouble() * 2 - 1) * 0.5 else 0.0
            ((body * env + transient) * g).toFloat()
        }
    }

    private fun beep(level: ClickLevel, sr: Int): FloatArray {
        val freq = when (level) {
            ClickLevel.ACCENT -> 1760.0
            ClickLevel.GROUP -> 1320.0
            ClickLevel.BEAT -> 880.0
            ClickLevel.SUB -> 660.0
        }
        val len = (sr * 0.05).toInt()
        val attack = sr / 1000 * 2
        val g = gain(level) * 0.8f
        return FloatArray(len) { i ->
            val t = i.toDouble() / sr
            val a = if (i < attack) i.toDouble() / attack else 1.0
            val r = if (i > len - attack * 4) (len - i).toDouble() / (attack * 4) else 1.0
            (sin(2 * PI * freq * t) * a * r * g).toFloat()
        }
    }

    private fun hihat(level: ClickLevel, sr: Int): FloatArray {
        val decay = if (level == ClickLevel.ACCENT) 45.0 else 70.0
        val len = (sr * 0.08).toInt()
        val g = gain(level)
        val rnd = Random(7)
        var prev = 0.0
        return FloatArray(len) { i ->
            val t = i.toDouble() / sr
            val noise = rnd.nextDouble() * 2 - 1
            val hp = noise - prev // einfacher Hochpass
            prev = noise
            (hp * 0.5 * exp(-t * decay) * g).toFloat()
        }
    }
}
