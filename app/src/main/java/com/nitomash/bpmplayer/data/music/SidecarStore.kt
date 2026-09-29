package com.nitomash.bpmplayer.data.music

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SidecarStore @Inject constructor(
    private val contentResolver: ContentResolver,
    private val scanner: FolderScanner,
) {

    suspend fun readFileRecords(uri: Uri): List<SidecarTrackRecord>? = withContext(Dispatchers.IO) {
        readText(uri)
            ?.let(SidecarCodec::decode)
            ?.records
    }

    suspend fun writeContent(uri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.flush()
                true
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "write failed for $uri", e)
            false
        }
    }

    suspend fun readRecords(treeUri: Uri): List<SidecarTrackRecord>? = withContext(Dispatchers.IO) {
        val uri = findSidecarDoc(treeUri) ?: return@withContext null
        readFileRecords(uri)
    }

    suspend fun readAllSources(treeUri: Uri): List<SidecarSource> = withContext(Dispatchers.IO) {
        scanner.findSidecarDocs(treeUri).mapNotNull { doc ->
            val records = readFileRecords(doc.uri) ?: return@mapNotNull null
            SidecarSource(directoryPath = doc.directoryPath, records = records)
        }
    }

    suspend fun writeSidecar(
        treeUri: Uri,
        folderName: String,
        records: List<SidecarTrackRecord>,
    ) {
        withContext(Dispatchers.IO) {
            val baseName = sanitizeFileName(folderName)
            val fileName = "$baseName.$SIDECAR_EXTENSION"
            val tmpName = ".$fileName.tmp"
            try {
                scanner.listChildren(treeUri).forEach { (uri, name) ->
                    val isOwnSidecar = name.startsWith("$baseName.") && name.endsWith(".$SIDECAR_EXTENSION")
                    val isOwnStaleTmp = name.startsWith(".$baseName.") && name.endsWith(".tmp")
                    if (isOwnSidecar || isOwnStaleTmp) {
                        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                            .onFailure { Log.w(TAG, "cleanup delete failed for $name", it) }
                    }
                }
                val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
                val tmpUri = DocumentsContract.createDocument(contentResolver, rootDocumentUri, SIDECAR_MIME, tmpName)
                    ?: run {
                        Log.w(TAG, "createDocument failed for $fileName")
                        return@withContext
                    }
                val wrote = contentResolver.openOutputStream(tmpUri, "wt")?.use { output ->
                    val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8), WRITE_BUFFER_CHARS)
                    SidecarCodec.encodeTo(records, writer)
                    writer.flush()
                    true
                } ?: false
                if (!wrote) {
                    Log.w(TAG, "openOutputStream failed for $tmpName")
                    return@withContext
                }
                val renamed = runCatching {
                    DocumentsContract.renameDocument(contentResolver, tmpUri, fileName)
                }.getOrNull()
                if (renamed == null) Log.w(TAG, "rename failed for $fileName")
                Log.i(TAG, "writeSidecar done for $folderName (${records.size} records)")
            } catch (e: Exception) {
                Log.e(TAG, "writeSidecar failed for $folderName", e)
            }
        }
    }

    private suspend fun findSidecarDoc(treeUri: Uri): Uri? {
        val candidate = scanner.findByExtension(treeUri, SIDECAR_EXTENSION) ?: return null
        val header = readText(candidate)?.take(SIDECAR_MAGIC.length) ?: return null
        return if (header == SIDECAR_MAGIC || header == SIDECAR_LEGACY_MAGIC) candidate else null
    }

    private fun readText(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        Log.w(TAG, "read failed for $uri", e)
        null
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .take(64)
            .ifBlank { "music" }

    private companion object {
        const val TAG = "BpmSidecar"
        const val WRITE_BUFFER_CHARS = 8 * 1024
    }
}