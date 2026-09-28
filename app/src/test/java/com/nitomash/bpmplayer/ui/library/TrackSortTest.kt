package com.nitomash.bpmplayer.ui.library

import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.ui.common.filterTracksByTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSortTest {

    private fun track(id: Long, title: String, bpm: Double? = null): TrackEntity =
        TrackEntity(
            id = id,
            folderId = 1L,
            accessUri = "uri$id",
            relativePath = "path$id",
            fileName = "file$id",
            title = title,
            artist = "",
            album = "",
            durationMs = 0L,
            bpm = bpm,
            addedAtMs = 0L,
        )

    @Test
    fun `by name is case insensitive and trimmed`() {
        val tracks = listOf(
            track(1, "Zebra"),
            track(2, "  alpha"),
            track(3, "Медведь"),
            track(4, "Apple"),
        )
        assertEquals(
            listOf("  alpha", "Apple", "Zebra", "Медведь"),
            sortTracks(tracks, TrackSort.BY_NAME).map { it.title },
        )
    }

    @Test
    fun `by bpm sorts ascending`() {
        val tracks = listOf(
            track(1, "Slow", bpm = 100.0),
            track(2, "Fast", bpm = 140.0),
            track(3, "Mid", bpm = 120.0),
        )
        assertEquals(
            listOf("Slow", "Mid", "Fast"),
            sortTracks(tracks, TrackSort.BY_BPM).map { it.title },
        )
    }

    @Test
    fun `by bpm puts undefined bpm at the bottom`() {
        val tracks = listOf(
            track(1, "NoBpm", bpm = null),
            track(2, "Low", bpm = 90.0),
            track(3, "High", bpm = 150.0),
            track(4, "AnotherNoBpm", bpm = null),
        )
        assertEquals(
            listOf("Low", "High", "AnotherNoBpm", "NoBpm"),
            sortTracks(tracks, TrackSort.BY_BPM).map { it.title },
        )
    }

    @Test
    fun `by bpm ties resolved by title`() {
        val tracks = listOf(
            track(1, "beta", bpm = 120.0),
            track(2, "alpha", bpm = 120.0),
        )
        assertEquals(
            listOf("alpha", "beta"),
            sortTracks(tracks, TrackSort.BY_BPM).map { it.title },
        )
    }

    @Test
    fun `filter by title is case insensitive and trimmed`() {
        val tracks = listOf(
            track(1, "Sunrise"),
            track(2, "sUnset"),
            track(3, "Песня о вечере"),
            track(4, "Midnight"),
        )
        assertEquals(
            listOf("Sunrise", "sUnset"),
            filterTracksByTitle(tracks, " SU ").map { it.title },
        )
    }

    @Test
    fun `filter by title matches cyrillic`() {
        val tracks = listOf(
            track(1, "Песня о вечере"),
            track(2, "Вечер в дороге"),
            track(3, "Midnight"),
        )
        assertEquals(
            listOf("Песня о вечере", "Вечер в дороге"),
            filterTracksByTitle(tracks, "вечер").map { it.title },
        )
    }

    @Test
    fun `filter by blank query returns all tracks`() {
        val tracks = listOf(
            track(1, "Alpha"),
            track(2, "Beta"),
        )
        assertEquals(tracks, filterTracksByTitle(tracks, "   "))
    }

    @Test
    fun `filter by title with no matches returns empty`() {
        val tracks = listOf(
            track(1, "Alpha"),
            track(2, "Beta"),
        )
        assertEquals(emptyList<TrackEntity>(), filterTracksByTitle(tracks, "gamma"))
    }
}