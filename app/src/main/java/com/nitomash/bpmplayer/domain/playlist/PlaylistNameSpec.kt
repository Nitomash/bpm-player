package com.nitomash.bpmplayer.domain.playlist

data class PlaylistNameSpec(
    val minBpm: Double?,
    val maxBpm: Double?,
    val trackCount: Int?,
) {
    companion object {
        fun parse(minText: String, maxText: String, countText: String): PlaylistNameSpec =
            PlaylistNameSpec(
                minBpm = minText.trim().toDoubleOrNull(),
                maxBpm = maxText.trim().toDoubleOrNull(),
                trackCount = countText.trim().toIntOrNull()?.coerceAtLeast(1),
            )
    }
}