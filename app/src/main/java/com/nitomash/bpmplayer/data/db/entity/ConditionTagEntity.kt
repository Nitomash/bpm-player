package com.nitomash.bpmplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "condition_tags",
    primaryKeys = ["condition_id", "tag_id"],
    indices = [Index(value = ["tag_id"])],
    foreignKeys = [
        ForeignKey(
            entity = ConditionEntity::class,
            parentColumns = ["id"],
            childColumns = ["condition_id"],
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
data class ConditionTagEntity(
    @ColumnInfo(name = "condition_id")
    val conditionId: Long,
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
    @ColumnInfo(name = "rating_min")
    val ratingMin: Int?,
    @ColumnInfo(name = "rating_max")
    val ratingMax: Int?,
)