package com.nitomash.bpmplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nitomash.bpmplayer.data.db.entity.TrackTagEntity

data class TrackTagRow(
    val trackId: Long,
    val name: String,
    val rating: Int,
)

@Dao
interface TrackTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TrackTagEntity>)

    @Query("DELETE FROM track_tags WHERE track_id IN (:trackIds)")
    suspend fun deleteForTracksChunk(trackIds: List<Long>)

    @Transaction
    suspend fun applyTagPatch(trackIds: List<Long>, rows: List<TrackTagEntity>) {
        trackIds.distinct().sqlChunks().forEach { deleteForTracksChunk(it) }
        rows.sqlChunks().forEach { insertAll(it) }
    }

    @Query(
        "SELECT tt.track_id AS trackId, tg.name AS name, tt.rating AS rating " +
            "FROM track_tags tt INNER JOIN tags tg ON tg.id = tt.tag_id " +
            "WHERE tt.track_id IN (SELECT id FROM tracks WHERE folder_id = :folderId)",
    )
    suspend fun getByFolder(folderId: Long): List<TrackTagRow>

    suspend fun getByTrackIds(trackIds: List<Long>): List<TrackTagRow> =
        trackIds.distinct().sqlChunks().flatMap { getByTrackIdsChunk(it) }

    @Query(
        "SELECT tt.track_id AS trackId, tg.name AS name, tt.rating AS rating " +
            "FROM track_tags tt INNER JOIN tags tg ON tg.id = tt.tag_id " +
            "WHERE tt.track_id IN (:trackIds)",
    )
    suspend fun getByTrackIdsChunk(trackIds: List<Long>): List<TrackTagRow>
}