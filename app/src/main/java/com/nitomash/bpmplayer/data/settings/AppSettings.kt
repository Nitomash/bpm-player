package com.nitomash.bpmplayer.data.settings

import com.nitomash.bpmplayer.data.bpm.PcmBpmDetector
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort

data class AppSettings(
    val schemaVersion: Int = SCHEMA_VERSION,
    val detectorMinBpm: Double = PcmBpmDetector.DEFAULT_MIN_BPM,
    val detectorMaxBpm: Double = PcmBpmDetector.DEFAULT_MAX_BPM,
    val playlistListSort: PlaylistListSort = PlaylistListSort.DATE_MODIFIED,
    val playbackProcessingEnabled: Boolean = false,
    val playbackLimitSeconds: Int = 90,
    val playbackGapSeconds: Int = 10,
    val suppressBpmPromptAfterScan: Boolean = false,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val BPM_LIMIT_MIN = 20.0
        const val BPM_LIMIT_MAX = 300.0
        const val BPM_STEP = 1.0
        const val PLAYBACK_LIMIT_MAX = 3600
        const val PLAYBACK_GAP_MAX = 600
    }
}
