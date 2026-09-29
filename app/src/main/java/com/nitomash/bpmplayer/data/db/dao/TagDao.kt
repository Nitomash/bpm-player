package com.nitomash.bpmplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nitomash.bpmplayer.data.db.entity.TagEntity

@Dao
interface TagDao {

    @Query("SELECT * FROM tags")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name IN (:names)")
    suspend fun getByNamesChunk(names: List<String>): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>): List<Long>

    @Transaction
    suspend fun getByNames(names: List<String>): List<TagEntity> =
        names.sqlChunks().flatMap { getByNamesChunk(it) }

    @Transaction
    suspend fun resolveIds(names: List<String>): Map<String, Long> {
        val distinct = names.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val known = getAll().associate { it.name to it.id }
        val missing = distinct.filterNot { it in known }
        if (missing.isEmpty()) return known
        missing.sqlChunks().forEach { chunk -> insertAll(chunk.map { TagEntity(name = it) }) }
        return known + getByNames(missing).associate { it.name to it.id }
    }
}
