package com.nitomash.bpmplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["folder_id", "relative_path"], unique = true),
        Index(value = ["access_uri"], unique = true),
        Index(value = ["bpm"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "folder_id")
    val folderId: Long,
    @ColumnInfo(name = "access_uri")
    val accessUri: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    val bpm: Double?,
    @ColumnInfo(name = "bpm_source")
    val bpmSource: BpmSource? = null,
    @ColumnInfo(name = "added_at_ms")
    val addedAtMs: Long,
)