package com.nitomash.bpmplayer.data.linkimport

import com.nitomash.bpmplayer.data.db.dao.ImportJobDao
import com.nitomash.bpmplayer.data.db.entity.ImportJobEntity
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import com.nitomash.bpmplayer.domain.linkimport.ImportJobSnapshot
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStatus
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStore
import com.nitomash.bpmplayer.domain.linkimport.ImportJobUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportJobStoreImpl @Inject constructor(
    private val dao: ImportJobDao,
) : ImportJobStore {

    override suspend fun create(jobId: ImportJobId, link: String, targetFolderName: String?) {
        val now = System.currentTimeMillis()
        dao.upsert(
            ImportJobEntity(
                id = jobId.value,
                sourceLink = link,
                providerId = null,
                targetFolderName = targetFolderName,
                destinationUri = null,
                folderId = 0L,
                status = ImportJobStatus.QUEUED.name,
                sourceName = null,
                currentPath = null,
                createdAtMs = now,
                updatedAtMs = now,
                downloadedFiles = 0,
                skippedFiles = 0,
                upToDateFiles = 0,
                duplicateNameFiles = 0,
                downloadedBytes = 0L,
                totalFiles = 0,
                totalBytes = null,
                errorMessage = null,
            ),
        )
    }

    override suspend fun get(jobId: ImportJobId): ImportJobSnapshot? =
        dao.get(jobId.value)?.let(::toSnapshot)

    override suspend fun active(): ImportJobSnapshot? = dao.active()?.let(::toSnapshot)

    override suspend fun getStatus(jobId: ImportJobId): ImportJobStatus? =
        dao.get(jobId.value)?.status?.let { value ->
            runCatching { ImportJobStatus.valueOf(value) }.getOrNull()
        }

    override suspend fun getLink(jobId: ImportJobId): String? = dao.get(jobId.value)?.sourceLink

    override suspend fun update(jobId: ImportJobId, update: ImportJobUpdate) {
        val current = dao.get(jobId.value) ?: return
        val status = if (current.status == ImportJobStatus.CANCELLED.name &&
            update.status != ImportJobStatus.CANCELLED
        ) {
            ImportJobStatus.CANCELLED.name
        } else {
            update.status?.name ?: current.status
        }
        dao.upsert(
            current.copy(
                status = status,
                sourceName = update.sourceName ?: current.sourceName,
                destinationUri = update.destinationUri ?: current.destinationUri,
                folderId = update.folderId ?: current.folderId,
                currentPath = update.currentPath ?: current.currentPath,
                downloadedFiles = update.downloadedFiles ?: current.downloadedFiles,
                skippedFiles = update.skippedFiles ?: current.skippedFiles,
                upToDateFiles = update.upToDateFiles ?: current.upToDateFiles,
                duplicateNameFiles = update.duplicateNameFiles ?: current.duplicateNameFiles,
                downloadedBytes = update.downloadedBytes ?: current.downloadedBytes,
                totalFiles = update.totalFiles ?: current.totalFiles,
                totalBytes = update.totalBytes ?: current.totalBytes,
                errorMessage = update.errorMessage ?: current.errorMessage,
                sourceLink = if (update.clearLink) null else current.sourceLink,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    override fun observe(jobId: ImportJobId): Flow<ImportJobSnapshot?> =
        dao.observe(jobId.value).map { entity -> entity?.let(::toSnapshot) }

    private fun toSnapshot(entity: ImportJobEntity) = ImportJobSnapshot(
        id = ImportJobId(entity.id),
        link = entity.sourceLink,
        targetFolderName = entity.targetFolderName,
        status = runCatching { ImportJobStatus.valueOf(entity.status) }
            .getOrDefault(ImportJobStatus.FAILED),
        sourceName = entity.sourceName,
        destinationUri = entity.destinationUri,
        folderId = entity.folderId,
        currentPath = entity.currentPath,
        completedFiles = entity.downloadedFiles + entity.upToDateFiles,
        totalFiles = entity.totalFiles,
        downloadedBytes = entity.downloadedBytes,
        totalBytes = entity.totalBytes,
        downloadedFiles = entity.downloadedFiles,
        skippedFiles = entity.skippedFiles,
        upToDateFiles = entity.upToDateFiles,
        duplicateNameFiles = entity.duplicateNameFiles,
        errorMessage = entity.errorMessage,
    )
}
