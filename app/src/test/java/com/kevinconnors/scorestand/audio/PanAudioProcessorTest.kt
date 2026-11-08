package com.kevinconnors.scorestand.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.kevinconnors.scorestand.ui.reader.audio.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class PanAudioProcessorTest {
    private fun run(pan: Float, channels: Int, vararg samples: Short): ShortArray {
        val p = PanAudioProcessor()
        p.pan = pan
        p.configure(AudioFormat(44_100, channels, C.ENCODING_PCM_16BIT))
        p.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.nativeOrder()).asShortBuffer()
        return ShortArray(out.remaining()).also { out.get(it) }
    }

    @Test
    fun centreLeavesStereoUntouched() {
        assertEquals(listOf<Short>(1000, -2000, 300, 400), run(0f, 2, 1000, -2000, 300, 400).toList())
    }

    @Test
    fun hardLeftSilencesRight() {
        assertEquals(listOf<Short>(1000, 0, 300, 0), run(-1f, 2, 1000, -2000, 300, 400).toList())
    }

    @Test
    fun halfRightHalvesLeft() {
        assertEquals(listOf<Short>(500, -2000), run(0.5f, 2, 1000, -2000).toList())
    }

    @Test
    fun monoBecomesPannedStereo() {
        assertEquals(listOf<Short>(1000, 250, -800, -200), run(-0.75f, 1, 1000, -800).toList())
    }

    @Test
    fun unsupportedChannelLayoutsAreLeftAlone() {
        val p = PanAudioProcessor()
        p.configure(AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))
        assertFalse(p.isActive)
    }

    @Test
    fun timeFormatting() {
        assertEquals("0:00", formatTime(0))
        assertEquals("1:05", formatTime(65_400))
        assertEquals("61:01", formatTime(3_661_000))
    }
}
