package com.nitomash.bpmplayer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListReorderTest {

    @Test
    fun `moved shifts the item to the requested index`() {
        assertEquals(listOf(1L, 3L, 2L, 4L), ListReorder.moved(listOf(1L, 2L, 3L, 4L), from = 1, to = 2))
    }

    @Test
    fun `moved up shifts the item backwards`() {
        assertEquals(listOf(1L, 4L, 2L, 3L), ListReorder.moved(listOf(1L, 2L, 3L, 4L), from = 3, to = 1))
    }

    @Test
    fun `moved to the same index keeps the same instance`() {
        val list = listOf(1L, 2L, 3L)

        assertTrue(list === ListReorder.moved(list, from = 1, to = 1))
    }

    @Test
    fun `moved ignores an out of bounds index`() {
        val list = listOf(1L, 2L, 3L)

        assertEquals(list, ListReorder.moved(list, from = 0, to = 7))
        assertEquals(list, ListReorder.moved(list, from = -1, to = 0))
    }

    @Test
    fun `moved keeps every element exactly once`() {
        val list = ListReorder.moved(listOf(1L, 2L, 3L, 4L, 5L), from = 0, to = 4)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), list.sorted())
        assertEquals(5, list.size)
    }

    @Test
    fun `dragging one item down slot by slot reaches the end`() {
        var list = listOf(1L, 2L, 3L, 4L, 5L)
        var from = 0

        repeat(4) {
            list = ListReorder.moved(list, from = from, to = from + 1)
            from += 1
        }

        assertEquals(listOf(2L, 3L, 4L, 5L, 1L), list)
    }
}
