package com.nitomash.bpmplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.playlist.PlaylistListSorter
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.data.settings.AppSettings
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackSession: PlaybackSession,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isPlaying = playbackSession.isPlaying
    val isPreview = playbackSession.isPreview
    val currentTitle = playbackSession.currentTitle
    val currentNumber = playbackSession.currentNumber
    val queueSize = playbackSession.queueSize
    val hasPrevious = playbackSession.hasPrevious
    val hasNext = playbackSession.hasNext
    val shuffleEnabled = playbackSession.shuffleEnabled
    val repeatMode = playbackSession.repeatMode
    val positionMs = playbackSession.positionMs
    val durationMs = playbackSession.durationMs
    val currentTrackId = playbackSession.currentTrackId
    val currentPlaylistId = playbackSession.currentPlaylistId
    val isGeneratedQueue = playbackSession.isGeneratedQueue

    val appSettings: StateFlow<AppSettings> = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val currentBpm: StateFlow<Double?> = currentTrackId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else musicRepository.observeById(id).map { it?.bpm }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val currentTrack: StateFlow<TrackEntity?> = currentTrackId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else musicRepository.observeById(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val playlists: StateFlow<List<PlaylistSummary>> = combine(
        playlistRepository.observePlaylists(),
        settingsRepository.observe(),
    ) { summaries, settings ->
        PlaylistListSorter.sort(summaries, settings.playlistListSort)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val playlistsContainingCurrentTrack: StateFlow<Set<Long>> = currentTrackId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptySet())
            else playlistRepository.observePlaylistIdsContaining(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet(),
        )

    fun toggleCurrentTrackInPlaylist(playlistId: Long) {
        val trackId = currentTrackId.value ?: return
        viewModelScope.launch {
            if (playlistId in playlistsContainingCurrentTrack.value) {
                playlistRepository.removeTracks(playlistId, setOf(trackId))
            } else {
                playlistRepository.addTracks(playlistId, listOf(trackId))
            }
        }
    }

    fun togglePlayback() = playbackSession.togglePlayback()

    fun setPlaybackProcessingEnabled(enabled: Boolean) {
        updateSettings { it.copy(playbackProcessingEnabled = enabled) }
    }

    fun setPlaybackLimitSeconds(seconds: Int) {
        updateSettings {
            it.copy(playbackLimitSeconds = seconds.coerceIn(0, AppSettings.PLAYBACK_LIMIT_MAX))
        }
    }

    fun setPlaybackGapSeconds(seconds: Int) {
        updateSettings {
            it.copy(playbackGapSeconds = seconds.coerceIn(0, AppSettings.PLAYBACK_GAP_MAX))
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun next() = playbackSession.next()

    fun previous() = playbackSession.previous()

    fun toggleShuffle() = playbackSession.toggleShuffle()

    fun cycleRepeat() = playbackSession.cycleRepeat()

    fun seekTo(positionMs: Long) = playbackSession.seekTo(positionMs)

    fun isRepeatOn(mode: Int) = mode != Player.REPEAT_MODE_OFF
}