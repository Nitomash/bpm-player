package com.nitomash.bpmplayer.ui.library

import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryContentTest {

    private val folders = listOf(folder(1, "Techno"), folder(2, "House"))

    @Test
    fun `empty inputs produce empty content`() {
        val content = buildLibraryContent(emptyList(), emptyList(), "", emptyMap())

        assertTrue(content.isEmpty)
        assertTrue(content.sortedTracks.isEmpty())
        assertTrue(content.tracksOf(1L).isEmpty())
        assertEquals(0, content.countOf(1L))
    }

    @Test
    fun `blank query keeps every folder even without tracks`() {
        val content = buildLibraryContent(folders, listOf(track(10, 1, "Alpha")), "", emptyMap())

        assertEquals(listOf(1L, 2L), content.visibleFolders.map { it.id })
        assertEquals(listOf(10L), content.tracksOf(1L).map { it.id })
        assertEquals(0, content.countOf(2L))
    }

    @Test
    fun `tracks are sorted by name by default`() {
        val content = buildLibraryContent(
            folders,
            listOf(track(10, 1, "Zebra"), track(11, 1, " alpha"), track(12, 1, "Mid")),
            "",
            emptyMap(),
        )

        assertEquals(listOf(" alpha", "Mid", "Zebra"), content.tracksOf(1L).map { it.title })
    }

    @Test
    fun `per folder sort is respected`() {
        val content = buildLibraryContent(
            folders,
            listOf(
                track(10, 1, "Zebra", bpm = 90.0),
                track(11, 1, "alpha", bpm = 140.0),
                track(20, 2, "Beta", bpm = 100.0),
            ),
            "",
            mapOf(1L to TrackSort.BY_BPM),
        )

        assertEquals(listOf(90.0, 140.0), content.tracksOf(1L).map { it.bpm })
        assertEquals(listOf("Beta"), content.tracksOf(2L).map { it.title })
    }

    @Test
    fun `counts match the visible tracks of each folder`() {
        val content = buildLibraryContent(
            folders,
            listOf(track(10, 1, "Alpha"), track(11, 1, "Beta"), track(20, 2, "Gamma")),
            "",
            emptyMap(),
        )

        assertEquals(2, content.countOf(1L))
        assertEquals(1, content.countOf(2L))
    }

    @Test
    fun `query hides folders without matches`() {
        val content = buildLibraryContent(
            folders,
            listOf(track(10, 1, "Alpha"), track(20, 2, "Gamma")),
            "gamm",
            emptyMap(),
        )

        assertEquals(listOf(2L), content.visibleFolders.map { it.id })
        assertEquals(listOf(20L), content.tracksOf(2L).map { it.id })
        assertEquals(0, content.countOf(1L))
        assertTrue(content.tracksOf(1L).isEmpty())
    }

    @Test
    fun `query with no matches reports empty content`() {
        val content = buildLibraryContent(folders, listOf(track(10, 1, "Alpha")), "zzz", emptyMap())

        assertTrue(content.isEmpty)
    }

    @Test
    fun `query keeps folder order of the source list`() {
        val content = buildLibraryContent(
            folders.reversed(),
            listOf(track(10, 1, "Alpha"), track(20, 2, "Gamma")),
            "a",
            emptyMap(),
        )

        assertEquals(listOf(2L, 1L), content.visibleFolders.map { it.id })
    }

    @Test
    fun `equal inputs produce equal content`() {
        val tracks = listOf(track(10, 1, "Alpha"), track(20, 2, "Gamma"))

        val first = buildLibraryContent(folders, tracks, "a", emptyMap())
        val second = buildLibraryContent(folders, tracks, "a", emptyMap())

        assertEquals(first, second)
    }

    private fun folder(id: Long, name: String) = FolderEntity(
        id = id,
        rootUri = "uri/$id",
        displayName = name,
        addedAtMs = 0L,
    )

    private fun track(id: Long, folderId: Long, title: String, bpm: Double? = null) = TrackEntity(
        id = id,
        folderId = folderId,
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
}
