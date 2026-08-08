package com.lastgenlabs.crunchylist.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the worst bug found in this project.
 *
 * Calibration once reported success while learning `MainActivity` — Crunchyroll's
 * entire catalogue — as an approved screen, which silently disabled the whole
 * filter while the UI still read "Guard: active". The first test below is that
 * exact scenario, using the real class names from the run that produced it.
 */
class CalibrationRulesTest {

    private val splash = "com.crunchyroll.crunchyroid.splash.ui.SplashActivity"
    private val startup = "com.crunchyroll.crunchyroid.startup.ui.StartupActivity"
    private val main = "com.crunchyroll.crunchyroid.main.ui.MainActivity"
    private val showDetails = "com.crunchyroll.crunchyroid.showdetails.ui.ShowDetailsActivity"

    @Test
    fun `rejects the catalogue screen that a normal launch also reaches`() {
        // The real failure: the home phase passed through splash -> startup -> main,
        // and the deep-link phase was sampled too early and also reported main.
        val outcome = CalibrationRules.evaluate(
            candidate = main,
            homePhaseSeen = setOf(splash, startup, main)
        )
        assertTrue(
            "Learning Crunchyroll's home screen must be refused",
            outcome is CalibrationRules.Outcome.Reject
        )
    }

    @Test
    fun `learns the show page, which a normal launch never reaches`() {
        val outcome = CalibrationRules.evaluate(
            candidate = showDetails,
            homePhaseSeen = setOf(splash, startup, main)
        )
        assertEquals(CalibrationRules.Outcome.Learn(showDetails), outcome)
    }

    @Test
    fun `rejects any transit screen, not just the final one`() {
        // The old rule compared only each phase's last class, so a candidate that
        // merely differed from the home phase's *final* screen slipped through.
        listOf(splash, startup).forEach { transit ->
            val outcome = CalibrationRules.evaluate(transit, setOf(splash, startup, main))
            assertTrue(
                "$transit appears during a normal launch and must be refused",
                outcome is CalibrationRules.Outcome.Reject
            )
        }
    }

    @Test
    fun `rejects when the deep link never settled`() {
        assertTrue(
            CalibrationRules.evaluate(null, setOf(main)) is CalibrationRules.Outcome.Reject
        )
        assertTrue(
            CalibrationRules.evaluate("", setOf(main)) is CalibrationRules.Outcome.Reject
        )
    }

    @Test
    fun `rejects when the home phase observed nothing`() {
        // With no baseline there is nothing to compare against, so "different from
        // home" is vacuously true — which is exactly how you learn a bad rule.
        assertTrue(
            CalibrationRules.evaluate(showDetails, emptySet()) is CalibrationRules.Outcome.Reject
        )
    }

    @Test
    fun `learns an obfuscated class name it cannot interpret`() {
        // Calibration must not depend on recognising what a class means — that is
        // the property that survives Crunchyroll obfuscating its activities.
        val outcome = CalibrationRules.evaluate("x.y.z", setOf("p.q.r", "s.t.u"))
        assertEquals(CalibrationRules.Outcome.Learn("x.y.z"), outcome)
    }

    @Test
    fun `every rejection explains itself`() {
        // The parent sees this text and has to be able to act on it.
        val rejections = listOf(
            CalibrationRules.evaluate(null, setOf(main)),
            CalibrationRules.evaluate(main, setOf(main)),
            CalibrationRules.evaluate(showDetails, emptySet())
        )
        rejections.forEach {
            val reason = (it as CalibrationRules.Outcome.Reject).reason
            assertTrue("Rejection reason should be a real sentence", reason.length > 20)
        }
    }
}
