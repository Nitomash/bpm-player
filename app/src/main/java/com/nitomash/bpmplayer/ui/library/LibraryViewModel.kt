package com.nitomash.bpmplayer.ui.library

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.music.SidecarSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val sidecarSync: SidecarSyncManager,
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
    }
}