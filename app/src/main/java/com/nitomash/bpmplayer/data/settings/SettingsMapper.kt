package com.nitomash.bpmplayer.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort

object SettingsMapper {

    private val SCHEMA_VERSION_KEY = intPreferencesKey("schema_version")
    private val DETECTOR_MIN_BPM_KEY = doublePreferencesKey("detector_min_bpm")
    private val DETECTOR_MAX_BPM_KEY = doublePreferencesKey("detector_max_bpm")
    private val PLAYLIST_LIST_SORT_KEY = intPreferencesKey("playlist_list_sort")

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
        )
    }

    fun write(prefs: MutablePreferences, settings: AppSettings) {
        val range = normalizeRange(settings.detectorMinBpm, settings.detectorMaxBpm)
        prefs[SCHEMA_VERSION_KEY] = settings.schemaVersion
        prefs[DETECTOR_MIN_BPM_KEY] = range.first
        prefs[DETECTOR_MAX_BPM_KEY] = range.second
        prefs[PLAYLIST_LIST_SORT_KEY] = settings.playlistListSort.value
    }

    fun normalizeRange(min: Double, max: Double): Pair<Double, Double> {
        val lowestMin = AppSettings.BPM_LIMIT_MIN
        val highestMin = AppSettings.BPM_LIMIT_MAX - AppSettings.BPM_STEP
        val low = min.coerceIn(lowestMin, highestMin)
        val high = max.coerceIn(low + AppSettings.BPM_STEP, AppSettings.BPM_LIMIT_MAX)
        return low to high
    }
}
