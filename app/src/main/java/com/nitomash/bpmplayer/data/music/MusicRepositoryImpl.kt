package com.nitomash.bpmplayer.data.music

import android.net.Uri
import android.util.Log
import com.nitomash.bpmplayer.data.db.dao.FolderDao
import com.nitomash.bpmplayer.data.db.dao.TrackDao
import com.nitomash.bpmplayer.data.db.dao.TrackTagDao
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val trackDao: TrackDao,
    private val trackTagDao: TrackTagDao,
    private val folderScanner: FolderScanner,
    private val sidecarStore: SidecarStore,
    private val tagBatchApplier: TagBatchApplier,
) : MusicRepository {

    override fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    override fun observeTracks(): Flow<List<TrackEntity>> = trackDao.observeAll()

    override fun observeById(id: Long): Flow<TrackEntity?> = trackDao.observeById(id)

    override suspend fun getTracksWithBpm(): List<TrackEntity> = trackDao.getWithBpm()

    override suspend fun getFolders(): List<FolderEntity> = folderDao.getAll()

    override suspend fun getByFolder(folderId: Long): List<TrackEntity> = trackDao.getByFolder(folderId)

    override suspend fun addFolder(uri: Uri): Long {
        val rootUri = uri.toString()
        Log.i(TAG, "addFolder: $rootUri")
        val displayName = FolderScanner.displayName(uri)
        val folderId = folderDao.getByRootUri(rootUri)?.let { existing ->
            if (existing.displayName != displayName) {
                folderDao.updateDisplayName(existing.id, displayName)
            }
            existing.id
        } ?: folderDao.insert(
            FolderEntity(
                rootUri = rootUri,
                displayName = displayName,
                addedAtMs = System.currentTimeMillis(),
            ),
        )

        val drafts = folderScanner.scan(uri)
        Log.i(TAG, "addFolder: ${drafts.size} drafts for folder $folderId")
        insertDrafts(folderId, drafts)
        importSidecars(uri, folderId, recursive = true)
        Log.i(TAG, "addFolder: done for folder $folderId")
        return folderId
    }

    override suspend fun rescanFolder(folderId: Long) {
        val folder = folderDao.getById(folderId) ?: return
        val drafts = folderScanner.scan(Uri.parse(folder.rootUri))
        Log.i(TAG, "rescanFolder $folderId: ${drafts.size} drafts")
        insertDrafts(folderId, drafts)
        val existingPaths = trackDao.getByFolder(folderId).map { it.relativePath }.toSet()
        val scannedPaths = drafts.map { it.relativePath }.toSet()
        val missingPaths = existingPaths - scannedPaths
        trackDao.deleteByPaths(folderId, missingPaths.toList())
        importSidecars(Uri.parse(folder.rootUri), folderId, recursive = false)
        Log.i(TAG, "rescanFolder $folderId: ${missingPaths.size} stale tracks removed")
    }

    private suspend fun importSidecars(rootUri: Uri, folderId: Long, recursive: Boolean) {
        if (recursive) {
            val sources = sidecarStore.readAllSources(rootUri)
            val records = mergeSidecarSources(sources)
            if (records.isNotEmpty()) applySidecarRecords(folderId, records)
        } else {
            val records = sidecarStore.readRecords(rootUri) ?: return
            applySidecarRecords(folderId, records)
        }
    }

    private suspend fun insertDrafts(folderId: Long, drafts: List<TrackDraft>) {
        val now = System.currentTimeMillis()
        drafts.chunked(BATCH_SIZE).forEach { chunk ->
            trackDao.insertTracks(chunk.map { draft ->
                TrackEntity(
                    folderId = folderId,
                    accessUri = draft.accessUri,
                    relativePath = draft.relativePath,
                    fileName = draft.fileName,
                    title = draft.fileName.substringBeforeLast('.'),
                    artist = "",
                    album = "",
                    durationMs = 0,
                    bpm = null,
                    addedAtMs = now,
                )
            })
        }
    }

    override suspend fun removeFolder(folderId: Long) {
        folderDao.delete(folderId)
    }

    override suspend fun updateBpm(trackId: Long, bpm: Double, source: BpmSource) {
        trackDao.updateBpm(trackId, bpm, source)
    }

    override suspend fun updateBpmBatch(updates: Map<Long, Double>, source: BpmSource): Set<Long> {
        if (updates.isEmpty()) return emptySet()
        trackDao.updateBpmBatch(updates.toList(), source)
        return trackDao.getFolderIdsByIds(updates.keys.toList()).toSet()
    }

    override suspend fun buildSidecarRecords(folderId: Long): List<SidecarTrackRecord> {
        val tracks = trackDao.getByFolder(folderId)
        val tagsByTrack = trackTagDao.getByFolder(folderId).groupBy { it.trackId }
        return tracks.map { track ->
            trackToRecord(
                track = track,
                tags = tagsByTrack[track.id]?.map { SidecarTag(it.name, it.rating) }.orEmpty(),
            )
        }
    }

    override suspend fun applySidecarRecords(folderId: Long, records: List<SidecarTrackRecord>) {
        if (records.isEmpty()) return
        val tracks = trackDao.getByFolder(folderId)
        applyPatches(folderId, tracks, planImport(tracks, records))
    }

    override suspend fun importSidecarFile(folderId: Long, fileUri: Uri): Int {
        val records = sidecarStore.readFileRecords(fileUri) ?: return 0
        val tracks = trackDao.getByFolder(folderId)
        val patches = planImportByName(tracks, records)
        applyPatches(folderId, tracks, patches)
        return patches.size
    }

    override suspend fun exportSidecarFile(folderId: Long, fileUri: Uri): Boolean {
        val records = buildSidecarRecords(folderId)
        val content = SidecarCodec.encodeAll(records)
        return sidecarStore.writeContent(fileUri, content)
    }

    private suspend fun applyPatches(
        folderId: Long,
        tracks: List<TrackEntity>,
        patches: List<SidecarPatch>,
    ) {
        if (patches.isEmpty()) return
        val byId = tracks.associateBy { it.id }
        val updated = mutableListOf<TrackEntity>()
        val tagged = mutableListOf<TaggedTrackTags>()
        for (patch in patches) {
            val track = byId[patch.trackId] ?: continue
            val merged = track.copy(
                title = patch.title ?: track.title,
                artist = patch.artist ?: track.artist,
                durationMs = patch.durationMs ?: track.durationMs,
                bpm = patch.bpm ?: track.bpm,
                bpmSource = patch.source ?: track.bpmSource,
            )
            if (merged != track) updated += merged
            patch.tags?.let { tagged += TaggedTrackTags(patch.trackId, it) }
        }
        trackDao.updateAll(updated)
        val tagRows = tagBatchApplier.apply(folderId, tagged)
        Log.i(
            TAG,
            "sidecar import: applied ${patches.size} patches, ${updated.size} track updates, " +
                "$tagRows tag rows for folder $folderId",
        )
    }

    private companion object {
        const val TAG = "BpmLibrary"
        const val BATCH_SIZE = 500
    }
}