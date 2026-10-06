package com.nitomash.bpmplayer.data.playlist.xspf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XspfLocationResolveTest {

    private val roots = listOf("/storage/emulated/0", "/storage/1A2B-3C4D")
    private val base = StoragePath.of("/storage/emulated/0", "Playlists")

    @Test
    fun `plain name resolves inside the base directory`() {
        assertEquals(
            "/storage/emulated/0/Playlists/a.mp3",
            XspfLocation.resolve(base, "a.mp3", roots)?.absolutePath,
        )
    }

    @Test
    fun `parent traversal climbs back`() {
        assertEquals(
            "/storage/emulated/0/Music/a.mp3",
            XspfLocation.resolve(base, "../Music/a.mp3", roots)?.absolutePath,
        )
    }

    @Test
    fun `parent traversal from a volume root folder climbs back`() {
        assertEquals(
            "/storage/emulated/0/a.mp3",
            XspfLocation.resolve(base, "../a.mp3", roots)?.absolutePath,
        )
    }

    @Test
    fun `traversal above the volume root is rejected`() {
        assertNull(XspfLocation.resolve(base, "../../a.mp3", roots))
    }

    @Test
    fun `traversal that would escape the volume is rejected`() {
        assertNull(XspfLocation.resolve(base, "../../../a.mp3", roots))
    }

    @Test
    fun `current directory segments are dropped`() {
        assertEquals(
            "/storage/emulated/0/Playlists/Music/a.mp3",
            XspfLocation.resolve(base, "./Music//a.mp3", roots)?.absolutePath,
        )
    }

    @Test
    fun `absolute location on a known volume is split into root and path`() {
        val resolved = XspfLocation.resolve(base, "/storage/emulated/0/Music/a.mp3", roots)

        assertEquals("/storage/emulated/0", resolved?.volumeRoot)
        assertEquals("Music/a.mp3", resolved?.relativePath)
    }

    @Test
    fun `absolute location on a card is resolved to the card volume`() {
        val resolved = XspfLocation.resolve(base, "/storage/1A2B-3C4D/Music/a.mp3", roots)

        assertEquals("/storage/1A2B-3C4D", resolved?.volumeRoot)
    }

    @Test
    fun `absolute location outside every known volume is rejected`() {
        assertNull(XspfLocation.resolve(base, "/data/local/a.mp3", roots))
    }

    @Test
    fun `location equal to the volume root itself is rejected`() {
        assertNull(XspfLocation.resolve(base, "/storage/emulated/0", roots))
    }

    @Test
    fun `blank location is rejected`() {
        assertNull(XspfLocation.resolve(base, "   ", roots))
    }

    @Test
    fun `backslashes are treated as separators`() {
        assertEquals(
            "/storage/emulated/0/Music/a.mp3",
            XspfLocation.resolve(base, "..\\Music\\a.mp3", roots)?.absolutePath,
        )
    }

    @Test
    fun `default known root is the base volume only`() {
        assertNull(XspfLocation.resolve(base, "/storage/1A2B-3C4D/a.mp3"))
        assertEquals(
            "/storage/emulated/0/a.mp3",
            XspfLocation.resolve(base, "/storage/emulated/0/a.mp3")?.absolutePath,
        )
    }

    @Test
    fun `cyrillic names survive resolution`() {
        assertEquals(
            "/storage/emulated/0/Музыка/Трек 1.mp3",
            XspfLocation.resolve(base, "../Музыка/Трек 1.mp3", roots)?.absolutePath,
        )
    }
}