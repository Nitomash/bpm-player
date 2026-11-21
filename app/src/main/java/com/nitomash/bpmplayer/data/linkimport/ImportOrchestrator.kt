package com.nitomash.bpmplayer.data.linkimport

import android.net.Uri
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.playlist.xspf.XspfImporter
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRootRepository
import com.nitomash.bpmplayer.domain.linkimport.DownloadedFileVerifier
import com.nitomash.bpmplayer.domain.linkimport.ImportCounters
import com.nitomash.bpmplayer.domain.linkimport.ImportDestination
import com.nitomash.bpmplayer.domain.linkimport.ImportEntry
import com.nitomash.bpmplayer.domain.linkimport.ImportFileKind
import com.nitomash.bpmplayer.domain.linkimport.ImportFolderName
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStatus
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStore
import com.nitomash.bpmplayer.domain.linkimport.ImportJobUpdate
import com.nitomash.bpmplayer.domain.linkimport.ImportLimits
import com.nitomash.bpmplayer.domain.linkimport.ImportOutcome
import com.nitomash.bpmplayer.domain.linkimport.ImportPlanBuilder
import com.nitomash.bpmplayer.domain.linkimport.ImportProgress
import com.nitomash.bpmplayer.domain.linkimport.PlannedFile
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProviderRegistry
import com.nitomash.bpmplayer.domain.linkimport.RelativeImportPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportOrchestrator @Inject constructor(
    private val roots: AppLibraryRootRepository,
    private val providers: PublicLinkProviderRegistry,
    private val planBuilder: ImportPlanBuilder,
    private val destination: ImportDestination,
    private val verifier: DownloadedFileVerifier,
    private val musicRepository: MusicRepository,
    private val xspfImporter: XspfImporter,
    private val jobStore: ImportJobStore,
    private val limits: ImportLimits,
) {

    suspend fun run(
        jobId: ImportJobId,
        link: String,
        targetFolderName: String?,
        targetDirectoryUri: String? = null,
        onProgress: suspend (ImportProgress) -> Unit,
    ): ImportOutcome {
        val root = roots.getRoot() ?: throw PublicLinkException.RootMissing
        val provider = providers.find(link) ?: throw PublicLinkException.UnsupportedLink
        ensureNotCancelled(jobId)

        jobStore.update(jobId, ImportJobUpdate(status = ImportJobStatus.PLANNING))
        val source = provider.inspect(link)
        val plan = planBuilder.build(source, limits)

        val targetDir = if (!targetDirectoryUri.isNullOrBlank()) {
            targetDirectoryUri
        } else {
            val folderName = ImportFolderName.resolve(targetFolderName, source)
            destination.findChild(root.uri, folderName)
                ?: destination.createDirectory(root.uri, folderName)
        }
        jobStore.update(
            jobId,
            ImportJobUpdate(
                status = ImportJobStatus.DOWNLOADING,
                sourceName = source.displayName,
                destinationUri = targetDir,
                totalFiles = plan.files.size,
                totalBytes = plan.knownTotalBytes,
                skippedFiles = plan.skippedFiles.size,
            ),
        )

        val directoryUris = mutableMapOf("" to targetDir)
        for (directory in plan.directories.sortedBy { it.relativePath.value.length }) {
            ensureNotCancelled(jobId)
            val path = directory.relativePath
            val parent = directoryUris[RelativeImportPath.parentOf(path).value] ?: targetDir
            val name = RelativeImportPath.nameOf(path)
            val created = destination.findChild(parent, name)
                ?: destination.createDirectory(parent, name)
            directoryUris[path.value] = created
        }

        val downloadedUris = mutableMapOf<String, String>()
        val entryCache = mutableMapOf<String, MutableMap<String, ImportEntry>>()
        suspend fun entriesOf(parent: String): MutableMap<String, ImportEntry> {
            entryCache[parent]?.let { return it }
            val entries = destination.listChildren(parent).associateBy { it.name }.toMutableMap()
            entryCache[parent] = entries
            return entries
        }

        var counters = ImportCounters()
        var lastCancelCheckMs = 0L

        for (file in plan.files) {
            ensureNotCancelled(jobId)
            val parent = directoryUris[RelativeImportPath.parentOf(file.relativePath).value] ?: targetDir
            val name = RelativeImportPath.nameOf(file.relativePath)
            val remoteSize = file.source.sizeBytes
            val present = entriesOf(parent)[name]
            val upToDate = present != null && remoteSize != null && present.size == remoteSize
            if (upToDate) {
                counters = counters.fileAlreadyPresent()
                downloadedUris[file.relativePath.value] = present!!.uri
                jobStore.update(
                    jobId,
                    ImportJobUpdate(
                        currentPath = file.relativePath.value,
                        downloadedFiles = counters.downloadedFiles,
                        skippedFiles = counters.totalSkipped(plan.skippedFiles.size),
                        upToDateFiles = counters.upToDateFiles,
                        downloadedBytes = counters.downloadedBytes,
                        totalFiles = plan.files.size,
                        totalBytes = plan.knownTotalBytes,
                    ),
                )
                onProgress(
                    ImportProgress(
                        currentPath = file.relativePath.value,
                        completedFiles = counters.processedFiles,
                        totalFiles = plan.files.size,
                        downloadedBytes = counters.downloadedBytes,
                        totalBytes = plan.knownTotalBytes,
                    ),
                )
                continue
            }
            val partName = "$name.${UUID.randomUUID()}.part"
            val part = destination.createFile(parent, partName, mimeOf(file))
            var finished = false
            try {
                destination.openOutput(part).use { output ->
                    provider.openDownload(file.source).use { download ->
                        download.copyTo(output) { bytes ->
                            val now = System.currentTimeMillis()
                            if (now - lastCancelCheckMs >= CANCEL_CHECK_INTERVAL_MS) {
                                lastCancelCheckMs = now
                                ensureNotCancelled(jobId)
                            }
                            onProgress(
                                ImportProgress(
                                    currentPath = file.relativePath.value,
                                    completedFiles = counters.processedFiles,
                                    totalFiles = plan.files.size,
                                    downloadedBytes = counters.downloadedBytes + bytes,
                                    totalBytes = plan.knownTotalBytes,
                                ),
                            )
                        }
                    }
                }
                if (!verifier.verify(part, file.kind)) {
                    counters = counters.fileRejected()
                    jobStore.update(
                        jobId,
                        ImportJobUpdate(skippedFiles = counters.totalSkipped(plan.skippedFiles.size)),
                    )
                    onProgress(
                        ImportProgress(
                            currentPath = file.relativePath.value,
                            completedFiles = counters.processedFiles,
                            totalFiles = plan.files.size,
                            downloadedBytes = counters.downloadedBytes,
                            totalBytes = plan.knownTotalBytes,
                        ),
                    )
                    continue
                }
                if (present != null && !destination.delete(present.uri)) {
                    throw PublicLinkException.Protocol("cannot replace existing file")
                }
                if (present != null) entriesOf(parent).remove(name)
                if (!destination.rename(part, name)) {
                    throw PublicLinkException.Protocol("cannot rename downloaded file")
                }
                finished = true
                downloadedUris[file.relativePath.value] = part
                entriesOf(parent)[name] = ImportEntry(name, part, file.source.sizeBytes)
                counters = counters.fileDownloaded(file.source.sizeBytes)
                jobStore.update(
                    jobId,
                    ImportJobUpdate(
                        currentPath = file.relativePath.value,
                        downloadedFiles = counters.downloadedFiles,
                        skippedFiles = counters.totalSkipped(plan.skippedFiles.size),
                        downloadedBytes = counters.downloadedBytes,
                        totalFiles = plan.files.size,
                        totalBytes = plan.knownTotalBytes,
                    ),
                )
            } finally {
                if (!finished) {
                    withContext(NonCancellable) { destination.delete(part) }
                }
            }
        }

        val totalSkipped = counters.totalSkipped(plan.skippedFiles.size)
        ensureNotCancelled(jobId)
        jobStore.update(jobId, ImportJobUpdate(status = ImportJobStatus.INDEXING))
        val scanResult = musicRepository.addFolder(Uri.parse(targetDir))
        musicRepository.addSourceLinks(scanResult.folderId, listOf(link))
        val importedPlaylists = importPlaylists(plan.files, downloadedUris)

        jobStore.update(
            jobId,
            ImportJobUpdate(
                status = ImportJobStatus.COMPLETED,
                destinationUri = targetDir,
                folderId = scanResult.folderId,
                downloadedFiles = counters.downloadedFiles,
                skippedFiles = totalSkipped,
                upToDateFiles = counters.upToDateFiles,
                clearLink = true,
            ),
        )
        return ImportOutcome.Success(
            folderId = scanResult.folderId,
            destinationUri = targetDir,
            downloadedFiles = counters.downloadedFiles,
            skippedFiles = totalSkipped,
            importedPlaylists = importedPlaylists,
            upToDateFiles = counters.upToDateFiles,
        )
    }

    private suspend fun ensureNotCancelled(jobId: ImportJobId) {
        currentCoroutineContext().ensureActive()
        if (jobStore.getStatus(jobId) == ImportJobStatus.CANCELLED) {
            throw CancellationException("import cancelled")
        }
    }

    private suspend fun importPlaylists(
        files: List<PlannedFile>,
        downloadedUris: Map<String, String>,
    ): Int {
        val playlists = files.filter { it.kind == ImportFileKind.PLAYLIST }
        if (playlists.isEmpty()) return 0
        val batch = xspfImporter.beginBatch()
        return playlists.count { file ->
            val uri = downloadedUris[file.relativePath.value] ?: return@count false
            runCatching { batch.import(Uri.parse(uri)).playlistId != null }.getOrDefault(false)
        }
    }

    private fun mimeOf(file: PlannedFile): String = when (file.kind) {
        ImportFileKind.AUDIO -> file.source.mimeType?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        ImportFileKind.SIDECAR -> "application/octet-stream"
        ImportFileKind.PLAYLIST -> "application/xspf+xml"
    }

    private companion object {
        const val CANCEL_CHECK_INTERVAL_MS = 500L
    }
}
