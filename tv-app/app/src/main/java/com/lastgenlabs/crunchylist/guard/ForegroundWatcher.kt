package com.lastgenlabs.crunchylist.guard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Reports which activity is currently in the foreground.
 *
 * Uses UsageStatsManager rather than an AccessibilityService. The key property —
 * verified 2026-08-07, see audit §4.2 — is that ACTIVITY_RESUMED events carry the
 * full activity CLASS NAME, not just the package, which is exactly the granularity
 * needed to tell an approved Crunchyroll show page from its catalogue.
 *
 * Requires the PACKAGE_USAGE_STATS app-op. That is special access, not an
 * install-time permission — see [GuardPermissions].
 */
class ForegroundWatcher(context: Context) {

    private val usm =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?

    data class Foreground(val packageName: String, val className: String?, val timestamp: Long)

    /**
     * The most recent ACTIVITY_RESUMED event, or null if none is visible.
     *
     * A null return is ambiguous — it means either "nothing resumed recently" or
     * "we lack permission". Callers must not treat it as "safe"; [GuardService]
     * only acts on a positive Crunchyroll sighting.
     */
    fun current(windowMs: Long = DEFAULT_WINDOW_MS): Foreground? {
        val manager = usm ?: return null
        val now = System.currentTimeMillis()

        val events = try {
            manager.queryEvents(now - windowMs, now)
        } catch (_: SecurityException) {
            return null
        } ?: return null

        var latest: Foreground? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            if (latest == null || event.timeStamp >= latest.timestamp) {
                latest = Foreground(event.packageName, event.className, event.timeStamp)
            }
        }
        return latest
    }

    /** True when we can actually read usage stats — used to surface a setup prompt. */
    fun hasAccess(): Boolean = current(windowMs = 60_000L) != null

    private companion object {
        // Long enough to survive a slow poll or a quiet moment, short enough that a
        // stale event from a previous session can't be mistaken for the present.
        const val DEFAULT_WINDOW_MS = 30_000L
    }
}
