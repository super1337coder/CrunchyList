package com.lastgenlabs.crunchylist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shelving fails quietly in both directions: a show in no shelf is on the
 * whitelist and nowhere on screen, and a show in the wrong shelf is simply never
 * found. Neither raises anything.
 */
class ShelvesTest {

    private fun show(id: String, category: String = "", title: String = id) =
        Show(seriesId = id, title = title, category = category)

    private fun titles(shelves: List<Shelves.Shelf>) = shelves.map { it.title }

    @Test
    fun `every show lands on a shelf`() {
        val shows = listOf(
            show("G1", "Comedy"),
            show("G2", "Quiet ones"),
            show("G3", "Something A Parent Invented"),
            show("G4")   // no category at all
        )

        val placed = Shelves.build(shows).flatMap { it.shows }.map { it.seriesId }.toSet()

        assertEquals(shows.map { it.seriesId }.toSet(), placed)
    }

    @Test
    fun `known categories come in the curated order, not alphabetically`() {
        val shows = listOf(
            show("G1", "Quiet ones"),
            show("G2", "Action and fights"),
            show("G3", "Comedy")
        )

        assertEquals(
            listOf("Action and fights", "Comedy", "Quiet ones"),
            titles(Shelves.build(shows))
        )
    }

    @Test
    fun `an unrecognised category still gets a shelf, after the known ones`() {
        val shows = listOf(show("G1", "Zebras"), show("G2", "Comedy"), show("G3", "Aardvarks"))

        assertEquals(listOf("Comedy", "Aardvarks", "Zebras"), titles(Shelves.build(shows)))
    }

    @Test
    fun `keep watching leads, newest first`() {
        val shows = listOf(show("G1", "Comedy"), show("G2", "Comedy"), show("G3", "Comedy"))

        val shelves = Shelves.build(shows, recentIds = listOf("G3", "G1"))

        assertEquals(Shelves.KEEP_WATCHING, shelves.first().title)
        assertEquals(listOf("G3", "G1"), shelves.first().shows.map { it.seriesId })
    }

    @Test
    fun `no keep watching shelf before anything has been played`() {
        val shelves = Shelves.build(listOf(show("G1", "Comedy")), recentIds = emptyList())

        assertEquals(listOf("Comedy"), titles(shelves))
    }

    @Test
    fun `a recent show that is no longer on the whitelist is skipped`() {
        // The parent removed it. It must not reappear on the kid-facing screen
        // just because it is in the play history.
        val shelves = Shelves.build(listOf(show("G1", "Comedy")), recentIds = listOf("GONE", "G1"))

        assertEquals(listOf("G1"), shelves.first().shows.map { it.seriesId })
    }

    @Test
    fun `keeping watching does not remove a show from its category shelf`() {
        val shelves = Shelves.build(listOf(show("G1", "Comedy")), recentIds = listOf("G1"))

        assertEquals(listOf(Shelves.KEEP_WATCHING, "Comedy"), titles(shelves))
        assertEquals("G1", shelves[1].shows.single().seriesId)
    }

    @Test
    fun `a list with no categories gets one shelf that is not called leftovers`() {
        // A whitelist built entirely by pasting URLs. "Everything else" with
        // nothing to be else than reads as though a shelf failed to render.
        assertEquals(listOf("All shows"), titles(Shelves.build(listOf(show("G1"), show("G2")))))
    }

    @Test
    fun `uncategorised shows sit at the end when categories exist`() {
        val shelves = Shelves.build(listOf(show("G1", "Comedy"), show("G2")))

        assertEquals(listOf("Comedy", "Everything else"), titles(shelves))
    }

    @Test
    fun `an empty whitelist produces no shelves`() {
        assertTrue(Shelves.build(emptyList(), listOf("G1")).isEmpty())
    }

    @Test
    fun `recent ids match regardless of case`() {
        val shelves = Shelves.build(listOf(show("g1", "Comedy")), recentIds = listOf("G1"))

        assertEquals(Shelves.KEEP_WATCHING, shelves.first().title)
    }

    @Test
    fun `shelf titles are unique so they can key a lazy list`() {
        val shows = listOf(show("G1", "Comedy"), show("G2", "Quiet ones"), show("G3"))
        val t = titles(Shelves.build(shows, recentIds = listOf("G1")))

        assertEquals(t.size, t.toSet().size)
    }
}
