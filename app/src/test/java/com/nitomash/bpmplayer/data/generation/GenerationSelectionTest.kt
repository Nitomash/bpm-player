package com.nitomash.bpmplayer.data.generation

import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationSelectionTest {

    private fun folder(id: Long, name: String): FolderEntity =
        FolderEntity(id = id, rootUri = "uri$id", displayName = name, addedAtMs = 0L)

    private fun track(id: Long, folderId: Long, title: String = "Track $id"): TrackEntity =
        TrackEntity(
            id = id,
            folderId = folderId,
            accessUri = "uri$id",
            relativePath = "$id.mp3",
            fileName = "$id.mp3",
            title = title,
            artist = "artist",
            album = "album",
            durationMs = 0L,
            bpm = 120.0,
            addedAtMs = 0L,
        )

    private fun playlist(id: Long, name: String): PlaylistSummary =
        PlaylistSummary(id = id, name = name, trackCount = 0, updatedAtMs = 0L, sortPosition = 0)

    private val folders = listOf(folder(1, "House"), folder(2, "Chillout"))
    private val playlists = listOf(playlist(10, "First"), playlist(20, "Second"), playlist(30, "Third"))

    @Test
    fun `playlists all flag returns every id in order`() {
        assertEquals(
            listOf(10L, 20L, 30L),
            resolvePlaylistIdsForGeneration(playlists, allPlaylists = true, selectedIds = emptySet()),
        )
    }

    @Test
    fun `playlists empty selection is treated as all`() {
        assertEquals(
            listOf(10L, 20L, 30L),
            resolvePlaylistIdsForGeneration(playlists, allPlaylists = false, selectedIds = emptySet()),
        )
    }

    @Test
    fun `playlists selection keeps source order`() {
        assertEquals(
            listOf(10L, 30L),
            resolvePlaylistIdsForGeneration(playlists, allPlaylists = false, selectedIds = setOf(30L, 10L)),
        )
    }

    @Test
    fun `playlists unknown ids are dropped`() {
        assertEquals(
            listOf(20L),
            resolvePlaylistIdsForGeneration(playlists, allPlaylists = false, selectedIds = setOf(20L, 99L)),
        )
    }

    @Test
    fun `selectTracksByPlaylists concatenates in playlist order`() {
        val tracks = mapOf(
            10L to listOf(track(1, 1), track(2, 1)),
            20L to listOf(track(3, 2)),
        )

        assertEquals(
            listOf(1L, 2L, 3L),
            selectTracksByPlaylists(listOf(10L, 20L), tracks).map { it.id },
        )
    }

    @Test
    fun `selectTracksByPlaylists drops duplicates keeping first occurrence`() {
        val tracks = mapOf(
            10L to listOf(track(1, 1), track(2, 1)),
            20L to listOf(track(2, 1), track(3, 2)),
        )

        assertEquals(
            listOf(1L, 2L, 3L),
            selectTracksByPlaylists(listOf(10L, 20L), tracks).map { it.id },
        )
    }

    @Test
    fun `selectTracksByPlaylists missing playlist yields nothing`() {
        val tracks = mapOf(10L to listOf(track(1, 1)))

        assertEquals(
            listOf(1L),
            selectTracksByPlaylists(listOf(10L, 99L), tracks).map { it.id },
        )
    }

    @Test
    fun `selectTracksByFolders filters by resolved folder ids`() {
        val tracks = listOf(track(1, 1), track(2, 2), track(3, 1))

        assertEquals(
            listOf(1L, 3L),
            selectTracksByFolders(tracks, folders, allFolders = false, selectedIds = setOf(1L)).map { it.id },
        )
    }

    @Test
    fun `selectTracksByFolders all flag keeps every track`() {
        val tracks = listOf(track(1, 1), track(2, 2))

        assertEquals(
            listOf(1L, 2L),
            selectTracksByFolders(tracks, folders, allFolders = true, selectedIds = emptySet()).map { it.id },
        )
    }
}
