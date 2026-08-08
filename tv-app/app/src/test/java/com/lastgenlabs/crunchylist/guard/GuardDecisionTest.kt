package com.lastgenlabs.crunchylist.guard

import com.lastgenlabs.crunchylist.guard.ScreenClassifier.Verdict
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard's timing rules.
 *
 * `UsageStatsManager` reports where the TV *was*, not where it is, so every case
 * here is about a reading that arrives late. Two directions of failure, both seen
 * on real hardware: acting on a stale reading bounced a kid out of a show they
 * were allowed to watch, and not acting on one would let an unapproved show
 * through.
 */
class GuardDecisionTest {

    // --- the bug that bounced a first play ------------------------------------

    @Test
    fun `a stale reading of our own screen mid-launch does not cancel the approval`() {
        // Sequence that broke it: openShow grants the session and starts a grace
        // window, then the very next tick still sees CrunchyList in front because
        // usage stats lag. Treating that as "the kid left Crunchyroll" threw the
        // approval away, and the show that then loaded got bounced.
        val a = GuardDecision.decide(Verdict.IGNORE, sessionApproved = true, graceActive = true)

        assertFalse("must not clear the session we just granted", a.clearSession)
        assertFalse(a.bounce)
    }

    @Test
    fun `landing on the show re-affirms the session rather than only ending the grace`() {
        // Belt and braces for the same race: if a stale reading did wipe the
        // approval, reaching an approved screen inside our own grace window is
        // proof this is our session, so put it back.
        val a = GuardDecision.decide(Verdict.ALLOW, sessionApproved = false, graceActive = true)

        assertTrue(a.affirmSession)
        assertTrue(a.clearGrace)
        assertFalse(a.bounce)
    }

    @Test
    fun `transit screens during our own launch are not bounced`() {
        // A legitimate deep link passes through Crunchyroll's Startup and Main
        // activities, both of which classify as BOUNCE on their own.
        val a = GuardDecision.decide(Verdict.BOUNCE, sessionApproved = true, graceActive = true)

        assertFalse(a.bounce)
    }

    // --- the things that must still be caught ---------------------------------

    @Test
    fun `an approved screen in a session nobody started here is bounced`() {
        // Google TV's own Continue-watching row lands straight on a show page.
        // The class name looks fine; the origin is the only signal there is.
        val a = GuardDecision.decide(Verdict.ALLOW, sessionApproved = false, graceActive = false)

        assertTrue(a.bounce)
    }

    @Test
    fun `an unapproved screen is bounced even inside an approved session`() {
        // Backing out of a show into the catalogue.
        val a = GuardDecision.decide(Verdict.BOUNCE, sessionApproved = true, graceActive = false)

        assertTrue(a.bounce)
    }

    @Test
    fun `leaving Crunchyroll ends the session`() {
        val a = GuardDecision.decide(Verdict.IGNORE, sessionApproved = true, graceActive = false)

        assertTrue(a.clearSession)
        assertFalse(a.bounce)
    }

    @Test
    fun `an approved session on an approved screen is left alone`() {
        val a = GuardDecision.decide(Verdict.ALLOW, sessionApproved = true, graceActive = false)

        assertFalse(a.bounce)
        assertFalse(a.clearSession)
        assertFalse(a.affirmSession)
    }

    @Test
    fun `no combination bounces and affirms at the same time`() {
        for (v in Verdict.entries) for (s in listOf(true, false)) for (g in listOf(true, false)) {
            val a = GuardDecision.decide(v, s, g)
            assertFalse("$v approved=$s grace=$g", a.bounce && a.affirmSession)
            assertFalse("$v approved=$s grace=$g", a.affirmSession && a.clearSession)
        }
    }

    @Test
    fun `every unapproved case with no grace is a bounce`() {
        // The fail-closed property, stated as a property rather than case by case.
        for (v in listOf(Verdict.ALLOW, Verdict.BOUNCE)) {
            assertTrue(
                "$v with no approval and no grace must bounce",
                GuardDecision.decide(v, sessionApproved = false, graceActive = false).bounce
            )
        }
    }
}
