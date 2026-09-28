package com.nitomash.bpmplayer.data.playlist

import androidx.room.ColumnInfo

data class PlaylistSummary(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "track_count")
    val trackCount: Int,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
    @ColumnInfo(name = "sort_position")
    val sortPosition: Int,
)
