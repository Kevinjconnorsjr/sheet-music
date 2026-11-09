package com.kevinconnors.scorestand.audio

import com.kevinconnors.scorestand.data.AudioMarker
import com.kevinconnors.scorestand.ui.reader.audio.PageTurns
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnsTest {
    private val turn = AudioMarker(id = 1, trackId = 9, positionMs = 10_000, label = "Page 3", page = 2)

    @Test
    fun nudgesStayInsideTheTrack() {
        assertEquals(10_100L, PageTurns.moved(turn, 100, 60_000).positionMs)
        assertEquals(0L, PageTurns.moved(turn, -20_000, 60_000).positionMs)
        assertEquals(60_000L, PageTurns.at(turn, 99_000, 60_000).positionMs)
    }

    @Test
    fun changingPageUpdatesAutomaticLabelsOnly() {
        assertEquals(AudioMarker(1, 9, 10_000, "Page 4", 3), PageTurns.toPage(turn, 3, 10))
        val named = turn.copy(label = "Chorus")
        assertEquals("Chorus", PageTurns.toPage(named, 3, 10).label)
        assertEquals(9, PageTurns.toPage(turn, 42, 10).page) // clamped to the last page
    }

    @Test
    fun shiftAllMovesOnlyPageTurns() {
        val plainMarker = AudioMarker(2, 9, 5_000, "Solo starts", -1)
        val shifted = PageTurns.shiftedAll(listOf(turn, plainMarker), 500, 60_000)
        assertEquals(listOf(10_500L), shifted.map { it.positionMs })
    }

    @Test
    fun preciseTimes() {
        assertEquals("0:00.0", PageTurns.formatPrecise(0))
        assertEquals("1:23.4", PageTurns.formatPrecise(83_456))
    }
}
