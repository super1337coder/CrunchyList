package com.lastgenlabs.crunchylist.guard

/**
 * What the guard should do, given what it can see.
 *
 * Pulled out of [GuardService] and made pure because the interesting bugs live
 * here rather than in [ScreenClassifier], and they are all timing bugs:
 *
 * The foreground reading comes from `UsageStatsManager`, which **lags**. It
 * reports where the TV was a moment ago, not where it is. So right after
 * CrunchyList fires a deep link, a tick can still see CrunchyList itself in
 * front — and the old code took that as "the kid left Crunchyroll" and threw away
 * the approval it had just granted. The launch then landed on a show page with no
 * approval behind it, the grace window got cleared by that same landing, and the
 * next tick bounced the kid straight back out of a show they were allowed to
 * watch. Playing it a second time worked, because the timing differed.
 *
 * Hence [graceActive] gating far more than it used to: while a launch we fired is
 * still in flight, a stale reading is expected and must not be acted on.
 */
object GuardDecision {

    data class Action(
        /** Send the kid back to CrunchyList. */
        val bounce: Boolean = false,
        /** The Crunchyroll session is over; coming back must be justified again. */
        val clearSession: Boolean = false,
        /** Our launch landed — this session is the approved one. */
        val affirmSession: Boolean = false,
        /** Stop being permissive; the launch we were waiting on has arrived. */
        val clearGrace: Boolean = false
    )

    fun decide(
        verdict: ScreenClassifier.Verdict,
        sessionApproved: Boolean,
        graceActive: Boolean
    ): Action = when (verdict) {

        ScreenClassifier.Verdict.IGNORE ->
            if (graceActive) {
                // Almost certainly a stale reading of CrunchyList, which is where
                // we were when we fired the intent. Clearing the session here is
                // what caused a first play to bounce.
                Action()
            } else {
                Action(clearSession = true)
            }

        ScreenClassifier.Verdict.ALLOW -> when {
            graceActive ->
                // The launch landed. Affirm rather than merely stopping the grace:
                // the approval may have been wiped in between by a stale reading,
                // and reaching an approved screen inside our own grace window is
                // proof this is our session.
                Action(affirmSession = true, clearGrace = true)

            sessionApproved -> Action()

            // An approved *kind* of screen in a session nobody started from here —
            // Google TV's own Continue-watching row does exactly this. The class
            // name cannot tell an approved show from an unapproved one, so origin
            // is the only thing standing between a kid and the whole catalogue.
            else -> Action(bounce = true)
        }

        ScreenClassifier.Verdict.BOUNCE -> when {
            // One of our own deep links is still in flight. A legitimate launch
            // transits Crunchyroll's Startup and Main activities on the way to the
            // show page, and bouncing there would make the guard fight itself.
            graceActive -> Action()
            else -> Action(bounce = true)
        }
    }
}
