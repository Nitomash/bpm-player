package com.nitomash.bpmplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort

@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["name"]),
        Index(value = ["condition_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ConditionEntity::class,
            parentColumns = ["id"],
            childColumns = ["condition_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "condition_id")
    val conditionId: Long?,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
    @ColumnInfo(name = "track_sort_mode", defaultValue = "0")
    val trackSortMode: PlaylistTrackSort = PlaylistTrackSort.CUSTOM,
)