package com.kevinconnors.scorestand.practice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Small polyphonic piano-like synthesizer: a few decaying harmonics per note with a quick attack.
 * [render] is pure (testable); [start]/[stop] run it on a low-latency audio thread.
 */
class PianoSynth(private val sampleRate: Int = 44_100) {
    private class Voice(val midi: Int, val frequency: Double) {
        var phase = 0.0
        var age = 0 // frames since key down
        var releasedAt = -1 // frame age when the key was let go
    }

    private val voices = ArrayList<Voice>()
    @Volatile var sustain = false
        set(value) {
            field = value
            // Lifting the pedal releases every note whose key is already up.
            if (!value) synchronized(voices) { voices.forEach { v -> if (v.midi !in held && v.releasedAt < 0) v.releasedAt = v.age } }
        }
    private val held = HashSet<Int>()

    fun noteOn(midi: Int) = synchronized(voices) {
        held += midi
        voices += Voice(midi, 440.0 * 2.0.pow((midi - 69) / 12.0))
        if (voices.size > MAX_VOICES) voices.removeAt(0)
    }

    fun noteOff(midi: Int) = synchronized(voices) {
        held -= midi
        if (!sustain) voices.forEach { if (it.midi == midi && it.releasedAt < 0) it.releasedAt = it.age }
    }

    fun allOff() = synchronized(voices) { held.clear(); voices.clear() }

    val activeVoices get() = synchronized(voices) { voices.size }

    /** Mixes the sounding notes into [out] (mono 16-bit). */
    fun render(out: ShortArray) = synchronized(voices) {
        val attackFrames = sampleRate * 4 / 1000
        for (i in out.indices) {
            var mix = 0.0
            for (v in voices) {
                val t = v.age.toDouble() / sampleRate
                // Higher notes fade faster, like real strings.
                val decay = exp(-t * (0.8 + (v.midi - 21) / 30.0))
                val attack = if (v.age < attackFrames) v.age.toDouble() / attackFrames else 1.0
                val release = if (v.releasedAt >= 0) exp(-(v.age - v.releasedAt).toDouble() / sampleRate * 18) else 1.0
                val p = v.phase
                val tone = sin(p) + 0.45 * sin(2 * p) * exp(-t * 2) + 0.22 * sin(3 * p) * exp(-t * 3) + 0.1 * sin(4 * p) * exp(-t * 4)
                mix += tone * decay * attack * release
                v.phase += 2 * PI * v.frequency / sampleRate
                if (v.phase > 2 * PI) v.phase -= 2 * PI
                v.age++
            }
            out[i] = (tanh(mix * 0.28) * 30_000).toInt().toShort()
        }
        // Drop notes that have faded out.
        voices.removeAll { v ->
            val t = v.age.toDouble() / sampleRate
            val release = if (v.releasedAt >= 0) exp(-(v.age - v.releasedAt).toDouble() / sampleRate * 18) else 1.0
            exp(-t * (0.8 + (v.midi - 21) / 30.0)) * release < 0.0005
        }
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuffer, 2048))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            val buffer = ShortArray(128)
            try {
                track.play()
                while (running) {
                    render(buffer)
                    track.write(buffer, 0, buffer.size)
                }
            } finally {
                track.stop()
                track.release()
            }
        }, "piano").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        allOff()
    }

    companion object {
        private const val MAX_VOICES = 16
    }
}

/** Note helpers for keyboards: MIDI 60 = middle C (C4). */
object Keys {
    private val names = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    fun isBlack(midi: Int) = Math.floorMod(midi, 12) in setOf(1, 3, 6, 8, 10)
    fun name(midi: Int) = names[Math.floorMod(midi, 12)]
    fun octave(midi: Int) = Math.floorDiv(midi, 12) - 1
    fun label(midi: Int) = "${name(midi)}${octave(midi)}"
}
