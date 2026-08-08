package com.lastgenlabs.crunchylist.guard

/**
 * Tracks whether the *current* Crunchyroll session was started by CrunchyList.
 *
 * Why this exists — the screen-type check alone is not enough.
 * [ScreenClassifier] only sees a package and a class name; it cannot tell which
 * series is on screen, so a show-details page or a player is approved no matter
 * what is playing in it. That was survivable only because Crunchyroll happens to
 * route through MainActivity (a bounce trigger) on its way to most destinations —
 * undocumented behaviour that could change in any release.
 *
 * It is also directly exploitable today: **Google TV's own home screen surfaces
 * Crunchyroll content**, with Resume buttons, in its "Continue watching" row.
 * Those launch Crunchyroll straight into content the parent never approved,
 * without CrunchyList being involved at all.
 *
 * So approval becomes two questions rather than one:
 *
 *   1. did this Crunchyroll session start from a CrunchyList tile?   (here)
 *   2. is the screen it is showing an approved kind?    ([ScreenClassifier])
 *
 * Both must hold. Leaving Crunchyroll ends the session, so coming back in by any
 * other route starts unapproved and is bounced.
 */
object SessionOrigin {

    @Volatile
    private var approved = false

    /** Call immediately before CrunchyList launches Crunchyroll itself. */
    fun beginApprovedSession() {
        approved = true
    }

    /**
     * Call when something other than Crunchyroll is in the foreground. The next
     * time Crunchyroll appears it must justify itself again.
     */
    fun leftCrunchyroll() {
        approved = false
    }

    fun isApproved(): Boolean = approved
}
