package com.nitomash.bpmplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nitomash.bpmplayer.data.db.entity.PlaylistEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistOrderEntity
import com.nitomash.bpmplayer.data.db.entity.PlaylistTrackEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.playlist.PlaylistNameRow
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackRow
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Transaction
    suspend fun createPlaylist(playlist: PlaylistEntity): Long {
        val id = insert(playlist)
        upsertPlaylistOrder(
            listOf(PlaylistOrderEntity(playlistId = id, position = getMaxPlaylistOrderPosition() + 1)),
        )
        return id
    }

    @Query(
        "SELECT p.id AS id, p.name AS name, p.updated_at_ms AS updated_at_ms, " +
            "COALESCE(po.position, 2147483647) AS sort_position, " +
            "COUNT(pt.playlist_id) AS track_count " +
            "FROM playlists p " +
            "LEFT JOIN playlist_order po ON po.playlist_id = p.id " +
            "LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id " +
            "GROUP BY p.id ORDER BY p.created_at_ms DESC, p.id DESC",
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observeById(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getById(playlistId: Long): PlaylistEntity?

    @Query("SELECT id, name FROM playlists WHERE id IN (:playlistIds)")
    suspend fun getNamesByIds(playlistIds: List<Long>): List<PlaylistNameRow>

    @Query("SELECT track_sort_mode FROM playlists WHERE id = :playlistId")
    suspend fun getTrackSortMode(playlistId: Long): PlaylistTrackSort?

    @Query("UPDATE playlists SET name = :name, updated_at_ms = :updatedAtMs WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, updatedAtMs: Long)

    @Query("UPDATE playlists SET updated_at_ms = :updatedAtMs WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, updatedAtMs: Long)

    @Query("UPDATE playlists SET track_sort_mode = :sortMode WHERE id = :playlistId")
    suspend fun setTrackSortMode(playlistId: Long, sortMode: PlaylistTrackSort)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun getPlaylistTrackRows(playlistId: Long): List<PlaylistTrackEntity>

    @Query(
        "SELECT t.*, pt.position AS position, pt.added_at_ms AS added_to_playlist_at_ms " +
            "FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id " +
            "WHERE pt.playlist_id = :playlistId ORDER BY pt.position",
    )
    suspend fun getTrackRows(playlistId: Long): List<PlaylistTrackRow>

    @Query(
        "SELECT t.*, pt.position AS position, pt.added_at_ms AS added_to_playlist_at_ms " +
            "FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id " +
            "WHERE pt.playlist_id = :playlistId ORDER BY pt.position",
    )
    fun observeTrackRows(playlistId: Long): Flow<List<PlaylistTrackRow>>

    @Query(
        "SELECT t.* FROM tracks t LEFT JOIN playlist_tracks pt ON pt.track_id = t.id AND pt.playlist_id = :playlistId " +
            "WHERE pt.playlist_id IS NULL ORDER BY t.title",
    )
    suspend fun getTracksNotInPlaylist(playlistId: Long): List<TrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(playlistTracks: List<PlaylistTrackEntity>)

    @Insert
    suspend fun insertAll(playlistTracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun deleteAllTracks(playlistId: Long)

    @Transaction
    suspend fun removeTracks(playlistId: Long, trackIds: Set<Long>) {
        val remaining = getPlaylistTrackRows(playlistId).filter { it.trackId !in trackIds }
        deleteAllTracks(playlistId)
        insertAll(remaining.mapIndexed { index, row -> row.copy(position = index) })
    }

    @Transaction
    suspend fun setTrackOrder(playlistId: Long, trackIds: List<Long>): Boolean {
        val byId = getPlaylistTrackRows(playlistId).associateBy { it.trackId }
        val requested = trackIds.toSet()
        if (requested.size != trackIds.size) return false
        if (byId.keys != requested) return false
        deleteAllTracks(playlistId)
        insertAll(trackIds.mapIndexed { index, trackId -> byId.getValue(trackId).copy(position = index) })
        return true
    }

    @Query("SELECT * FROM playlist_order ORDER BY position")
    suspend fun getPlaylistOrder(): List<PlaylistOrderEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_order")
    suspend fun getMaxPlaylistOrderPosition(): Int

    @Upsert
    suspend fun upsertPlaylistOrder(rows: List<PlaylistOrderEntity>)

    @Query("SELECT playlist_id FROM playlist_order")
    suspend fun getPlaylistOrderIds(): List<Long>

    @Query("DELETE FROM playlist_order WHERE playlist_id IN (:playlistIds)")
    suspend fun deletePlaylistOrderIdsChunk(playlistIds: List<Long>)

    @Transaction
    suspend fun deletePlaylistOrderExcept(keepIds: List<Long>) {
        stalePlaylistIds(getPlaylistOrderIds(), keepIds)
            .sqlChunks()
            .forEach { deletePlaylistOrderIdsChunk(it) }
    }

    @Transaction
    suspend fun setPlaylistOrder(playlistIds: List<Long>) {
        val distinct = playlistIds.distinct()
        if (distinct.isEmpty()) return
        deletePlaylistOrderExcept(distinct)
        upsertPlaylistOrder(
            distinct.mapIndexed { index, playlistId -> PlaylistOrderEntity(playlistId, index) },
        )
    }
}

internal fun stalePlaylistIds(existing: List<Long>, keep: List<Long>): List<Long> {
    if (keep.isEmpty()) return existing
    val keepSet = keep.toHashSet()
    return existing.filter { it !in keepSet }
}
