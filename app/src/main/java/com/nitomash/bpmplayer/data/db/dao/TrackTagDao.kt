package com.nitomash.bpmplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM track_tags WHERE track_id = :trackId")
    suspend fun deleteForTrack(trackId: Long)

    @Query(
        "SELECT tt.track_id AS trackId, tg.name AS name, tt.rating AS rating " +
            "FROM track_tags tt INNER JOIN tags tg ON tg.id = tt.tag_id " +
            "WHERE tt.track_id IN (SELECT id FROM tracks WHERE folder_id = :folderId)",
    )
    suspend fun getByFolder(folderId: Long): List<TrackTagRow>
}