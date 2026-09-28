package com.nitomash.bpmplayer.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
import com.nitomash.bpmplayer.ui.library.AutoDetectUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BatchTrackStatus { IDLE, DETECTING, DETECTED, FAILED }

data class BatchTrack(
    val trackId: Long,
    val folderId: Long,
    val folderName: String,
    val title: String,
    val accessUri: String,
    val bpm: Double?,
    val bpmSource: BpmSource?,
    val status: BatchTrackStatus,
)

private data class PendingBpm(val bpm: Double, val source: BpmSource)

@HiltViewModel
class MassBpmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
    private val sidecarSync: SidecarSyncManager,
    private val playbackSession: PlaybackSession,
    private val autoBpmDetector: AutoBpmDetector,
) : ViewModel() {

    private val allTracks: Boolean = savedStateHandle.get<Boolean>(ARG_ALL_TRACKS) ?: true
    private val folderIds: Set<Long> = savedStateHandle.get<String>(ARG_FOLDER_IDS)
        .orEmpty()
        .split(',')
        .mapNotNullTo(linkedSetOf()) { it.toLongOrNull() }

    private val _items = MutableStateFlow<List<BatchTrack>>(emptyList())
    val items: StateFlow<List<BatchTrack>> = _items.asStateFlow()

    private val _detecting = MutableStateFlow(false)
    val detecting: StateFlow<Boolean> = _detecting.asStateFlow()

    private val _selectedTarget = MutableStateFlow<TrackEntity?>(null)
    val selectedTarget: StateFlow<TrackEntity?> = _selectedTarget.asStateFlow()

    private val _autoDetectState = MutableStateFlow<AutoDetectUiState>(AutoDetectUiState.Idle)
    val autoDetectState: StateFlow<AutoDetectUiState> = _autoDetectState.asStateFlow()

    private val _hasChanges = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = _hasChanges.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    val isPlaying: StateFlow<Boolean> = playbackSession.isPlaying

    private val base = mutableListOf<TrackEntity>()
    private val folderNames = mutableMapOf<Long, String>()
    private val pending = mutableMapOf<Long, PendingBpm>()
    private val failedIds = mutableSetOf<Long>()
    private var currentDetectingId: Long? = null
    private var batchJob: Job? = null
    private var singleDetectJob: Job? = null
    private var resumeIndex = 0

    init {
        viewModelScope.launch {
            val folders = repository.getFolders().filter { it.id in folderIds }
            folderNames.putAll(folders.associate { it.id to it.displayName })
            val tracks = folders.flatMap { folder -> repository.getByFolder(folder.id) }
                .filter { allTracks || it.bpm == null }
            base += tracks
            publish()
        }
    }

    fun startDetection() {
        if (batchJob?.isActive == true) return
        val tracks = base.toList()
        if (tracks.isEmpty()) return
        _detecting.value = true
        batchJob = viewModelScope.launch {
            var index = resumeIndex
            while (index < tracks.size) {
                val track = tracks[index]
                resumeIndex = index
                currentDetectingId = track.id
                publish()
                val result = try {
                    autoBpmDetector.detect(Uri.parse(track.accessUri)) {
                        coroutineContext.ensureActive()
                        true
                    }
                } catch (e: CancellationException) {
                    currentDetectingId = null
                    _detecting.value = false
                    publish()
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("BpmMassDetect", "detect crashed for ${track.title}", e)
                    BpmDetectionResult.Failure(BpmDetectionFailure.DECODE_FAILED)
                }
                when (result) {
                    is BpmDetectionResult.Success -> {
                        pending[track.id] = PendingBpm(result.bpm, BpmSource.AUTO)
                        failedIds.remove(track.id)
                    }
                    is BpmDetectionResult.Failure -> failedIds += track.id
                }
                currentDetectingId = null
                publish()
                index++
            }
            resumeIndex = 0
            _detecting.value = false
        }
    }

    fun cancelDetection() {
        batchJob?.cancel()
    }

    fun saveAll() {
        viewModelScope.launch {
            if (pending.isEmpty()) return@launch
            val bySource = pending.entries.groupBy({ it.value.source }) { entry ->
                entry.key to entry.value.bpm
            }
            val affectedFolders = linkedSetOf<Long>()
            for ((source, entries) in bySource) {
                val folderIdsHere = entries.mapNotNull { (trackId, _) ->
                    base.firstOrNull { it.id == trackId }?.folderId
                }
                affectedFolders += folderIdsHere
                repository.updateBpmBatch(entries.toMap(), source)
            }
            affectedFolders.forEach { sidecarSync.requestFlush(it) }
            pending.clear()
            failedIds.clear()
            publish()
            _saved.emit(Unit)
        }
    }

    fun discard() {
        cancelDetection()
        cancelSingleDetect()
        playbackSession.endPreview()
    }

    fun openTarget(trackId: Long) {
        cancelSingleDetect()
        val baseTrack = base.firstOrNull { it.id == trackId } ?: return
        val p = pending[trackId]
        _selectedTarget.value = baseTrack.copy(
            bpm = p?.bpm ?: baseTrack.bpm,
            bpmSource = p?.source ?: baseTrack.bpmSource,
        )
        playbackSession.setStopOnTrackEnd(true)
        playbackSession.previewTrack(baseTrack.toPlayerTrack())
    }

    fun togglePlayback() {
        val target = _selectedTarget.value ?: return
        playbackSession.togglePreviewPlayback(target.toPlayerTrack())
    }

    fun skipForward() {
        playbackSession.skipForward()
    }

    fun saveFromEditor(bpm: Double, source: BpmSource) {
        val target = _selectedTarget.value ?: return
        pending[target.id] = PendingBpm(bpm, source)
        failedIds.remove(target.id)
        publish()
        dismissEditor()
    }

    fun autoDetectSingle() {
        val target = _selectedTarget.value ?: return
        if (singleDetectJob?.isActive == true) return
        _autoDetectState.value = AutoDetectUiState.Detecting
        singleDetectJob = viewModelScope.launch {
            try {
                val result = autoBpmDetector.detect(Uri.parse(target.accessUri)) {
                    coroutineContext.ensureActive()
                    true
                }
                _autoDetectState.value = when (result) {
                    is BpmDetectionResult.Success -> AutoDetectUiState.Detected(result.bpm)
                    is BpmDetectionResult.Failure -> AutoDetectUiState.Failed(result.reason)
                }
            } catch (e: CancellationException) {
                _autoDetectState.value = AutoDetectUiState.Idle
                throw e
            } catch (e: Exception) {
                android.util.Log.w("BpmMassDetect", "single detect crashed", e)
                _autoDetectState.value = AutoDetectUiState.Failed(BpmDetectionFailure.DECODE_FAILED)
            }
        }
    }

    fun cancelSingleDetect() {
        singleDetectJob?.cancel()
        _autoDetectState.value = AutoDetectUiState.Idle
    }

    fun dismissEditor() {
        cancelSingleDetect()
        playbackSession.setStopOnTrackEnd(false)
        playbackSession.endPreview()
        _selectedTarget.value = null
    }

    private fun publish() {
        _hasChanges.value = pending.isNotEmpty()
        _items.value = base.map { track ->
            val p = pending[track.id]
            val status = when {
                track.id == currentDetectingId -> BatchTrackStatus.DETECTING
                track.id in failedIds -> BatchTrackStatus.FAILED
                p != null -> BatchTrackStatus.DETECTED
                else -> BatchTrackStatus.IDLE
            }
            BatchTrack(
                trackId = track.id,
                folderId = track.folderId,
                folderName = folderNames[track.folderId].orEmpty(),
                title = track.title,
                accessUri = track.accessUri,
                bpm = p?.bpm ?: track.bpm,
                bpmSource = p?.source ?: track.bpmSource,
                status = status,
            )
        }
    }

    override fun onCleared() {
        playbackSession.endPreview()
    }

    companion object {
        const val ARG_ALL_TRACKS = "allTracks"
        const val ARG_FOLDER_IDS = "folderIds"
        const val ROUTE = "mass_bpm"

        fun route(scope: MassBpmScope, folderIds: Set<Long>): String =
            "$ROUTE?allTracks=${scope == MassBpmScope.ALL}&folderIds=${folderIds.joinToString(",")}"
    }
}