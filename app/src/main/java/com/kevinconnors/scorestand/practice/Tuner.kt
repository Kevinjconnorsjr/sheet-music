package com.kevinconnors.scorestand.practice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** YIN pitch detection (de Cheveigné & Kawahara, 2002). */
object PitchDetector {
    /**
     * Returns the fundamental frequency of [samples] in Hz, or null when there's no clear pitch
     * (silence, noise, or several notes at once).
     */
    fun detect(samples: FloatArray, sampleRate: Int, minHz: Float = 27f, maxHz: Float = 4200f, threshold: Float = 0.12f): Float? {
        val half = samples.size / 2
        val maxTau = minOf(half - 1, (sampleRate / minHz).toInt())
        val minTau = maxOf(2, (sampleRate / maxHz).toInt())
        if (maxTau <= minTau + 2) return null

        // Difference function, then cumulative-mean-normalised difference.
        val d = FloatArray(maxTau + 2)
        for (tau in 1..maxTau + 1) {
            var sum = 0f
            for (i in 0 until half) {
                val delta = samples[i] - samples[i + tau]
                sum += delta * delta
            }
            d[tau] = sum
        }
        val cmnd = FloatArray(maxTau + 2)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..maxTau + 1) {
            running += d[tau]
            cmnd[tau] = if (running == 0f) 1f else d[tau] * tau / running
        }

        var tau = minTau
        while (tau <= maxTau) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                break
            }
            tau++
        }
        if (tau > maxTau) return null

        // Parabolic interpolation around the minimum for sub-sample accuracy.
        val x0 = cmnd[tau - 1]; val x1 = cmnd[tau]; val x2 = cmnd[tau + 1]
        val denom = x0 + x2 - 2 * x1
        val refined = if (denom != 0f) tau + (x0 - x2) / (2 * denom) else tau.toFloat()
        return sampleRate / refined
    }

    fun rms(samples: FloatArray): Float = sqrt(samples.fold(0f) { acc, v -> acc + v * v } / samples.size.coerceAtLeast(1))
}

data class NoteReading(val name: String, val octave: Int, val cents: Int, val frequency: Float, val targetHz: Float)

object Notes {
    private val sharps = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val flats = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

    fun reading(frequency: Float, a4: Float = 440f, useFlats: Boolean = false): NoteReading {
        val midiExact = 69 + 12 * log2(frequency / a4)
        val midi = midiExact.roundToInt()
        val cents = ((midiExact - midi) * 100).roundToInt()
        val names = if (useFlats) flats else sharps
        val target = a4 * 2f.pow((midi - 69) / 12f)
        return NoteReading(names[Math.floorMod(midi, 12)], Math.floorDiv(midi, 12) - 1, cents, frequency, target)
    }
}

/** Listens to the microphone and reports pitches. Requires RECORD_AUDIO permission. */
class TunerEngine(private val onPitch: (Float?) -> Unit) {
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // callers check the permission first
    fun start() {
        if (running) return
        running = true
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val rate = 44_100
            val window = 4096
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            // Prefer the raw microphone (no noise suppression bending the pitch); not every phone has it.
            val record = listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC).firstNotNullOfOrNull { src ->
                val r = try {
                    AudioRecord(src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, window * 4))
                } catch (_: Exception) {
                    null
                }
                if (r?.state == AudioRecord.STATE_INITIALIZED) r else { r?.release(); null }
            }
            if (record == null) {
                running = false; onPitch(null); return@Thread
            }
            val shorts = ShortArray(window / 2)
            val samples = FloatArray(window)
            val recent = ArrayDeque<Float>()
            try {
                record.startRecording()
                while (running) {
                    // Slide by half a window each read for ~20 readings a second.
                    var read = 0
                    while (read < shorts.size && running) {
                        val n = record.read(shorts, read, shorts.size - read)
                        if (n <= 0) break
                        read += n
                    }
                    System.arraycopy(samples, shorts.size, samples, 0, samples.size - shorts.size)
                    for (i in shorts.indices) samples[samples.size - shorts.size + i] = shorts[i] / 32768f
                    val pitch = if (PitchDetector.rms(samples) < 0.004f) null else PitchDetector.detect(samples, rate)
                    if (pitch == null) {
                        recent.clear(); onPitch(null)
                    } else {
                        // Median of the last three readings steadies the needle.
                        recent.addLast(pitch); while (recent.size > 3) recent.removeFirst()
                        onPitch(recent.sorted()[recent.size / 2])
                    }
                }
            } finally {
                record.stop()
                record.release()
            }
        }, "tuner").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}

/** Difference in cents between two frequencies. */
fun centsBetween(f: Float, reference: Float): Float = 1200 * log2(f / reference)
