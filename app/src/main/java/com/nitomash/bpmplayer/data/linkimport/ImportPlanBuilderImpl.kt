package com.nitomash.bpmplayer.data.linkimport

import com.nitomash.bpmplayer.domain.linkimport.ImportFileKind
import com.nitomash.bpmplayer.domain.linkimport.ImportLimits
import com.nitomash.bpmplayer.domain.linkimport.ImportPlan
import com.nitomash.bpmplayer.domain.linkimport.ImportPlanBuilder
import com.nitomash.bpmplayer.domain.linkimport.ImportSourceKind
import com.nitomash.bpmplayer.domain.linkimport.PlannedDirectory
import com.nitomash.bpmplayer.domain.linkimport.PlannedFile
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProviderRegistry
import com.nitomash.bpmplayer.domain.linkimport.RemoteDirectory
import com.nitomash.bpmplayer.domain.linkimport.RemoteFile
import com.nitomash.bpmplayer.domain.linkimport.RemoteItem
import com.nitomash.bpmplayer.domain.linkimport.RelativeImportPath
import com.nitomash.bpmplayer.domain.linkimport.SkipReason
import com.nitomash.bpmplayer.domain.linkimport.SkippedRemoteFile
import com.nitomash.bpmplayer.domain.linkimport.SupportedImportFormats
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportPlanBuilderImpl @Inject constructor(
    private val providers: PublicLinkProviderRegistry,
) : ImportPlanBuilder {

    override suspend fun build(root: RemoteItem, limits: ImportLimits): ImportPlan {
        val directories = mutableListOf<PlannedDirectory>()
        val files = mutableListOf<PlannedFile>()
        val skipped = mutableListOf<SkippedRemoteFile>()
        val visited = mutableSetOf<String>()
        var entries = 0

        if (root is RemoteFile) {
            val path = RelativeImportPath.of(root.displayName)
            if (path == null) {
                skipped += SkippedRemoteFile(RelativeImportPath.ROOT, SkipReason.INVALID_NAME)
            } else {
                val kind = SupportedImportFormats.classify(root.displayName, root.mimeType)
                if (kind == null) {
                    skipped += SkippedRemoteFile(path, SkipReason.UNSUPPORTED_FORMAT)
                } else {
                    files += PlannedFile(root, path, kind)
                }
            }
            return ImportPlan(root.displayName, ImportSourceKind.FILE, directories, files, skipped)
        }

        val provider = providers.findById(root.providerId)
            ?: throw PublicLinkException.UnsupportedLink

        suspend fun walk(directory: RemoteDirectory, path: RelativeImportPath, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(directory.remoteId)) throw PublicLinkException.LimitExceeded("cycle")
            if (depth > limits.maxDepth) throw PublicLinkException.LimitExceeded("depth")

            var token: String? = null
            do {
                val page = provider.listChildren(directory, token)
                for (item in page.items) {
                    entries++
                    if (entries > limits.maxEntries) {
                        throw PublicLinkException.LimitExceeded("entries")
                    }
                    val childPath = RelativeImportPath.child(path, item.displayName)
                    if (childPath == null) {
                        skipped += SkippedRemoteFile(path, SkipReason.INVALID_NAME)
                        continue
                    }
                    when (item) {
                        is RemoteDirectory -> {
                            directories += PlannedDirectory(childPath)
                            walk(item, childPath, depth + 1)
                        }

                        is RemoteFile -> {
                            if (item.sizeBytes != null && item.sizeBytes > limits.maxKnownFileSizeBytes) {
                                skipped += SkippedRemoteFile(childPath, SkipReason.SIZE_LIMIT)
                                continue
                            }
                            val kind = SupportedImportFormats.classify(item.displayName, item.mimeType)
                            if (kind == null) {
                                skipped += SkippedRemoteFile(childPath, SkipReason.UNSUPPORTED_FORMAT)
                            } else {
                                files += PlannedFile(item, childPath, kind)
                            }
                        }
                    }
                }
                token = page.nextPageToken?.takeIf { it.isNotEmpty() }
            } while (token != null)
        }

        walk(root as RemoteDirectory, RelativeImportPath.ROOT, 0)
        return ImportPlan(root.displayName, ImportSourceKind.DIRECTORY, directories, files, skipped)
    }
}
