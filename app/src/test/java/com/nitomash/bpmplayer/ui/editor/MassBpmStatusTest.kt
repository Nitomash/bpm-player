package com.nitomash.bpmplayer.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MassBpmStatusTest {

    @Test
    fun `idle when nothing is known about the track`() {
        val status = resolveBatchStatus(TRACK_ID, null, emptySet(), emptySet())

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    @Test
    fun `detecting wins over failed and pending`() {
        val status = resolveBatchStatus(TRACK_ID, TRACK_ID, setOf(TRACK_ID), setOf(TRACK_ID))

        assertEquals(BatchTrackStatus.DETECTING, status)
    }

    @Test
    fun `detecting only applies to the current track`() {
        val status = resolveBatchStatus(OTHER_ID, TRACK_ID, emptySet(), emptySet())

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    @Test
    fun `failed wins over pending`() {
        val status = resolveBatchStatus(TRACK_ID, null, setOf(TRACK_ID), setOf(TRACK_ID))

        assertEquals(BatchTrackStatus.FAILED, status)
    }

    @Test
    fun `pending marks the track detected`() {
        val status = resolveBatchStatus(TRACK_ID, null, emptySet(), setOf(TRACK_ID))

        assertEquals(BatchTrackStatus.DETECTED, status)
    }

    @Test
    fun `failed applies without pending`() {
        val status = resolveBatchStatus(TRACK_ID, null, setOf(OTHER_ID, TRACK_ID), emptySet())

        assertEquals(BatchTrackStatus.FAILED, status)
    }

    @Test
    fun `failed and pending of other tracks are ignored`() {
        val status = resolveBatchStatus(TRACK_ID, null, setOf(OTHER_ID), setOf(OTHER_ID))

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    @Test
    fun `detecting id absent from pending still shows detecting`() {
        val status = resolveBatchStatus(TRACK_ID, TRACK_ID, emptySet(), setOf(OTHER_ID))

        assertEquals(BatchTrackStatus.DETECTING, status)
    }

    @Test
    fun `empty sets with a current track do not crash`() {
        val status = resolveBatchStatus(TRACK_ID, null, emptySet<Long>(), emptySet())

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    private companion object {
        const val TRACK_ID = 10L
        const val OTHER_ID = 11L
    }
}
