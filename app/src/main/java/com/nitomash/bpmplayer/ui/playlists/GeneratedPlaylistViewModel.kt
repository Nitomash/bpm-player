package com.nitomash.bpmplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _savedEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val savedEvents: SharedFlow<Long> = _savedEvents.asSharedFlow()

    fun playFrom(index: Int) {
        val playlist = store.current.value ?: return
        val queue = playlist.tracks.map { it.toPlayerTrack() }
        if (queue.isEmpty()) return
        playbackSession.openQueue(
            queue,
            playlistId = playlist.savedPlaylistId,
            startIndex = index,
            generated = playlist.savedPlaylistId == null,
        )
    }

    fun save() {
        val playlist = store.current.value ?: return
        if (playlist.savedPlaylistId != null) return
        viewModelScope.launch {
            val name = playlist.name.ifBlank { "Сгенерированный плейлист" }
            val id = playlistRepository.create(name)
            playlistRepository.addTracks(id, playlist.tracks.map { it.id })
            store.markSaved(id)
            playbackSession.setCurrentPlaylistId(id)
            _savedEvents.emit(id)
        }
    }

    companion object {
        const val ROUTE = "generated_playlist"
    }
}
