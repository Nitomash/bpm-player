package com.nitomash.bpmplayer.data.playlist.export

data class XspfExportedFile(
    val playlistId: Long,
    val playlistName: String,
    val fileName: String?,
    val trackCount: Int,
    val relativeCount: Int,
    val absoluteCount: Int,
    val failed: Boolean,
)

data class XspfExportResult(
    val files: List<XspfExportedFile>,
    val baseResolved: Boolean,
) {
    val succeededCount: Int
        get() = files.count { !it.failed }

    val failedCount: Int
        get() = files.count { it.failed }

    val absoluteTrackCount: Int
        get() = files.sumOf { it.absoluteCount }
}