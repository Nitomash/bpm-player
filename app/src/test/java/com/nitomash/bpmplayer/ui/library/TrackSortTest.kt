package com.nitomash.bpmplayer.ui.library

import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `by name keeps input order when keys are equal`() {
        val tracks = listOf(
            track(1, "Same"),
            track(2, " same "),
            track(3, "SAME"),
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            sortTracks(tracks, TrackSort.BY_NAME).map { it.id },
        )
    }

    @Test
    fun `by bpm keeps input order when bpm and title are equal`() {
        val tracks = listOf(
            track(1, "Same", bpm = 120.0),
            track(2, " same ", bpm = 120.0),
            track(3, "SAME", bpm = 120.0),
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            sortTracks(tracks, TrackSort.BY_BPM).map { it.id },
        )
    }

    @Test
    fun `by bpm over a large duplicated input is fully ordered`() {
        val titles = listOf("alpha", "Beta", " gamma ", "ALPHA", "delta")
        val tracks = (0 until 500).map { index ->
            track(
                id = index.toLong(),
                title = titles[index % titles.size],
                bpm = if (index % 7 == 0) null else (90 + index % 5).toDouble(),
            )
        }

        val sorted = sortTracks(tracks, TrackSort.BY_BPM)
        assertEquals(tracks.size, sorted.size)
        assertEquals(tracks.map { it.id }.toSet(), sorted.map { it.id }.toSet())

        var seenNoBpm = false
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            if (prev.bpm == null) seenNoBpm = true
            if (seenNoBpm) {
                assertTrue("track with bpm at $i must not follow one without", cur.bpm == null)
            }
            if (prev.bpm != null && cur.bpm != null) {
                assertTrue("bpm order broken at $i: ${prev.bpm} then ${cur.bpm}", prev.bpm <= cur.bpm)
            }
            if (prev.bpm != null && prev.bpm == cur.bpm) {
                assertTrue(
                    "title order broken at $i: '${prev.title}' then '${cur.title}'",
                    titleKey(prev.title) <= titleKey(cur.title),
                )
            }
        }
    }

    @Test
    fun `sorting does not modify the input list`() {
        val tracks = listOf(
            track(1, "Zebra", bpm = 120.0),
            track(2, "  alpha"),
            track(3, "Middle"),
        )
        val before = tracks.toList()

        sortTracks(tracks, TrackSort.BY_NAME)
        sortTracks(tracks, TrackSort.BY_BPM)

        assertEquals(before, tracks)
    }

    private fun titleKey(value: String): String = value.trim().lowercase()
}