package com.kevinconnors.scorestand.practice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Produces the metronome's audio, one buffer at a time. Clicks start on exact sample frames,
 * so the tempo never drifts. Tempo/beat changes take effect from the next beat.
 */
class ClickGenerator(private val sampleRate: Int = 44_100) {
    @Volatile var bpm: Int = 100
    @Volatile var beatsPerBar: Int = 4
    @Volatile var accentFirstBeat: Boolean = true
    @Volatile var volume: Float = 0.8f

    private val accentClick = click(1_760.0)
    private val normalClick = click(1_100.0)

    /** Total frames produced so far. */
    var framesWritten = 0L
        private set
    private var nextBeatFrame = 0L
    private var beatCount = 0L
    private var clickPos = Int.MAX_VALUE
    private var currentClick = normalClick

    private val beatFrames = LongArray(16)
    private val beatNumbers = IntArray(16)
    private var beatHead = 0

    fun framesPerBeat(): Long = sampleRate * 60L / bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun fill(buffer: ShortArray) {
        val vol = volume.coerceIn(0f, 1f)
        for (i in buffer.indices) {
            val frame = framesWritten + i
            if (frame >= nextBeatFrame) {
                val beatInBar = (beatCount % beatsPerBar.coerceAtLeast(1)).toInt()
                currentClick = if (accentFirstBeat && beatInBar == 0 && beatsPerBar > 1) accentClick else normalClick
                clickPos = 0
                synchronized(beatFrames) {
                    beatFrames[beatHead] = frame
                    beatNumbers[beatHead] = beatInBar
                    beatHead = (beatHead + 1) % beatFrames.size
                }
                beatCount++
                nextBeatFrame += framesPerBeat()
            }
            buffer[i] = if (clickPos < currentClick.size) (currentClick[clickPos++] * vol).toInt().toShort() else 0
        }
        framesWritten += buffer.size
    }

    /** The beat-in-bar (0-based) that started most recently at or before [playedFrame], or -1. */
    fun beatAt(playedFrame: Long): Int = synchronized(beatFrames) {
        val filled = minOf(beatCount, beatFrames.size.toLong()).toInt()
        var bestFrame = -1L
        var beat = -1
        for (i in 0 until filled) {
            val f = beatFrames[i]
            if (f <= playedFrame && f > bestFrame) { bestFrame = f; beat = beatNumbers[i] }
        }
        beat
    }

    private fun click(freq: Double): ShortArray {
        val length = sampleRate * 35 / 1000
        return ShortArray(length) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2 * PI * freq * t) * exp(-t * 140) * 30_000).toInt().toShort()
        }
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 300
    }
}

/** Plays [ClickGenerator] output through a low-latency audio stream on its own thread. */
class MetronomeEngine {
    @Volatile var bpm = 100
        set(value) { field = value.coerceIn(ClickGenerator.MIN_BPM, ClickGenerator.MAX_BPM); generator.bpm = field }
    @Volatile var beatsPerBar = 4
        set(value) { field = value.coerceIn(1, 16); generator.beatsPerBar = field }
    @Volatile var accentFirstBeat = true
        set(value) { field = value; generator.accentFirstBeat = value }
    @Volatile var volume = 0.8f
        set(value) { field = value; generator.volume = value }

    /** Replaced on every start so beat 1 always lands on the first click. */
    @Volatile private var generator = ClickGenerator(SAMPLE_RATE)
    @Volatile private var running = false
    @Volatile private var track: AudioTrack? = null
    private var thread: Thread? = null

    val isRunning get() = running

    fun start() {
        if (running) return
        val gen = ClickGenerator(SAMPLE_RATE).also {
            it.bpm = bpm; it.beatsPerBar = beatsPerBar; it.accentFirstBeat = accentFirstBeat; it.volume = volume
        }
        generator = gen
        running = true
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuffer, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            val buffer = ShortArray(256)
            try {
                t.play()
                track = t
                while (running) {
                    gen.fill(buffer)
                    t.write(buffer, 0, buffer.size)
                }
            } finally {
                track = null
                t.stop()
                t.release()
            }
        }, "metronome").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    /** Beat-in-bar now sounding (0-based), or -1 when stopped. */
    fun currentBeat(): Int {
        val t = track ?: return -1
        return generator.beatAt(t.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        const val SAMPLE_RATE = 44_100
    }
}

/** Estimates tempo from taps: averages the last few intervals, restarting after a pause. */
class TapTempo(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val taps = ArrayDeque<Long>()

    /** Records a tap; returns the tempo once there are at least two taps. */
    fun tap(): Int? {
        val t = now()
        if (taps.isNotEmpty() && t - taps.last() > 2_000) taps.clear()
        taps.addLast(t)
        while (taps.size > 5) taps.removeFirst()
        if (taps.size < 2) return null
        val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        return (60_000 / avg).toInt().coerceIn(ClickGenerator.MIN_BPM, ClickGenerator.MAX_BPM)
    }
}

fun tempoName(bpm: Int): String = when {
    bpm < 45 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 72 -> "Adagio"
    bpm < 92 -> "Andante"
    bpm < 112 -> "Moderato"
    bpm < 140 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}
