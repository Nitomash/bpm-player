package com.nitomash.bpmplayer.data.playlist

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.nitomash.bpmplayer.data.db.entity.TrackEntity

data class PlaylistTrackRow(
    @Embedded
    val track: TrackEntity,
    val position: Int,
    @ColumnInfo(name = "added_to_playlist_at_ms")
    val addedToPlaylistAtMs: Long,
)
