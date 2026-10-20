package com.nitomash.bpmplayer.data.playlist.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import com.nitomash.bpmplayer.data.db.dao.TrackTagDao
import com.nitomash.bpmplayer.data.music.FolderScanner
import com.nitomash.bpmplayer.data.music.SIDECAR_EXTENSION
import com.nitomash.bpmplayer.data.music.SIDECAR_MIME
import com.nitomash.bpmplayer.data.music.SidecarCodec
import com.nitomash.bpmplayer.data.music.SidecarTag
import com.nitomash.bpmplayer.data.playlist.xspf.XspfFileName
import com.nitomash.bpmplayer.data.playlist.xspf.XspfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistBundleExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: PlaylistExportSource,
    private val trackTagDao: TrackTagDao,
    private val scanner: FolderScanner,
) {

    suspend fun export(
        targetTreeUri: Uri,
        playlistId: Long,
        onProgress: (BundleExportProgress) -> Unit,
    ): BundleExportResult = withContext(Dispatchers.IO) {
        val playlist = source.load(listOf(playlistId)).firstOrNull()
        if (playlist == null || playlist.tracks.isEmpty()) {
            return@withContext BundleExportResult(
                playlistName = playlist?.name.orEmpty(),
                folderName = null,
                copiedCount = 0,
                error = BundleExportError.EMPTY_PLAYLIST,
            )
        }

        val takenNames = takenNames(targetTreeUri)
        val folderName = BundleExportPlanner.planFolderName(playlist.name, takenNames)
        val rootUri = rootDocumentUri(targetTreeUri)
        val folderUri = createDirectory(rootUri, folderName)
            ?: return@withContext BundleExportResult(
                playlistName = playlist.name,
                folderName = null,
                copiedCount = 0,
                error = BundleExportError.FOLDER_CREATE_FAILED,
            )
        val finalFolderName = displayName(folderUri) ?: folderName

        val jobs = BundleExportPlanner.planJobs(playlist.tracks)
        jobs.forEachIndexed { index, job ->
            currentCoroutineContext().ensureActive()
            onProgress(BundleExportProgress(copied = index, total = jobs.size, currentFileName = job.targetName))
            val copied = copyFile(job, folderUri)
            if (!copied) {
                return@withContext BundleExportResult(
                    playlistName = playlist.name,
                    folderName = finalFolderName,
                    copiedCount = index,
                    error = BundleExportError.COPY_FAILED,
                    failedFileName = job.track.fileName,
                )
            }
        }
        onProgress(BundleExportProgress(copied = jobs.size, total = jobs.size, currentFileName = ""))

        val innerNames = jobs.map { it.targetName }.toMutableSet()
        val metaName = uniqueInnerName("$finalFolderName.$SIDECAR_EXTENSION", innerNames)
        innerNames += metaName
        val records = BundleExportPlanner.buildRecords(jobs, loadTags(jobs))
        val metaWritten = SafDocumentWriter.writeText(
            context.contentResolver,
            folderUri,
            metaName,
            SIDECAR_MIME,
            SidecarCodec.encodeAll(records),
        )

        val xspfName = XspfFileName.unique(XspfFileName.sanitize(playlist.name), innerNames)
        val xspf = XspfWriter.encode(BundleExportPlanner.buildXspf(playlist.name, jobs))
        val xspfWritten = SafDocumentWriter.writeText(
            context.contentResolver,
            folderUri,
            xspfName,
            XSPF_MIME,
            xspf,
        )

        if (!metaWritten || !xspfWritten) {
            return@withContext BundleExportResult(
                playlistName = playlist.name,
                folderName = finalFolderName,
                copiedCount = jobs.size,
                error = BundleExportError.WRITE_FAILED,
                failedFileName = if (!metaWritten) metaName else xspfName,
            )
        }

        BundleExportResult(
            playlistName = playlist.name,
            folderName = finalFolderName,
            copiedCount = jobs.size,
        )
    }

    private suspend fun loadTags(jobs: List<CopyJob>): Map<Long, List<SidecarTag>> =
        trackTagDao.getByTrackIds(jobs.map { it.track.trackId })
            .groupBy({ it.trackId }, { SidecarTag(it.name, it.rating) })

    private suspend fun copyFile(job: CopyJob, folderUri: Uri): Boolean {
        val resolver = context.contentResolver
        val sourceUri = Uri.parse(job.track.accessUri)
        val mime = mimeTypeOf(job.track.fileName, sourceUri)
        val destUri = runCatching {
            DocumentsContract.createDocument(resolver, folderUri, mime, job.targetName)
        }.getOrNull() ?: return false
        try {
            if (!copyFast(sourceUri, destUri)) {
                copyStreams(sourceUri, destUri)
            }
        } catch (e: CancellationException) {
            deleteQuietly(destUri)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "copy failed for ${job.track.fileName}", e)
            deleteQuietly(destUri)
            return false
        }
        return true
    }

    private fun copyFast(sourceUri: Uri, destUri: Uri): Boolean {
        val resolver = context.contentResolver
        val source = runCatching { resolver.openFileDescriptor(sourceUri, "r") }.getOrNull() ?: return false
        val dest = runCatching { resolver.openFileDescriptor(destUri, "wt") }.getOrNull()
        if (dest == null) {
            runCatching { source.close() }
            return false
        }
        try {
            FileInputStream(source.fileDescriptor).use { input ->
                FileOutputStream(dest.fileDescriptor).use { output ->
                    val src = input.channel
                    val dst = output.channel
                    val size = src.size()
                    if (size <= 0L) return false
                    var position = 0L
                    while (position < size) {
                        val transferred = src.transferTo(position, size - position, dst)
                        if (transferred <= 0L) break
                        position += transferred
                    }
                }
            }
            return true
        } catch (e: IOException) {
            Log.w(TAG, "fast copy path failed, falling back", e)
            return false
        } finally {
            runCatching { source.close() }
            runCatching { dest.close() }
        }
    }

    private suspend fun copyStreams(sourceUri: Uri, destUri: Uri) {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(sourceUri) ?: error("openInputStream returned null")
        input.use { stream ->
            val output = resolver.openOutputStream(destUri, "wt") ?: error("openOutputStream returned null")
            output.use { sink ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                }
                sink.flush()
            }
        }
    }

    private fun mimeTypeOf(fileName: String, sourceUri: Uri): String {
        context.contentResolver.getType(sourceUri)?.let { return it }
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private suspend fun takenNames(targetTreeUri: Uri): Set<String> =
        runCatching { scanner.listChildren(targetTreeUri).map { it.second }.toSet() }
            .getOrElse {
                Log.w(TAG, "cannot list target folder", it)
                emptySet()
            }

    private fun rootDocumentUri(treeUri: Uri): Uri {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
    }

    private suspend fun createDirectory(rootUri: Uri?, folderName: String): Uri? {
        if (rootUri == null) return null
        return runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                rootUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                folderName,
            )
        }.getOrNull()
    }

    private fun displayName(documentUri: Uri): String? = runCatching {
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun deleteQuietly(documentUri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, documentUri) }
    }

    private fun uniqueInnerName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.indexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while ("$base ($index)$extension" in taken) index++
        return "$base ($index)$extension"
    }

    private companion object {
        const val TAG = "BundleExporter"
        const val XSPF_MIME = "application/xspf+xml"
        const val BUFFER_SIZE = 64 * 1024
    }
}
