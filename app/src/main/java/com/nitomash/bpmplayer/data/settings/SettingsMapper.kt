package com.nitomash.bpmplayer.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort

object SettingsMapper {

    private val SCHEMA_VERSION_KEY = intPreferencesKey("schema_version")
    private val DETECTOR_MIN_BPM_KEY = doublePreferencesKey("detector_min_bpm")
    private val DETECTOR_MAX_BPM_KEY = doublePreferencesKey("detector_max_bpm")
    private val PLAYLIST_LIST_SORT_KEY = intPreferencesKey("playlist_list_sort")
    private val PLAYBACK_PROCESSING_KEY = booleanPreferencesKey("playback_processing_enabled")
    private val PLAYBACK_LIMIT_KEY = intPreferencesKey("playback_limit_seconds")
    private val PLAYBACK_GAP_KEY = intPreferencesKey("playback_gap_seconds")
    private val SUPPRESS_BPM_PROMPT_AFTER_SCAN_KEY = booleanPreferencesKey("suppress_bpm_prompt_after_scan")
    private val SHUFFLE_ENABLED_KEY = booleanPreferencesKey("shuffle_enabled")
    private val REPEAT_MODE_KEY = intPreferencesKey("repeat_mode")

    fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val min = prefs[DETECTOR_MIN_BPM_KEY] ?: defaults.detectorMinBpm
        val max = prefs[DETECTOR_MAX_BPM_KEY] ?: defaults.detectorMaxBpm
        val range = normalizeRange(min, max)
        return defaults.copy(
            schemaVersion = prefs[SCHEMA_VERSION_KEY] ?: defaults.schemaVersion,
            detectorMinBpm = range.first,
            detectorMaxBpm = range.second,
            playlistListSort = prefs[PLAYLIST_LIST_SORT_KEY]
                ?.let { PlaylistListSort.fromValue(it) }
                ?: defaults.playlistListSort,
            playbackProcessingEnabled = prefs[PLAYBACK_PROCESSING_KEY]
                ?: defaults.playbackProcessingEnabled,
            playbackLimitSeconds = normalizePlaybackSeconds(
                prefs[PLAYBACK_LIMIT_KEY] ?: defaults.playbackLimitSeconds,
                AppSettings.PLAYBACK_LIMIT_MAX,
            ),
            playbackGapSeconds = normalizePlaybackSeconds(
                prefs[PLAYBACK_GAP_KEY] ?: defaults.playbackGapSeconds,
                AppSettings.PLAYBACK_GAP_MAX,
            ),
            suppressBpmPromptAfterScan = prefs[SUPPRESS_BPM_PROMPT_AFTER_SCAN_KEY]
                ?: defaults.suppressBpmPromptAfterScan,
            shuffleEnabled = prefs[SHUFFLE_ENABLED_KEY] ?: defaults.shuffleEnabled,
            repeatMode = prefs[REPEAT_MODE_KEY] ?: defaults.repeatMode,
        )
    }

    fun write(prefs: MutablePreferences, settings: AppSettings) {
        val range = normalizeRange(settings.detectorMinBpm, settings.detectorMaxBpm)
        prefs[SCHEMA_VERSION_KEY] = settings.schemaVersion
        prefs[DETECTOR_MIN_BPM_KEY] = range.first
        prefs[DETECTOR_MAX_BPM_KEY] = range.second
        prefs[PLAYLIST_LIST_SORT_KEY] = settings.playlistListSort.value
        prefs[PLAYBACK_PROCESSING_KEY] = settings.playbackProcessingEnabled
        prefs[PLAYBACK_LIMIT_KEY] = normalizePlaybackSeconds(
            settings.playbackLimitSeconds,
            AppSettings.PLAYBACK_LIMIT_MAX,
        )
        prefs[PLAYBACK_GAP_KEY] = normalizePlaybackSeconds(
            settings.playbackGapSeconds,
            AppSettings.PLAYBACK_GAP_MAX,
        )
        prefs[SUPPRESS_BPM_PROMPT_AFTER_SCAN_KEY] = settings.suppressBpmPromptAfterScan
        prefs[SHUFFLE_ENABLED_KEY] = settings.shuffleEnabled
        prefs[REPEAT_MODE_KEY] = settings.repeatMode
    }

    fun normalizePlaybackSeconds(value: Int, max: Int): Int = value.coerceIn(0, max)

    fun normalizeRange(min: Double, max: Double): Pair<Double, Double> {
        val lowestMin = AppSettings.BPM_LIMIT_MIN
        val highestMin = AppSettings.BPM_LIMIT_MAX - AppSettings.BPM_STEP
        val low = min.coerceIn(lowestMin, highestMin)
        val high = max.coerceIn(low + AppSettings.BPM_STEP, AppSettings.BPM_LIMIT_MAX)
        return low to high
    }
}
