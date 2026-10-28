package com.nitomash.bpmplayer.ui.playlists

import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedPlaylist(
    val name: String,
    val tracks: List<TrackEntity>,
)

@Singleton
class GeneratedPlaylistStore @Inject constructor() {

    private val _current = MutableStateFlow<GeneratedPlaylist?>(null)
    val current: StateFlow<GeneratedPlaylist?> = _current.asStateFlow()

    fun put(playlist: GeneratedPlaylist) {
        _current.value = playlist
    }
}
