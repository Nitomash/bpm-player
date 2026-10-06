package com.nitomash.bpmplayer.data.playlist.xspf

enum class XspfImportError {
    READ_FAILED,
    NOT_XSPF,
    EMPTY_PLAYLIST,
    BASE_UNRESOLVED,
    NO_MATCHES,
}

data class XspfImportResult(
    val playlistName: String,
    val playlistId: Long?,
    val matchedCount: Int,
    val totalCount: Int,
    val error: XspfImportError?,
) {
    val unmatchedCount: Int
        get() = (totalCount - matchedCount).coerceAtLeast(0)

    val succeeded: Boolean
        get() = error == null
}