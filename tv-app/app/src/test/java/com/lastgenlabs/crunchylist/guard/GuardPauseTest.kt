package com.lastgenlabs.crunchylist.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pause is a hole in the only thing that makes this a parental control, so the
 * only interesting question is how it ends. Every case here is one where it
 * fails to end.
 */
class GuardPauseTest {

    private val window = 15 * 60_000L

    @Test
    fun `never paused means enforcing`() {
        assertEquals(0L, GuardPause.remaining(pausedUntil = 0L, now = 1_000L, windowMs = window))
    }

    @Test
    fun `counts down while the window is open`() {
        assertEquals(
            60_000L,
            GuardPause.remaining(pausedUntil = 160_000L, now = 100_000L, windowMs = window)
        )
    }

    @Test
    fun `expires on its own`() {
        assertEquals(
            0L,
            GuardPause.remaining(pausedUntil = 100_000L, now = 100_001L, windowMs = window)
        )
    }

    @Test
    fun `a reboot re-arms the guard`() {
        // The deadline is wall-clock, so after a reboot it is simply in the past.
        // Stored as elapsedRealtime it would have landed far in the future and the
        // TV would have come back up unprotected — which is the whole reason for
        // the choice.
        val pausedBeforeReboot = 1_000_000L
        val nowAfterReboot = 5_000_000L
        assertEquals(0L, GuardPause.remaining(pausedBeforeReboot, nowAfterReboot, window))
    }

    @Test
    fun `a clock jumped backwards does not leave the guard off for years`() {
        // The TV correcting itself against the network, or someone changing the
        // date. A deadline further out than the window it was granted for cannot
        // be honest, so it is treated as expired.
        val pausedUntil = 1_000_000_000L
        val nowAfterClockWentBackwards = 1_000L
        assertEquals(0L, GuardPause.remaining(pausedUntil, nowAfterClockWentBackwards, window))
    }

    @Test
    fun `a deadline exactly at the window edge still counts`() {
        assertEquals(window, GuardPause.remaining(window + 500L, 500L, window))
    }

    @Test
    fun `the countdown reads as minutes and seconds`() {
        assertEquals("15:00", GuardPause.format(15 * 60_000L))
        assertEquals("1:05", GuardPause.format(65_000L))
        assertEquals("0:01", GuardPause.format(1L))
        assertEquals("0:00", GuardPause.format(0L))
    }

    @Test
    fun `the default window is long enough to sign in on a remote`() {
        // Signing in to Crunchyroll on a TV is a device-code dance with an
        // on-screen keyboard. Anything under ten minutes strands a parent midway.
        assertTrue(GuardPause.DEFAULT_MINUTES >= 10)
    }
}
