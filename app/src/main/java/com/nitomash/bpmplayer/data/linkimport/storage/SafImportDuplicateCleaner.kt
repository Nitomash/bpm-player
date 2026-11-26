package com.nitomash.bpmplayer.data.linkimport.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nitomash.bpmplayer.domain.linkimport.ImportDuplicateCleaner
import com.nitomash.bpmplayer.domain.linkimport.ImportDuplicateNames
import com.nitomash.bpmplayer.domain.linkimport.ImportEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafImportDuplicateCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportDuplicateCleaner {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun clean(destinationUri: String): Int = withContext(Dispatchers.IO) {
        val byDirectory = collect(destinationUri, 0)
        val toDelete = byDirectory.values.flatMap(ImportDuplicateNames::toDelete)
        var removed = 0
        for (entry in toDelete) {
            val deleted = runCatching {
                DocumentsContract.deleteDocument(resolver, Uri.parse(entry.uri))
            }.getOrDefault(false)
            if (deleted) removed++
        }
        removed
    }

    private fun collect(parent: String, depth: Int): Map<String, List<ImportEntry>> {
        if (depth > MAX_DEPTH) return emptyMap()
        val parentUri = Uri.parse(parent)
        val documentId = runCatching { DocumentsContract.getDocumentId(parentUri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parentUri) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val files = mutableListOf<ImportEntry>()
        val directories = mutableListOf<String>()
        resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (idIndex < 0 || nameIndex < 0) continue
                val childId = cursor.getString(idIndex)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, childId).toString()
                val isDirectory = mimeIndex >= 0 &&
                    cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDirectory) {
                    directories += childUri
                    continue
                }
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    null
                }
                files += ImportEntry(cursor.getString(nameIndex), childUri, size)
            }
        }
        val result = mutableMapOf<String, List<ImportEntry>>()
        if (files.isNotEmpty()) result[parent] = files
        directories.forEach { child -> result.putAll(collect(child, depth + 1)) }
        return result
    }

    private companion object {
        const val MAX_DEPTH = 32
    }
}
