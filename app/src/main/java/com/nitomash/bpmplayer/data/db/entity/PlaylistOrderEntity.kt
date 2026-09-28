package com.nitomash.bpmplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_order",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    val position: Int,
)
