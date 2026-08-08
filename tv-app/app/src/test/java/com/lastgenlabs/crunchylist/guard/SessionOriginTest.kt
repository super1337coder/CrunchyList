package com.lastgenlabs.crunchylist.guard

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The second half of the approval decision.
 *
 * [ScreenClassifier] can only see a package and a class name, so it cannot tell an
 * approved show from an unapproved one on the same *kind* of screen. This is what
 * stops Crunchyroll being entered by any route CrunchyList did not initiate —
 * notably Google TV's own "Continue watching" row, which resumes arbitrary shows
 * straight into the player.
 */
class SessionOriginTest {

    @Before fun reset() = SessionOrigin.leftCrunchyroll()
    @After fun cleanUp() = SessionOrigin.leftCrunchyroll()

    @Test
    fun `starts unapproved`() {
        // Fail closed: nothing has vouched for this session.
        assertFalse(SessionOrigin.isApproved())
    }

    @Test
    fun `a launch from CrunchyList approves the session`() {
        SessionOrigin.beginApprovedSession()
        assertTrue(SessionOrigin.isApproved())
    }

    @Test
    fun `leaving Crunchyroll ends the session`() {
        SessionOrigin.beginApprovedSession()
        SessionOrigin.leftCrunchyroll()
        assertFalse(SessionOrigin.isApproved())
    }

    @Test
    fun `approval does not survive a round trip out and back`() {
        // The real sequence: kid opens an approved show, presses Home, then hits
        // Resume on Google TV's home row. That second entry must not inherit the
        // first one's approval.
        SessionOrigin.beginApprovedSession()
        assertTrue(SessionOrigin.isApproved())

        SessionOrigin.leftCrunchyroll()          // guard sees the launcher in front

        assertFalse(
            "Re-entering Crunchyroll outside CrunchyList must not be approved",
            SessionOrigin.isApproved()
        )
    }

    @Test
    fun `repeated approvals are idempotent`() {
        repeat(3) { SessionOrigin.beginApprovedSession() }
        assertTrue(SessionOrigin.isApproved())
        SessionOrigin.leftCrunchyroll()
        assertFalse("One exit clears it, however many times it was set", SessionOrigin.isApproved())
    }
}
