package com.lastgenlabs.crunchylist.crunchyroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the undocumented `crunchyroll://` grammar.
 *
 * Both traps here fail *silently* — a wrong URI lands on Crunchyroll's home
 * screen with no error, which is indistinguishable from the app misbehaving and
 * would quietly hand a kid the full catalogue. Re-derive with
 * `tools/probe-deeplinks.ps1` if Crunchyroll ever changes it.
 */
class CrunchyrollUriTest {

    @Test
    fun `series uri is path-only`() {
        assertEquals("crunchyroll://series/G4PH0WXVJ", Crunchyroll.seriesUri("G4PH0WXVJ"))
    }

    @Test
    fun `series uri carries no host segment`() {
        // TRAP: crunchyroll://www.crunchyroll.com/series/ID silently opens
        // Crunchyroll's home screen instead of the series.
        val uri = Crunchyroll.seriesUri("G4PH0WXVJ")
        assertFalse("URI must not contain a host", uri.contains("crunchyroll.com"))
        assertTrue("URI must be scheme + path only", uri.startsWith("crunchyroll://series/"))
    }

    @Test
    fun `episode uri uses the episode verb, not watch`() {
        // TRAP: the web URL path is /watch/, but crunchyroll://watch/ID is not a
        // route and falls back to the home screen. The verb is 'episode'.
        val uri = Crunchyroll.episodeUri("GZ7UVPVX5")
        assertEquals("crunchyroll://episode/GZ7UVPVX5", uri)
        assertFalse("'watch' is not a valid scheme verb", uri.contains("watch"))
    }

    @Test
    fun `package name is the crunchyroll android app`() {
        assertEquals("com.crunchyroll.crunchyroid", Crunchyroll.PACKAGE)
    }
}
