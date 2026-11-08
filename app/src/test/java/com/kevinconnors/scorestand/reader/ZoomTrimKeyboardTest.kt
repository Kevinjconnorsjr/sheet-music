package com.kevinconnors.scorestand.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.kevinconnors.scorestand.practice.Keys
import com.kevinconnors.scorestand.practice.PianoSynth
import com.kevinconnors.scorestand.render.ContentBox
import com.kevinconnors.scorestand.ui.reader.ZoomState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ContentBoxTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun page(w: Int, h: Int, ink: (Int, Int) -> Boolean) = IntArray(w * h) { i -> if (ink(i % w, i / w)) black else white }

    @Test
    fun findsInkWithPadding() {
        // 100×200 page with a block of "music" from x 10..89 and y 20..179.
        val box = ContentBox.find(page(100, 200) { x, y -> x in 10..89 && y in 20..179 }, 100, 200, padding = 0f)
        assertEquals(0.10f, box.left, 1e-4f)
        assertEquals(0.90f, box.right, 1e-4f)
        assertEquals(0.10f, box.top, 1e-4f)
        assertEquals(0.90f, box.bottom, 1e-4f)
    }

    @Test
    fun ignoresSingleSpecks() {
        val box = ContentBox.find(page(100, 100) { x, y -> (x in 20..79 && y in 20..79) || (x == 2 && y == 2) }, 100, 100, padding = 0f)
        assertEquals(0.20f, box.left, 1e-4f)
    }

    @Test
    fun blankOrTinyPagesAreNotCropped() {
        assertEquals(ContentBox.FULL, ContentBox.find(page(50, 50) { _, _ -> false }, 50, 50))
        assertEquals(ContentBox.FULL, ContentBox.find(page(100, 100) { x, y -> x in 90..94 && y in 90..94 }, 100, 100))
    }

    @Test
    fun transparentPixelsCountAsPaper() {
        val pixels = IntArray(100) { 0x00000000 }
        assertEquals(ContentBox.FULL, ContentBox.find(pixels, 10, 10))
    }
}

class ZoomStateTest {
    private fun zoom() = ZoomState().apply { size = IntSize(1000, 2000) }

    @Test
    fun pinchKeepsTheFingerPointStill() {
        val z = zoom()
        val centroid = Offset(400f, 600f)
        z.transform(centroid, Offset.Zero, 2f)
        assertEquals(2f, z.scale)
        // The content point under the fingers stays under the fingers.
        val contentPoint = (centroid - z.offset) / z.scale
        assertEquals(400f, contentPoint.x, 0.01f)
        assertEquals(600f, contentPoint.y, 0.01f)
    }

    @Test
    fun cannotZoomOutPastFullSizeAndPanIsClampedWithLeftover() {
        val z = zoom()
        val leftover = z.transform(Offset(500f, 500f), Offset(0f, 300f), 0.5f)
        assertEquals(1f, z.scale)
        assertEquals(Offset.Zero, z.offset)
        // At full size all of the vertical pan is left over for scrolling.
        assertEquals(300f, leftover.y, 0.01f)
    }

    @Test
    fun panStopsAtEdges() {
        val z = zoom()
        z.transform(Offset.Zero, Offset.Zero, 2f)
        val leftover = z.transform(Offset.Zero, Offset(-5000f, 0f), 1f)
        assertEquals(-1000f, z.offset.x, 0.01f) // right edge of a 2× page on a 1000 px screen
        assertEquals(-4000f, leftover.x, 0.01f)
    }

    @Test
    fun settleSnapsAndRoundsRenderScale() {
        val z = zoom()
        z.transform(Offset.Zero, Offset.Zero, 1.01f)
        z.settle()
        assertFalse(z.isZoomed)
        assertEquals(1f, z.settledScale)
        z.transform(Offset.Zero, Offset.Zero, 2.3f)
        z.settle()
        assertEquals(2.5f, z.settledScale)
        z.transform(Offset.Zero, Offset.Zero, 10f)
        assertEquals(ZoomState.MAX, z.scale)
    }
}

class PianoSynthTest {
    private fun rms(s: ShortArray) = kotlin.math.sqrt(s.sumOf { it.toDouble() * it } / s.size)

    @Test
    fun noteSoundsThenFadesAfterRelease() {
        val synth = PianoSynth(44_100)
        val buf = ShortArray(4410)
        synth.render(buf)
        assertEquals(0.0, rms(buf), 0.0)
        synth.noteOn(60)
        synth.render(buf)
        assertTrue(rms(buf) > 1000)
        synth.noteOff(60)
        repeat(20) { synth.render(buf) } // 2 s after release
        assertTrue(rms(buf) < 50)
        assertEquals(0, synth.activeVoices)
    }

    @Test
    fun sustainHoldsNotesUntilLifted() {
        val synth = PianoSynth(44_100)
        val buf = ShortArray(4410)
        synth.sustain = true
        synth.noteOn(48)
        synth.noteOff(48)
        repeat(3) { synth.render(buf) }
        val held = rms(buf)
        synth.sustain = false
        repeat(5) { synth.render(buf) }
        assertTrue(rms(buf) < held / 10)
    }

    @Test
    fun playsAtTheRightPitch() {
        val synth = PianoSynth(44_100)
        synth.noteOn(69) // A4
        val buf = ShortArray(44_100)
        synth.render(buf)
        // Count upward zero crossings over the second half (harmonics have faded a lot by then).
        val crossings = (22_051 until 44_100).count { buf[it - 1] < 0 && buf[it] >= 0 }
        assertTrue("got $crossings", abs(crossings - 220) <= 3)
    }

    @Test
    fun keyNames() {
        assertEquals("C4", Keys.label(60))
        assertEquals("A0", Keys.label(21))
        assertTrue(Keys.isBlack(61))
        assertFalse(Keys.isBlack(64))
    }
}
