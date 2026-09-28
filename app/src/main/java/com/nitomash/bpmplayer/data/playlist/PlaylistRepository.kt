package com.nitomash.bpmplayer.data.playlist

import com.nitomash.bpmplayer.data.db.entity.PlaylistEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<PlaylistSummary>>
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>
    fun observeTrackRows(playlistId: Long): Flow<List<PlaylistTrackRow>>

    suspend fun getTracksInDisplayOrder(playlistId: Long): List<TrackEntity>
    suspend fun getTracksNotInPlaylist(playlistId: Long): List<TrackEntity>
    suspend fun create(name: String): Long
    suspend fun rename(playlistId: Long, name: String)
    suspend fun delete(playlistId: Long)
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>)
    suspend fun removeTracks(playlistId: Long, trackIds: Set<Long>)
    suspend fun setTrackOrder(playlistId: Long, trackIds: List<Long>)
    suspend fun setTrackSortMode(playlistId: Long, sort: PlaylistTrackSort)
    suspend fun setPlaylistOrder(playlistIds: List<Long>)
}
