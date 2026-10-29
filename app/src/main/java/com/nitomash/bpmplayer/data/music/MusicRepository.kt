package com.nitomash.bpmplayer.data.music

import android.net.Uri
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun observeFolders(): Flow<List<FolderEntity>>
    fun observeTracks(): Flow<List<TrackEntity>>
    fun observeById(id: Long): Flow<TrackEntity?>

    suspend fun getById(id: Long): TrackEntity?

    suspend fun getTracksWithBpm(): List<TrackEntity>
    suspend fun getFolders(): List<FolderEntity>
    suspend fun getByFolder(folderId: Long): List<TrackEntity>
    suspend fun addFolder(uri: Uri): FolderScanResult
    suspend fun removeFolder(folderId: Long)
    suspend fun rescanFolder(folderId: Long): FolderScanResult?
    suspend fun updateBpm(trackId: Long, bpm: Double, source: BpmSource)
    suspend fun updateBpmBatch(updates: Map<Long, Double>, source: BpmSource): Set<Long>
    suspend fun buildSidecarRecords(folderId: Long): List<SidecarTrackRecord>
    suspend fun applySidecarRecords(folderId: Long, records: List<SidecarTrackRecord>)
    suspend fun importSidecarFile(folderId: Long, fileUri: Uri): Int
    suspend fun exportSidecarFile(folderId: Long, fileUri: Uri): Boolean
}