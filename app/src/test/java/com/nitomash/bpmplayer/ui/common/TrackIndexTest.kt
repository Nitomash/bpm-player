package com.nitomash.bpmplayer.ui.common

import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackIndexTest {

    @Test
    fun `empty query returns every track`() {
        val tracks = listOf(track(1, "Alpha"), track(2, "Beta"))
        val index = buildIndex(tracks)

        assertEquals(tracks, index.query(""))
    }

    @Test
    fun `blank query returns the same list instance`() {
        val tracks = listOf(track(1, "Alpha"), track(2, "Beta"))
        val index = buildIndex(tracks)

        assertSame(tracks, index.query("   "))
    }

    @Test
    fun `empty index answers every query with nothing`() {
        val index = buildIndex(emptyList())

        assertEquals(0, index.size)
        assertTrue(index.query("alpha").isEmpty())
        assertTrue(index.query("").isEmpty())
    }

    @Test
    fun `query is case insensitive and trimmed`() {
        val index = buildIndex(
            listOf(
                track(1, "Sunrise"),
                track(2, "sUnset"),
                track(3, "Песня о вечере"),
                track(4, "Midnight"),
            ),
        )

        assertEquals(listOf("Sunrise", "sUnset"), index.query(" SU ").map { it.title })
    }

    @Test
    fun `query matches cyrillic`() {
        val index = buildIndex(
            listOf(
                track(1, "Песня о вечере"),
                track(2, "Вечер в дороге"),
                track(3, "Midnight"),
            ),
        )

        assertEquals(
            listOf("Песня о вечере", "Вечер в дороге"),
            index.query("вечер").map { it.title },
        )
    }

    @Test
    fun `query matches anywhere in the title`() {
        val index = buildIndex(listOf(track(1, "  Midnight Drive  "), track(2, "Dawn")))

        assertEquals(listOf("  Midnight Drive  "), index.query("night dr").map { it.title })
    }

    @Test
    fun `query with no match returns empty`() {
        val index = buildIndex(listOf(track(1, "Alpha"), track(2, "Beta")))

        assertEquals(emptyList<TrackEntity>(), index.query("gamma"))
    }

    @Test
    fun `query keeps input order`() {
        val index = buildIndex(
            listOf(track(1, "aa one"), track(2, "zzz"), track(3, "aa two")),
        )

        assertEquals(listOf(1L, 3L), index.query("aa").map { it.id })
    }

    @Test
    fun `repeated queries on one index stay consistent`() {
        val index = buildIndex(listOf(track(1, "One"), track(2, "Two"), track(3, "Three")))

        assertEquals(listOf(2L, 3L), index.query("t").map { it.id })
        assertEquals(listOf(2L, 3L), index.query("t").map { it.id })
        assertEquals(3, index.size)
    }

    @Test
    fun `titles differing only by case and spaces are one key`() {
        val index = buildIndex(
            listOf(track(1, "Same"), track(2, " same "), track(3, "SAME")),
        )

        assertEquals(listOf(1L, 2L, 3L), index.query("same").map { it.id })
    }

    @Test
    fun `large index is queried without rebuilding keys`() {
        val index = buildIndex((0 until 5000).map { track(it.toLong(), "Track $it marker") })

        val found = index.query("track 499 marker")

        assertEquals(listOf(499L), found.map { it.id })
    }

    private fun track(id: Long, title: String) = TrackEntity(
        id = id,
        folderId = 1L,
        accessUri = "uri$id",
        relativePath = "path$id",
        fileName = "file$id",
        title = title,
        artist = "",
        album = "",
        durationMs = 0L,
        bpm = null,
        addedAtMs = 0L,
    )
}
