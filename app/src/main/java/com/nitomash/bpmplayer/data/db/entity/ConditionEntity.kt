package com.nitomash.bpmplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conditions", indices = [Index(value = ["name"])])
data class ConditionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "bpm_min")
    val bpmMin: Double?,
    @ColumnInfo(name = "bpm_max")
    val bpmMax: Double?,
    @ColumnInfo(name = "order_by")
    val orderBy: String,
    @ColumnInfo(name = "weights_json")
    val weightsJson: String?,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
)