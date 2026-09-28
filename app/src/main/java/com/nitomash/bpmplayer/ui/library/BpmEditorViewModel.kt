package com.nitomash.bpmplayer.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.bpm.AutoBpmDetector
import com.nitomash.bpmplayer.data.bpm.BpmDetectionFailure
import com.nitomash.bpmplayer.data.bpm.BpmDetectionResult
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.music.SidecarSyncManager
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AutoDetectUiState {
    data object Idle : AutoDetectUiState
    data object Detecting : AutoDetectUiState
    data class Detected(val bpm: Double) : AutoDetectUiState
    data class Failed(val reason: BpmDetectionFailure) : AutoDetectUiState
}

@HiltViewModel
class BpmEditorViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackSession: PlaybackSession,
    private val sidecarSync: SidecarSyncManager,
    private val autoBpmDetector: AutoBpmDetector,
) : ViewModel() {

    private val _selectedTrack = MutableStateFlow<TrackEntity?>(null)
    val selectedTrack: StateFlow<TrackEntity?> = _selectedTrack.asStateFlow()

    private val _autoDetectState = MutableStateFlow<AutoDetectUiState>(AutoDetectUiState.Idle)
    val autoDetectState: StateFlow<AutoDetectUiState> = _autoDetectState.asStateFlow()

    private var detectJob: Job? = null

    val isPlaying: StateFlow<Boolean> = playbackSession.isPlaying

    fun open(track: TrackEntity) {
        cancelAutoDetect()
        _selectedTrack.value = track
        playbackSession.setStopOnTrackEnd(true)
        playbackSession.previewTrack(track.toPlayerTrack())
    }

    fun show(track: TrackEntity) {
        cancelAutoDetect()
        _selectedTrack.value = track
        playbackSession.setStopOnTrackEnd(true)
    }

    fun togglePlayback() {
        val track = _selectedTrack.value ?: return
        playbackSession.togglePreviewPlayback(track.toPlayerTrack())
    }

    fun skipForward() {
        playbackSession.skipForward()
    }

    fun saveBpm(bpm: Double, source: BpmSource) {
        val track = _selectedTrack.value ?: return
        viewModelScope.launch {
            repository.updateBpm(track.id, bpm, source)
            sidecarSync.requestFlush(track.folderId)
        }
    }

    fun autoDetectBpm() {
        val track = _selectedTrack.value ?: return
        if (detectJob?.isActive == true) return
        android.util.Log.i("BpmDetect", "autoDetectBpm start for ${track.title}")
        _autoDetectState.value = AutoDetectUiState.Detecting
        detectJob = viewModelScope.launch {
            try {
                val result = autoBpmDetector.detect(Uri.parse(track.accessUri)) {
                    coroutineContext.ensureActive()
                    true
                }
                when (result) {
                    is BpmDetectionResult.Success -> {
                        _autoDetectState.value = AutoDetectUiState.Detected(result.bpm)
                        android.util.Log.i("BpmDetect", "autoDetect success bpm=${result.bpm} quality=${result.quality}")
                    }
                    is BpmDetectionResult.Failure -> {
                        android.util.Log.w("BpmDetect", "autoDetect failed reason=${result.reason}")
                        _autoDetectState.value = AutoDetectUiState.Failed(result.reason)
                    }
                }
            } catch (e: CancellationException) {
                _autoDetectState.value = AutoDetectUiState.Idle
                throw e
            } catch (e: Exception) {
                android.util.Log.w("BpmDetect", "autoDetect crashed", e)
                _autoDetectState.value = AutoDetectUiState.Failed(BpmDetectionFailure.DECODE_FAILED)
            }
        }
    }

    fun cancelAutoDetect() {
        detectJob?.cancel()
        _autoDetectState.value = AutoDetectUiState.Idle
    }

    fun dismiss() {
        cancelAutoDetect()
        playbackSession.setStopOnTrackEnd(false)
        playbackSession.endPreview()
        _selectedTrack.value = null
    }
}