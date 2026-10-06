package com.nitomash.bpmplayer.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistNamingTest {

    @Test
    fun `free name is kept`() {
        assertEquals("BPM set", PlaylistNaming.unique("BPM set", emptySet()))
    }

    @Test
    fun `taken name gets the first free index`() {
        assertEquals("BPM set (1)", PlaylistNaming.unique("BPM set", setOf("BPM set")))
    }

    @Test
    fun `several taken names get the next free index`() {
        assertEquals(
            "BPM set (3)",
            PlaylistNaming.unique("BPM set", setOf("BPM set", "BPM set (1)", "BPM set (2)")),
        )
    }

    @Test
    fun `unrelated names do not block`() {
        assertEquals("BPM set", PlaylistNaming.unique("BPM set", setOf("Other", "BPM set (1)")))
    }

    @Test
    fun `blank name falls back`() {
        assertEquals("Импортированный плейлист", PlaylistNaming.unique("  ", emptySet()))
    }

    @Test
    fun `fallback also gets an index when taken`() {
        assertEquals(
            "Импортированный плейлист (1)",
            PlaylistNaming.unique("", setOf("Импортированный плейлист")),
        )
    }

    @Test
    fun `cyrillic names survive`() {
        assertEquals("Мой плейлист", PlaylistNaming.unique("Мой плейлист", emptySet()))
    }
}