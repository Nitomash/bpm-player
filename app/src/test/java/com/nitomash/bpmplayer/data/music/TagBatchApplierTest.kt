package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.dao.FakeTagDao
import com.nitomash.bpmplayer.data.db.dao.FakeTrackTagDao
import com.nitomash.bpmplayer.data.db.entity.TagEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagBatchApplierTest {

    private val tagDao = FakeTagDao()
    private val trackTagDao = FakeTrackTagDao().apply {
        nameOf = { id -> tagDao.all().first { it.id == id }.name }
    }
    private val applier = TagBatchApplier(tagDao, trackTagDao)

    @Test
    fun `200 patches insert every tag with its rating and drop the old ones`() = runBlocking {
        val genreIds = seedTags((0 until 7).map { "genre$it" })
        (0 until 200).forEach { trackId ->
            genreIds.values.forEach { id -> trackTagDao.put(trackId.toLong(), id, 1) }
        }
        val tagged = (0 until 200).map { trackId ->
            TaggedTrackTags(
                trackId = trackId.toLong(),
                tags = listOf(
                    SidecarTag("genre${trackId % 7}", 3),
                    SidecarTag("mood${trackId % 5}", 5),
                ),
            )
        }

        val inserted = applier.apply(FOLDER_ID, tagged)

        assertEquals(400, inserted)
        assertEquals(12, tagDao.all().size)
        tagged.forEach { patch ->
            val actual = patch.tags.associate { tag ->
                val id = requireNotNull(tagDao.idOf(tag.name))
                tag.name to trackTagDao.ratingOf(patch.trackId, id)
            }
            assertEquals(patch.tags.associate { it.name to it.rating }, actual)
            assertEquals(2, trackTagDao.tagIdsOf(patch.trackId).size)
        }
    }

    @Test
    fun `batch apply issues a single delete and a single insert`() = runBlocking {
        val tagged = (0 until 200).map { trackId ->
            TaggedTrackTags(trackId.toLong(), listOf(SidecarTag("rock", 3)))
        }

        applier.apply(FOLDER_ID, tagged)

        assertEquals(1, trackTagDao.getByFolderCalls)
        assertEquals(1, trackTagDao.deleteChunkCalls)
        assertEquals(1, trackTagDao.insertAllCalls)
        assertEquals(1, tagDao.insertAllCalls)
    }

    @Test
    fun `existing tags are reused instead of reinserted`() = runBlocking {
        applier.apply(FOLDER_ID, listOf(TaggedTrackTags(1L, listOf(SidecarTag("rock", 4)))))
        val rockId = requireNotNull(tagDao.idOf("rock"))
        val insertCallsAfterFirst = tagDao.insertAllCalls

        applier.apply(FOLDER_ID, listOf(TaggedTrackTags(2L, listOf(SidecarTag("rock", 2)))))

        assertEquals(1, tagDao.all().size)
        assertEquals(insertCallsAfterFirst, tagDao.insertAllCalls)
        assertEquals(4, trackTagDao.ratingOf(1L, rockId))
        assertEquals(2, trackTagDao.ratingOf(2L, rockId))
    }

    @Test
    fun `unchanged track is not rewritten`() = runBlocking {
        applier.apply(FOLDER_ID, listOf(TaggedTrackTags(1L, listOf(SidecarTag("rock", 3)))))
        val rockId = requireNotNull(tagDao.idOf("rock"))
        val deleteCallsAfterFirst = trackTagDao.deleteChunkCalls

        val inserted = applier.apply(FOLDER_ID, listOf(TaggedTrackTags(1L, listOf(SidecarTag("rock", 3)))))

        assertEquals(0, inserted)
        assertEquals(deleteCallsAfterFirst, trackTagDao.deleteChunkCalls)
        assertEquals(3, trackTagDao.ratingOf(1L, rockId))
    }

    @Test
    fun `empty tag list clears the track`() = runBlocking {
        applier.apply(FOLDER_ID, listOf(TaggedTrackTags(1L, listOf(SidecarTag("rock", 3)))))
        val rockId = requireNotNull(tagDao.idOf("rock"))

        val inserted = applier.apply(FOLDER_ID, listOf(TaggedTrackTags(1L, emptyList())))

        assertEquals(0, inserted)
        assertNull(trackTagDao.ratingOf(1L, rockId))
    }

    @Test
    fun `tags filtered by planner never reach the database`() = runBlocking {
        val inserted = applier.apply(
            FOLDER_ID,
            listOf(
                TaggedTrackTags(
                    1L,
                    listOf(
                        SidecarTag("rock", 3),
                        SidecarTag("", 3),
                        SidecarTag("house", 7),
                        SidecarTag("rock", 1),
                    ),
                ),
            ),
        )

        assertEquals(1, inserted)
        assertEquals(listOf("rock"), tagDao.all().map { it.name })
        assertEquals(3, trackTagDao.ratingOf(1L, requireNotNull(tagDao.idOf("rock"))))
    }

    @Test
    fun `empty input touches nothing`() = runBlocking {
        val inserted = applier.apply(FOLDER_ID, emptyList())

        assertEquals(0, inserted)
        assertTrue(trackTagDao.rows.isEmpty())
        assertEquals(0, trackTagDao.getByFolderCalls)
    }

    @Test
    fun `many tracks and tags cross both chunk boundaries`() = runBlocking {
        val tagged = (0 until 600).map { trackId ->
            TaggedTrackTags(
                trackId = trackId.toLong(),
                tags = listOf(SidecarTag("t$trackId", 3), SidecarTag("shared", 1)),
            )
        }

        val inserted = applier.apply(FOLDER_ID, tagged)

        assertEquals(1200, inserted)
        assertEquals(2, tagDao.insertAllCalls)
        assertTrue(trackTagDao.insertAllCalls > 1)
        assertEquals(601, tagDao.all().size)
        assertEquals(1200, trackTagDao.rows.size)
        (0 until 600).forEach { trackId ->
            assertEquals(2, trackTagDao.tagIdsOf(trackId.toLong()).size)
        }
    }

    private suspend fun seedTags(names: List<String>): Map<String, Long> {
        tagDao.insertAll(names.map { TagEntity(name = it) })
        return names.associateWith { requireNotNull(tagDao.idOf(it)) }
    }

    private companion object {
        const val FOLDER_ID = 1L
    }
}
