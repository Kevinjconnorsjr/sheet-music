package com.kevinconnors.scorestand.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Decodes an audio file once to get its loudness outline, for drawing a waveform. */
object Waveform {
    const val BINS = 600

    /** Returns [BINS] peak levels in 0..1, or null if the file can't be decoded. */
    fun compute(file: File): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else return null
            if (durationUs <= 0) return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val peaks = FloatArray(BINS)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIndex >= 0 -> {
                        val out = codec.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0) {
                            out.position(info.offset); out.limit(info.offset + info.size)
                            accumulate(out, info.presentationTimeUs, durationUs, sampleRate, channels, peaks)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            fillGaps(peaks)
            val max = peaks.maxOrNull()?.takeIf { it > 0f } ?: return peaks
            for (i in peaks.indices) peaks[i] = peaks[i] / max
            return peaks
        } catch (_: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release()
            extractor.release()
        }
    }

    private fun accumulate(buf: ByteBuffer, startUs: Long, durationUs: Long, sampleRate: Int, channels: Int, peaks: FloatArray) {
        val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = shorts.remaining() / channels.coerceAtLeast(1)
        val step = 4 // every 4th frame is plenty for an outline
        var f = 0
        while (f < frames) {
            val timeUs = startUs + f * 1_000_000L / sampleRate.coerceAtLeast(1)
            val bin = (timeUs * BINS / durationUs).toInt().coerceIn(0, BINS - 1)
            val v = abs(shorts.get(f * channels).toInt()) / 32768f
            if (v > peaks[bin]) peaks[bin] = v
            f += step
        }
    }

    /** Very short files can leave empty bins between buffers; borrow the neighbour's level. */
    private fun fillGaps(peaks: FloatArray) {
        for (i in 1 until peaks.size) if (peaks[i] == 0f) peaks[i] = peaks[i - 1] * 0.98f
    }

    fun write(file: File, peaks: FloatArray) {
        val bytes = ByteBuffer.allocate(peaks.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asFloatBuffer().put(peaks)
        file.writeBytes(bytes.array())
    }

    fun read(file: File): FloatArray? = try {
        val fb = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        FloatArray(fb.remaining()).also { fb.get(it) }.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}
