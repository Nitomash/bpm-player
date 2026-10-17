package com.nitomash.bpmplayer.data.player

import com.nitomash.bpmplayer.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLimitsTest {

    @Test
    fun `settings map to limits`() {
        val limits = AppSettings(
            playbackProcessingEnabled = false,
            playbackLimitSeconds = 30,
            playbackGapSeconds = 5,
        ).toPlaybackLimits()

        assertFalse(limits.enabled)
        assertEquals(30, limits.limitSeconds)
        assertEquals(5, limits.gapSeconds)
    }

    @Test
    fun `defaults give no clip and no gap`() {
        val limits = PlaybackLimits()

        assertTrue(limits.enabled)
        assertFalse(clipNow(limits))
        assertFalse(shouldStartGap(limits, isPlaylistMode = true, stopOnTrackEnd = false))
    }

    @Test
    fun `clip fires when position reaches the limit`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 45)

        assertTrue(clipNow(limits, positionMs = 45_000L))
        assertTrue(clipNow(limits, positionMs = 46_000L))
        assertFalse(clipNow(limits, positionMs = 44_999L))
    }

    @Test
    fun `track shorter than the limit never clips`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertFalse(clipNow(limits, positionMs = 60_000L, durationMs = 60_000L))
    }

    @Test
    fun `unknown duration still clips by position`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertTrue(clipNow(limits, positionMs = 90_000L, durationMs = 0L))
    }

    @Test
    fun `clip does not fire outside playlist mode`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 45)

        assertFalse(clipNow(limits, isPlaylistMode = false))
    }

    @Test
    fun `clip does not fire during gap or while editor holds playback`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 45)

        assertFalse(clipNow(limits, gapActive = true))
        assertFalse(clipNow(limits, stopOnTrackEnd = true))
    }

    @Test
    fun `clip does not fire while paused`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 45)

        assertFalse(clipNow(limits, isPlaying = false))
    }

    @Test
    fun `clip does not fire when disabled or zero`() {
        assertFalse(clipNow(PlaybackLimits(enabled = false, limitSeconds = 45)))
        assertFalse(clipNow(PlaybackLimits(enabled = true, limitSeconds = 0)))
    }

    @Test
    fun `gap starts in playlist`() {
        val limits = PlaybackLimits(gapSeconds = 10)

        assertTrue(shouldStartGap(limits, isPlaylistMode = true, stopOnTrackEnd = false))
    }

    @Test
    fun `gap does not start outside playlist mode`() {
        val limits = PlaybackLimits(gapSeconds = 10)

        assertFalse(shouldStartGap(limits, isPlaylistMode = false, stopOnTrackEnd = false))
    }

    @Test
    fun `gap does not start when editor holds playback`() {
        val limits = PlaybackLimits(gapSeconds = 10)

        assertFalse(shouldStartGap(limits, isPlaylistMode = true, stopOnTrackEnd = true))
    }

    @Test
    fun `gap does not start when disabled or zero`() {
        val disabled = PlaybackLimits(enabled = false, gapSeconds = 10)
        val zeroed = PlaybackLimits(enabled = true, gapSeconds = 0)

        assertFalse(shouldStartGap(disabled, isPlaylistMode = true, stopOnTrackEnd = false))
        assertFalse(shouldStartGap(zeroed, isPlaylistMode = true, stopOnTrackEnd = false))
    }

    @Test
    fun `fade keeps full volume before the last two seconds`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertNull(clipFade(limits, positionMs = 87_000L))
        assertEquals(1f, clipFade(limits, positionMs = 88_000L)!!, 0.001f)
    }

    @Test
    fun `fade drops proportionally inside the window`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertEquals(0.5f, clipFade(limits, positionMs = 89_000L)!!, 0.001f)
        assertEquals(0.25f, clipFade(limits, positionMs = 89_500L)!!, 0.001f)
    }

    @Test
    fun `fade reaches zero at the limit`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertEquals(0f, clipFade(limits, positionMs = 90_000L)!!, 0.001f)
        assertEquals(0f, clipFade(limits, positionMs = 91_000L)!!, 0.001f)
    }

    @Test
    fun `fade is skipped without an active limit`() {
        assertNull(clipFade(PlaybackLimits(enabled = false, limitSeconds = 90), positionMs = 89_500L))
        assertNull(clipFade(PlaybackLimits(enabled = true, limitSeconds = 0), positionMs = 89_500L))
    }

    @Test
    fun `fade is skipped outside playlist or while editor holds playback`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertNull(clipFade(limits, positionMs = 89_500L, isPlaylistMode = false))
        assertNull(clipFade(limits, positionMs = 89_500L, stopOnTrackEnd = true))
    }

    @Test
    fun `fade is skipped when the track ends before the limit`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertNull(clipFade(limits, positionMs = 89_500L, durationMs = 60_000L))
    }

    @Test
    fun `fade follows position when duration is unknown`() {
        val limits = PlaybackLimits(enabled = true, limitSeconds = 90)

        assertEquals(0.5f, clipFade(limits, positionMs = 89_000L, durationMs = 0L)!!, 0.001f)
    }

    @Test
    fun `advance to next item at the very end of the track`() {
        assertTrue(shouldAdvanceToNextItem(hasNext = true, positionMs = 140_000L, durationMs = 140_000L))
        assertTrue(shouldAdvanceToNextItem(hasNext = true, positionMs = 139_500L, durationMs = 140_000L))
    }

    @Test
    fun `no advance without a next item`() {
        assertFalse(shouldAdvanceToNextItem(hasNext = false, positionMs = 140_000L, durationMs = 140_000L))
    }

    @Test
    fun `no advance when the player already moved to the next track`() {
        assertFalse(shouldAdvanceToNextItem(hasNext = true, positionMs = 0L, durationMs = 180_000L))
        assertFalse(shouldAdvanceToNextItem(hasNext = true, positionMs = 137_000L, durationMs = 140_000L))
    }

    @Test
    fun `no advance when duration is unknown`() {
        assertFalse(shouldAdvanceToNextItem(hasNext = true, positionMs = 0L, durationMs = 0L))
        assertFalse(shouldAdvanceToNextItem(hasNext = true, positionMs = 140_000L, durationMs = 0L))
    }

    private fun clipFade(
        limits: PlaybackLimits,
        positionMs: Long,
        durationMs: Long = 300_000L,
        isPlaylistMode: Boolean = true,
        stopOnTrackEnd: Boolean = false,
    ): Float? = clipFadeVolume(
        limits = limits,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaylistMode = isPlaylistMode,
        stopOnTrackEnd = stopOnTrackEnd,
    )

    private fun clipNow(
        limits: PlaybackLimits,
        positionMs: Long = 60_000L,
        durationMs: Long = 300_000L,
        isPlaylistMode: Boolean = true,
        gapActive: Boolean = false,
        stopOnTrackEnd: Boolean = false,
        isPlaying: Boolean = true,
    ): Boolean = shouldClipNow(
        limits = limits,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaylistMode = isPlaylistMode,
        gapActive = gapActive,
        stopOnTrackEnd = stopOnTrackEnd,
        isPlaying = isPlaying,
    )
}
