package com.nitomash.bpmplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GeneratedPlaylistViewModel @Inject constructor(
    private val store: GeneratedPlaylistStore,
    private val playbackSession: PlaybackSession,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    val playlist: StateFlow<GeneratedPlaylist?> = store.current
    val currentTrackId = playbackSession.currentTrackId
    val currentPlaylistId = playbackSession.currentPlaylistId
    val isPreview = playbackSession.isPreview

    private val _savedPlaylistId = MutableStateFlow<Long?>(null)
    val savedPlaylistId: StateFlow<Long?> = _savedPlaylistId.asStateFlow()

    fun playFrom(index: Int) {
        val queue = store.current.value?.tracks.orEmpty().map { it.toPlayerTrack() }
        if (queue.isEmpty()) return
        playbackSession.openQueue(queue, startIndex = index, generated = true)
    }

    fun save() {
        val pl = store.current.value ?: return
        if (_savedPlaylistId.value != null) return
        viewModelScope.launch {
            val name = pl.name.ifBlank { "Сгенерированный плейлист" }
            val id = playlistRepository.create(name)
            playlistRepository.addTracks(id, pl.tracks.map { it.id })
            playbackSession.setCurrentPlaylistId(id)
            _savedPlaylistId.value = id
        }
    }

    companion object {
        const val ROUTE = "generated_playlist"
    }
}