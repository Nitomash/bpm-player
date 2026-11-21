package com.nitomash.bpmplayer.domain.linkimport

data class ImportEntry(
    val name: String,
    val uri: String,
    val size: Long?,
)

interface ImportDestination {
    suspend fun findChild(parent: String, name: String): String?
    suspend fun listChildren(parent: String): List<ImportEntry>
    suspend fun sizeOf(entry: String): Long?
    suspend fun createDirectory(parent: String, name: String): String
    suspend fun createFile(parent: String, name: String, mimeType: String): String
    suspend fun openOutput(entry: String): java.io.OutputStream
    suspend fun rename(entry: String, name: String): Boolean
    suspend fun delete(entry: String): Boolean
}

interface ImportJobStore {
    suspend fun create(jobId: ImportJobId, link: String, targetFolderName: String?)
    suspend fun get(jobId: ImportJobId): ImportJobSnapshot?
    suspend fun active(): ImportJobSnapshot?
    suspend fun getStatus(jobId: ImportJobId): ImportJobStatus?
    suspend fun getLink(jobId: ImportJobId): String?
    suspend fun update(jobId: ImportJobId, update: ImportJobUpdate)
    fun observe(jobId: ImportJobId): kotlinx.coroutines.flow.Flow<ImportJobSnapshot?>
}

data class ImportJobUpdate(
    val status: ImportJobStatus? = null,
    val sourceName: String? = null,
    val destinationUri: String? = null,
    val folderId: Long? = null,
    val currentPath: String? = null,
    val totalFiles: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val downloadedFiles: Int? = null,
    val skippedFiles: Int? = null,
    val upToDateFiles: Int? = null,
    val errorMessage: String? = null,
    val clearLink: Boolean = false,
)

data class ImportJobSnapshot(
    val id: ImportJobId,
    val link: String?,
    val targetFolderName: String?,
    val status: ImportJobStatus,
    val sourceName: String?,
    val destinationUri: String?,
    val folderId: Long,
    val currentPath: String?,
    val completedFiles: Int,
    val totalFiles: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val downloadedFiles: Int,
    val skippedFiles: Int,
    val errorMessage: String?,
    val upToDateFiles: Int = 0,
)
