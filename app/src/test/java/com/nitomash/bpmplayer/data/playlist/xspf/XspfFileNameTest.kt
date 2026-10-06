package com.nitomash.bpmplayer.data.playlist.xspf

import org.junit.Assert.assertEquals
import org.junit.Test

class XspfFileNameTest {

    @Test
    fun `plain name is kept`() {
        assertEquals("BPM set", XspfFileName.sanitize("BPM set"))
    }

    @Test
    fun `cyrillic name is kept`() {
        assertEquals("Мой плейлист", XspfFileName.sanitize("Мой плейлист"))
    }

    @Test
    fun `path separators are replaced`() {
        assertEquals("a_b", XspfFileName.sanitize("a/b"))
    }

    @Test
    fun `windows illegal characters are replaced`() {
        assertEquals("a_b_c_d_e", XspfFileName.sanitize("a:b*c?d\"e"))
    }

    @Test
    fun `blank name falls back`() {
        assertEquals("playlist", XspfFileName.sanitize("   "))
    }

    @Test
    fun `name of only dots falls back`() {
        assertEquals("playlist", XspfFileName.sanitize("..."))
    }

    @Test
    fun `long name is trimmed`() {
        assertEquals(96, XspfFileName.sanitize("я".repeat(200)).length)
    }

    @Test
    fun `free name gets the extension`() {
        assertEquals("A.xspf", XspfFileName.unique("A", emptySet()))
    }

    @Test
    fun `existing name gets the first free index`() {
        assertEquals("A (1).xspf", XspfFileName.unique("A", setOf("A.xspf")))
    }

    @Test
    fun `several existing names get the next free index`() {
        assertEquals("A (3).xspf", XspfFileName.unique("A", setOf("A.xspf", "A (1).xspf", "A (2).xspf")))
    }

    @Test
    fun `unrelated names do not block`() {
        assertEquals("A.xspf", XspfFileName.unique("A", setOf("B.xspf", "A (1).xspf")))
    }

    @Test
    fun `gap in indexes is reused`() {
        assertEquals("A (1).xspf", XspfFileName.unique("A", setOf("A.xspf", "A (2).xspf")))
    }
}