package com.lastgenlabs.crunchylist.guard

import android.content.Context

/**
 * A deliberate, time-boxed hole in the guard, for a parent.
 *
 * Found on the first real TV: with the guard running there is no way to reach
 * Crunchyroll's sign-in screen. It is not a show page and it is not the player,
 * so it fails closed and bounces — which means a fresh device cannot be set up at
 * all. The same wall sits in front of Crunchyroll's own account settings,
 * subscription and profiles.
 *
 * Two decisions worth keeping:
 *
 * **Time-boxed, not a toggle.** A switch marked "guard off" gets flipped for a
 * minute and found a fortnight later. This expires on its own.
 *
 * **Wall clock, not [android.os.SystemClock.elapsedRealtime].** elapsedRealtime
 * resets to zero on reboot, so a stored deadline would land far in the future and
 * the TV would come back up unprotected. Wall clock puts it in the past instead:
 * a reboot re-arms the guard, which is the direction to fail in.
 */
object GuardPause {

    /** Long enough to sign in with an on-screen keyboard, which is slow. */
    const val DEFAULT_MINUTES = 15

    fun begin(context: Context, minutes: Int = DEFAULT_MINUTES) {
        prefs(context).edit()
            .putLong(KEY_UNTIL, System.currentTimeMillis() + minutes * 60_000L)
            .putLong(KEY_WINDOW, minutes * 60_000L)
            .apply()
    }

    fun cancel(context: Context) {
        prefs(context).edit().remove(KEY_UNTIL).remove(KEY_WINDOW).apply()
    }

    /** Milliseconds left, or 0 when the guard is enforcing. */
    fun remainingMs(context: Context): Long {
        val p = prefs(context)
        return remaining(
            pausedUntil = p.getLong(KEY_UNTIL, 0L),
            now = System.currentTimeMillis(),
            windowMs = p.getLong(KEY_WINDOW, DEFAULT_MINUTES * 60_000L)
        )
    }

    fun isActive(context: Context): Boolean = remainingMs(context) > 0L

    /**
     * Pure, so the awkward cases can be pinned down without a device.
     *
     * [windowMs] caps how far in the future a deadline is believed. If the clock
     * is moved backwards — by hand, or by the TV correcting itself against the
     * network — an uncapped deadline could sit there for years. Anything beyond
     * the window it was granted for is treated as expired.
     */
    fun remaining(pausedUntil: Long, now: Long, windowMs: Long): Long {
        if (pausedUntil <= 0L) return 0L
        val left = pausedUntil - now
        return when {
            left <= 0L -> 0L
            left > windowMs -> 0L
            else -> left
        }
    }

    /** "14:32", for a countdown that has to be readable across a room. */
    fun format(remainingMs: Long): String {
        val total = (remainingMs + 999) / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("guard_pause", Context.MODE_PRIVATE)

    private const val KEY_UNTIL = "paused_until"
    private const val KEY_WINDOW = "window_ms"
}
