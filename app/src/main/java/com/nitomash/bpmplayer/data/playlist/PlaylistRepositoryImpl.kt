package com.nitomash.bpmplayer.data.playlist

import com.nitomash.bpmplayer.data.db.dao.PlaylistDao
import com.nitomash.bpmplayer.data.db.entity.PlaylistEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistTrackEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = playlistDao.observeSummaries()

    override fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?> = playlistDao.observeById(playlistId)

    override fun observeTrackRows(playlistId: Long): Flow<List<PlaylistTrackRow>> =
        playlistDao.observeTrackRows(playlistId)

    override suspend fun getTracksInDisplayOrder(playlistId: Long): List<TrackEntity> {
        val sort = playlistDao.getTrackSortMode(playlistId) ?: PlaylistTrackSort.CUSTOM
        return PlaylistTrackSorter.sort(playlistDao.getTrackRows(playlistId), sort).map { it.track }
    }

    override suspend fun getTracksNotInPlaylist(playlistId: Long): List<TrackEntity> =
        playlistDao.getTracksNotInPlaylist(playlistId)

    override fun observePlaylistIdsContaining(trackId: Long): Flow<Set<Long>> =
        playlistDao.observePlaylistIdsForTrack(trackId).map { it.toSet() }

    override suspend fun getPlaylists(): List<PlaylistSummary> =
        playlistDao.observeSummaries().first()

    override suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.createPlaylist(
            PlaylistEntity(
                name = name,
                conditionId = null,
                createdAtMs = now,
                updatedAtMs = now,
            ),
        )
    }

    override suspend fun getPlaylistNames(): List<String> =
        playlistDao.observeSummaries().first().map { it.name }

    override suspend fun rename(playlistId: Long, name: String) {
        playlistDao.rename(playlistId, name, System.currentTimeMillis())
    }

    override suspend fun delete(playlistId: Long) {
        playlistDao.delete(playlistId)
    }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) {
        val distinct = trackIds.distinct()
        if (distinct.isEmpty()) return
        val now = System.currentTimeMillis()
        val start = playlistDao.getMaxPosition(playlistId) + 1
        playlistDao.insertTracks(
            distinct.mapIndexed { index, trackId ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = start + index,
                    addedAtMs = now,
                )
            },
        )
        playlistDao.touch(playlistId, now)
    }

    override suspend fun removeTracks(playlistId: Long, trackIds: Set<Long>) {
        if (trackIds.isEmpty()) return
        playlistDao.removeTracks(playlistId, trackIds)
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    override suspend fun setTrackOrder(playlistId: Long, trackIds: List<Long>) {
        val applied = playlistDao.setTrackOrder(playlistId, trackIds)
        if (!applied) return
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    override suspend fun setTrackSortMode(playlistId: Long, sort: PlaylistTrackSort) {
        playlistDao.setTrackSortMode(playlistId, sort)
    }

    override suspend fun setPlaylistOrder(playlistIds: List<Long>) {
        playlistDao.setPlaylistOrder(playlistIds)
    }
}
