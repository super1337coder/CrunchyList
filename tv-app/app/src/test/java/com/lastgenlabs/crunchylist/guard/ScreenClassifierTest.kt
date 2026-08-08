package com.lastgenlabs.crunchylist.guard

import com.lastgenlabs.crunchylist.guard.ScreenClassifier.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The guard's central decision.
 *
 * These tests are written from one angle: **what makes the filter silently stop
 * filtering?** Every real bug in this project failed that way, so the cases below
 * lean heavily on "unknown/odd input must still bounce" rather than on the happy path.
 */
class ScreenClassifierTest {

    private val CR = ScreenClassifier.CRUNCHYROLL
    private val showDetails = "com.crunchyroll.crunchyroid.showdetails.ui.ShowDetailsActivity"
    private val player = "com.crunchyroll.crunchyroid.player.ui.PlayerActivity"
    private val main = "com.crunchyroll.crunchyroid.main.ui.MainActivity"
    private val splash = "com.crunchyroll.crunchyroid.splash.ui.SplashActivity"
    private val startup = "com.crunchyroll.crunchyroid.startup.ui.StartupActivity"

    private fun classify(pkg: String?, cls: String?, learned: Set<String> = emptySet()) =
        ScreenClassifier.classify(pkg, cls, learned)

    // --- the screens a kid is allowed to be on -------------------------------

    @Test
    fun `show details page is allowed`() {
        assertEquals(Verdict.ALLOW, classify(CR, showDetails))
    }

    @Test
    fun `player is allowed`() {
        assertEquals(Verdict.ALLOW, classify(CR, player))
    }

    // --- the escape routes ---------------------------------------------------

    @Test
    fun `crunchyroll home is bounced`() {
        assertEquals(Verdict.BOUNCE, classify(CR, main))
    }

    @Test
    fun `cold-launch screens are bounced`() {
        assertEquals(Verdict.BOUNCE, classify(CR, splash))
        assertEquals(Verdict.BOUNCE, classify(CR, startup))
    }

    // --- fail-closed behaviour ----------------------------------------------

    @Test
    fun `null class name bounces rather than allowing`() {
        // Crunchyroll is foreground but we cannot tell which screen. Not knowing
        // is not a reason to permit.
        assertEquals(Verdict.BOUNCE, classify(CR, null))
    }

    @Test
    fun `blank class name bounces`() {
        assertEquals(Verdict.BOUNCE, classify(CR, ""))
        assertEquals(Verdict.BOUNCE, classify(CR, "   "))
    }

    @Test
    fun `unrecognised crunchyroll screen bounces`() {
        // e.g. a screen added by a future Crunchyroll update
        assertEquals(Verdict.BOUNCE, classify(CR, "com.crunchyroll.crunchyroid.browse.BrowseActivity"))
        assertEquals(Verdict.BOUNCE, classify(CR, "com.crunchyroll.crunchyroid.search.SearchActivity"))
    }

    @Test
    fun `obfuscated class names bounce when nothing has been learned`() {
        // If Crunchyroll ever ships R8-renamed activities, the shape heuristics
        // stop matching. That must degrade to "unusable", never to "unfiltered".
        assertEquals(Verdict.BOUNCE, classify(CR, "a.b.c"))
    }

    // --- calibration ---------------------------------------------------------

    @Test
    fun `a learned class is allowed even when it matches no shape`() {
        // This is the whole point of calibration: surviving a rename.
        val learned = setOf("a.b.c")
        assertEquals(Verdict.ALLOW, classify(CR, "a.b.c", learned))
    }

    @Test
    fun `learning one screen does not allow other screens`() {
        val learned = setOf("a.b.c")
        assertEquals(Verdict.BOUNCE, classify(CR, "a.b.d", learned))
        assertEquals(Verdict.BOUNCE, classify(CR, main, learned))
    }

    // --- other apps ----------------------------------------------------------

    @Test
    fun `other apps are ignored, not bounced`() {
        // CrunchyList only polices Crunchyroll. Bouncing the system launcher or
        // CrunchyList itself would make the TV unusable.
        assertEquals(Verdict.IGNORE, classify("com.lastgenlabs.crunchylist", "…MainActivity"))
        assertEquals(Verdict.IGNORE, classify("com.google.android.apps.tv.launcherx", "…HomeActivity"))
        assertEquals(Verdict.IGNORE, classify(null, null))
    }

    @Test
    fun `a lookalike package is not treated as crunchyroll`() {
        assertEquals(Verdict.IGNORE, classify("com.crunchyroll.crunchyroid.evil", showDetails))
        assertEquals(Verdict.IGNORE, classify("com.evil.crunchyroll.crunchyroid", showDetails))
    }
}
