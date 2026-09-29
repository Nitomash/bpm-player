package com.nitomash.bpmplayer.ui.editor

enum class BatchTrackStatus { IDLE, DETECTING, DETECTED, FAILED }

fun resolveBatchStatus(
    trackId: Long,
    detectingIds: Set<Long>,
    failedIds: Set<Long>,
    pendingIds: Set<Long>,
): BatchTrackStatus = when {
    trackId in detectingIds -> BatchTrackStatus.DETECTING
    trackId in failedIds -> BatchTrackStatus.FAILED
    trackId in pendingIds -> BatchTrackStatus.DETECTED
    else -> BatchTrackStatus.IDLE
}
