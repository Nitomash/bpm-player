package com.nitomash.bpmplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.player.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackSession: PlaybackSession,
    private val musicRepository: MusicRepository,
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

    fun togglePlayback() = playbackSession.togglePlayback()

    fun next() = playbackSession.next()

    fun previous() = playbackSession.previous()

    fun toggleShuffle() = playbackSession.toggleShuffle()

    fun cycleRepeat() = playbackSession.cycleRepeat()

    fun seekTo(positionMs: Long) = playbackSession.seekTo(positionMs)

    fun isRepeatOn(mode: Int) = mode != Player.REPEAT_MODE_OFF
}