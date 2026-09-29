package com.nitomash.bpmplayer.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlushQueueTest {

    @Test
    fun `new queue has no work`() {
        assertFalse(FlushQueue().hasWork)
    }

    @Test
    fun `enqueue makes the queue non empty`() {
        val queue = FlushQueue()

        assertTrue(queue.enqueue(1L))
        assertTrue(queue.hasWork)
    }

    @Test
    fun `repeated enqueue of one folder is deduplicated`() {
        val queue = FlushQueue()

        assertTrue(queue.enqueue(1L))
        assertFalse(queue.enqueue(1L))
        assertFalse(queue.enqueue(1L))

        assertEquals(listOf(1L), queue.drain())
    }

    @Test
    fun `drain keeps insertion order`() {
        val queue = FlushQueue()
        listOf(3L, 1L, 2L, 1L).forEach(queue::enqueue)

        assertEquals(listOf(3L, 1L, 2L), queue.drain())
    }

    @Test
    fun `drain empties the queue`() {
        val queue = FlushQueue()
        queue.enqueue(1L)

        queue.drain()

        assertFalse(queue.hasWork)
        assertEquals(emptyList<Long>(), queue.drain())
    }

    @Test
    fun `request during a flush is not lost`() {
        val queue = FlushQueue()
        queue.enqueue(1L)

        assertEquals(listOf(1L), queue.drain())
        assertFalse(queue.hasWork)

        queue.enqueue(1L)

        assertTrue(queue.hasWork)
        assertEquals(listOf(1L), queue.drain())
    }

    @Test
    fun `request for another folder during a flush waits for its turn`() {
        val queue = FlushQueue()
        queue.enqueue(1L)
        queue.enqueue(2L)
        val batch = queue.drain()

        assertEquals(listOf(1L, 2L), batch)
        queue.enqueue(1L)

        assertEquals(listOf(1L), queue.drain())
    }

    @Test
    fun `concurrent enqueues keep every folder`() {
        val queue = FlushQueue()
        val workers = 8
        val perWorker = 200
        val threads = (0 until workers).map { worker ->
            Thread {
                repeat(perWorker) { index -> queue.enqueue((worker * perWorker + index).toLong()) }
            }
        }

        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        val drained = queue.drain()
        assertEquals(workers * perWorker, drained.size)
        assertEquals(workers * perWorker, drained.toSet().size)
        assertFalse(queue.hasWork)
    }
}
