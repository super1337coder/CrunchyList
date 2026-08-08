package com.lastgenlabs.crunchylist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two ways bundle reconciliation goes wrong are opposite and both silent.
 *
 * Too shy and shipping better copy is a no-op — the APK carries new text and the
 * device keeps rendering what it cached on first run, with everything reporting
 * success. Too eager and a show the parent deliberately removed comes back on the
 * next update, which is the fail-open bug the Chrome extension had (audit §3.9).
 *
 * Neither shows up in a screenshot, so they get pinned here instead.
 */
class SeedMergeTest {

    private fun show(id: String, about: String = "", title: String = id) =
        Show(seriesId = id, title = title, about = about)

    @Test
    fun `first run seeds the whole bundle`() {
        val bundle = listOf(show("G1"), show("G2"))
        val r = SeedMerge.merge(current = emptyList(), bundled = bundle, everSeeded = emptySet())

        assertEquals(listOf("G1", "G2"), r.shows.map { it.seriesId })
        assertEquals(setOf("G1", "G2"), r.seenIds)
    }

    @Test
    fun `an update refreshes the write-up of a show already on the list`() {
        val current = listOf(show("G1", about = "terse"))
        val bundle = listOf(show("G1", about = "much longer"))

        val r = SeedMerge.merge(current, bundle, everSeeded = setOf("G1"))

        assertEquals(1, r.shows.size)
        assertEquals("much longer", r.shows[0].about)
    }

    @Test
    fun `a removed show does not come back`() {
        // The parent deleted G2 under a previous build. It is still in the
        // bundle, and it must stay gone however many updates land.
        val r = SeedMerge.merge(
            current = listOf(show("G1")),
            bundled = listOf(show("G1"), show("G2")),
            everSeeded = setOf("G1", "G2")
        )

        assertEquals(listOf("G1"), r.shows.map { it.seriesId })
    }

    @Test
    fun `a show new to this bundle is added`() {
        val r = SeedMerge.merge(
            current = listOf(show("G1")),
            bundled = listOf(show("G1"), show("GNEW")),
            everSeeded = setOf("G1")
        )

        assertEquals(listOf("G1", "GNEW"), r.shows.map { it.seriesId })
        assertTrue("GNEW" in r.seenIds)
    }

    @Test
    fun `a show the parent added by hand is left alone`() {
        val hand = Show(seriesId = "GMINE", title = "Added by URL", dateAdded = "2026-01-01")
        val r = SeedMerge.merge(listOf(hand), listOf(show("G1")), everSeeded = setOf("G1"))

        assertEquals(hand, r.shows.first { it.seriesId == "GMINE" })
    }

    @Test
    fun `refreshing keeps the date the parent added it`() {
        val current = listOf(Show(seriesId = "G1", title = "Old", dateAdded = "2026-08-07"))
        val bundle = listOf(Show(seriesId = "G1", title = "New", dateAdded = "1970-01-01"))

        val r = SeedMerge.merge(current, bundle, everSeeded = setOf("G1"))

        assertEquals("2026-08-07", r.shows[0].dateAdded)
        assertEquals("New", r.shows[0].title)
    }

    @Test
    fun `series ids match regardless of case`() {
        // add() and remove() both compare case-insensitively, so a bundle that
        // disagreed on case would otherwise duplicate the show rather than
        // refresh it.
        val r = SeedMerge.merge(
            current = listOf(show("g1", about = "terse")),
            bundled = listOf(show("G1", about = "longer")),
            everSeeded = setOf("G1")
        )

        assertEquals(1, r.shows.size)
        assertEquals("longer", r.shows[0].about)
        assertEquals("g1", r.shows[0].seriesId)   // the parent's record stays authoritative
    }

    @Test
    fun `emptying the list entirely sticks`() {
        // Removing every show is a legitimate thing to want. The extension
        // re-seeded whenever the list hit zero, so it was impossible.
        val bundle = listOf(show("G1"), show("G2"))
        val r = SeedMerge.merge(emptyList(), bundle, everSeeded = setOf("G1", "G2"))

        assertTrue("an emptied list must stay empty", r.shows.isEmpty())
    }

    @Test
    fun `a bundled show already present but never recorded is refreshed not duplicated`() {
        // The migration case: an install upgrading from a build that recorded
        // only *that* it seeded, not what.
        val r = SeedMerge.merge(
            current = listOf(show("G1", about = "terse")),
            bundled = listOf(show("G1", about = "longer")),
            everSeeded = emptySet()
        )

        assertEquals(1, r.shows.size)
        assertEquals("longer", r.shows[0].about)
    }

    @Test
    fun `merging twice changes nothing the second time`() {
        val bundle = listOf(show("G1", about = "text"), show("G2", about = "text"))
        val first = SeedMerge.merge(emptyList(), bundle, emptySet())
        val second = SeedMerge.merge(first.shows, bundle, first.seenIds)

        assertEquals(first.shows, second.shows)
        assertEquals(first.seenIds, second.seenIds)
    }

    @Test
    fun `long read prefers about and falls back to the panel text`() {
        assertEquals("long", Show("G1", "t", about = "long", description = "short").longRead)
        assertEquals("short", Show("G1", "t", description = "short").longRead)
        assertFalse(Show("G1", "t").hasMoreInfo)
        assertTrue(Show("G1", "t", about = "long").hasMoreInfo)
    }
}
