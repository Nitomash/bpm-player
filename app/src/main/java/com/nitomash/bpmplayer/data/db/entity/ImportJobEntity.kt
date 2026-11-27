package com.nitomash.bpmplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_jobs",
    indices = [
        Index(value = ["status"]),
        Index(value = ["created_at_ms"]),
    ],
)
data class ImportJobEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "source_link")
    val sourceLink: String?,
    @ColumnInfo(name = "provider_id")
    val providerId: String?,
    @ColumnInfo(name = "target_folder_name")
    val targetFolderName: String?,
    @ColumnInfo(name = "destination_uri")
    val destinationUri: String?,
    @ColumnInfo(name = "folder_id")
    val folderId: Long,
    val status: String,
    @ColumnInfo(name = "source_name")
    val sourceName: String?,
    @ColumnInfo(name = "current_path")
    val currentPath: String?,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
    @ColumnInfo(name = "downloaded_files")
    val downloadedFiles: Int,
    @ColumnInfo(name = "skipped_files")
    val skippedFiles: Int,
    @ColumnInfo(name = "up_to_date_files")
    val upToDateFiles: Int,
    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long,
    @ColumnInfo(name = "total_files")
    val totalFiles: Int,
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long?,
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
)
