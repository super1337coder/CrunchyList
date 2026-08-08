package com.lastgenlabs.crunchylist.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyPlayedTest {

    @Test
    fun `the newest play goes first`() {
        assertEquals(listOf("G2", "G1"), RecentlyPlayed.push(listOf("G1"), "G2"))
    }

    @Test
    fun `replaying moves a show to the front rather than duplicating it`() {
        assertEquals(
            listOf("G1", "G3", "G2"),
            RecentlyPlayed.push(listOf("G3", "G2", "G1"), "G1")
        )
    }

    @Test
    fun `case does not create a duplicate`() {
        assertEquals(listOf("g1"), RecentlyPlayed.push(listOf("G1"), "g1"))
    }

    @Test
    fun `the list is capped`() {
        val full = listOf("G1", "G2", "G3", "G4", "G5", "G6")

        val next = RecentlyPlayed.push(full, "G7")

        assertEquals(RecentlyPlayed.MAX, next.size)
        assertEquals("G7", next.first())
        assertEquals(false, next.contains("G6"))
    }

    @Test
    fun `a blank id is ignored`() {
        assertEquals(listOf("G1"), RecentlyPlayed.push(listOf("G1"), ""))
    }
}
