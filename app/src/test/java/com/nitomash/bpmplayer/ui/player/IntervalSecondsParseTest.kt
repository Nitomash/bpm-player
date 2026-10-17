package com.nitomash.bpmplayer.ui.player

import com.nitomash.bpmplayer.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalSecondsParseTest {

    @Test
    fun `empty text is rejected`() {
        assertNull(parseSecondsInput("", AppSettings.PLAYBACK_LIMIT_MAX))
    }

    @Test
    fun `blank text is rejected`() {
        assertNull(parseSecondsInput("   ", AppSettings.PLAYBACK_LIMIT_MAX))
    }

    @Test
    fun `non numeric text is rejected`() {
        assertNull(parseSecondsInput("abc", AppSettings.PLAYBACK_LIMIT_MAX))
        assertNull(parseSecondsInput("12a", AppSettings.PLAYBACK_LIMIT_MAX))
    }

    @Test
    fun `valid value is parsed`() {
        assertEquals(45, parseSecondsInput("45", AppSettings.PLAYBACK_LIMIT_MAX))
    }

    @Test
    fun `value is trimmed`() {
        assertEquals(10, parseSecondsInput(" 10 ", AppSettings.PLAYBACK_GAP_MAX))
    }

    @Test
    fun `value is clamped to max`() {
        assertEquals(
            AppSettings.PLAYBACK_LIMIT_MAX,
            parseSecondsInput("9999", AppSettings.PLAYBACK_LIMIT_MAX),
        )
    }
}
