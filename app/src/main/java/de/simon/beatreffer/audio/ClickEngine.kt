package de.simon.beatreffer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import de.simon.beatreffer.core.Click
import de.simon.beatreffer.core.ClickLevel
import de.simon.beatreffer.core.ClickSound
import kotlin.math.roundToLong

/**
 * Spielt Klicks sample-genau ueber einen AudioTrack im Streaming-Modus ab.
 *
 * Alle Klicks werden vorab auf Sample-Positionen umgerechnet und beim Fuellen
 * des Puffers eingemischt. Dadurch gibt es keinen Jitter durch Timer oder Threads.
 *
 * Die Zeitachse fuer Tipps kommt aus [AudioTrack.getTimestamp]: Er sagt, welches
 * Sample zu welchem Zeitpunkt (System.nanoTime) tatsaechlich am Ausgang war.
 * So laesst sich jeder Touch-Zeitpunkt exakt auf die Klick-Zeitachse abbilden.
 */
class ClickEngine {

    val sampleRate: Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: 48000

    @Volatile private var running = false
    private var thread: Thread? = null

    // Zuordnung Sample <-> Zeit, vom Audio-Thread aktualisiert
    private class Ref(val frame: Long, val nanos: Long)
    @Volatile private var ref = Ref(0, System.nanoTime())
    @Volatile private var hasRef = false
    @Volatile private var leadFrames: Long = 0

    val isRunning: Boolean get() = running

    /**
     * Startet die Wiedergabe. Zeit 0 der Klickliste liegt [leadInSec] nach dem Start,
     * damit das Audiosystem sicher laeuft, bevor der erste Klick kommt.
     * [totalSec] ist die Laenge der Zeitachse, danach endet die Wiedergabe von selbst.
     */
    fun start(clicks: List<Click>, sound: ClickSound, totalSec: Double, leadInSec: Double = 0.5, onEnd: (() -> Unit)? = null) {
        stop()
        val sr = sampleRate
        val samples = ClickLevel.entries.associateWith { ClickSounds.render(sound, it, sr) }
        val lead = (leadInSec * sr).roundToLong()
        leadFrames = lead
        val events = clicks.sortedBy { it.timeSec }.map { (lead + (it.timeSec * sr).roundToLong()) to samples.getValue(it.level) }
        val endFrame = lead + ((totalSec + 0.3) * sr).roundToLong()

        hasRef = false
        ref = Ref(0, System.nanoTime())
        running = true

        thread = Thread({ runLoop(events, endFrame, onEnd) }, "ClickEngine").apply { start() }
    }

    fun stop() {
        running = false
        thread?.let {
            it.join(500)
        }
        thread = null
    }

    /** Bildet einen System.nanoTime-Zeitpunkt auf die Zeitachse der Klickliste ab (Sekunden). */
    fun timelineSec(nanoTime: Long): Double {
        val r = ref
        val frame = r.frame + (nanoTime - r.nanos) * sampleRate / 1e9
        return (frame - leadFrames) / sampleRate
    }

    fun nowSec(): Double = timelineSec(System.nanoTime())

    private fun runLoop(events: List<Pair<Long, FloatArray>>, endFrame: Long, onEnd: (() -> Unit)?) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val sr = sampleRate
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf * 2, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        val chunk = 256
        val mix = FloatArray(chunk)
        val out = ShortArray(chunk)
        // Aktive Klicks: Sample-Array und Leseposition
        val voices = ArrayList<Voice>()
        var next = 0
        var written = 0L
        val ts = AudioTimestamp()
        val startNanos = System.nanoTime()

        try {
            track.play()
            while (running && written < endFrame) {
                java.util.Arrays.fill(mix, 0f)
                val chunkEnd = written + chunk
                while (next < events.size && events[next].first < chunkEnd) {
                    val (frame, data) = events[next]
                    voices += Voice(data, (frame - written).toInt().coerceAtLeast(0), 0)
                    next++
                }
                val iter = voices.iterator()
                while (iter.hasNext()) {
                    val v = iter.next()
                    var i = v.startInChunk
                    while (i < chunk && v.pos < v.data.size) {
                        mix[i] += v.data[v.pos]
                        i++
                        v.pos++
                    }
                    v.startInChunk = 0
                    if (v.pos >= v.data.size) iter.remove()
                }
                for (i in 0 until chunk) {
                    out[i] = (mix[i].coerceIn(-1f, 1f) * 32000f).toInt().toShort()
                }
                track.write(out, 0, chunk)
                written += chunk

                if (track.getTimestamp(ts) && ts.nanoTime > 0) {
                    ref = Ref(ts.framePosition, ts.nanoTime)
                    hasRef = true
                } else if (!hasRef) {
                    // Notloesung, bis der erste echte Zeitstempel da ist
                    val head = track.playbackHeadPosition.toLong()
                    ref = if (head > 0) Ref(head, System.nanoTime()) else Ref(0, startNanos)
                }
            }
        } finally {
            try {
                track.pause()
                track.flush()
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
            val finishedNormally = running
            running = false
            if (finishedNormally) onEnd?.invoke()
        }
    }

    private class Voice(val data: FloatArray, var startInChunk: Int, var pos: Int)
}
