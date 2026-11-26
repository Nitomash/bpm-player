package com.nitomash.bpmplayer.data.linkimport

import android.net.Uri
import com.nitomash.bpmplayer.data.playlist.xspf.XspfImporter
import com.nitomash.bpmplayer.domain.linkimport.ImportFileKind
import com.nitomash.bpmplayer.domain.linkimport.PlannedFile
import com.nitomash.bpmplayer.domain.linkimport.PlaylistImportSupport
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XspfPlaylistImportSupport @Inject constructor(
    private val importer: XspfImporter,
) : PlaylistImportSupport {

    override suspend fun import(files: List<PlannedFile>, downloadedUris: Map<String, String>): Int {
        val playlists = files.filter { it.kind == ImportFileKind.PLAYLIST }
        if (playlists.isEmpty()) return 0
        val batch = importer.beginBatch()
        return playlists.count { file ->
            val uri = downloadedUris[file.relativePath.value] ?: return@count false
            runCatching { batch.import(Uri.parse(uri)).playlistId != null }.getOrDefault(false)
        }
    }
}
