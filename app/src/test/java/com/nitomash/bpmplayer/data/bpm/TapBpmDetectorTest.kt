package com.nitomash.bpmplayer.data.bpm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapBpmDetectorTest {

    private fun assertBpm(expected: Int, bpm: Int?, delta: Int = 0) {
        requireNotNull(bpm)
        if (delta == 0) {
            assertEquals(expected, bpm)
        } else {
            assertEquals(expected.toDouble(), bpm.toDouble(), delta.toDouble())
        }
    }

    private fun taps(detector: TapBpmDetector, intervalMs: Long, count: Int) {
        repeat(count) { index ->
            detector.tap(index * intervalMs)
        }
    }

    @Test
    fun `first tap returns null`() {
        val detector = TapBpmDetector()
        assertNull(detector.tap(0))
    }

    @Test
    fun `two taps at 120 bpm produce estimate`() {
        val detector = TapBpmDetector()
        detector.tap(0)
        assertBpm(120, detector.tap(500))
    }

    @Test
    fun `120 bpm with many taps stays stable`() {
        val detector = TapBpmDetector()
        taps(detector, intervalMs = 500, count = 12)
        assertBpm(120, detector.currentBpm())
    }

    @Test
    fun `90 bpm estimates correctly`() {
        val detector = TapBpmDetector()
        taps(detector, intervalMs = 667, count = 10)
        assertBpm(90, detector.currentBpm())
    }

    @Test
    fun `very short tap is ignored`() {
        val detector = TapBpmDetector()
        detector.tap(0)
        assertBpm(120, detector.tap(500))
        assertEquals(120, detector.tap(510))
        detector.tap(1000)
        assertBpm(120, detector.currentBpm(), delta = 1)
    }

    @Test
    fun `manual outlier tap does not shift estimate`() {
        val detector = TapBpmDetector()
        val timestamps = longArrayOf(
            0, 500, 1000, 1500, 2000, 2500, 3000,
            3400,
            3900, 4400, 4900, 5400, 5900,
        )
        for (t in timestamps) detector.tap(t)
        assertBpm(120, detector.currentBpm(), delta = 1)
    }

    @Test
    fun `missed beat extends series but keeps bpm`() {
        val detector = TapBpmDetector()
        taps(detector, intervalMs = 500, count = 5)
        detector.tap(4000)
        detector.tap(4500)
        assertBpm(120, detector.currentBpm())
    }

    @Test
    fun `two taps at half bpm double period`() {
        val detector = TapBpmDetector()
        detector.tap(0)
        assertBpm(60, detector.tap(1000))
    }

    @Test
    fun `reset clears state`() {
        val detector = TapBpmDetector()
        taps(detector, intervalMs = 500, count = 6)
        detector.reset()
        assertEquals(0, detector.tapCount)
        assertNull(detector.currentBpm())
        detector.tap(0)
        assertNull(detector.tap(0))
    }

    @Test
    fun `bpm is rounded to integer`() {
        val detector = TapBpmDetector()
        detector.tap(0)
        val bpm = detector.tap(350)
        assertEquals(171, bpm)
    }

    @Test
    fun `tap count increments`() {
        val detector = TapBpmDetector()
        detector.tap(0)
        detector.tap(500)
        detector.tap(1000)
        assertEquals(3, detector.tapCount)
    }

    @Test
    fun `long gap starts a new series`() {
        val detector = TapBpmDetector()
        taps(detector, intervalMs = 500, count = 6)
        assertBpm(120, detector.currentBpm())

        assertNull(detector.tap(6000))

        assertNull(detector.currentBpm())
        assertBpm(120, detector.tap(6500))
    }

    @Test
    fun `long gap counts taps from zero`() {
        val detector = TapBpmDetector()
        taps(detector, intervalMs = 500, count = 4)
        detector.tap(5000)
        assertEquals(1, detector.tapCount)
        detector.tap(5500)
        assertEquals(2, detector.tapCount)
    }

    @Test
    fun `new series determines bpm from scratch`() {
        val detector = TapBpmDetector()
        taps(detector, intervalMs = 500, count = 4)
        detector.tap(5000)
        detector.tap(5667)
        assertBpm(90, detector.currentBpm())
    }

    @Test
    fun `gap at most series limit is an interval`() {
        val detector = TapBpmDetector()
        detector.tap(0)
        detector.tap(3000)
        assertBpm(20, detector.currentBpm())
    }

    @Test
    fun `long tap sequence averages jitter`() {
        val detector = TapBpmDetector()
        val offsets = longArrayOf(-30, 20, 35, -40, 15, -25, 30, -20, 45, -35)
        var last = 0L
        detector.tap(last)
        for (offset in offsets) {
            val next = 500 + offset
            detector.tap(last + next)
            last += next
        }
        assertBpm(120, detector.currentBpm(), delta = 1)
    }
}