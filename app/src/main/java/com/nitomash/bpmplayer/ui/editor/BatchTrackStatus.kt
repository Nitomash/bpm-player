package com.nitomash.bpmplayer.ui.editor

enum class BatchTrackStatus { IDLE, DETECTING, DETECTED, FAILED }

fun resolveBatchStatus(
    trackId: Long,
    currentDetectingId: Long?,
    failedIds: Set<Long>,
    pendingIds: Set<Long>,
): BatchTrackStatus = when {
    trackId == currentDetectingId -> BatchTrackStatus.DETECTING
    trackId in failedIds -> BatchTrackStatus.FAILED
    trackId in pendingIds -> BatchTrackStatus.DETECTED
    else -> BatchTrackStatus.IDLE
}
