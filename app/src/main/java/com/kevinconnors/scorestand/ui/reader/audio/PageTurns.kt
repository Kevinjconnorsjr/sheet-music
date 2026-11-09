package com.kevinconnors.scorestand.ui.reader.audio

import com.kevinconnors.scorestand.data.AudioMarker

/** Rules for editing the page turns recorded on a track. */
object PageTurns {
    private val autoLabel = Regex("""Page \d+""")

    /** A turn's time, moved by [deltaMs] and kept inside the track. */
    fun moved(marker: AudioMarker, deltaMs: Long, durationMs: Long) = at(marker, marker.positionMs + deltaMs, durationMs)

    fun at(marker: AudioMarker, positionMs: Long, durationMs: Long) =
        marker.copy(positionMs = positionMs.coerceIn(0, if (durationMs > 0) durationMs else Long.MAX_VALUE))

    /** Points a turn at another page; automatic "Page N" labels follow along, custom names stay. */
    fun toPage(marker: AudioMarker, page: Int, pageCount: Int): AudioMarker {
        val p = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val label = if (autoLabel.matches(marker.label)) "Page ${p + 1}" else marker.label
        return marker.copy(page = p, label = label)
    }

    /** Moves every page turn by the same amount (e.g. the recording starts later than thought). */
    fun shiftedAll(markers: List<AudioMarker>, deltaMs: Long, durationMs: Long) =
        markers.filter { it.page >= 0 }.map { moved(it, deltaMs, durationMs) }

    /** "1:23.4" — tenths of a second, so small nudges are visible. */
    fun formatPrecise(ms: Long): String {
        val tenths = ms.coerceAtLeast(0) / 100
        return "%d:%02d.%d".format(tenths / 600, (tenths / 10) % 60, tenths % 10)
    }
}
