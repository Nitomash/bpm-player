package com.nitomash.bpmplayer.domain.linkimport

import java.io.Closeable
import java.io.OutputStream

sealed interface RemoteItem {
    val providerId: String
    val remoteId: String
    val displayName: String
    val modifiedAtMs: Long?
}

data class RemoteDirectory(
    override val providerId: String,
    override val remoteId: String,
    override val displayName: String,
    override val modifiedAtMs: Long? = null,
) : RemoteItem

data class RemoteFile(
    override val providerId: String,
    override val remoteId: String,
    override val displayName: String,
    override val modifiedAtMs: Long? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
) : RemoteItem

data class RemotePage(
    val items: List<RemoteItem>,
    val nextPageToken: String?,
)

interface RemoteDownload : Closeable {
    val contentLength: Long?
    val mimeType: String?

    suspend fun copyTo(
        output: OutputStream,
        onBytesCopied: suspend (downloadedBytes: Long) -> Unit,
    )
}
