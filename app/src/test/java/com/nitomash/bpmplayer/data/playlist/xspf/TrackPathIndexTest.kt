package com.nitomash.bpmplayer.data.playlist.xspf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackPathIndexTest {

    private val roots = listOf("/storage/emulated/0")
    private val base = StoragePath.of("/storage/emulated/0", "Playlists")

    @Test
    fun `exact path wins over canonical fallback`() {
        val composed = "/storage/emulated/0/Music/The Marías - Hush.mp3"
        val decomposed = "/storage/emulated/0/Music/The Mari\u0301as - Hush.mp3"
        val index = TrackPathIndex.of(mapOf(composed to 1L, decomposed to 2L))

        assertEquals(1L, index.find(composed))
        assertEquals(2L, index.find(decomposed))
    }

    @Test
    fun `composed library path is found by a decomposed path`() {
        val composed = "/storage/emulated/0/Music/The Marías - Hush.mp3"
        val decomposed = "/storage/emulated/0/Music/The Mari\u0301as - Hush.mp3"
        val index = TrackPathIndex.of(mapOf(composed to 42L))

        assertEquals(42L, index.find(decomposed))
    }

    @Test
    fun `decomposed directory name does not break matching`() {
        val libraryPath = "/storage/emulated/0/Música/Track.mp3"
        val resolved = XspfLocation.resolve(base, "../Mu\u0301sica/Track.mp3", roots)
        val index = TrackPathIndex.of(mapOf(libraryPath to 7L))

        assertEquals(7L, resolved?.absolutePath?.let(index::find))
    }

    @Test
    fun `unrelated path is not matched`() {
        val index = TrackPathIndex.of(mapOf("/storage/emulated/0/Music/a.mp3" to 1L))

        assertNull(index.find("/storage/emulated/0/Music/b.mp3"))
    }
}
