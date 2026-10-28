package com.nitomash.bpmplayer.ui.playlists

import androidx.lifecycle.ViewModel
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GeneratedPlaylistViewModel @Inject constructor(
    private val store: GeneratedPlaylistStore,
    private val playbackSession: PlaybackSession,
) : ViewModel() {

    val playlist: StateFlow<GeneratedPlaylist?> = store.current
    val currentTrackId = playbackSession.currentTrackId
    val currentPlaylistId = playbackSession.currentPlaylistId
    val isPreview = playbackSession.isPreview

    fun playFrom(index: Int) {
        val queue = store.current.value?.tracks.orEmpty().map { it.toPlayerTrack() }
        if (queue.isEmpty()) return
        playbackSession.openQueue(queue, startIndex = index, generated = true)
    }

    companion object {
        const val ROUTE = "generated_playlist"
    }
}
