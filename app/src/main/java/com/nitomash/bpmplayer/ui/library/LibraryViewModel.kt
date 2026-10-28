package com.nitomash.bpmplayer.ui.library

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.music.SidecarSyncManager
import com.nitomash.bpmplayer.data.playlist.PlaylistListSorter
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val sidecarSync: SidecarSyncManager,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<TrackEntity>> = repository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    private val _importResult = MutableSharedFlow<Int>()
    val importResult: SharedFlow<Int> = _importResult.asSharedFlow()

    private val _folderDeleted = MutableSharedFlow<Unit>()
    val folderDeleted: SharedFlow<Unit> = _folderDeleted.asSharedFlow()

    private val _exportError = MutableSharedFlow<String>()
    val exportError: SharedFlow<String> = _exportError.asSharedFlow()

    private val _folderSort = MutableStateFlow<Map<Long, TrackSort>>(emptyMap())
    val folderSort: StateFlow<Map<Long, TrackSort>> = _folderSort.asStateFlow()

    private val _collapsedFolders = MutableStateFlow<Set<Long>>(emptySet())
    val collapsedFolders: StateFlow<Set<Long>> = _collapsedFolders.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val filterQuery: StateFlow<String> = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val content: StateFlow<LibraryContent> = combine(
        folders,
        tracks,
        filterQuery,
        _folderSort,
    ) { folderList, trackList, query, sortByFolder ->
        buildLibraryContent(folderList, trackList, query, sortByFolder)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContent())

    val playlists: StateFlow<List<PlaylistSummary>> = combine(
        playlistRepository.observePlaylists(),
        settingsRepository.observe(),
    ) { summaries, settings ->
        PlaylistListSorter.sort(summaries, settings.playlistListSort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _menuTrackId = MutableStateFlow<Long?>(null)
    val menuTrackId: StateFlow<Long?> = _menuTrackId.asStateFlow()

    val playlistsContainingMenuTrack: StateFlow<Set<Long>> = _menuTrackId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptySet())
            else playlistRepository.observePlaylistIdsContaining(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openPlaylistMenu(trackId: Long) {
        _menuTrackId.value = trackId
    }

    fun closePlaylistMenu() {
        _menuTrackId.value = null
    }

    fun toggleTrackInPlaylist(trackId: Long, playlistId: Long) {
        val isMember = playlistId in playlistsContainingMenuTrack.value
        viewModelScope.launch {
            if (isMember) {
                playlistRepository.removeTracks(playlistId, setOf(trackId))
            } else {
                playlistRepository.addTracks(playlistId, listOf(trackId))
            }
        }
    }

    fun setFolderSort(folderId: Long, sort: TrackSort) {
        _folderSort.value = _folderSort.value + (folderId to sort)
    }

    fun toggleFolderCollapsed(folderId: Long) {
        _collapsedFolders.value = if (folderId in _collapsedFolders.value) {
            _collapsedFolders.value - folderId
        } else {
            _collapsedFolders.value + folderId
        }
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val folderId = repository.addFolder(uri)
                sidecarSync.requestFlush(folderId)
            } catch (e: Exception) {
                Log.e(TAG, "addFolder failed for $uri", e)
                _error.emit(e.toString())
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun removeFolder(folderId: Long) {
        viewModelScope.launch {
            repository.removeFolder(folderId)
            _folderDeleted.emit(Unit)
        }
    }

    fun rescanFolder(folderId: Long) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.rescanFolder(folderId)
                sidecarSync.requestFlush(folderId)
            } catch (e: Exception) {
                Log.e(TAG, "rescanFolder failed for $folderId", e)
                _error.emit(e.toString())
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            if (_isScanning.value) return@launch
            _isScanning.value = true
            try {
                val folderList = repository.getFolders()
                folderList.forEach { folder ->
                    try {
                        repository.rescanFolder(folder.id)
                        sidecarSync.requestFlush(folder.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "refreshAll failed for ${folder.id}", e)
                        _error.emit(e.toString())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshAll failed", e)
                _error.emit(e.toString())
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun importSidecarFile(folderId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                val count = repository.importSidecarFile(folderId, uri)
                if (count > 0) sidecarSync.requestFlush(folderId)
                _importResult.emit(count)
            } catch (e: Exception) {
                Log.e(TAG, "importSidecarFile failed for $uri", e)
                _error.emit(e.toString())
            }
        }
    }

    fun exportSidecarFile(folderId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                val ok = repository.exportSidecarFile(folderId, uri)
                if (!ok) _exportError.emit("write failed")
            } catch (e: Exception) {
                Log.e(TAG, "exportSidecarFile failed for $uri", e)
                _exportError.emit(e.toString())
            }
        }
    }

    private companion object {
        const val TAG = "BpmLibrary"
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}