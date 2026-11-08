package com.kevinconnors.scorestand.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class ClickGeneratorTest {
    private fun clickStarts(gen: ClickGenerator, frames: Int): List<Int> {
        val out = ShortArray(frames)
        val chunk = ShortArray(256)
        var pos = 0
        while (pos < frames) {
            gen.fill(chunk)
            System.arraycopy(chunk, 0, out, pos, minOf(256, frames - pos))
            pos += 256
        }
        // A click starts where the signal goes from silence to sound.
        return (out.indices).filter { i -> out[i].toInt() != 0 && (i == 0 || (1..40).all { k -> i - k < 0 || out[i - k].toInt() == 0 }) }
    }

    @Test
    fun clicksLandOnExactFrames() {
        val gen = ClickGenerator(44_100).apply { bpm = 120 }
        // 120 BPM = 22 050 frames per beat. The first sample of each click is sin(0) = 0, so
        // sound begins one frame later.
        val starts = clickStarts(gen, 44_100 * 2)
        assertEquals(listOf(1, 22_051, 44_101, 66_151), starts)
    }

    @Test
    fun accentIsLouderOnBeatOne() {
        val gen = ClickGenerator(44_100).apply { bpm = 240; beatsPerBar = 3 }
        val buf = ShortArray(11_025 * 3)
        val chunk = ShortArray(256)
        var p = 0
        while (p < buf.size) { gen.fill(chunk); System.arraycopy(chunk, 0, buf, p, minOf(256, buf.size - p)); p += 256 }
        fun zeroCrossings(from: Int) = (from + 1 until from + 1000).count { (buf[it - 1] < 0) != (buf[it] < 0) }
        // Accent click is higher-pitched, so it crosses zero more often.
        assertTrue(zeroCrossings(0) > zeroCrossings(11_025) + 10)
        assertEquals(zeroCrossings(11_025), zeroCrossings(22_050))
    }

    @Test
    fun beatTrackingFollowsPlayback() {
        val gen = ClickGenerator(44_100).apply { bpm = 60; beatsPerBar = 4 }
        val chunk = ShortArray(44_100)
        repeat(3) { gen.fill(chunk) }
        assertEquals(0, gen.beatAt(10))
        assertEquals(1, gen.beatAt(44_100 + 5))
        assertEquals(2, gen.beatAt(2 * 44_100 + 5))
    }

    @Test
    fun tapTempoAveragesIntervals() {
        var t = 0L
        val tap = TapTempo { t }
        assertNull(tap.tap())
        t += 500; tap.tap()
        t += 500; tap.tap()
        t += 500
        assertEquals(120, tap.tap())
        t += 5_000 // long pause starts over
        assertNull(tap.tap())
    }

    @Test
    fun tempoNames() {
        assertEquals("Adagio", tempoName(66))
        assertEquals("Allegro", tempoName(120))
    }
}

class PitchDetectorTest {
    private fun sine(hz: Double, n: Int = 4096, rate: Int = 44_100, harmonics: Boolean = false) = FloatArray(n) { i ->
        val t = i.toDouble() / rate
        var v = 0.5 * sin(2 * PI * hz * t)
        if (harmonics) v += 0.3 * sin(2 * PI * 2 * hz * t) + 0.15 * sin(2 * PI * 3 * hz * t)
        v.toFloat()
    }

    private fun assertDetects(hz: Double, harmonics: Boolean = false) {
        val f = PitchDetector.detect(sine(hz, harmonics = harmonics), 44_100)
        assertNotNull("no pitch for $hz", f)
        assertTrue("got $f for $hz", abs(centsBetween(f!!, hz.toFloat())) < 3)
    }

    @Test fun concertA() = assertDetects(440.0)
    @Test fun lowEOnBass() = assertDetects(41.2)
    @Test fun guitarLowEWithHarmonics() = assertDetects(82.41, harmonics = true)
    @Test fun highA() = assertDetects(1760.0)

    @Test
    fun noiseHasNoPitch() {
        val r = Random(1)
        assertNull(PitchDetector.detect(FloatArray(4096) { r.nextFloat() - 0.5f }, 44_100))
    }

    @Test
    fun noteNames() {
        Notes.reading(440f).let { assertEquals("A", it.name); assertEquals(4, it.octave); assertEquals(0, it.cents) }
        Notes.reading(261.63f).let { assertEquals("C", it.name); assertEquals(4, it.octave) }
        Notes.reading(446f).let { assertEquals("A", it.name); assertEquals(23, it.cents) }
        Notes.reading(466.16f, useFlats = true).let { assertEquals("B♭", it.name) }
        Notes.reading(415.3f, a4 = 415f).let { assertEquals("A", it.name); assertEquals(1, it.cents) }
    }
}
