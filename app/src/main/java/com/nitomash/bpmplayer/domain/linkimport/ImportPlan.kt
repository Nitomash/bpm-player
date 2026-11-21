package com.nitomash.bpmplayer.domain.linkimport

enum class ImportSourceKind {
    FILE,
    DIRECTORY,
}

enum class SkipReason {
    UNSUPPORTED_FORMAT,
    INVALID_NAME,
    SIZE_LIMIT,
}

data class PlannedDirectory(
    val relativePath: RelativeImportPath,
)

data class PlannedFile(
    val source: RemoteFile,
    val relativePath: RelativeImportPath,
    val kind: ImportFileKind,
)

data class SkippedRemoteFile(
    val relativePath: RelativeImportPath,
    val reason: SkipReason,
)

data class ImportPlan(
    val sourceName: String,
    val sourceKind: ImportSourceKind,
    val directories: List<PlannedDirectory>,
    val files: List<PlannedFile>,
    val skippedFiles: List<SkippedRemoteFile>,
) {
    val supportedFileCount: Int get() = files.size
    val unsupportedFileCount: Int get() = skippedFiles.size

    val knownTotalBytes: Long? get() =
        if (files.isNotEmpty() && files.all { it.source.sizeBytes != null }) {
            files.sumOf { it.source.sizeBytes ?: 0L }
        } else {
            null
        }
}
