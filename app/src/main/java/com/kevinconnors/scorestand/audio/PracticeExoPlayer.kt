package com.kevinconnors.scorestand.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * An ExoPlayer for practice: [pan] runs in the audio chain, and speed/pitch are applied by
 * Android's AudioTrack (PlaybackParams) rather than ExoPlayer's Sonic processor. In media3 1.5.1
 * a pitch change through Sonic can make the audio sink's drain spin forever and freeze playback
 * (found and fixed the same way in FoldPlayer); AudioTrack params change instantly instead.
 * Takes audio focus (so a playlist and a score's track never play over each other) and pauses
 * when headphones are unplugged.
 */
@UnstableApi
fun buildPracticeExoPlayer(context: Context, pan: PanAudioProcessor): ExoPlayer {
    val renderers = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(pan))
                .build()
    }.setEnableAudioTrackPlaybackParams(true)
    return ExoPlayer.Builder(context, renderers)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
}
