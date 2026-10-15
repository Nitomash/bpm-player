package com.nitomash.bpmplayer.data.playlist

import com.nitomash.bpmplayer.data.db.dao.FakePlaylistDao
import com.nitomash.bpmplayer.data.db.entity.PlaylistTrackEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRepositoryImplTest {

    private val dao = FakePlaylistDao()
    private val repository = PlaylistRepositoryImpl(dao)

    @Test
    fun `create assigns id and returns it`() = runBlocking {
        val id = repository.create("Плейлист")

        assertEquals(1L, id)
    }

    @Test
    fun `add tracks to empty playlist starts from zero`() = runBlocking {
        val id = repository.create("Плейлист")

        repository.addTracks(id, listOf(10L, 11L))

        assertEquals(listOf(0 to 10L, 1 to 11L), dao.rowsOf(id).map { it.position to it.trackId })
    }

    @Test
    fun `add tracks appends after current maximum`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L))

        repository.addTracks(id, listOf(12L))

        assertEquals(
            listOf(0 to 10L, 1 to 11L, 2 to 12L),
            dao.rowsOf(id).map { it.position to it.trackId },
        )
    }

    @Test
    fun `add tracks keeps incoming order`() = runBlocking {
        val id = repository.create("Плейлист")

        repository.addTracks(id, listOf(30L, 10L, 20L))

        assertEquals(listOf(30L, 10L, 20L), dao.rowsOf(id).map { it.trackId })
    }

    @Test
    fun `add tracks drops duplicates before numbering`() = runBlocking {
        val id = repository.create("Плейлист")

        repository.addTracks(id, listOf(10L, 10L, 11L))

        assertEquals(listOf(0 to 10L, 1 to 11L), dao.rowsOf(id).map { it.position to it.trackId })
    }

    @Test
    fun `add empty list changes nothing`() = runBlocking {
        val id = repository.create("Плейлист")

        repository.addTracks(id, emptyList())

        assertTrue(dao.rowsOf(id).isEmpty())
    }

    @Test
    fun `add tracks to one playlist does not shift another`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")
        repository.addTracks(first, listOf(10L))
        repository.addTracks(second, listOf(20L))

        repository.addTracks(first, listOf(11L))

        assertEquals(listOf(0 to 10L, 1 to 11L), dao.rowsOf(first).map { it.position to it.trackId })
        assertEquals(listOf(0 to 20L), dao.rowsOf(second).map { it.position to it.trackId })
    }

    @Test
    fun `remove tracks compacts positions and keeps order`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L, 12L, 13L))

        repository.removeTracks(id, setOf(11L, 13L))

        assertEquals(
            listOf(0 to 10L, 1 to 12L),
            dao.rowsOf(id).map { it.position to it.trackId },
        )
    }

    @Test
    fun `remove all tracks leaves empty contiguous positions`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L))

        repository.removeTracks(id, setOf(10L, 11L))

        assertTrue(dao.rowsOf(id).isEmpty())
    }

    @Test
    fun `remove unknown tracks keeps positions`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L))

        repository.removeTracks(id, setOf(99L))

        assertEquals(
            listOf(0 to 10L, 1 to 11L),
            dao.rowsOf(id).map { it.position to it.trackId },
        )
    }

    @Test
    fun `remove empty set changes nothing`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L))

        repository.removeTracks(id, emptySet())

        assertEquals(
            listOf(0 to 10L, 1 to 11L),
            dao.rowsOf(id).map { it.position to it.trackId },
        )
    }

    @Test
    fun `playlist ids containing a track report every membership`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")
        repository.addTracks(first, listOf(10L))
        repository.addTracks(second, listOf(10L, 20L))

        assertEquals(setOf(first, second), repository.observePlaylistIdsContaining(10L).first())
        assertEquals(setOf(second), repository.observePlaylistIdsContaining(20L).first())
        assertTrue(repository.observePlaylistIdsContaining(99L).first().isEmpty())
    }

    @Test
    fun `playlist ids containing a track drop it after removal`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L))

        repository.removeTracks(id, setOf(10L))

        assertTrue(repository.observePlaylistIdsContaining(10L).first().isEmpty())
    }

    @Test
    fun `add after removal continues from compacted maximum`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L, 12L))
        repository.removeTracks(id, setOf(10L))

        repository.addTracks(id, listOf(13L))

        assertEquals(
            listOf(0 to 11L, 1 to 12L, 2 to 13L),
            dao.rowsOf(id).map { it.position to it.trackId },
        )
    }

    @Test
    fun `delete playlist drops its rows`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")
        repository.addTracks(first, listOf(10L))
        repository.addTracks(second, listOf(20L))

        repository.delete(first)

        assertTrue(dao.rowsOf(first).isEmpty())
        assertEquals(listOf(0 to 20L), dao.rowsOf(second).map { it.position to it.trackId })
    }

    @Test
    fun `add tracks stamps the same addedAtMs for the whole batch`() = runBlocking {
        val id = repository.create("Плейлист")

        repository.addTracks(id, listOf(10L, 11L))

        val stamps = dao.rowsOf(id).map { it.addedAtMs }
        assertTrue(stamps.all { it > 0L })
        assertEquals(1, stamps.distinct().size)
    }

    @Test
    fun `add tracks touches updatedAtMs`() = runBlocking {
        val id = repository.create("Плейлист")
        dao.rename(id, "Плейлист", 0L)

        repository.addTracks(id, listOf(10L))

        assertTrue(dao.playlistOf(id)!!.updatedAtMs > 0L)
    }

    @Test
    fun `remove tracks touches updatedAtMs`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L))
        dao.rename(id, "Плейлист", 0L)

        repository.removeTracks(id, setOf(10L))

        assertTrue(dao.playlistOf(id)!!.updatedAtMs > 0L)
    }

    @Test
    fun `set track sort mode persists and leaves updatedAtMs alone`() = runBlocking {
        val id = repository.create("Плейлист")
        dao.rename(id, "Плейлист", 0L)

        repository.setTrackSortMode(id, PlaylistTrackSort.BPM_DESC)

        assertEquals(PlaylistTrackSort.BPM_DESC, dao.playlistOf(id)!!.trackSortMode)
        assertEquals(0L, dao.playlistOf(id)!!.updatedAtMs)
    }

    @Test
    fun `tracks in display order follow the stored sort mode`() = runBlocking {
        val id = repository.create("Плейлист")
        dao.putTrack(track(1, "Slow", 90.0))
        dao.putTrack(track(2, "Fast", 150.0))
        repository.addTracks(id, listOf(1L, 2L))

        repository.setTrackSortMode(id, PlaylistTrackSort.BPM_DESC)
        assertEquals(listOf("Fast", "Slow"), repository.getTracksInDisplayOrder(id).map { it.title })

        repository.setTrackSortMode(id, PlaylistTrackSort.BPM_ASC)
        assertEquals(listOf("Slow", "Fast"), repository.getTracksInDisplayOrder(id).map { it.title })

        repository.setTrackSortMode(id, PlaylistTrackSort.CUSTOM)
        assertEquals(listOf("Slow", "Fast"), repository.getTracksInDisplayOrder(id).map { it.title })
    }

    @Test
    fun `set track order persists the new order`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L, 12L))

        repository.setTrackOrder(id, listOf(12L, 10L, 11L))

        assertEquals(
            listOf(0 to 12L, 1 to 10L, 2 to 11L),
            dao.rowsOf(id).map { it.position to it.trackId },
        )
    }

    @Test
    fun `set track order keeps addedAtMs of every row`() = runBlocking {
        val id = repository.create("Плейлист")
        dao.insertTracks(
            listOf(
                PlaylistTrackEntity(playlistId = id, trackId = 10L, position = 0, addedAtMs = 100L),
                PlaylistTrackEntity(playlistId = id, trackId = 11L, position = 1, addedAtMs = 200L),
                PlaylistTrackEntity(playlistId = id, trackId = 12L, position = 2, addedAtMs = 300L),
            ),
        )

        repository.setTrackOrder(id, listOf(12L, 10L, 11L))

        assertEquals(
            listOf(Triple(0, 12L, 300L), Triple(1, 10L, 100L), Triple(2, 11L, 200L)),
            dao.rowsOf(id).map { Triple(it.position, it.trackId, it.addedAtMs) },
        )
    }

    @Test
    fun `set track order touches updatedAtMs`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L))
        dao.rename(id, "Плейлист", 0L)

        repository.setTrackOrder(id, listOf(11L, 10L))

        assertTrue(dao.playlistOf(id)!!.updatedAtMs > 0L)
    }

    @Test
    fun `set track order rejects an incomplete list`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L, 12L))
        dao.rename(id, "Плейлист", 0L)

        repository.setTrackOrder(id, listOf(12L, 10L))

        assertEquals(
            listOf(0 to 10L, 1 to 11L, 2 to 12L),
            dao.rowsOf(id).map { it.position to it.trackId },
        )
        assertEquals(0L, dao.playlistOf(id)!!.updatedAtMs)
    }

    @Test
    fun `set track order rejects a duplicated list`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L))

        repository.setTrackOrder(id, listOf(10L, 10L))

        assertEquals(listOf(0 to 10L, 1 to 11L), dao.rowsOf(id).map { it.position to it.trackId })
    }

    @Test
    fun `set track order rejects a foreign track id`() = runBlocking {
        val id = repository.create("Плейлист")
        repository.addTracks(id, listOf(10L, 11L))

        repository.setTrackOrder(id, listOf(10L, 99L))

        assertEquals(listOf(0 to 10L, 1 to 11L), dao.rowsOf(id).map { it.position to it.trackId })
    }

    @Test
    fun `set track order does not touch another playlist`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")
        repository.addTracks(first, listOf(10L, 11L))
        repository.addTracks(second, listOf(20L, 21L))

        repository.setTrackOrder(first, listOf(11L, 10L))

        assertEquals(listOf(0 to 20L, 1 to 21L), dao.rowsOf(second).map { it.position to it.trackId })
    }

    @Test
    fun `create puts the playlist at the end of the custom order`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")
        val third = repository.create("Третий")

        assertEquals(listOf(first, second, third), dao.playlistOrderOf().map { it.playlistId })
        assertEquals(listOf(0, 1, 2), dao.playlistOrderOf().map { it.position })
    }

    @Test
    fun `set playlist order replaces the whole order`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Вторый")
        val third = repository.create("Третий")

        repository.setPlaylistOrder(listOf(third, first, second))

        assertEquals(
            listOf(third to 0, first to 1, second to 2),
            dao.playlistOrderOf().map { it.playlistId to it.position },
        )
    }

    @Test
    fun `set playlist order drops rows for playlists left out`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")
        val third = repository.create("Третий")

        repository.setPlaylistOrder(listOf(third, first))

        assertEquals(
            listOf(third to 0, first to 1),
            dao.playlistOrderOf().map { it.playlistId to it.position },
        )
        assertTrue(second !in dao.playlistOrderOf().map { it.playlistId })
    }

    @Test
    fun `set playlist order with empty list changes nothing`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")

        repository.setPlaylistOrder(emptyList())

        assertEquals(listOf(0, 1), dao.playlistOrderOf().map { it.position })
        assertEquals(listOf(first, second), dao.playlistOrderOf().map { it.playlistId })
    }

    @Test
    fun `delete playlist drops its custom order row`() = runBlocking {
        val first = repository.create("Первый")
        val second = repository.create("Второй")

        repository.delete(first)

        assertEquals(listOf(second), dao.playlistOrderOf().map { it.playlistId })
    }

    private fun track(id: Long, title: String, bpm: Double?): TrackEntity =
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
}