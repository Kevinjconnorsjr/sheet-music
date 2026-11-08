package com.kevinconnors.scorestand.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Left/right balance for 16-bit PCM. Mono input is spread to stereo first, so panning a mono
 * recording works too. [pan] can change at any time; it applies from the next buffer.
 */
@UnstableApi
class PanAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var pan: Float = 0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw UnhandledAudioFormatException(inputAudioFormat)
        if (inputAudioFormat.channelCount != 1 && inputAudioFormat.channelCount != 2) return AudioFormat.NOT_SET
        return AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (2 * channels)
        val out = replaceOutputBuffer(frames * 4)
        val (left, right) = gains(pan)
        repeat(frames) {
            val l = inputBuffer.short
            val r = if (channels == 2) inputBuffer.short else l
            out.putShort(scale(l, left))
            out.putShort(scale(r, right))
        }
        out.flip()
    }

    companion object {
        /** Balance law: the far side fades out, the near side stays at full level. */
        fun gains(pan: Float): Pair<Float, Float> {
            val p = pan.coerceIn(-1f, 1f)
            return (if (p > 0) 1f - p else 1f) to (if (p < 0) 1f + p else 1f)
        }

        private fun scale(sample: Short, gain: Float): Short =
            if (gain >= 1f) sample else (sample * gain).toInt().toShort()
    }
}
