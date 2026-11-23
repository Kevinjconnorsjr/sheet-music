package com.kevinconnors.scorestand.playlist

/** An active sleep timer: pause at [endsAt] (epoch ms), or when the current track ends. */
data class SleepTimer(val endsAt: Long? = null, val endOfTrack: Boolean = false)

const val SLEEP_FADE_MS = 20_000L

/** Volume during the last [fadeMs] before the timer pauses playback: 1 down to 0. */
fun sleepFadeVolume(leftMs: Long, fadeMs: Long = SLEEP_FADE_MS): Float =
    if (leftMs >= fadeMs) 1f else (leftMs.toFloat() / fadeMs).coerceIn(0f, 1f)

/** 754_000 ms -> "12:34"; over an hour -> "1:02:03". */
fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
