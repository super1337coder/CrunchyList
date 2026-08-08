package com.lastgenlabs.crunchylist.crunchyroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Series-ID parsing.
 *
 * Why strictness matters: a bad ID cannot be validated at add-time, so it is
 * stored happily and only surfaces as a 404 *inside the Crunchyroll app*, where a
 * parent has no way to connect cause to effect. During testing a stray keystroke
 * produced "EG4PH0WXVJ" and the loose original pattern accepted it.
 */
class SeriesIdParsingTest {

    private val id = "G4PH0WXVJ"

    // --- URLs ----------------------------------------------------------------

    @Test
    fun `parses a plain series url`() {
        assertEquals(id, CrunchyrollApi.parseSeriesId("https://www.crunchyroll.com/series/$id/spy-x-family"))
    }

    @Test
    fun `parses a url with a locale prefix`() {
        assertEquals(id, CrunchyrollApi.parseSeriesId("https://www.crunchyroll.com/en-us/series/$id/spy-x-family"))
        assertEquals(id, CrunchyrollApi.parseSeriesId("https://www.crunchyroll.com/fr/series/$id/spy-x-family"))
        assertEquals(id, CrunchyrollApi.parseSeriesId("https://www.crunchyroll.com/pt-br/series/$id/spy-x-family"))
    }

    @Test
    fun `parses a url without a slug`() {
        assertEquals(id, CrunchyrollApi.parseSeriesId("https://www.crunchyroll.com/series/$id"))
    }

    @Test
    fun `parses a url with surrounding whitespace`() {
        assertEquals(id, CrunchyrollApi.parseSeriesId("  https://www.crunchyroll.com/series/$id/spy-x-family  "))
    }

    @Test
    fun `normalises case`() {
        assertEquals(id, CrunchyrollApi.parseSeriesId("https://www.crunchyroll.com/series/${id.lowercase()}/x"))
    }

    // --- bare IDs ------------------------------------------------------------

    @Test
    fun `accepts a bare id of the right shape`() {
        assertEquals(id, CrunchyrollApi.parseSeriesId(id))
        assertEquals(id, CrunchyrollApi.parseSeriesId("  $id  "))
        assertEquals("GEXH3WKP7", CrunchyrollApi.parseSeriesId("gexh3wkp7"))
    }

    @Test
    fun `rejects the typo that actually happened`() {
        // A stray leading character. The old pattern accepted this and stored a
        // whitelist entry that 404s inside Crunchyroll.
        assertNull(CrunchyrollApi.parseSeriesId("EG4PH0WXVJ"))
        assertNull(CrunchyrollApi.parseSeriesId("eG4PH0WXVJ"))
    }

    @Test
    fun `rejects ids of the wrong length`() {
        assertNull(CrunchyrollApi.parseSeriesId("G4PH0WXV"))    // too short
        assertNull(CrunchyrollApi.parseSeriesId("G4PH0WXVJJ"))  // too long
    }

    @Test
    fun `rejects ids that do not start with G`() {
        assertNull(CrunchyrollApi.parseSeriesId("X4PH0WXVJ"))
    }

    @Test
    fun `rejects junk`() {
        assertNull(CrunchyrollApi.parseSeriesId(""))
        assertNull(CrunchyrollApi.parseSeriesId("   "))
        assertNull(CrunchyrollApi.parseSeriesId("spy x family"))
        assertNull(CrunchyrollApi.parseSeriesId("https://example.com/series/G4PH0WXVJ"))
    }

    // --- the typo hint -------------------------------------------------------

    @Test
    fun `flags near-miss ids as probable typos so the message can be specific`() {
        assertTrue(CrunchyrollApi.looksLikeTypo("EG4PH0WXVJ"))
        assertTrue(CrunchyrollApi.looksLikeTypo("G4PH0WXV"))
    }

    @Test
    fun `does not flag things that were never meant to be an id`() {
        assertTrue(!CrunchyrollApi.looksLikeTypo(""))
        assertTrue(!CrunchyrollApi.looksLikeTypo("https://www.crunchyroll.com/series/G4PH0WXVJ"))
    }
}
