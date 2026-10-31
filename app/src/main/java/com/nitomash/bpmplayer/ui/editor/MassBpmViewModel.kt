package com.nitomash.bpmplayer.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.bpm.AutoBpmDetector
import com.nitomash.bpmplayer.data.bpm.BpmDetectionDispatcher
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlin.math.min

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

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

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
    private val baseItems = mutableListOf<BatchTrack>()
    private val folderNames = mutableMapOf<Long, String>()
    private val trackById = mutableMapOf<Long, TrackEntity>()
    private val pending = mutableMapOf<Long, PendingBpm>()
    private val failedIds = mutableSetOf<Long>()
    private val detectingIds = mutableSetOf<Long>()
    private val completedIds = mutableSetOf<Long>()
    private var batchJob: Job? = null
    private var singleDetectJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val folders = repository.getFolders().filter { it.id in folderIds }
                folderNames.putAll(folders.associate { it.id to it.displayName })
                val tracks = folders.flatMap { folder ->
                    repository.getByFolder(folder.id)
                        .filter { allTracks || it.bpm == null }
                        .sortedBy { it.title.trim().lowercase() }
                }
                base += tracks
                trackById.putAll(tracks.associateBy { it.id })
                baseItems += tracks.map { track ->
                    BatchTrack(
                        trackId = track.id,
                        folderId = track.folderId,
                        folderName = folderNames[track.folderId].orEmpty(),
                        title = track.title,
                        accessUri = track.accessUri,
                        bpm = track.bpm,
                        bpmSource = track.bpmSource,
                        status = BatchTrackStatus.IDLE,
                    )
                }
                publish()
            } finally {
                _loaded.value = true
            }
        }
    }

    fun startDetection() {
        if (batchJob?.isActive == true) return
        val tracks = base.filter { it.id !in completedIds }
        if (tracks.isEmpty()) return
        _detecting.value = true
        batchJob = viewModelScope.launch {
            val queue = ConcurrentLinkedQueue(tracks)
            val results = Channel<Pair<TrackEntity, BpmDetectionResult>>(tracks.size)
            try {
                coroutineScope {
                    val workers = min(BpmDetectionDispatcher.permits, tracks.size)
                    repeat(workers) {
                        launch {
                            while (true) {
                                val track = queue.poll() ?: break
                                results.send(track to detectTrack(track))
                            }
                        }
                    }
                    repeat(tracks.size) {
                        val (track, result) = results.receive()
                        applyResult(track, result)
                    }
                }
                completedIds.clear()
            } finally {
                results.close()
                detectingIds.clear()
                _detecting.value = false
                publish()
            }
        }
    }

    private suspend fun detectTrack(track: TrackEntity): BpmDetectionResult {
        detectingIds += track.id
        publish()
        val caller = currentCoroutineContext()
        return try {
            autoBpmDetector.detect(
                Uri.parse(track.accessUri),
                savedBpm = track.bpm,
                savedSource = track.bpmSource,
            ) {
                caller.ensureActive()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BpmMassDetect", "detect crashed for ${track.title}", e)
            BpmDetectionResult.Failure(BpmDetectionFailure.DECODE_FAILED)
        }
    }

    private fun applyResult(track: TrackEntity, result: BpmDetectionResult) {
        detectingIds -= track.id
        completedIds += track.id
        when (result) {
            is BpmDetectionResult.Success -> {
                pending[track.id] = PendingBpm(result.bpm, BpmSource.AUTO)
                failedIds.remove(track.id)
            }
            is BpmDetectionResult.Failure -> failedIds += track.id
        }
        publish()
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
                    trackById[trackId]?.folderId
                }
                affectedFolders += folderIdsHere
                repository.updateBpmBatch(entries.toMap(), source)
            }
            affectedFolders.forEach { sidecarSync.requestFlush(it) }
            pending.clear()
            failedIds.clear()
            completedIds.clear()
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
        val baseTrack = trackById[trackId] ?: return
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
                val result = autoBpmDetector.detect(
                    Uri.parse(target.accessUri),
                    savedBpm = target.bpm,
                    savedSource = target.bpmSource,
                ) {
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
        val current = _items.value
        val pendingIds = pending.keys
        val rebuilt = ArrayList<BatchTrack>(baseItems.size)
        var changed = current.size != baseItems.size
        baseItems.forEachIndexed { index, baseItem ->
            val p = pending[baseItem.trackId]
            val next = baseItem.copy(
                status = resolveBatchStatus(baseItem.trackId, detectingIds, failedIds, pendingIds),
                bpm = p?.bpm ?: baseItem.bpm,
                bpmSource = p?.source ?: baseItem.bpmSource,
            )
            val previous = current.getOrNull(index)
            if (previous == next) {
                rebuilt += previous
            } else {
                changed = true
                rebuilt += next
            }
        }
        if (changed) _items.value = rebuilt
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