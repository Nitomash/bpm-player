package com.nitomash.bpmplayer.data.playlist.xspf

import android.net.Uri
import com.nitomash.bpmplayer.data.db.dao.FolderDao
import com.nitomash.bpmplayer.data.db.dao.TrackDao
import com.nitomash.bpmplayer.data.playlist.export.StorageVolumes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryTrackIndex @Inject constructor(
    private val trackDao: TrackDao,
    private val folderDao: FolderDao,
    private val volumes: StorageVolumes,
) {

    suspend fun build(): Map<String, Long> {
        val folderRoots = folderDao.getAll().associate { it.id to Uri.parse(it.rootUri) }
        return trackDao.getAll().mapNotNull { track ->
            val root = folderRoots[track.folderId] ?: return@mapNotNull null
            val path = volumes.pathOfTree(root, track.relativePath) ?: return@mapNotNull null
            path.absolutePath to track.id
        }.toMap()
    }
}