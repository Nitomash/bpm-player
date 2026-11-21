package com.nitomash.bpmplayer.data.linkimport

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.nitomash.bpmplayer.ui.common.BpmLog
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import com.nitomash.bpmplayer.domain.linkimport.ImportJobSnapshot
import com.nitomash.bpmplayer.domain.linkimport.ImportJobState
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStatus
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStore
import com.nitomash.bpmplayer.domain.linkimport.ImportJobUpdate
import com.nitomash.bpmplayer.domain.linkimport.ImportLimits
import com.nitomash.bpmplayer.domain.linkimport.ImportPlanBuilder
import com.nitomash.bpmplayer.domain.linkimport.ImportPreview
import com.nitomash.bpmplayer.domain.linkimport.ImportRequest
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkImportUseCase
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicLinkImportUseCaseImpl @Inject constructor(
    private val providers: PublicLinkProviderRegistry,
    private val planBuilder: ImportPlanBuilder,
    private val jobStore: ImportJobStore,
    private val workManager: WorkManager,
    private val limits: ImportLimits,
) : PublicLinkImportUseCase {

    override suspend fun resolveName(link: String): String {
        val provider = providers.find(link) ?: throw PublicLinkException.UnsupportedLink
        return provider.inspect(link).displayName
    }

    override suspend fun inspect(link: String): ImportPreview {
        val provider = providers.find(link) ?: throw PublicLinkException.UnsupportedLink
        val source = provider.inspect(link)
        val plan = planBuilder.build(source, limits)
        return ImportPreview(
            sourceKind = plan.sourceKind,
            sourceName = plan.sourceName,
            providerId = provider.id,
            supportedFileCount = plan.supportedFileCount,
            unsupportedFileCount = plan.unsupportedFileCount,
            knownTotalBytes = plan.knownTotalBytes,
            directoriesCount = plan.directories.size,
        )
    }

    override suspend fun enqueue(request: ImportRequest): ImportJobId {
        val jobId = ImportJobId(UUID.randomUUID().toString())
        jobStore.create(jobId, request.link, request.targetFolderName)
        val work = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(
                workDataOf(
                    ImportWorker.KEY_JOB_ID to jobId.value,
                    ImportWorker.KEY_FOLDER_NAME to request.targetFolderName,
                ),
            )
            .build()
        workManager.beginUniqueWork(jobId.value, ExistingWorkPolicy.KEEP, work).enqueue().await()
        return jobId
    }

    override suspend fun cancel(jobId: ImportJobId) {
        BpmLog.d("IMPORT_CANCEL enter jobId=${jobId.value}")
        val status = jobStore.getStatus(jobId)
        BpmLog.d("IMPORT_CANCEL status=$status")
        if (status == null ||
            status == ImportJobStatus.COMPLETED ||
            status == ImportJobStatus.CANCELLED
        ) {
            return
        }
        runCatching { workManager.cancelUniqueWork(jobId.value).await() }
            .onFailure { BpmLog.d("IMPORT_CANCEL work cancel failed: ${it.message}") }
        jobStore.update(
            jobId,
            ImportJobUpdate(status = ImportJobStatus.CANCELLED, clearLink = true),
        )
        BpmLog.d("IMPORT_CANCEL stored CANCELLED")
    }

    override suspend fun resume(jobId: ImportJobId?): ImportJobSnapshot? =
        if (jobId != null) jobStore.get(jobId) else jobStore.active()

    override fun observe(jobId: ImportJobId): Flow<ImportJobState> =
        jobStore.observe(jobId).filterNotNull().map(::toState)

    private fun toState(snapshot: ImportJobSnapshot): ImportJobState = when (snapshot.status) {
        ImportJobStatus.QUEUED -> ImportJobState.Queued
        ImportJobStatus.PLANNING -> ImportJobState.Planning(snapshot.sourceName)
        ImportJobStatus.DOWNLOADING -> ImportJobState.Downloading(
            currentPath = snapshot.currentPath.orEmpty(),
            completedFiles = snapshot.completedFiles,
            totalFiles = snapshot.totalFiles,
            downloadedBytes = snapshot.downloadedBytes,
            totalBytes = snapshot.totalBytes,
        )

        ImportJobStatus.INDEXING -> ImportJobState.Indexing(snapshot.destinationUri.orEmpty())
        ImportJobStatus.COMPLETED -> ImportJobState.Completed(
            folderId = snapshot.folderId,
            destinationUri = snapshot.destinationUri.orEmpty(),
            downloadedFiles = snapshot.downloadedFiles,
            skippedFiles = snapshot.skippedFiles,
            upToDateFiles = snapshot.upToDateFiles,
            totalFiles = snapshot.totalFiles,
        )

        ImportJobStatus.CANCELLED -> ImportJobState.Cancelled(
            snapshot.downloadedFiles,
            snapshot.skippedFiles,
        )

        ImportJobStatus.FAILED -> ImportJobState.Failed(
            snapshot.errorMessage ?: "Unknown error",
            snapshot.downloadedFiles,
            snapshot.skippedFiles,
        )
    }
}
