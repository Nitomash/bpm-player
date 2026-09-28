package com.nitomash.bpmplayer.data.db.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlChunksTest {

    @Test
    fun `empty list yields no chunks`() {
        assertEquals(emptyList<List<Int>>(), emptyList<Int>().sqlChunks())
    }

    @Test
    fun `a short list stays in one chunk`() {
        val chunks = listOf(1, 2, 3).sqlChunks()

        assertEquals(1, chunks.size)
        assertEquals(listOf(1, 2, 3), chunks.single())
    }

    @Test
    fun `no chunk exceeds the sql variable limit`() {
        val counts = listOf(1, 499, 500, 501, 999, 1000, 1001, 2500)

        counts.forEach { count ->
            val chunks = List(count) { it }.sqlChunks()
            assertTrue(
                "count=$count sizes=${chunks.map { it.size }}",
                chunks.all { it.size in 1..SQL_CHUNK_SIZE },
            )
        }
    }

    @Test
    fun `chunks stay contiguous and cover every element exactly once`() {
        val source = List(1234) { it * 3 }

        assertEquals(source, source.sqlChunks().flatten())
    }

    @Test
    fun `a list at the limit is one chunk and one past the limit is two`() {
        assertEquals(1, List(SQL_CHUNK_SIZE) { it }.sqlChunks().size)
        assertEquals(2, List(SQL_CHUNK_SIZE + 1) { it }.sqlChunks().size)
    }

    @Test
    fun `stale playlist ids keeps only what is missing from keep`() {
        assertEquals(
            listOf(4L, 7L),
            stalePlaylistIds(listOf(1L, 2L, 4L, 7L), listOf(1L, 2L)),
        )
    }

    @Test
    fun `stale playlist ids returns nothing when everything is kept`() {
        assertEquals(emptyList<Long>(), stalePlaylistIds(listOf(1L, 2L), listOf(1L, 2L, 3L)))
    }

    @Test
    fun `stale playlist ids with empty keep returns every existing id`() {
        assertEquals(listOf(1L, 2L), stalePlaylistIds(listOf(1L, 2L), emptyList()))
    }

    @Test
    fun `stale playlist ids stays correct past the sql variable limit`() {
        val existing = (1L..1200L).toList()
        val keep = (1L..700L).toList()

        val stale = stalePlaylistIds(existing, keep)

        assertEquals((701L..1200L).toList(), stale)
        assertTrue(stale.sqlChunks().all { it.size <= SQL_CHUNK_SIZE })
        assertEquals(existing.toSet(), (keep + stale).toSet())
    }

    @Test
    fun `deleting stale ids chunk by chunk never removes a kept id`() {
        val existing = (1L..1200L).toList()
        val keep = (1L..600L).toList()
        val remaining = existing.toMutableList()

        stalePlaylistIds(existing, keep).sqlChunks().forEach { chunk ->
            remaining.removeAll { it in chunk }
        }

        assertEquals(keep, remaining)
    }
}
