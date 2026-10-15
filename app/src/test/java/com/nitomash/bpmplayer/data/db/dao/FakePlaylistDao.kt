package com.nitomash.bpmplayer.data.db.dao

import com.nitomash.bpmplayer.data.db.entity.PlaylistEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistOrderEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistTrackEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.playlist.PlaylistNameRow
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackRow
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakePlaylistDao : PlaylistDao {

    private var nextPlaylistId = 1L
    private val playlists = mutableListOf<PlaylistEntity>()
    private val rows = mutableListOf<PlaylistTrackEntity>()
    private val tracks = mutableMapOf<Long, TrackEntity>()
    private val playlistOrder = mutableListOf<PlaylistOrderEntity>()

    fun rowsOf(playlistId: Long): List<PlaylistTrackEntity> =
        rows.filter { it.playlistId == playlistId }.sortedBy { it.position }

    fun playlistOrderOf(): List<PlaylistOrderEntity> = playlistOrder.sortedBy { it.position }

    fun playlistOf(playlistId: Long): PlaylistEntity? = playlists.firstOrNull { it.id == playlistId }

    fun putTrack(track: TrackEntity) {
        tracks[track.id] = track
    }

    private fun trackStub(trackId: Long): TrackEntity = tracks.getOrPut(trackId) {
        TrackEntity(
            id = trackId,
            folderId = 1L,
            accessUri = "uri$trackId",
            relativePath = "path$trackId",
            fileName = "file$trackId",
            title = "Track $trackId",
            artist = "",
            album = "",
            durationMs = 0L,
            bpm = null,
            addedAtMs = 0L,
        )
    }

    override suspend fun insert(playlist: PlaylistEntity): Long {
        val id = nextPlaylistId++
        playlists += playlist.copy(id = id)
        return id
    }

    override fun observeSummaries(): Flow<List<PlaylistSummary>> = flowOf(emptyList())

    override fun observeById(playlistId: Long): Flow<PlaylistEntity?> =
        flowOf(playlistOf(playlistId))

    override suspend fun getById(playlistId: Long): PlaylistEntity? = playlistOf(playlistId)

    override suspend fun getNamesByIds(playlistIds: List<Long>): List<PlaylistNameRow> =
        playlists.filter { it.id in playlistIds }.map { PlaylistNameRow(it.id, it.name) }

    override suspend fun getTrackSortMode(playlistId: Long): PlaylistTrackSort? =
        playlistOf(playlistId)?.trackSortMode

    override suspend fun rename(playlistId: Long, name: String, updatedAtMs: Long) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(name = name, updatedAtMs = updatedAtMs)
        }
    }

    override suspend fun touch(playlistId: Long, updatedAtMs: Long) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(updatedAtMs = updatedAtMs)
        }
    }

    override suspend fun setTrackSortMode(playlistId: Long, sortMode: PlaylistTrackSort) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(trackSortMode = sortMode)
        }
    }

    override suspend fun delete(playlistId: Long) {
        playlists.removeAll { it.id == playlistId }
        rows.removeAll { it.playlistId == playlistId }
        playlistOrder.removeAll { it.playlistId == playlistId }
    }

    override suspend fun getPlaylistTrackRows(playlistId: Long): List<PlaylistTrackEntity> = rowsOf(playlistId)

    override suspend fun getTrackRows(playlistId: Long): List<PlaylistTrackRow> =
        rowsOf(playlistId).map { row ->
            PlaylistTrackRow(
                track = trackStub(row.trackId),
                position = row.position,
                addedToPlaylistAtMs = row.addedAtMs,
            )
        }

    override fun observeTrackRows(playlistId: Long): Flow<List<PlaylistTrackRow>> = flowOf(emptyList())

    override suspend fun getTracksNotInPlaylist(playlistId: Long): List<TrackEntity> = emptyList()

    override fun observePlaylistIdsForTrack(trackId: Long): Flow<List<Long>> =
        flowOf(rows.filter { it.trackId == trackId }.map { it.playlistId })

    override suspend fun getMaxPosition(playlistId: Long): Int =
        rowsOf(playlistId).maxOfOrNull { it.position } ?: -1

    override suspend fun insertTracks(playlistTracks: List<PlaylistTrackEntity>) {
        rows.addAll(playlistTracks)
        playlistTracks.forEach { trackStub(it.trackId) }
    }

    override suspend fun insertAll(playlistTracks: List<PlaylistTrackEntity>) {
        rows.addAll(playlistTracks)
        playlistTracks.forEach { trackStub(it.trackId) }
    }

    override suspend fun deleteAllTracks(playlistId: Long) {
        rows.removeAll { it.playlistId == playlistId }
    }

    override suspend fun getPlaylistOrder(): List<PlaylistOrderEntity> = playlistOrderOf()

    override suspend fun getMaxPlaylistOrderPosition(): Int =
        playlistOrder.maxOfOrNull { it.position } ?: -1

    override suspend fun upsertPlaylistOrder(rows: List<PlaylistOrderEntity>) {
        rows.forEach { row -> playlistOrder.removeAll { it.playlistId == row.playlistId } }
        playlistOrder.addAll(rows)
    }

    override suspend fun getPlaylistOrderIds(): List<Long> = playlistOrder.map { it.playlistId }

    override suspend fun deletePlaylistOrderIdsChunk(playlistIds: List<Long>) {
        playlistOrder.removeAll { it.playlistId in playlistIds }
    }
}
