package com.nitomash.bpmplayer.ui.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class BatchWorkQueueTest {

    @Test
    fun `poll hands every item to exactly one worker`() {
        val taken = runBlocking { drainConcurrently(ITEMS, WORKERS) }

        assertEquals("каждый элемент должен быть обработан ровно один раз", ITEMS.size, taken.size)
        assertEquals("дубликатов быть не должно", ITEMS.size, taken.toSet().size)
    }

    @Test
    fun `poll covers every item when workers outnumber items`() {
        val items = listOf(1, 2)
        val taken = runBlocking { drainConcurrently(items, WORKERS) }

        assertEquals(items.size, taken.size)
        assertEquals(items.toSet(), taken.toSet())
    }

    @Test
    fun `poll covers every item when items outnumber workers`() {
        val taken = runBlocking { drainConcurrently(ITEMS, 2) }

        assertEquals(ITEMS.size, taken.size)
        assertEquals(ITEMS.toSet(), taken.toSet())
    }

    @Test
    fun `empty queue leaves workers idle`() {
        val taken = runBlocking { drainConcurrently(emptyList<Int>(), WORKERS) }

        assertTrue(taken.isEmpty())
    }

    @Test
    fun `iterating the shared queue does not split the work`() {
        val queue = ConcurrentLinkedQueue(ITEMS)
        val perWorker = runBlocking {
            coroutineScope {
                List(WORKERS) { async(Dispatchers.Default) { queue.count() } }.awaitAll()
            }
        }

        assertEquals(
            "итерация не разделяет элементы, поэтому в батче используется poll()",
            ITEMS.size * WORKERS,
            perWorker.sum(),
        )
        assertTrue(
            "каждый итератор воркера увидел весь список целиком",
            perWorker.all { it == ITEMS.size },
        )
    }

    private suspend fun <T> drainConcurrently(items: List<T>, workers: Int): List<T> {
        val queue = ConcurrentLinkedQueue(items)
        return coroutineScope {
            val guard = Mutex()
            val taken = mutableListOf<T>()
            List(workers) {
                async(Dispatchers.Default) {
                    while (true) {
                        val item = queue.poll() ?: break
                        guard.withLock { taken += item }
                    }
                }
            }.awaitAll()
            guard.withLock { taken.toList() }
        }
    }

    private companion object {
        val ITEMS = (1..60).toList()
        const val WORKERS = 3
    }
}
