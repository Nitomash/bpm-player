package com.nitomash.bpmplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualCountdownTest {

    @Test
    fun `rounds remaining time up to the next whole second`() {
        assertEquals(5L, countdownSeconds(5_000L))
        assertEquals(5L, countdownSeconds(4_001L))
        assertEquals(1L, countdownSeconds(1L))
    }

    @Test
    fun `keeps a visible final second for zero or negative input`() {
        assertEquals(1L, countdownSeconds(0L))
        assertEquals(1L, countdownSeconds(-500L))
    }
}
