package com.nitomash.bpmplayer.data.playlist

import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTrackSorterTest {

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

    private fun row(
        id: Long,
        title: String,
        bpm: Double? = null,
        position: Int = id.toInt(),
        addedAtMs: Long = 0L,
    ): PlaylistTrackRow = PlaylistTrackRow(
        track = track(id, title, bpm),
        position = position,
        addedToPlaylistAtMs = addedAtMs,
    )

    @Test
    fun `custom follows stored position`() {
        val rows = listOf(
            row(1, "Third", position = 7),
            row(2, "First", position = 2),
            row(3, "Second", position = 5),
        )

        assertEquals(
            listOf(2, 5, 7),
            PlaylistTrackSorter.sort(rows, PlaylistTrackSort.CUSTOM).map { it.position },
        )
    }

    @Test
    fun `bpm ascending puts undefined bpm at the bottom`() {
        val rows = listOf(
            row(1, "NoBpm"),
            row(2, "High", bpm = 150.0),
            row(3, "Low", bpm = 90.0),
            row(4, "AnotherNoBpm"),
        )

        assertEquals(
            listOf("Low", "High", "AnotherNoBpm", "NoBpm"),
            PlaylistTrackSorter.sort(rows, PlaylistTrackSort.BPM_ASC).map { it.track.title },
        )
    }

    @Test
    fun `bpm descending puts undefined bpm at the bottom`() {
        val rows = listOf(
            row(1, "NoBpm"),
            row(2, "High", bpm = 150.0),
            row(3, "Low", bpm = 90.0),
            row(4, "AnotherNoBpm"),
        )

        assertEquals(
            listOf("High", "Low", "AnotherNoBpm", "NoBpm"),
            PlaylistTrackSorter.sort(rows, PlaylistTrackSort.BPM_DESC).map { it.track.title },
        )
    }

    @Test
    fun `bpm ties resolved by title then id`() {
        val rows = listOf(
            row(1, "beta", bpm = 120.0),
            row(2, "alpha", bpm = 120.0),
            row(3, "Alpha", bpm = 120.0),
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            PlaylistTrackSorter.sort(rows, PlaylistTrackSort.BPM_ASC).map { it.track.id },
        )
    }

    @Test
    fun `date added puts newest first and falls back to position`() {
        val rows = listOf(
            row(1, "Oldest", addedAtMs = 100L),
            row(2, "NewestSecond", addedAtMs = 300L),
            row(3, "NewestFirst", addedAtMs = 300L, position = 0),
            row(4, "Middle", addedAtMs = 200L),
        )

        assertEquals(
            listOf(3L, 2L, 4L, 1L),
            PlaylistTrackSorter.sort(rows, PlaylistTrackSort.DATE_ADDED).map { it.track.id },
        )
    }

    @Test
    fun `title sorting is case insensitive and trimmed`() {
        val rows = listOf(
            row(1, "Zebra"),
            row(2, "  alpha"),
            row(3, "Медведь"),
            row(4, "Apple"),
        )

        assertEquals(
            listOf(2L, 4L, 1L, 3L),
            PlaylistTrackSorter.sort(rows, PlaylistTrackSort.TITLE).map { it.track.id },
        )
    }

    @Test
    fun `title ties resolved by id`() {
        val rows = listOf(
            row(2, "Same"),
            row(1, "same"),
        )

        assertEquals(
            listOf(1L, 2L),
            PlaylistTrackSorter.sort(rows, PlaylistTrackSort.TITLE).map { it.track.id },
        )
    }

    @Test
    fun `every mode keeps the same multiset of rows`() {
        val rows = listOf(
            row(1, "a", bpm = 100.0, addedAtMs = 10L),
            row(2, "b", bpm = null, addedAtMs = 20L),
            row(3, "c", bpm = 90.0, addedAtMs = 30L),
        )

        PlaylistTrackSort.entries.forEach { mode ->
            val sorted = PlaylistTrackSorter.sort(rows, mode)
            assertEquals(rows.size, sorted.size)
            assertEquals(rows.map { it.track.id }.toSet(), sorted.map { it.track.id }.toSet())
        }
    }

    @Test
    fun `empty input returns empty output for every mode`() {
        PlaylistTrackSort.entries.forEach { mode ->
            assertEquals(emptyList<PlaylistTrackRow>(), PlaylistTrackSorter.sort(emptyList(), mode))
        }
    }

    @Test
    fun `single row is returned for every mode`() {
        val rows = listOf(row(1, "Only", bpm = 128.0))

        PlaylistTrackSort.entries.forEach { mode ->
            assertEquals(rows, PlaylistTrackSorter.sort(rows, mode))
        }
    }
}
