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

    @Test
    fun `apply draft orders items by the draft sequence`() {
        val items = listOf(Item(1, "a"), Item(2, "b"), Item(3, "c"))

        assertEquals(
            listOf(3L, 1L, 2L),
            ListReorder.applyDraft(items, listOf(3L, 1L, 2L), Item::id).map { it.id },
        )
    }

    @Test
    fun `apply draft result does not depend on the incoming order`() {
        val draft = listOf(4L, 2L, 5L, 1L, 3L)
        val ordered = listOf(1L, 2L, 3L, 4L, 5L).map { Item(it, "t$it") }
        val reversed = ordered.reversed()
        val shuffled = listOf(3L, 1L, 5L, 2L, 4L).map { Item(it, "t$it") }

        val expected = draft
        assertEquals(expected, ListReorder.applyDraft(ordered, draft, Item::id).map { it.id })
        assertEquals(expected, ListReorder.applyDraft(reversed, draft, Item::id).map { it.id })
        assertEquals(expected, ListReorder.applyDraft(shuffled, draft, Item::id).map { it.id })
    }

    @Test
    fun `apply draft puts items missing from the draft last ordered by id`() {
        val items = listOf(Item(7, "g"), Item(2, "b"), Item(9, "i"), Item(3, "c"))

        assertEquals(
            listOf(3L, 2L, 7L, 9L),
            ListReorder.applyDraft(items, listOf(3L, 2L), Item::id).map { it.id },
        )
    }

    @Test
    fun `apply draft ignores unknown ids in the draft`() {
        val items = listOf(Item(1, "a"), Item(2, "b"))

        assertEquals(
            listOf(2L, 1L),
            ListReorder.applyDraft(items, listOf(99L, 2L, 1L), Item::id).map { it.id },
        )
    }

    @Test
    fun `apply draft with an empty draft orders by id`() {
        val items = listOf(Item(3, "c"), Item(1, "a"), Item(2, "b"))

        assertEquals(
            listOf(1L, 2L, 3L),
            ListReorder.applyDraft(items, emptyList(), Item::id).map { it.id },
        )
    }

    @Test
    fun `apply draft keeps every item exactly once`() {
        val items = (1L..40L).map { Item(it, "t$it") }
        val draft = ListReorder.moved(items.map { it.id }, from = 0, to = 39)

        val result = ListReorder.applyDraft(items, draft, Item::id)

        assertEquals(40, result.size)
        assertEquals(draft.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun `apply draft returns the same instance for zero and one item`() {
        val single = listOf(Item(1, "a"))

        assertTrue(ListReorder.applyDraft(emptyList(), listOf(1L), Item::id) === emptyList<Item>())
        assertTrue(ListReorder.applyDraft(single, listOf(1L), Item::id) === single)
    }

    @Test
    fun `apply draft reflects every intermediate drag step`() {
        val items = listOf(Item(1, "a"), Item(2, "b"), Item(3, "c"), Item(4, "d"), Item(5, "e"))
        var draft = items.map { it.id }

        draft = ListReorder.moved(draft, from = 0, to = 4)

        assertEquals(listOf(2L, 3L, 4L, 5L, 1L), ListReorder.applyDraft(items, draft, Item::id).map { it.id })
    }

    private data class Item(val id: Long, val title: String)
}
