package com.nitomash.bpmplayer.domain.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistNameSpecTest {

    @Test
    fun `parses both bounds and count`() {
        val spec = PlaylistNameSpec.parse("90", "110", "20")

        assertEquals(90.0, spec.minBpm!!, 0.0)
        assertEquals(110.0, spec.maxBpm!!, 0.0)
        assertEquals(20, spec.trackCount)
    }

    @Test
    fun `blank inputs become null`() {
        val spec = PlaylistNameSpec.parse("", "  ", "")

        assertNull(spec.minBpm)
        assertNull(spec.maxBpm)
        assertNull(spec.trackCount)
    }

    @Test
    fun `invalid inputs become null`() {
        val spec = PlaylistNameSpec.parse("abc", "100-120", "many")

        assertNull(spec.minBpm)
        assertNull(spec.maxBpm)
        assertNull(spec.trackCount)
    }

    @Test
    fun `decimal bpm is supported`() {
        val spec = PlaylistNameSpec.parse("89.5", "90.5", "5")

        assertEquals(89.5, spec.minBpm!!, 0.001)
        assertEquals(90.5, spec.maxBpm!!, 0.001)
        assertEquals(5, spec.trackCount)
    }

    @Test
    fun `count is coerced to at least one`() {
        assertEquals(1, PlaylistNameSpec.parse("", "", "0").trackCount)
        assertEquals(1, PlaylistNameSpec.parse("", "", "-7").trackCount)
    }
}