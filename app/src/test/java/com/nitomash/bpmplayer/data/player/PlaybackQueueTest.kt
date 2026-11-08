package com.nitomash.bpmplayer.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueTest {

    private fun track(id: Long) = PlayerTrack(
        trackId = id,
        uri = "content://tree/document/$id",
        title = "Track $id",
    )

    private fun queue(vararg ids: Long, index: Int = 0) = PlaybackQueue(
        source = ids.map(::track),
        order = ids.map(::track),
        index = index,
    )

    @Test
    fun `empty queue always ends`() {
        val queue = PlaybackQueue.Empty

        assertEquals(QueueAdvance.EndOfQueue, queue.advance(RepeatMode.OFF))
        assertEquals(QueueAdvance.EndOfQueue, queue.advance(RepeatMode.ALL))
        assertEquals(QueueAdvance.EndOfQueue, queue.advance(RepeatMode.ONE))
    }

    @Test
    fun `middle of queue advances to the next track`() {
        val queue = queue(1, 2, 3, index = 1)

        assertEquals(QueueAdvance.To(2), queue.advance(RepeatMode.OFF))
        assertEquals(QueueAdvance.To(2), queue.advance(RepeatMode.ALL))
    }

    @Test
    fun `repeat one keeps the same track anywhere in the queue`() {
        assertEquals(QueueAdvance.To(0), queue(1, 2, 3, index = 0).advance(RepeatMode.ONE))
        assertEquals(QueueAdvance.To(1), queue(1, 2, 3, index = 1).advance(RepeatMode.ONE))
        assertEquals(QueueAdvance.To(2), queue(1, 2, 3, index = 2).advance(RepeatMode.ONE))
    }

    @Test
    fun `last track ends without repeat and restarts with repeat all`() {
        val queue = queue(1, 2, 3, index = 2)

        assertEquals(QueueAdvance.EndOfQueue, queue.advance(RepeatMode.OFF))
        assertEquals(QueueAdvance.RestartQueue, queue.advance(RepeatMode.ALL))
    }

    @Test
    fun `single track ends without repeat and restarts with repeat all`() {
        val queue = queue(1)

        assertEquals(QueueAdvance.EndOfQueue, queue.advance(RepeatMode.OFF))
        assertEquals(QueueAdvance.RestartQueue, queue.advance(RepeatMode.ALL))
        assertEquals(QueueAdvance.To(0), queue.advance(RepeatMode.ONE))
    }

    @Test
    fun `move to clamps the index inside the queue`() {
        val queue = queue(1, 2, 3, index = 1)

        assertEquals(2, queue.moveTo(99).index)
        assertEquals(0, queue.moveTo(-5).index)
        assertEquals(1, queue.moveTo(1).index)
    }

    @Test
    fun `move to on empty queue keeps index zero`() {
        assertEquals(0, PlaybackQueue.Empty.moveTo(5).index)
    }
}
