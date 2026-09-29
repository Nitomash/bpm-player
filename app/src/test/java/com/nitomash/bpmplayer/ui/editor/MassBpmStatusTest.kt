package com.nitomash.bpmplayer.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MassBpmStatusTest {

    @Test
    fun `idle when nothing is known about the track`() {
        val status = resolveBatchStatus(TRACK_ID, emptySet(), emptySet(), emptySet())

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    @Test
    fun `detecting wins over failed and pending`() {
        val status = resolveBatchStatus(TRACK_ID, setOf(TRACK_ID), setOf(TRACK_ID), setOf(TRACK_ID))

        assertEquals(BatchTrackStatus.DETECTING, status)
    }

    @Test
    fun `detecting only applies to listed tracks`() {
        val status = resolveBatchStatus(OTHER_ID, setOf(TRACK_ID), emptySet(), emptySet())

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    @Test
    fun `detecting applies to every track in flight`() {
        val status = resolveBatchStatus(SECOND_ID, setOf(TRACK_ID, SECOND_ID), emptySet(), emptySet())

        assertEquals(BatchTrackStatus.DETECTING, status)
    }

    @Test
    fun `failed wins over pending`() {
        val status = resolveBatchStatus(TRACK_ID, emptySet(), setOf(TRACK_ID), setOf(TRACK_ID))

        assertEquals(BatchTrackStatus.FAILED, status)
    }

    @Test
    fun `pending marks the track detected`() {
        val status = resolveBatchStatus(TRACK_ID, emptySet(), emptySet(), setOf(TRACK_ID))

        assertEquals(BatchTrackStatus.DETECTED, status)
    }

    @Test
    fun `failed applies without pending`() {
        val status = resolveBatchStatus(TRACK_ID, emptySet(), setOf(OTHER_ID, TRACK_ID), emptySet())

        assertEquals(BatchTrackStatus.FAILED, status)
    }

    @Test
    fun `failed and pending of other tracks are ignored`() {
        val status = resolveBatchStatus(TRACK_ID, emptySet(), setOf(OTHER_ID), setOf(OTHER_ID))

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    @Test
    fun `detecting id absent from pending still shows detecting`() {
        val status = resolveBatchStatus(TRACK_ID, setOf(TRACK_ID), emptySet(), setOf(OTHER_ID))

        assertEquals(BatchTrackStatus.DETECTING, status)
    }

    @Test
    fun `empty sets do not crash`() {
        val status = resolveBatchStatus(TRACK_ID, emptySet<Long>(), emptySet(), emptySet())

        assertEquals(BatchTrackStatus.IDLE, status)
    }

    private companion object {
        const val TRACK_ID = 10L
        const val OTHER_ID = 11L
        const val SECOND_ID = 12L
    }
}
