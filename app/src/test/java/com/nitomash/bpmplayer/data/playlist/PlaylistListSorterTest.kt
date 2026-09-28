package com.nitomash.bpmplayer.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistListSorterTest {

    private fun summary(
        id: Long,
        name: String,
        updatedAtMs: Long = 0L,
        sortPosition: Int = Int.MAX_VALUE,
    ): PlaylistSummary = PlaylistSummary(
        id = id,
        name = name,
        trackCount = 0,
        updatedAtMs = updatedAtMs,
        sortPosition = sortPosition,
    )

    @Test
    fun `date modified puts newest first and prefers higher id on ties`() {
        val summaries = listOf(
            summary(1, "Old", updatedAtMs = 100L),
            summary(2, "NewLowId", updatedAtMs = 300L),
            summary(3, "NewHighId", updatedAtMs = 300L),
            summary(4, "Middle", updatedAtMs = 200L),
        )

        assertEquals(
            listOf(3L, 2L, 4L, 1L),
            PlaylistListSorter.sort(summaries, PlaylistListSort.DATE_MODIFIED).map { it.id },
        )
    }

    @Test
    fun `title sorting is case insensitive and trimmed`() {
        val summaries = listOf(
            summary(1, "Zebra"),
            summary(2, "  alpha"),
            summary(3, "Медведь"),
            summary(4, "Apple"),
        )

        assertEquals(
            listOf(2L, 4L, 1L, 3L),
            PlaylistListSorter.sort(summaries, PlaylistListSort.TITLE).map { it.id },
        )
    }

    @Test
    fun `title ties resolved by id`() {
        val summaries = listOf(
            summary(2, "Same"),
            summary(1, "same"),
        )

        assertEquals(
            listOf(1L, 2L),
            PlaylistListSorter.sort(summaries, PlaylistListSort.TITLE).map { it.id },
        )
    }

    @Test
    fun `custom follows sort position and keeps unknown positions at the bottom`() {
        val summaries = listOf(
            summary(1, "Last"),
            summary(2, "First", sortPosition = 0),
            summary(3, "Second", sortPosition = 5),
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            PlaylistListSorter.sort(summaries, PlaylistListSort.CUSTOM).map { it.id },
        )
    }

    @Test
    fun `empty input returns empty output for every mode`() {
        PlaylistListSort.entries.forEach { mode ->
            assertEquals(emptyList<PlaylistSummary>(), PlaylistListSorter.sort(emptyList(), mode))
        }
    }

    @Test
    fun `single row is returned for every mode`() {
        val summaries = listOf(summary(1, "Only", updatedAtMs = 10L, sortPosition = 0))

        PlaylistListSort.entries.forEach { mode ->
            assertEquals(summaries, PlaylistListSorter.sort(summaries, mode))
        }
    }
}
