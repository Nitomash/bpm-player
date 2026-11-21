package com.nitomash.bpmplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nitomash.bpmplayer.data.db.entity.ImportJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportJobDao {

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    suspend fun get(id: String): ImportJobEntity?

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    fun observe(id: String): Flow<ImportJobEntity?>

    @Query("SELECT * FROM import_jobs ORDER BY created_at_ms DESC LIMIT 1")
    suspend fun latest(): ImportJobEntity?

    @Query(
        "SELECT * FROM import_jobs WHERE status IN ('QUEUED', 'PLANNING', 'DOWNLOADING', 'INDEXING') " +
            "ORDER BY created_at_ms DESC LIMIT 1",
    )
    suspend fun active(): ImportJobEntity?

    @Upsert
    suspend fun upsert(entity: ImportJobEntity)
}
