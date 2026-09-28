package com.nitomash.bpmplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Query("SELECT * FROM tracks WHERE folder_id = :folderId ORDER BY title")
    fun observeByFolder(folderId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folder_id = :folderId")
    suspend fun getByFolder(folderId: Long): List<TrackEntity>

    @Query("DELETE FROM tracks WHERE folder_id = :folderId AND relative_path IN (:paths)")
    suspend fun deleteByPathsChunk(folderId: Long, paths: List<String>)

    @Transaction
    suspend fun deleteByPaths(folderId: Long, paths: List<String>) {
        paths.sqlChunks().forEach { deleteByPathsChunk(folderId, it) }
    }

    @Query("SELECT * FROM tracks ORDER BY title")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observeById(id: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE bpm IS NOT NULL")
    suspend fun getWithBpm(): List<TrackEntity>

    @Query("UPDATE tracks SET bpm = :bpm, bpm_source = :bpmSource WHERE id = :trackId")
    suspend fun updateBpm(trackId: Long, bpm: Double, bpmSource: BpmSource)

    @Transaction
    suspend fun updateBpmBatch(updates: List<Pair<Long, Double>>, bpmSource: BpmSource) {
        for ((trackId, bpm) in updates) {
            updateBpm(trackId, bpm, bpmSource)
        }
    }

    @Query("SELECT DISTINCT folder_id FROM tracks WHERE id IN (:trackIds)")
    suspend fun getFolderIdsByIdsChunk(trackIds: List<Long>): List<Long>

    @Transaction
    suspend fun getFolderIdsByIds(trackIds: List<Long>): List<Long> =
        trackIds.sqlChunks().flatMap { getFolderIdsByIdsChunk(it) }.distinct()

    @Update
    suspend fun update(track: TrackEntity)
}