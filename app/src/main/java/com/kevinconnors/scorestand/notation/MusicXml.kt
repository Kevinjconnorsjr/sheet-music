package com.kevinconnors.scorestand.notation

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

data class PartInfo(val id: String, val name: String, /** General MIDI program, 0-based. */ val program: Int)

/** One sounding note, at concert pitch. Times are in quarter notes from the start. */
data class NoteEvent(val part: Int, val start: Double, val duration: Double, val pitch: Int)

data class TempoChange(val at: Double, val bpm: Double)

class ParsedScore(
    val title: String?,
    val composer: String?,
    val parts: List<PartInfo>,
    val notes: List<NoteEvent>,
    /** Sorted, always starting at 0. */
    val tempos: List<TempoChange>,
    /** Start of each bar, in quarter notes. */
    val measureStarts: List<Double>,
    val length: Double,
)

/**
 * Reads MusicXML (partwise, plain or compressed .mxl) into notes for playback.
 * Repeats are played straight through (no jumps).
 */
object MusicXml {
    const val MIME = "application/vnd.recordare.musicxml+xml"

    /** Byte-order mark some editors put at the start of XML files. */
    private const val BOM = "\uFEFF"

    /** Returns the MusicXML text from either a plain file or a compressed .mxl archive. */
    fun readText(bytes: ByteArray): String {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return String(bytes, Charsets.UTF_8).removePrefix(BOM)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) entries[e.name] = zip.readBytes()
            }
        }
        // META-INF/container.xml names the main score file.
        val container = entries["META-INF/container.xml"]?.let { String(it, Charsets.UTF_8) }
        val root = container?.let { Regex("full-path=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        val main = root?.let { entries[it] }
            ?: entries.entries.firstOrNull { !it.key.startsWith("META-INF") && (it.key.endsWith(".xml") || it.key.endsWith(".musicxml")) }?.value
            ?: throw IOException("This .mxl file has no score inside")
        return String(main, Charsets.UTF_8).removePrefix(BOM)
    }

    fun looksLikeMusicXml(text: String) = text.contains("<score-partwise") || text.contains("<score-timewise")

    fun parse(xml: String): ParsedScore {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
        }
        val builder = factory.newDocumentBuilder().apply {
            // MusicXML files reference an online DTD; never fetch it.
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val doc = builder.parse(InputSource(StringReader(xml)))
        val root = doc.documentElement
        if (root.tagName == "score-timewise") throw IOException("Timewise MusicXML isn't supported — export it as partwise")
        if (root.tagName != "score-partwise") throw IOException("This isn't a MusicXML score")

        val title = root.child("work")?.child("work-title")?.text() ?: root.child("movement-title")?.text()
        val composer = root.child("identification")?.children("creator")?.firstOrNull { it.getAttribute("type") == "composer" }?.text()

        val partList = root.child("part-list")?.children("score-part").orEmpty()
        val parts = partList.map { sp ->
            val program = sp.children("midi-instrument").firstNotNullOfOrNull { it.child("midi-program")?.text()?.toIntOrNull() }
            PartInfo(
                id = sp.getAttribute("id"),
                name = sp.child("part-name")?.text()?.takeIf { it.isNotBlank() } ?: sp.getAttribute("id"),
                program = ((program ?: 1) - 1).coerceIn(0, 127),
            )
        }
        val partIndex = parts.withIndex().associate { it.value.id to it.index }

        val notes = ArrayList<MutableNote>()
        val tempos = ArrayList<TempoChange>()
        val measureStarts = ArrayList<Double>()
        var length = 0.0

        root.children("part").forEach { part ->
            val pi = partIndex[part.getAttribute("id")] ?: return@forEach
            var divisions = 1.0
            var chromatic = 0
            var measureStart = 0.0
            val openTies = HashMap<Int, MutableNote>()
            part.children("measure").forEach { measure ->
                if (pi == 0) measureStarts += measureStart
                var t = 0.0
                var maxT = 0.0
                var lastStart = 0.0
                for (el in measure.elements()) {
                    when (el.tagName) {
                        "attributes" -> {
                            el.child("divisions")?.text()?.toDoubleOrNull()?.takeIf { it > 0 }?.let { divisions = it }
                            el.child("transpose")?.let { tr ->
                                chromatic = (tr.child("chromatic")?.text()?.toDoubleOrNull()?.roundToInt() ?: 0) +
                                    12 * (tr.child("octave-change")?.text()?.toIntOrNull() ?: 0)
                            }
                        }
                        "note" -> {
                            if (el.child("grace") != null || el.child("cue") != null) continue
                            val dur = (el.child("duration")?.text()?.toDoubleOrNull() ?: 0.0) / divisions
                            val chord = el.child("chord") != null
                            val start = if (chord) lastStart else t
                            val pitch = el.child("pitch")
                            if (pitch != null && el.child("rest") == null) {
                                val midi = midiOf(pitch) + chromatic
                                val ties = el.children("tie").map { it.getAttribute("type") }
                                val tieStop = "stop" in ties
                                val tieStart = "start" in ties
                                val open = if (tieStop) openTies[midi] else null
                                val abs = measureStart + start
                                if (open != null && abs <= open.start + open.duration + 1e-6) {
                                    open.duration = abs + dur - open.start
                                    if (!tieStart) openTies.remove(midi)
                                } else {
                                    val n = MutableNote(pi, abs, dur, midi)
                                    notes += n
                                    if (tieStart) openTies[midi] = n else openTies.remove(midi)
                                }
                            }
                            if (!chord) {
                                lastStart = t
                                t += dur
                            }
                            maxT = maxOf(maxT, t)
                        }
                        "backup" -> t -= (el.child("duration")?.text()?.toDoubleOrNull() ?: 0.0) / divisions
                        "forward" -> { t += (el.child("duration")?.text()?.toDoubleOrNull() ?: 0.0) / divisions; maxT = maxOf(maxT, t) }
                        "sound" -> tempoOf(el)?.let { tempos += TempoChange(measureStart + t, it) }
                        "direction" -> el.child("sound")?.let(::tempoOf)?.let { tempos += TempoChange(measureStart + t, it) }
                    }
                }
                measureStart += maxT
            }
            length = maxOf(length, measureStart)
        }

        val tempoMap = tempos.sortedBy { it.at }.distinctBy { (it.at * 1000).roundToInt() }.toMutableList()
        if (tempoMap.isEmpty() || tempoMap.first().at > 1e-6) tempoMap.add(0, TempoChange(0.0, tempoMap.firstOrNull()?.bpm ?: 120.0))
        return ParsedScore(
            title = title?.trim()?.takeIf { it.isNotEmpty() },
            composer = composer?.trim()?.takeIf { it.isNotEmpty() },
            parts = parts,
            notes = notes.map { NoteEvent(it.part, it.start, it.duration, it.pitch) }.sortedBy { it.start },
            tempos = tempoMap,
            measureStarts = measureStarts.ifEmpty { listOf(0.0) },
            length = length,
        )
    }

    private class MutableNote(val part: Int, val start: Double, var duration: Double, val pitch: Int)

    private val steps = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)

    private fun midiOf(pitch: Element): Int {
        val step = steps[pitch.child("step")?.text()?.trim()?.uppercase()] ?: 0
        val alter = pitch.child("alter")?.text()?.toDoubleOrNull()?.roundToInt() ?: 0
        val octave = pitch.child("octave")?.text()?.toIntOrNull() ?: 4
        return (octave + 1) * 12 + step + alter
    }

    private fun tempoOf(sound: Element): Double? = sound.getAttribute("tempo").toDoubleOrNull()?.takeIf { it > 0 }

    private fun Element.elements(): List<Element> {
        val out = ArrayList<Element>()
        val list = childNodes
        for (i in 0 until list.length) (list.item(i) as? Element)?.let(out::add)
        return out
    }

    private fun Element.children(tag: String) = elements().filter { it.tagName == tag }
    private fun Element.child(tag: String) = elements().firstOrNull { it.tagName == tag }
    private fun Node.text(): String = textContent ?: ""
}
