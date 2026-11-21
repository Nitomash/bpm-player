package com.nitomash.bpmplayer.data.linkimport

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStatus
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStore
import com.nitomash.bpmplayer.domain.linkimport.ImportJobUpdate
import com.nitomash.bpmplayer.domain.linkimport.ImportOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: ImportOrchestrator,
    private val jobStore: ImportJobStore,
    private val notifier: ImportNotifier,
    private val musicRepository: MusicRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID)?.let(::ImportJobId)
            ?: return Result.failure()
        val link = jobStore.getLink(jobId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "missing link"))
        val folderName = inputData.getString(KEY_FOLDER_NAME)
        val targetDir = inputData.getString(KEY_TARGET_DIR)

        var lastNotificationMs = 0L
        var lastProgressMs = 0L
        return try {
            setForeground(
                notifier.foregroundInfo(
                    jobId = jobId,
                    title = context.getString(R.string.import_notification_title),
                    text = context.getString(R.string.import_notification_preparing),
                    progress = 0,
                    max = 0,
                    indeterminate = true,
                ),
            )
            val outcome = orchestrator.run(jobId, link, folderName, targetDir) { progress ->
                val now = System.currentTimeMillis()
                if (now - lastProgressMs >= PROGRESS_INTERVAL_MS) {
                    lastProgressMs = now
                    setProgress(
                        workDataOf(
                            KEY_CURRENT_PATH to progress.currentPath,
                            KEY_COMPLETED to progress.completedFiles,
                            KEY_TOTAL to progress.totalFiles,
                        ),
                    )
                }
                if (now - lastNotificationMs >= NOTIFICATION_INTERVAL_MS) {
                    lastNotificationMs = now
                    setForeground(
                        notifier.foregroundInfo(
                            jobId = jobId,
                            title = context.getString(R.string.import_notification_title),
                            text = progress.currentPath,
                            progress = progress.completedFiles,
                            max = progress.totalFiles,
                            indeterminate = false,
                        ),
                    )
                }
            }
            when (outcome) {
                is ImportOutcome.Success -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.import_complete, Toast.LENGTH_SHORT).show()
                    }
                    Result.success(
                        workDataOf(
                            KEY_FOLDER_ID to outcome.folderId,
                            KEY_DOWNLOADED to outcome.downloadedFiles,
                            KEY_PLAYLISTS to outcome.importedPlaylists,
                        ),
                    )
                }

                ImportOutcome.Cancelled -> Result.failure()
                is ImportOutcome.Failed -> Result.failure(
                    workDataOf(KEY_ERROR to (outcome.error.message ?: "unknown error")),
                )
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val snapshot = jobStore.get(jobId)
                if (snapshot != null && snapshot.downloadedFiles > 0 &&
                    snapshot.destinationUri != null
                ) {
                    val folder = musicRepository.addFolder(Uri.parse(snapshot.destinationUri))
                    musicRepository.addSourceLinks(folder.folderId, listOf(link))
                }
                jobStore.update(
                    jobId,
                    ImportJobUpdate(status = ImportJobStatus.CANCELLED, clearLink = true),
                )
            }
            throw e
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                jobStore.update(
                    jobId,
                    ImportJobUpdate(
                        status = ImportJobStatus.FAILED,
                        errorMessage = e.message,
                        clearLink = true,
                    ),
                )
            }
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "unknown error")))
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_TARGET_DIR = "target_dir"
        const val KEY_ERROR = "error"
        const val KEY_CURRENT_PATH = "current_path"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_PLAYLISTS = "playlists"

        private const val NOTIFICATION_INTERVAL_MS = 1_000L
        private const val PROGRESS_INTERVAL_MS = 1_000L
    }
}
