package com.kevinconnors.scorestand.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {
    @Test
    fun fullVolumeUntilTheLastTwentySeconds() {
        assertEquals(1f, sleepFadeVolume(60_000), 0f)
        assertEquals(1f, sleepFadeVolume(20_000), 0f)
    }

    @Test
    fun fadesSmoothlyToSilence() {
        assertEquals(0.5f, sleepFadeVolume(10_000), 1e-6f)
        assertEquals(0.05f, sleepFadeVolume(1_000), 1e-6f)
        assertEquals(0f, sleepFadeVolume(0), 0f)
        assertEquals(0f, sleepFadeVolume(-500), 0f)
    }

    @Test
    fun countdownText() {
        assertEquals("0:00", formatCountdown(0))
        assertEquals("12:34", formatCountdown(754_000))
        assertEquals("0:01", formatCountdown(1)) // rounds up, so it never shows 0:00 while running
        assertEquals("1:02:03", formatCountdown(3_723_000))
    }
}
