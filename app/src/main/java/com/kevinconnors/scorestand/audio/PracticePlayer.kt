package com.kevinconnors.scorestand.audio

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.io.File
import kotlin.math.pow

/**
 * ExoPlayer set up for practice: independent speed and pitch (via Media3's built-in Sonic
 * processor), left/right pan (via [PanAudioProcessor]) and volume. Must be used on the main thread.
 */
@OptIn(UnstableApi::class)
class PracticePlayer(context: Context) {
    private val panProcessor = PanAudioProcessor()

    private val renderers = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(panProcessor))
                .build()
    }

    private val exo: ExoPlayer = ExoPlayer.Builder(context, renderers)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
        .build()

    var isPlaying by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var ended by mutableStateOf(false)
        private set

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = exo.duration.coerceAtLeast(0)
                ended = state == Player.STATE_ENDED
            }
        })
    }

    val positionMs: Long get() = exo.currentPosition

    fun load(file: File) {
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        durationMs = 0
    }

    fun unload() {
        exo.stop()
        exo.clearMediaItems()
        durationMs = 0
    }

    fun play() {
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        exo.play()
    }

    fun pause() = exo.pause()
    fun seekTo(ms: Long) = exo.seekTo(ms.coerceAtLeast(0))

    fun setSpeedAndPitch(speed: Float, pitchCents: Int) {
        exo.playbackParameters = PlaybackParameters(speed.coerceIn(0.1f, 4f), 2f.pow(pitchCents / 1200f))
    }

    fun setPan(pan: Float) { panProcessor.pan = pan }
    fun setVolume(volume: Float) { exo.volume = volume.coerceIn(0f, 1f) }

    fun release() = exo.release()
}
