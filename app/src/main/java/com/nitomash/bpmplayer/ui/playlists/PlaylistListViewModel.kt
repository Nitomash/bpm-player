package com.nitomash.bpmplayer.ui.playlists

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort
import com.nitomash.bpmplayer.data.playlist.PlaylistListSorter
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.data.playlist.export.BundleExportError
import com.nitomash.bpmplayer.data.playlist.export.BundleExportProgress
import com.nitomash.bpmplayer.data.playlist.export.BundleExportResult
import com.nitomash.bpmplayer.data.playlist.export.PlaylistBundleExporter
import com.nitomash.bpmplayer.data.playlist.export.XspfExportResult
import com.nitomash.bpmplayer.data.playlist.export.XspfExporter
import com.nitomash.bpmplayer.data.playlist.xspf.XspfImportResult
import com.nitomash.bpmplayer.data.playlist.xspf.XspfImporter
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import com.nitomash.bpmplayer.data.testing.TesterBeacon
import com.nitomash.bpmplayer.data.testing.TesterProgress
import com.nitomash.bpmplayer.ui.common.ListReorder
import com.nitomash.bpmplayer.ui.common.SelectAll
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackSession: PlaybackSession,
    private val xspfExporter: XspfExporter,
    private val xspfImporter: XspfImporter,
    private val bundleExporter: PlaylistBundleExporter,
    private val generatedPlaylistStore: GeneratedPlaylistStore,
    private val testerProgress: TesterProgress,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val listSort: StateFlow<PlaylistListSort> = settingsRepository.observe()
        .map { it.playlistListSort }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistListSort.DATE_MODIFIED)

    private val _reorderIds = MutableStateFlow<List<Long>?>(null)
    val reorderIds: StateFlow<List<Long>?> = _reorderIds.asStateFlow()

    private val _reorderSession = MutableStateFlow(0)
    val reorderSession: StateFlow<Int> = _reorderSession.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val playlists: StateFlow<List<PlaylistSummary>> = combine(
        playlistRepository.observePlaylists(),
        listSort,
        _reorderIds,
    ) { summaries, sort, draft ->
        if (draft == null) {
            PlaylistListSorter.sort(summaries, sort)
        } else {
            ListReorder.applyDraft(summaries, draft) { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create() {
        viewModelScope.launch {
            playlistRepository.create(context.getString(R.string.playlist_new_default))
            testerProgress.mark(TesterBeacon.PLAYLIST_CREATED)
        }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.delete(playlistId)
            playbackSession.clearPlaylistIdIf(playlistId)
            generatedPlaylistStore.clearSaved(playlistId)
            testerProgress.mark(TesterBeacon.PLAYLIST_DELETED)
        }
    }

    fun play(playlistId: Long, onStarted: () -> Unit) {
        viewModelScope.launch {
            val tracks = playlistRepository.getTracksInDisplayOrder(playlistId)
            if (tracks.isEmpty()) return@launch
            val playlistName = playlists.value.firstOrNull { it.id == playlistId }?.name
            playbackSession.openQueue(
                tracks.map { it.toPlayerTrack() },
                playlistId,
                0,
                randomStart = true,
                playlistName = playlistName,
            )
            testerProgress.mark(TesterBeacon.PLAYLIST_PLAY_STARTED)
            onStarted()
        }
    }

    fun setListSort(sort: PlaylistListSort) {
        if (sort == listSort.value) return
        discardReorder()
        viewModelScope.launch {
            persistListSort(sort)
            testerProgress.mark(TesterBeacon.PLAYLIST_LIST_SORT_CHANGED)
        }
    }

    fun enterReorder() {
        if (playlists.value.size < 2) return
        _reorderIds.value = playlists.value.map { it.id }
        _reorderSession.value += 1
        viewModelScope.launch { testerProgress.mark(TesterBeacon.PLAYLIST_LIST_REORDER_ENTERED) }
    }

    fun onReorderMove(fromIndex: Int, toIndex: Int) {
        val draft = _reorderIds.value ?: return
        val next = ListReorder.moved(draft, fromIndex, toIndex)
        if (next === draft) return
        _reorderIds.value = next
    }

    fun commitReorder() {
        val draft = _reorderIds.value ?: return
        viewModelScope.launch {
            playlistRepository.setPlaylistOrder(draft)
            persistListSort(PlaylistListSort.CUSTOM)
            _reorderIds.value = null
            testerProgress.mark(TesterBeacon.PLAYLIST_LIST_REORDER_COMMITTED)
        }
    }

    fun exitReorder() {
        discardReorder()
    }

    private suspend fun persistListSort(sort: PlaylistListSort) {
        settingsRepository.update { it.copy(playlistListSort = sort) }
    }

    private fun discardReorder() {
        _reorderIds.value = null
    }

    fun enterSelectionMode(initialSelection: Long? = null) {
        if (!_isSelectionMode.value) {
            _selectedIds.value = emptySet()
        }
        _isSelectionMode.value = true
        if (initialSelection != null) {
            _selectedIds.value = _selectedIds.value + initialSelection
        }
        viewModelScope.launch { testerProgress.mark(TesterBeacon.PLAYLIST_SELECTION_MODE_USED) }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        if (!_isSelectionMode.value) {
            enterSelectionMode(id)
            return
        }
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(id)) current - id else current + id
    }

    fun toggleSelectAll() {
        _selectedIds.value = SelectAll.toggled(playlists.value.map { it.id }, _selectedIds.value)
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                playlistRepository.delete(id)
                playbackSession.clearPlaylistIdIf(id)
                generatedPlaylistStore.clearSaved(id)
            }
            testerProgress.mark(TesterBeacon.PLAYLIST_DELETED_SELECTED)
            exitSelectionMode()
        }
    }

    private val _exportEvents = MutableSharedFlow<XspfExportResult>(extraBufferCapacity = 1)
    val exportEvents: SharedFlow<XspfExportResult> = _exportEvents.asSharedFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    fun exportTo(treeUri: Uri, playlistIds: List<Long>) {
        if (playlistIds.isEmpty() || _exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                val result = xspfExporter.export(treeUri, playlistIds)
                if (result.succeededCount > 0) {
                    testerProgress.mark(TesterBeacon.XSPF_EXPORT)
                    if (playlistIds.toSet() == _selectedIds.value) {
                        exitSelectionMode()
                    }
                }
                _exportEvents.emit(result)
            } finally {
                _exporting.value = false
            }
        }
    }

    private val _bundleExportProgress = MutableStateFlow<BundleExportProgress?>(null)
    val bundleExportProgress: StateFlow<BundleExportProgress?> = _bundleExportProgress.asStateFlow()

    private val _bundleExportEvents = MutableSharedFlow<BundleExportResult>(extraBufferCapacity = 1)
    val bundleExportEvents: SharedFlow<BundleExportResult> = _bundleExportEvents.asSharedFlow()

    private var bundleExportJob: Job? = null

    fun exportWithFiles(treeUri: Uri, playlistId: Long) {
        if (bundleExportJob?.isActive == true) return
        bundleExportJob = viewModelScope.launch {
            try {
                val result = bundleExporter.export(treeUri, playlistId) { progress ->
                    _bundleExportProgress.value = progress
                }
                if (result.error == null) {
                    testerProgress.mark(TesterBeacon.BUNDLE_EXPORT)
                } else if (result.error == BundleExportError.CANCELLED) {
                    testerProgress.mark(TesterBeacon.BUNDLE_EXPORT_CANCELLED)
                }
                _bundleExportEvents.emit(result)
            } finally {
                _bundleExportProgress.value = null
            }
        }
    }

    fun cancelBundleExport() {
        val job = bundleExportJob ?: return
        bundleExportJob = null
        val progress = _bundleExportProgress.value
        _bundleExportProgress.value = null
        job.cancel()
        viewModelScope.launch {
            testerProgress.mark(TesterBeacon.BUNDLE_EXPORT_CANCELLED)
            _bundleExportEvents.emit(
                BundleExportResult(
                    playlistName = "",
                    folderName = null,
                    copiedCount = progress?.copied ?: 0,
                    error = BundleExportError.CANCELLED,
                ),
            )
        }
    }

    private val _importProgress = MutableStateFlow<PlaylistImportProgress?>(null)
    val importProgress: StateFlow<PlaylistImportProgress?> = _importProgress.asStateFlow()

    private val _importSummary = MutableSharedFlow<PlaylistImportSummary>(extraBufferCapacity = 1)
    val importSummary: SharedFlow<PlaylistImportSummary> = _importSummary.asSharedFlow()

    private val importMutex = Mutex()
    private val importQueue = ArrayDeque<Uri>()
    private val importQueuedKeys = mutableSetOf<String>()
    private var importJob: Job? = null
    private var importCompleted = 0
    private var importCurrentName: String? = null
    private val importResults = mutableListOf<XspfImportResult>()

    fun enqueueImports(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importMutex.withLock {
                var added = false
                uris.forEach { uri ->
                    if (importQueuedKeys.add(uri.toString())) {
                        importQueue.addLast(uri)
                        added = true
                    }
                }
                if (!added) return@withLock
                if (importJob == null) {
                    importJob = viewModelScope.launch { runImportConsumer() }
                }
                updateImportProgressLocked()
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    private suspend fun runImportConsumer() {
        val batch = xspfImporter.beginBatch()
        var finalized = false
        try {
            while (currentCoroutineContext().isActive) {
                val item = importMutex.withLock {
                    importQueue.removeFirstOrNull()?.also {
                        importCurrentName = batch.documentDisplayName(it)
                        updateImportProgressLocked()
                    }
                }
                if (item == null) {
                    val summary = importMutex.withLock {
                        if (importQueue.isNotEmpty()) {
                            null
                        } else {
                            val result = PlaylistImportSummary(importResults.toList())
                            importResults.clear()
                            importCompleted = 0
                            importCurrentName = null
                            importQueuedKeys.clear()
                            _importProgress.value = null
                            importJob = null
                            finalized = true
                            result
                        }
                    }
                    if (summary != null) {
                        emitImportSummary(summary)
                        return
                    }
                    continue
                }

                val result = batch.import(item)
                importMutex.withLock {
                    importResults += result
                    importCompleted++
                    importQueuedKeys.remove(item.toString())
                    importCurrentName = null
                    updateImportProgressLocked()
                }
            }
        } finally {
            if (!finalized) {
                withContext(NonCancellable) {
                    val summary = importMutex.withLock {
                        val result = if (importResults.isNotEmpty()) {
                            PlaylistImportSummary(importResults.toList())
                        } else {
                            null
                        }
                        importQueue.clear()
                        importQueuedKeys.clear()
                        importResults.clear()
                        importCompleted = 0
                        importCurrentName = null
                        _importProgress.value = null
                        importJob = null
                        result
                    }
                    if (summary != null) {
                        emitImportSummary(summary)
                    }
                }
            }
        }
    }

    private fun updateImportProgressLocked() {
        val total = importCompleted + (if (importCurrentName != null) 1 else 0) + importQueue.size
        _importProgress.value = if (total == 0) {
            null
        } else {
            PlaylistImportProgress(
                completed = importCompleted,
                total = total,
                currentName = importCurrentName.orEmpty(),
            )
        }
    }

    private suspend fun emitImportSummary(summary: PlaylistImportSummary) {
        if (summary.results.any { it.succeeded }) {
            testerProgress.mark(TesterBeacon.XSPF_IMPORT)
        }
        _importSummary.emit(summary)
    }
}

data class PlaylistImportProgress(
    val completed: Int,
    val total: Int,
    val currentName: String,
)

data class PlaylistImportSummary(
    val results: List<XspfImportResult>,
)
