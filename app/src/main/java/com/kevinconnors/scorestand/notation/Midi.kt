package com.kevinconnors.scorestand.notation

import java.io.ByteArrayOutputStream
import kotlin.math.roundToLong

/** What to play: key change, speed, and which parts are heard. */
data class PlaybackOptions(
    val transpose: Int = 0,
    val tempoPercent: Int = 100,
    val muted: Set<Int> = emptySet(),
    /** null = each part's own instrument from the file. */
    val program: Int? = null,
)

/** Converts between score time (quarter notes) and playback time (ms) at a speed. */
class TempoMap(private val tempos: List<TempoChange>, tempoPercent: Int) {
    private val factor = tempoPercent.coerceIn(10, 400) / 100.0
    private val startsMs: DoubleArray

    init {
        startsMs = DoubleArray(tempos.size)
        for (i in 1 until tempos.size) {
            startsMs[i] = startsMs[i - 1] + (tempos[i].at - tempos[i - 1].at) * msPerQuarter(i - 1)
        }
    }

    private fun msPerQuarter(i: Int) = 60_000.0 / (tempos[i].bpm * factor)

    fun msAt(quarter: Double): Long {
        val i = tempos.indexOfLast { it.at <= quarter }.coerceAtLeast(0)
        return (startsMs[i] + (quarter - tempos[i].at) * msPerQuarter(i)).roundToLong()
    }

    fun quarterAt(ms: Long): Double {
        val i = startsMs.indexOfLast { it <= ms }.coerceAtLeast(0)
        return tempos[i].at + (ms - startsMs[i]) / msPerQuarter(i)
    }

    fun bpmAt(quarter: Double) = tempos[tempos.indexOfLast { it.at <= quarter }.coerceAtLeast(0)].bpm * factor
}

/** Writes Standard MIDI Files (format 1): a tempo track plus one track per audible part. */
object MidiWriter {
    const val PPQ = 480

    /** General MIDI sounds offered in the app. */
    val sounds = linkedMapOf("Piano" to 0, "Choir" to 52, "Strings" to 48, "Organ" to 19, "Flute" to 73, "Voice oohs" to 53)

    fun write(score: ParsedScore, options: PlaybackOptions): ByteArray {
        val factor = options.tempoPercent.coerceIn(10, 400) / 100.0
        val tracks = ArrayList<ByteArray>()

        // Track 0: tempo map.
        tracks += track {
            var last = 0L
            for (t in score.tempos) {
                val tick = ticks(t.at)
                val usPerQuarter = (60_000_000.0 / (t.bpm * factor)).roundToLong().coerceIn(1, 0xFFFFFF)
                delta(tick - last); last = tick
                bytes(0xFF, 0x51, 0x03, (usPerQuarter shr 16).toInt(), (usPerQuarter shr 8).toInt(), usPerQuarter.toInt())
            }
        }

        val audible = score.parts.indices.filter { it !in options.muted }
        audible.forEachIndexed { slot, partIndex ->
            // Channel 10 (index 9) is drums in General MIDI, so skip it.
            val channel = (if (slot >= 9) slot + 1 else slot) % 16
            val program = options.program ?: score.parts[partIndex].program
            data class Ev(val tick: Long, val on: Boolean, val pitch: Int)
            val events = score.notes.filter { it.part == partIndex }.flatMap { n ->
                val p = (n.pitch + options.transpose).coerceIn(0, 127)
                val on = ticks(n.start)
                val off = maxOf(on + 1, ticks(n.start + n.duration) - 1)
                listOf(Ev(on, true, p), Ev(off, false, p))
            }.sortedWith(compareBy<Ev> { it.tick }.thenBy { it.on }) // note-offs before note-ons at the same tick
            tracks += track {
                delta(0); bytes(0xC0 or channel, program)
                delta(0); bytes(0xB0 or channel, 7, 100)
                var last = 0L
                for (e in events) {
                    delta(e.tick - last); last = e.tick
                    if (e.on) bytes(0x90 or channel, e.pitch, 80) else bytes(0x80 or channel, e.pitch, 0)
                }
            }
        }

        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray())
        out.int32(6); out.int16(1); out.int16(tracks.size); out.int16(PPQ)
        for (t in tracks) {
            out.write("MTrk".toByteArray())
            out.int32(t.size)
            out.write(t)
        }
        return out.toByteArray()
    }

    private fun ticks(quarters: Double) = (quarters * PPQ).roundToLong().coerceAtLeast(0)

    private class TrackBuilder {
        val out = ByteArrayOutputStream()
        fun delta(value: Long) {
            var v = value.coerceAtLeast(0)
            val stack = ArrayList<Int>()
            stack += (v and 0x7F).toInt()
            v = v shr 7
            while (v > 0) { stack += ((v and 0x7F) or 0x80).toInt(); v = v shr 7 }
            for (b in stack.asReversed()) out.write(b)
        }
        fun bytes(vararg b: Int) = b.forEach { out.write(it and 0xFF) }
    }

    private fun track(block: TrackBuilder.() -> Unit): ByteArray {
        val b = TrackBuilder()
        b.block()
        b.delta(0); b.bytes(0xFF, 0x2F, 0x00) // end of track
        return b.out.toByteArray()
    }

    private fun ByteArrayOutputStream.int32(v: Int) { write(v ushr 24); write(v ushr 16); write(v ushr 8); write(v) }
    private fun ByteArrayOutputStream.int16(v: Int) { write(v ushr 8); write(v) }
}
