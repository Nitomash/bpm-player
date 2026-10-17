package com.nitomash.bpmplayer.data.player

import com.nitomash.bpmplayer.data.settings.AppSettings

data class PlaybackLimits(
    val enabled: Boolean = true,
    val limitSeconds: Int = 0,
    val gapSeconds: Int = 0,
)

fun AppSettings.toPlaybackLimits(): PlaybackLimits = PlaybackLimits(
    enabled = playbackProcessingEnabled,
    limitSeconds = playbackLimitSeconds,
    gapSeconds = playbackGapSeconds,
)

fun shouldClipNow(
    limits: PlaybackLimits,
    positionMs: Long,
    durationMs: Long,
    isPlaylistMode: Boolean,
    gapActive: Boolean,
    stopOnTrackEnd: Boolean,
    isPlaying: Boolean,
): Boolean {
    if (!limits.enabled || limits.limitSeconds <= 0) return false
    if (!isPlaylistMode || gapActive || stopOnTrackEnd || !isPlaying) return false
    val limitMs = limits.limitSeconds.toLong() * SECOND_MS
    if (durationMs in 1L until limitMs) return false
    return positionMs >= limitMs
}

fun shouldStartGap(
    limits: PlaybackLimits,
    isPlaylistMode: Boolean,
    stopOnTrackEnd: Boolean,
): Boolean {
    if (!limits.enabled) return false
    if (limits.gapSeconds <= 0) return false
    if (!isPlaylistMode) return false
    if (stopOnTrackEnd) return false
    return true
}

fun shouldAdvanceToNextItem(
    hasNext: Boolean,
    positionMs: Long,
    durationMs: Long,
): Boolean {
    if (!hasNext) return false
    if (durationMs <= 0L) return false
    return positionMs >= durationMs - TRACK_END_TOLERANCE_MS
}

fun clipFadeVolume(
    limits: PlaybackLimits,
    positionMs: Long,
    durationMs: Long,
    isPlaylistMode: Boolean,
    stopOnTrackEnd: Boolean,
): Float? {
    if (!limits.enabled || limits.limitSeconds <= 0) return null
    if (!isPlaylistMode || stopOnTrackEnd) return null
    val limitMs = limits.limitSeconds.toLong() * SECOND_MS
    if (durationMs in 1L until limitMs) return null
    val remainingMs = limitMs - positionMs
    if (remainingMs > FADE_OUT_MS) return null
    if (remainingMs <= 0L) return 0f
    return remainingMs / FADE_OUT_MS.toFloat()
}

private const val SECOND_MS = 1000L
private const val FADE_OUT_MS = 2_000L
private const val TRACK_END_TOLERANCE_MS = 1_000L
