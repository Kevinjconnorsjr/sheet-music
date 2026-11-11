package com.kevinconnors.scorestand.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiddleEllipsizeTest {
    private fun fitsIn(chars: Int): (String) -> Boolean = { it.length <= chars }

    @Test
    fun shortNamesAreUnchanged() {
        assertEquals("Basin Street Blues", middleEllipsize("Basin Street Blues", fitsIn(40)))
    }

    @Test
    fun keepsTheStartAndTheEnd() {
        val name = "Everybody Loves Somebody - Tenor predominant"
        val shown = middleEllipsize(name, fitsIn(30))
        assertTrue(shown, shown.length <= 30)
        assertTrue(shown, shown.startsWith("Everybody Loves"))
        assertTrue(shown, shown.endsWith("predominant"))
        assertTrue(shown, "…" in shown)
    }

    @Test
    fun usesAllTheRoomAvailable() {
        val shown = middleEllipsize("abcdefghijklmnopqrstuvwxyz", fitsIn(11))
        assertEquals("abcde…vwxyz", shown)
    }

    @Test
    fun tinySpaceStillShowsSomething() {
        assertEquals("…", middleEllipsize("Everybody Loves Somebody", fitsIn(1)))
    }
}
