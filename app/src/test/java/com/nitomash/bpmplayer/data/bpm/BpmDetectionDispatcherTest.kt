package com.nitomash.bpmplayer.data.bpm

import org.junit.Assert.assertEquals
import org.junit.Test

class BpmDetectionDispatcherTest {

    @Test
    fun `single core still gets one slot`() {
        assertEquals(1, BpmDetectionDispatcher.permitsFor(1))
    }

    @Test
    fun `two cores give one slot`() {
        assertEquals(1, BpmDetectionDispatcher.permitsFor(2))
    }

    @Test
    fun `four cores give two slots`() {
        assertEquals(2, BpmDetectionDispatcher.permitsFor(4))
    }

    @Test
    fun `eight cores give four slots`() {
        assertEquals(4, BpmDetectionDispatcher.permitsFor(8))
    }

    @Test
    fun `many cores scale without a ceiling`() {
        assertEquals(8, BpmDetectionDispatcher.permitsFor(16))
    }

    @Test
    fun `zero cores does not disable detection`() {
        assertEquals(1, BpmDetectionDispatcher.permitsFor(0))
    }
}
