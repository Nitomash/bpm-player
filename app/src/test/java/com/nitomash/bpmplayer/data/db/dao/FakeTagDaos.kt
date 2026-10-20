package com.nitomash.bpmplayer.data.db.dao

import com.nitomash.bpmplayer.data.db.entity.TagEntity
import com.nitomash.bpmplayer.data.db.entity.TrackTagEntity

class FakeTagDao : TagDao {

    private val tags = mutableListOf<TagEntity>()
    private var nextId = 1L

    var getAllCalls = 0
        private set
    var insertAllCalls = 0
        private set

    fun all(): List<TagEntity> = tags.toList()

    fun idOf(name: String): Long? = tags.firstOrNull { it.name == name }?.id

    override suspend fun getAll(): List<TagEntity> {
        getAllCalls++
        return tags.toList()
    }

    override suspend fun getByNamesChunk(names: List<String>): List<TagEntity> =
        tags.filter { it.name in names }

    override suspend fun insertAll(tags: List<TagEntity>): List<Long> {
        insertAllCalls++
        return tags.map { tag ->
            if (this.tags.any { it.name == tag.name }) {
                -1L
            } else {
                val id = nextId++
                this.tags += tag.copy(id = id)
                id
            }
        }
    }
}

class FakeTrackTagDao : TrackTagDao {

    val rows = mutableListOf<TrackTagEntity>()

    var nameOf: (Long) -> String = { "" }

    var deleteChunkCalls = 0
        private set
    var insertAllCalls = 0
        private set
    var getByFolderCalls = 0
        private set

    fun put(trackId: Long, tagId: Long, rating: Int) {
        rows += TrackTagEntity(trackId = trackId, tagId = tagId, rating = rating)
    }

    fun tagIdsOf(trackId: Long): List<Long> =
        rows.filter { it.trackId == trackId }.map { it.tagId }.sorted()

    fun ratingOf(trackId: Long, tagId: Long): Int? =
        rows.firstOrNull { it.trackId == trackId && it.tagId == tagId }?.rating

    override suspend fun insertAll(items: List<TrackTagEntity>) {
        insertAllCalls++
        items.forEach { item ->
            if (rows.none { it.trackId == item.trackId && it.tagId == item.tagId }) rows += item
        }
    }

    override suspend fun deleteForTracksChunk(trackIds: List<Long>) {
        deleteChunkCalls++
        rows.removeAll { it.trackId in trackIds }
    }

    override suspend fun getByFolder(folderId: Long): List<TrackTagRow> {
        getByFolderCalls++
        return rows.map { row -> TrackTagRow(row.trackId, nameOf(row.tagId), row.rating) }
    }

    override suspend fun getByTrackIdsChunk(trackIds: List<Long>): List<TrackTagRow> =
        rows.filter { it.trackId in trackIds }
            .map { row -> TrackTagRow(row.trackId, nameOf(row.tagId), row.rating) }
}
