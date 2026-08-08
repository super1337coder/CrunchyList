package com.lastgenlabs.crunchylist.guard

/**
 * The decision at the heart of the guard, as a pure function.
 *
 * Split out from [ScreenPolicy] (which owns storage) so it can be tested without
 * a device. That matters more here than anywhere else in the app: every serious
 * bug found in this project failed in the "looks fine, protects nothing"
 * direction, and this is the code that decides whether anything is protected.
 */
object ScreenClassifier {

    enum class Verdict {
        /** An approved Crunchyroll screen. Leave it alone. */
        ALLOW,

        /** Crunchyroll, but somewhere the kid shouldn't be. Bounce. */
        BOUNCE,

        /** Not Crunchyroll — CrunchyList itself, system UI, anything else. */
        IGNORE
    }

    const val CRUNCHYROLL = "com.crunchyroll.crunchyroid"

    /**
     * Substrings identifying approved screens.
     *
     * Matched against the class name rather than pinned to a fully-qualified
     * name, so a package reshuffle like `.ui.showdetails.DetailsActivity` still
     * matches. Calibration adds exact learned names on top, which is what keeps
     * the guard working if these substrings ever stop appearing.
     */
    val APPROVED_SHAPES = listOf("ShowDetails", "Player")

    /**
     * FAIL CLOSED. Anything not positively recognised as approved is bounced —
     * so a Crunchyroll rename makes CrunchyList unusable, never permissive.
     */
    fun classify(packageName: String?, className: String?, learned: Set<String>): Verdict {
        if (packageName != CRUNCHYROLL) return Verdict.IGNORE

        // Crunchyroll is foreground but we can't tell which screen. Not a reason
        // to allow it.
        if (className.isNullOrBlank()) return Verdict.BOUNCE

        if (className in learned) return Verdict.ALLOW
        if (APPROVED_SHAPES.any { className.contains(it, ignoreCase = true) }) return Verdict.ALLOW

        return Verdict.BOUNCE
    }
}
