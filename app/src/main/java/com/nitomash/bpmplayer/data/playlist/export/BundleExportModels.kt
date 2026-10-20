package com.nitomash.bpmplayer.data.playlist.export

data class CopyJob(
    val track: ExportTrack,
    val targetName: String,
)

data class BundleExportProgress(
    val copied: Int,
    val total: Int,
    val currentFileName: String,
)

enum class BundleExportError {
    EMPTY_PLAYLIST,
    FOLDER_CREATE_FAILED,
    COPY_FAILED,
    WRITE_FAILED,
    CANCELLED,
}

data class BundleExportResult(
    val playlistName: String,
    val folderName: String?,
    val copiedCount: Int,
    val error: BundleExportError? = null,
    val failedFileName: String? = null,
) {
    val success: Boolean get() = error == null
}
