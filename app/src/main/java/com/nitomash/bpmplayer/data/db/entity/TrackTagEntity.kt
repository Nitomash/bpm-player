package com.nitomash.bpmplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_tags",
    primaryKeys = ["track_id", "tag_id"],
    indices = [
        Index(value = ["tag_id"]),
        Index(value = ["rating"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackTagEntity(
    @ColumnInfo(name = "track_id")
    val trackId: Long,
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
    val rating: Int,
)