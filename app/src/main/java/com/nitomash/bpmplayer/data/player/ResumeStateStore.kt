package com.nitomash.bpmplayer.data.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nitomash.bpmplayer.di.ResumeDataStore
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class ResumeState(
    val playlistId: Long?,
    val trackIds: List<Long>,
    val currentTrackId: Long?,
    val positionMs: Long,
    val name: String?,
)

internal fun encodeTrackIds(ids: List<Long>): String = ids.joinToString(TRACK_IDS_SEPARATOR)

internal fun decodeTrackIds(raw: String?): List<Long> =
    raw?.split(TRACK_IDS_SEPARATOR)?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

private const val TRACK_IDS_SEPARATOR = ","

@Singleton
class ResumeStateStore @Inject constructor(
    @ResumeDataStore private val dataStore: DataStore<Preferences>,
) {

    suspend fun save(state: ResumeState) {
        val name = state.name
        dataStore.edit { prefs ->
            prefs[KEY_TRACK_IDS] = encodeTrackIds(state.trackIds)
            prefs[KEY_PLAYLIST_ID] = state.playlistId ?: NO_ID
            prefs[KEY_CURRENT_TRACK_ID] = state.currentTrackId ?: NO_ID
            prefs[KEY_POSITION_MS] = state.positionMs.coerceAtLeast(0L)
            if (name.isNullOrEmpty()) prefs.remove(KEY_NAME) else prefs[KEY_NAME] = name
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TRACK_IDS)
            prefs.remove(KEY_PLAYLIST_ID)
            prefs.remove(KEY_CURRENT_TRACK_ID)
            prefs.remove(KEY_POSITION_MS)
            prefs.remove(KEY_NAME)
        }
    }

    suspend fun load(): ResumeState? {
        val prefs = dataStore.data.firstOrNull() ?: return null
        val trackIds = decodeTrackIds(prefs[KEY_TRACK_IDS])
        if (trackIds.isEmpty()) return null
        return ResumeState(
            playlistId = prefs[KEY_PLAYLIST_ID]?.takeIf { it != NO_ID },
            trackIds = trackIds,
            currentTrackId = prefs[KEY_CURRENT_TRACK_ID]?.takeIf { it != NO_ID },
            positionMs = (prefs[KEY_POSITION_MS] ?: 0L).coerceAtLeast(0L),
            name = prefs[KEY_NAME],
        )
    }

    private companion object {
        const val NO_ID = -1L
        val KEY_TRACK_IDS = stringPreferencesKey("resume_track_ids")
        val KEY_PLAYLIST_ID = longPreferencesKey("resume_playlist_id")
        val KEY_CURRENT_TRACK_ID = longPreferencesKey("resume_current_track_id")
        val KEY_POSITION_MS = longPreferencesKey("resume_position_ms")
        val KEY_NAME = stringPreferencesKey("resume_name")
    }
}
