package com.kevinconnors.scorestand.notation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MusicXmlTest {
    private val xml = javaClass.classLoader!!.getResource("two-parts.musicxml")!!.readText()
    private val score = MusicXml.parse(xml)

    @Test
    fun readsTitlePartsAndInstruments() {
        assertEquals("Test Song", score.title)
        assertEquals("A. Composer", score.composer)
        assertEquals(listOf("Soprano", "Clarinet in B♭"), score.parts.map { it.name })
        assertEquals(0, score.parts[0].program)   // default piano
        assertEquals(71, score.parts[1].program)  // MusicXML 72 (1-based) = clarinet
    }

    @Test
    fun readsNotesChordsTiesRestsAndGraces() {
        val sop = score.notes.filter { it.part == 0 }.map { Triple(it.start, it.duration, it.pitch) }
        assertEquals(
            listOf(
                Triple(0.0, 1.0, 60),  // C4
                Triple(1.0, 1.0, 64),  // E4 …
                Triple(1.0, 1.0, 67),  // … with G4 (chord)
                // rest on beat 3, then F#4 tied across the bar into a half note = 3 beats
                Triple(3.0, 3.0, 66),
                Triple(6.0, 2.0, 70),  // B♭4 (the grace note is skipped)
            ),
            sop,
        )
    }

    @Test
    fun transposingInstrumentsSoundAtConcertPitch() {
        // Clarinet in B♭ written D4 sounds C4; voices with backup/forward line up.
        val cl = score.notes.filter { it.part == 1 }.map { Triple(it.start, it.duration, it.pitch) }.sortedWith(compareBy({ it.first }, { it.third }))
        assertEquals(listOf(Triple(0.0, 4.0, 60), Triple(4.0, 2.0, 58), Triple(4.0, 2.0, 62)), cl)
    }

    @Test
    fun barsAndTempoChanges() {
        assertEquals(listOf(0.0, 4.0), score.measureStarts)
        assertEquals(8.0, score.length, 1e-9)
        assertEquals(listOf(TempoChange(0.0, 120.0), TempoChange(6.0, 60.0)), score.tempos)
    }

    @Test
    fun readsCompressedMxl() {
        val zipped = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("META-INF/container.xml"))
                z.write("""<container><rootfiles><rootfile full-path="score/song.xml"/></rootfiles></container>""".toByteArray())
                z.putNextEntry(ZipEntry("score/song.xml"))
                z.write(xml.toByteArray())
            }
        }.toByteArray()
        assertEquals("Test Song", MusicXml.parse(MusicXml.readText(zipped)).title)
    }
}

class TempoAndMidiTest {
    private val score = MusicXml.parse(javaClass.classLoader!!.getResource("two-parts.musicxml")!!.readText())

    @Test
    fun tempoMapConvertsBothWays() {
        val map = TempoMap(score.tempos, 100)
        assertEquals(0L, map.msAt(0.0))
        assertEquals(3000L, map.msAt(6.0))   // 6 quarters at 120 = 3 s
        assertEquals(5000L, map.msAt(8.0))   // then 2 quarters at 60 = 2 s
        assertEquals(7.0, map.quarterAt(4000), 1e-9)
        val slow = TempoMap(score.tempos, 50)
        assertEquals(6000L, slow.msAt(6.0))
        assertEquals(60.0, slow.bpmAt(0.0), 1e-9)
    }

    /** Minimal MIDI reader: returns (track count, note-on events as (tick, channel, pitch)). */
    private fun readMidi(bytes: ByteArray): Pair<Int, List<Triple<Long, Int, Int>>> {
        var p = 0
        fun u8() = bytes[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun vlq(): Long { var v = 0L; while (true) { val b = u8(); v = (v shl 7) or (b and 0x7F).toLong(); if (b and 0x80 == 0) return v } }
        check(String(bytes, 0, 4) == "MThd"); p = 8
        u16(); val tracks = u16(); u16()
        val ons = ArrayList<Triple<Long, Int, Int>>()
        repeat(tracks) {
            check(String(bytes, p, 4) == "MTrk"); p += 4
            val end = u32() + p
            var tick = 0L
            while (p < end) {
                tick += vlq()
                val status = u8()
                when {
                    status == 0xFF -> { u8(); val len = vlq().toInt(); p += len }
                    status and 0xF0 == 0x90 -> { val pitch = u8(); val vel = u8(); if (vel > 0) ons += Triple(tick, status and 0x0F, pitch) }
                    status and 0xF0 == 0x80 || status and 0xF0 == 0xB0 -> { u8(); u8() }
                    status and 0xF0 == 0xC0 -> u8()
                }
            }
        }
        return tracks to ons
    }

    @Test
    fun midiHasEveryNoteTransposedAndMutedPartsLeftOut() {
        val (tracks, ons) = readMidi(MidiWriter.write(score, PlaybackOptions(transpose = 2)))
        assertEquals(3, tracks) // tempo + 2 parts
        assertEquals(score.notes.size, ons.size)
        assertTrue(ons.any { it.first == 0L && it.third == 62 }) // C4 + 2 = D4 at the start
        val (tracks2, ons2) = readMidi(MidiWriter.write(score, PlaybackOptions(muted = setOf(1))))
        assertEquals(2, tracks2)
        assertEquals(score.notes.count { it.part == 0 }, ons2.size)
        assertTrue(ons2.all { it.second == 0 })
    }

    @Test
    fun notesStartOnTheRightTicks() {
        val (_, ons) = readMidi(MidiWriter.write(score, PlaybackOptions()))
        val sop = ons.filter { it.second == 0 }.map { it.first }.sorted()
        assertEquals(listOf(0L, 480L, 480L, 1440L, 2880L), sop)
    }
}
