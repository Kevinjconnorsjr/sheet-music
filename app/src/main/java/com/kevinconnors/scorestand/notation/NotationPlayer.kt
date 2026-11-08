package com.kevinconnors.scorestand.notation

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Plays a [ParsedScore] through Android's built-in General MIDI synthesizer. Any change to
 * key, speed or parts writes a fresh MIDI file and carries on from the same spot.
 */
class NotationPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var tempoMap: TempoMap? = null
    /** Where playback was paused/started, in quarter notes. */
    private var resumeAt = 0.0

    var isPlaying by mutableStateOf(false)
        private set

    /** Current position in quarter notes. */
    fun position(): Double {
        val p = player ?: return resumeAt
        val map = tempoMap ?: return resumeAt
        return if (isPlaying) map.quarterAt(p.currentPosition.toLong()) else resumeAt
    }

    fun play(score: ParsedScore, options: PlaybackOptions, from: Double = resumeAt) {
        release()
        val file = File(context.cacheDir, "notation/playback.mid").apply { parentFile?.mkdirs() }
        file.writeBytes(MidiWriter.write(score, options))
        val map = TempoMap(score.tempos, options.tempoPercent)
        tempoMap = map
        resumeAt = from.coerceIn(0.0, score.length)
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(file.path)
            prepare()
            setOnCompletionListener { this@NotationPlayer.isPlaying = false; resumeAt = 0.0 }
            if (resumeAt > 0) seekTo(map.msAt(resumeAt).toInt())
            start()
        }
        isPlaying = true
    }

    fun pause() {
        resumeAt = position()
        player?.pause()
        isPlaying = false
    }

    fun stop() {
        release()
        resumeAt = 0.0
    }

    fun seek(quarter: Double) { resumeAt = quarter.coerceAtLeast(0.0) }

    fun release() {
        player?.release()
        player = null
        isPlaying = false
    }
}
