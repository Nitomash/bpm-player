package com.nitomash.bpmplayer.data.playlist.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.nitomash.bpmplayer.data.music.FolderScanner
import com.nitomash.bpmplayer.data.playlist.xspf.StoragePath
import com.nitomash.bpmplayer.data.playlist.xspf.XspfFileName
import com.nitomash.bpmplayer.data.playlist.xspf.XspfLocation
import com.nitomash.bpmplayer.data.playlist.xspf.XspfPlaylist
import com.nitomash.bpmplayer.data.playlist.xspf.XspfTrack
import com.nitomash.bpmplayer.data.playlist.xspf.XspfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XspfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: PlaylistExportSource,
    private val volumes: StorageVolumes,
    private val scanner: FolderScanner,
) {

    suspend fun export(targetTreeUri: Uri, playlistIds: List<Long>): XspfExportResult =
        withContext(Dispatchers.IO) {
            val base = volumes.pathOfTree(targetTreeUri, "")
            val taken = takenNames(targetTreeUri).toMutableSet()
            val rootUri = runCatching { rootDocumentUri(targetTreeUri) }.getOrNull()

            val files = source.load(playlistIds).map { playlist ->
                if (rootUri == null) {
                    failure(playlist, base)
                } else {
                    writePlaylist(playlist, base, rootUri, taken)
                }
            }
            XspfExportResult(files = files, baseResolved = base != null)
        }

    private suspend fun writePlaylist(
        playlist: ExportPlaylist,
        base: StoragePath?,
        rootUri: Uri,
        taken: MutableSet<String>,
    ): XspfExportedFile {
        val tracks = mutableListOf<XspfTrack>()
        var absoluteCount = 0
        playlist.tracks.forEach { track ->
            val trackPath = volumes.pathOfTree(Uri.parse(track.folderRootUri), track.relativePath)
            val resolved = XspfLocation.location(base, trackPath, track.relativePath)
            if (!resolved.relative) absoluteCount++
            tracks += XspfTrack(
                location = resolved.value,
                title = track.title.ifBlank { track.fileName.substringBeforeLast('.') },
            )
        }

        val fileName = XspfFileName.unique(XspfFileName.sanitize(playlist.name), taken)
        taken += fileName
        val written = writeDocument(rootUri, fileName, encode(XspfPlaylist(playlist.name, tracks)))
        return XspfExportedFile(
            playlistId = playlist.playlistId,
            playlistName = playlist.name,
            fileName = if (written) fileName else null,
            trackCount = tracks.size,
            relativeCount = tracks.size - absoluteCount,
            absoluteCount = absoluteCount,
            failed = !written,
        )
    }

    private suspend fun failure(playlist: ExportPlaylist, base: StoragePath?): XspfExportedFile {
        Log.w(TAG, "cannot resolve target document for ${playlist.name}")
        return XspfExportedFile(
            playlistId = playlist.playlistId,
            playlistName = playlist.name,
            fileName = null,
            trackCount = playlist.tracks.size,
            relativeCount = if (base == null) 0 else playlist.tracks.size,
            absoluteCount = if (base == null) playlist.tracks.size else 0,
            failed = true,
        )
    }

    private fun encode(playlist: XspfPlaylist): String = XspfWriter.encode(playlist)

    private suspend fun writeDocument(rootUri: Uri, fileName: String, content: String): Boolean =
        SafDocumentWriter.writeText(context.contentResolver, rootUri, fileName, XSPF_MIME, content)

    private suspend fun takenNames(targetTreeUri: Uri): List<String> =
        runCatching { scanner.listChildren(targetTreeUri).map { it.second } }
            .getOrElse {
                Log.w(TAG, "cannot list target folder", it)
                emptyList()
            }

    private fun rootDocumentUri(treeUri: Uri): Uri {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
    }

    private companion object {
        const val TAG = "XspfExporter"
        const val XSPF_MIME = "application/xspf+xml"
    }
}