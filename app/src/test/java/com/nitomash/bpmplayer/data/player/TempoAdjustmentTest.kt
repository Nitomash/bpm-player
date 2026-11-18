package com.nitomash.bpmplayer.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TempoAdjustmentTest {

    @Test
    fun `zero position keeps original speed`() {
        assertEquals(1f, TempoAdjustment.speed(128.0, 0f), 0.0001f)
        assertEquals(1f, TempoAdjustment.speed(null, 0f), 0.0001f)
    }

    @Test
    fun `known bpm uses fifteen bpm range`() {
        assertEquals(145f / 130f, TempoAdjustment.speed(130.0, 1f), 0.0001f)
        assertEquals(115f / 130f, TempoAdjustment.speed(130.0, -1f), 0.0001f)
    }

    @Test
    fun `unknown bpm uses ten percent range`() {
        assertEquals(1.1f, TempoAdjustment.speed(null, 1f), 0.0001f)
        assertEquals(0.9f, TempoAdjustment.speed(null, -1f), 0.0001f)
    }

    @Test
    fun `position is constrained to slider range`() {
        assertEquals(1.1f, TempoAdjustment.speed(null, 2f), 0.0001f)
        assertEquals(0.9f, TempoAdjustment.speed(null, -2f), 0.0001f)
    }

    @Test
    fun `invalid bpm falls back to percentage adjustment`() {
        assertEquals(1.1f, TempoAdjustment.speed(0.0, 1f), 0.0001f)
        assertEquals(0.9f, TempoAdjustment.speed(Double.NaN, -1f), 0.0001f)
    }
}
