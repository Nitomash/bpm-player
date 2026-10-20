package com.nitomash.bpmplayer.data.playlist.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

object SafDocumentWriter {

    private const val TAG = "SafDocumentWriter"

    fun writeText(
        contentResolver: ContentResolver,
        parentDocUri: Uri,
        fileName: String,
        mimeType: String,
        content: String,
    ): Boolean = runCatching {
        val uri = DocumentsContract.createDocument(contentResolver, parentDocUri, mimeType, fileName)
            ?: return false
        contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: return false
        true
    }.getOrElse {
        Log.e(TAG, "write failed for $fileName", it)
        false
    }
}
