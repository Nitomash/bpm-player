package com.nitomash.bpmplayer.data.settings

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMapperTest {

    @Test
    fun `empty preferences map to defaults`() {
        assertEquals(AppSettings(), SettingsMapper.toSettings(emptyPreferences()))
    }

    @Test
    fun `stored range is read back`() {
        val prefs = mutablePreferencesOf(
            doublePreferencesKey("detector_min_bpm") to 90.0,
            doublePreferencesKey("detector_max_bpm") to 150.0,
        )

        val settings = SettingsMapper.toSettings(prefs)

        assertEquals(90.0, settings.detectorMinBpm, 0.001)
        assertEquals(150.0, settings.detectorMaxBpm, 0.001)
    }

    @Test
    fun `range is clamped to limits`() {
        val prefs = mutablePreferencesOf(
            doublePreferencesKey("detector_min_bpm") to 5.0,
            doublePreferencesKey("detector_max_bpm") to 900.0,
        )

        val settings = SettingsMapper.toSettings(prefs)

        assertEquals(AppSettings.BPM_LIMIT_MIN, settings.detectorMinBpm, 0.001)
        assertEquals(AppSettings.BPM_LIMIT_MAX, settings.detectorMaxBpm, 0.001)
    }

    @Test
    fun `min above max is reordered with a step gap`() {
        val prefs = mutablePreferencesOf(
            doublePreferencesKey("detector_min_bpm") to 150.0,
            doublePreferencesKey("detector_max_bpm") to 100.0,
        )

        val settings = SettingsMapper.toSettings(prefs)

        assertEquals(150.0, settings.detectorMinBpm, 0.001)
        assertEquals(150.0 + AppSettings.BPM_STEP, settings.detectorMaxBpm, 0.001)
    }

    @Test
    fun `write stores normalized range`() {
        val prefs = mutablePreferencesOf()

        SettingsMapper.write(
            prefs,
            AppSettings(detectorMinBpm = 100.0, detectorMaxBpm = 100.0),
        )

        val settings = SettingsMapper.toSettings(prefs)
        assertEquals(100.0, settings.detectorMinBpm, 0.001)
        assertEquals(100.0 + AppSettings.BPM_STEP, settings.detectorMaxBpm, 0.001)
    }

    @Test
    fun `normalize keeps valid range untouched`() {
        assertEquals(80.0 to 140.0, SettingsMapper.normalizeRange(80.0, 140.0))
    }

    @Test
    fun `normalize keeps top limit reachable`() {
        val (low, high) = SettingsMapper.normalizeRange(AppSettings.BPM_LIMIT_MAX, AppSettings.BPM_LIMIT_MAX)

        assertEquals(AppSettings.BPM_LIMIT_MAX - AppSettings.BPM_STEP, low, 0.001)
        assertEquals(AppSettings.BPM_LIMIT_MAX, high, 0.001)
    }

    @Test
    fun `playlist list sort defaults to date modified`() {
        assertEquals(PlaylistListSort.DATE_MODIFIED, AppSettings().playlistListSort)
        assertEquals(PlaylistListSort.DATE_MODIFIED, SettingsMapper.toSettings(emptyPreferences()).playlistListSort)
    }

    @Test
    fun `playlist list sort is written and read back`() {
        val prefs = mutablePreferencesOf()

        SettingsMapper.write(prefs, AppSettings(playlistListSort = PlaylistListSort.CUSTOM))

        assertEquals(PlaylistListSort.CUSTOM, SettingsMapper.toSettings(prefs).playlistListSort)
    }

    @Test
    fun `playlist list sort round trips through every value`() {
        PlaylistListSort.entries.forEach { mode ->
            val prefs = mutablePreferencesOf()

            SettingsMapper.write(prefs, AppSettings(playlistListSort = mode))

            assertEquals(mode, SettingsMapper.toSettings(prefs).playlistListSort)
        }
    }

    @Test
    fun `unknown playlist list sort falls back to the default`() {
        val prefs = mutablePreferencesOf(intPreferencesKey("playlist_list_sort") to 99)

        assertEquals(PlaylistListSort.DATE_MODIFIED, SettingsMapper.toSettings(prefs).playlistListSort)
    }

    @Test
    fun `writing settings does not drop the playlist list sort`() {
        val prefs = mutablePreferencesOf(intPreferencesKey("playlist_list_sort") to PlaylistListSort.TITLE.value)

        SettingsMapper.write(prefs, AppSettings(playlistListSort = PlaylistListSort.CUSTOM))
        SettingsMapper.write(prefs, SettingsMapper.toSettings(prefs))

        assertEquals(PlaylistListSort.CUSTOM, SettingsMapper.toSettings(prefs).playlistListSort)
    }
}
