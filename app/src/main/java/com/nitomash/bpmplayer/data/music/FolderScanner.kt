package com.nitomash.bpmplayer.data.music

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class TrackDraft(
    val accessUri: String,
    val relativePath: String,
    val fileName: String,
)

data class SidecarDoc(
    val uri: Uri,
    val directoryPath: String,
)

@Singleton
class FolderScanner @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    private val scannerLogTag = "BpmScanner"

    suspend fun scan(treeUri: Uri): List<TrackDraft> = withContext(Dispatchers.IO) {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        Log.i(scannerLogTag, "Scan started: $treeUri, rootId=$rootDocumentId")
        val drafts = mutableListOf<TrackDraft>()
        collectAudio(treeUri, rootDocumentId, "", drafts, rootDocumentId)
        Log.i(scannerLogTag, "Scan finished: ${drafts.size} tracks")
        drafts
    }

    suspend fun findByExtension(treeUri: Uri, extension: String): Uri? = withContext(Dispatchers.IO) {
        listChildrenIds(treeUri).asSequence()
            .firstOrNull { (_, name) -> name.endsWith(".$extension", ignoreCase = true) }
            ?.first
    }

    suspend fun findSidecarDocs(treeUri: Uri): List<SidecarDoc> = withContext(Dispatchers.IO) {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val docs = mutableListOf<SidecarDoc>()
        collectSidecarDocs(treeUri, rootDocumentId, "", docs, rootDocumentId)
        docs
    }

    suspend fun listChildren(treeUri: Uri): List<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        listChildrenIds(treeUri)
    }

    private suspend fun listChildrenIds(treeUri: Uri): List<Pair<Uri, String>> {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)
        val children = mutableListOf<Pair<Uri, String>>()
        contentResolver.query(childrenUri, COLUMNS, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (idIndex < 0 || nameIndex < 0) continue
                val id = cursor.getString(idIndex) ?: continue
                val name = cursor.getString(nameIndex) ?: continue
                children += DocumentsContract.buildDocumentUriUsingTree(treeUri, id) to name
            }
        }
        return children
    }

    private suspend fun checkActive() {
        currentCoroutineContext().job?.ensureActive()
    }

    private suspend fun collectAudio(
        treeUri: Uri,
        documentId: String,
        relativePath: String,
        out: MutableList<TrackDraft>,
        rootDocumentId: String,
    ) {
        walkChildren(
            treeUri = treeUri,
            documentId = documentId,
            relativePath = relativePath,
            rootDocumentId = rootDocumentId,
            onFile = { childId, name, mime ->
                if (isAudio(name, mime)) {
                    out += TrackDraft(
                        accessUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString(),
                        relativePath = if (relativePath.isEmpty()) name else "$relativePath/$name",
                        fileName = name,
                    )
                }
            },
            onDirectory = { childId, childPath ->
                collectAudio(treeUri, childId, childPath, out, rootDocumentId)
            },
        )
    }

    private suspend fun collectSidecarDocs(
        treeUri: Uri,
        documentId: String,
        relativePath: String,
        out: MutableList<SidecarDoc>,
        rootDocumentId: String,
    ) {
        walkChildren(
            treeUri = treeUri,
            documentId = documentId,
            relativePath = relativePath,
            rootDocumentId = rootDocumentId,
            onFile = { childId, name, _ ->
                if (name.endsWith(".$SIDECAR_EXTENSION", ignoreCase = true)) {
                    out += SidecarDoc(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                        directoryPath = relativePath,
                    )
                }
            },
            onDirectory = { childId, childPath ->
                collectSidecarDocs(treeUri, childId, childPath, out, rootDocumentId)
            },
        )
    }

    private suspend fun walkChildren(
        treeUri: Uri,
        documentId: String,
        relativePath: String,
        rootDocumentId: String,
        onFile: (childId: String, name: String, mimeType: String?) -> Unit,
        onDirectory: suspend (childId: String, childPath: String) -> Unit,
    ) {
        checkActive()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        try {
            val query = contentResolver.query(
                childrenUri,
                COLUMNS,
                null,
                null,
                null,
            )
            query?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) {
                    Log.w(scannerLogTag, "Provider missed columns (id=$idIndex, name=$nameIndex, mime=$mimeIndex) for $childrenUri")
                }
                while (cursor.moveToNext()) {
                    checkActive()
                    val childId = if (idIndex >= 0) cursor.getString(idIndex) else null
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    if (childId == null || name == null) {
                        Log.w(scannerLogTag, "Skip row missing id or name in $childrenUri")
                        continue
                    }
                    when {
                        mime == DIRECTORY_MIME_TYPE -> {
                            val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                            onDirectory(childId, childPath)
                        }

                        else -> onFile(childId, name, mime)
                    }
                }
            }
            if (query == null) {
                Log.w(scannerLogTag, "query returned null for $childrenUri")
                if (documentId == rootDocumentId) {
                    throw IllegalStateException("DocumentsProvider returned null for children of root")
                }
            }
        } catch (e: Exception) {
            Log.e(scannerLogTag, "Failed to query children of $childrenUri", e)
            if (documentId == rootDocumentId) throw e
        }
    }

    private fun isAudio(fileName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith(AUDIO_MIME_PREFIX) == true) return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS
    }

    companion object {
        private const val DIRECTORY_MIME_TYPE = DocumentsContract.Document.MIME_TYPE_DIR
        private const val AUDIO_MIME_PREFIX = "audio/"
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "flac", "wav", "ogg", "oga", "opus", "aac", "wma", "aiff", "aif", "ape",
        )
        private val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        fun displayName(treeUri: Uri): String {
            val lastSegment = treeUri.lastPathSegment ?: return treeUri.toString()
            val decoded = Uri.decode(lastSegment)
            val name = decoded.substringAfter(':', decoded)
            return name.ifEmpty { decoded }
        }
    }
}