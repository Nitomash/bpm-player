package com.nitomash.bpmplayer.data.linkimport.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nitomash.bpmplayer.domain.linkimport.ImportDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafImportDestination @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportDestination {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun findChild(parent: String, name: String): String? = withContext(Dispatchers.IO) {
        val parentUri = Uri.parse(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            startingDocumentId(parentUri),
        )
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (idIndex < 0 || nameIndex < 0) continue
                if (cursor.getString(nameIndex) == name) {
                    return@withContext DocumentsContract.buildDocumentUriUsingTree(
                        parentUri,
                        cursor.getString(idIndex),
                    ).toString()
                }
            }
        }
        null
    }

    override suspend fun sizeOf(entry: String): Long? = withContext(Dispatchers.IO) {
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        resolver.query(Uri.parse(entry), columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (mimeIndex >= 0 &&
                cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR
            ) {
                return@withContext null
            }
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            if (sizeIndex < 0 || cursor.isNull(sizeIndex)) null else cursor.getLong(sizeIndex)
        }
    }

    override suspend fun createDirectory(parent: String, name: String): String = withContext(Dispatchers.IO) {
        create(parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
    }

    override suspend fun createFile(parent: String, name: String, mimeType: String): String = withContext(Dispatchers.IO) {
        create(parent, mimeType, name)
    }

    override suspend fun openOutput(entry: String): OutputStream = withContext(Dispatchers.IO) {
        resolver.openOutputStream(Uri.parse(entry), "wt")
            ?: throw IllegalStateException("Cannot open output stream")
    }

    override suspend fun rename(entry: String, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentsContract.renameDocument(resolver, Uri.parse(entry), name) != null
        }.getOrDefault(false)
    }

    override suspend fun delete(entry: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentsContract.deleteDocument(resolver, Uri.parse(entry))
        }.getOrDefault(false)
    }

    private fun startingDocumentId(uri: Uri): String =
        runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(uri) }

    private fun documentUriOf(uri: Uri): Uri =
        runCatching {
            DocumentsContract.getDocumentId(uri)
            uri
        }.getOrElse {
            DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
        }

    private fun create(parent: String, mimeType: String, name: String): String {
        val document = DocumentsContract.createDocument(
            resolver,
            documentUriOf(Uri.parse(parent)),
            mimeType,
            name,
        ) ?: throw IllegalStateException("Cannot create document $name")
        return document.toString()
    }
}
