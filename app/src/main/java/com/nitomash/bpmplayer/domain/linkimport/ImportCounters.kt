package com.nitomash.bpmplayer.domain.linkimport

data class ImportCounters(
    val downloadedFiles: Int = 0,
    val invalidFiles: Int = 0,
    val upToDateFiles: Int = 0,
    val duplicateNameFiles: Int = 0,
    val downloadedBytes: Long = 0L,
) {

    val processedFiles: Int get() = downloadedFiles + invalidFiles + upToDateFiles

    fun fileDownloaded(sizeBytes: Long?): ImportCounters = copy(
        downloadedFiles = downloadedFiles + 1,
        downloadedBytes = downloadedBytes + (sizeBytes ?: 0L),
    )

    fun fileRejected(): ImportCounters = copy(invalidFiles = invalidFiles + 1)

    fun fileAlreadyPresent(): ImportCounters = copy(upToDateFiles = upToDateFiles + 1)

    fun duplicatesFound(count: Int): ImportCounters =
        copy(duplicateNameFiles = duplicateNameFiles + count)

    fun totalSkipped(planSkipped: Int): Int = planSkipped + invalidFiles
}
