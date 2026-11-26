package com.nitomash.bpmplayer.domain.linkimport

@JvmInline
value class ImportJobId(val value: String)

data class ImportRequest(
    val link: String,
    val targetFolderName: String?,
    val targetDirectoryUri: String? = null,
)

data class ResolvedImportName(
    val name: String,
    val isSidecar: Boolean,
)

data class ImportPreview(
    val sourceKind: ImportSourceKind,
    val sourceName: String,
    val providerId: String,
    val supportedFileCount: Int,
    val unsupportedFileCount: Int,
    val knownTotalBytes: Long?,
    val directoriesCount: Int,
)

enum class ImportJobStatus {
    QUEUED,
    PLANNING,
    DOWNLOADING,
    INDEXING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

sealed interface ImportJobState {
    data object Queued : ImportJobState
    data class Planning(val sourceName: String?) : ImportJobState
    data class Downloading(
        val currentPath: String,
        val completedFiles: Int,
        val totalFiles: Int,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : ImportJobState

    data class Indexing(val destinationName: String) : ImportJobState
    data class Completed(
        val folderId: Long,
        val destinationUri: String,
        val downloadedFiles: Int,
        val skippedFiles: Int,
        val upToDateFiles: Int,
        val duplicateNameFiles: Int,
        val totalFiles: Int,
    ) : ImportJobState

    data class Cancelled(
        val downloadedFiles: Int,
        val skippedFiles: Int,
    ) : ImportJobState

    data class Failed(
        val message: String,
        val downloadedFiles: Int,
        val skippedFiles: Int,
    ) : ImportJobState
}

data class ImportProgress(
    val currentPath: String,
    val completedFiles: Int,
    val totalFiles: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?,
)

sealed interface ImportOutcome {
    data class Success(
        val folderId: Long,
        val destinationUri: String,
        val downloadedFiles: Int,
        val skippedFiles: Int,
        val importedPlaylists: Int,
        val upToDateFiles: Int = 0,
        val duplicateNameFiles: Int = 0,
    ) : ImportOutcome

    data object Cancelled : ImportOutcome

    data class Failed(val error: Throwable) : ImportOutcome
}
