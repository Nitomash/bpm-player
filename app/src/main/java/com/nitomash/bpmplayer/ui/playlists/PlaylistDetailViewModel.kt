package com.nitomash.bpmplayer.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.db.entity.PlaylistEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSorter
import com.nitomash.bpmplayer.ui.common.BpmLog
import com.nitomash.bpmplayer.ui.common.ListReorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val playbackSession: PlaybackSession,
) : ViewModel() {

    val playlistId: Long = savedStateHandle.get<Long>(ARG_PLAYLIST_ID)
        ?: error("playlistId argument missing")

    val playlist: StateFlow<PlaylistEntity?> = playlistRepository.observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sortMode: StateFlow<PlaylistTrackSort> = playlistRepository.observePlaylist(playlistId)
        .map { it?.trackSortMode ?: PlaylistTrackSort.CUSTOM }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistTrackSort.CUSTOM)

    private val _reorderIds = MutableStateFlow<List<Long>?>(null)
    val reorderIds: StateFlow<List<Long>?> = _reorderIds.asStateFlow()

    private val _reorderSession = MutableStateFlow(0)
    val reorderSession: StateFlow<Int> = _reorderSession.asStateFlow()

    val tracks: StateFlow<List<TrackEntity>> = combine(
        playlistRepository.observeTrackRows(playlistId),
        sortMode,
        _reorderIds,
    ) { rows, mode, draft ->
        val sorted = PlaylistTrackSorter.sort(rows, mode).map { it.track }
        if (draft == null) {
            sorted
        } else {
            val positions = draft.withIndex().associate { (index, id) -> id to index }
            sorted.sortedWith(
                compareBy({ positions[it.id] ?: Int.MAX_VALUE }, { it.id }),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentTrackId = playbackSession.currentTrackId
    val currentPlaylistId = playbackSession.currentPlaylistId
    val isPreview = playbackSession.isPreview

    private val _mode = MutableStateFlow(PlaylistDetailMode.NORMAL)
    val mode: StateFlow<PlaylistDetailMode> = _mode.asStateFlow()

    val selectionMode: StateFlow<Boolean> = _mode
        .map { it == PlaylistDetailMode.SELECT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.rename(playlistId, trimmed)
        }
    }

    fun setSort(sort: PlaylistTrackSort) {
        if (sort == sortMode.value) return
        if (sort != PlaylistTrackSort.CUSTOM) clearReorder()
        viewModelScope.launch {
            playlistRepository.setTrackSortMode(playlistId, sort)
        }
    }

    fun playFrom(index: Int) {
        viewModelScope.launch {
            val queue = playlistRepository.getTracksInDisplayOrder(playlistId)
            if (queue.isEmpty()) return@launch
            playbackSession.openQueue(queue.map { it.toPlayerTrack() }, playlistId, index)
        }
    }

    fun enterReorder() {
        BpmLog.d("VM detail: enterReorder n=${tracks.value.size} sort=${sortMode.value}")
        if (tracks.value.size < 2) return
        _reorderIds.value = tracks.value.map { it.id }
        _reorderSession.value += 1
        _mode.value = PlaylistDetailMode.REORDER
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
            playlistRepository.setTrackOrder(playlistId, draft)
            playlistRepository.setTrackSortMode(playlistId, PlaylistTrackSort.CUSTOM)
            BpmLog.d("VM detail: committed $draft")
            clearReorder()
        }
    }

    fun exitReorder() {
        clearReorder()
    }

    private fun clearReorder() {
        _reorderIds.value = null
        if (_mode.value == PlaylistDetailMode.REORDER) {
            _mode.value = PlaylistDetailMode.NORMAL
        }
    }

    fun exitSelection() {
        _mode.value = PlaylistDetailMode.NORMAL
        _selectedIds.value = emptySet()
    }

    fun enterSelection(trackId: Long) {
        _selectedIds.value = setOf(trackId)
        _mode.value = PlaylistDetailMode.SELECT
    }

    fun toggleSelected(trackId: Long) {
        _selectedIds.value = if (trackId in _selectedIds.value) {
            _selectedIds.value - trackId
        } else {
            _selectedIds.value + trackId
        }
    }

    fun removeSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.removeTracks(playlistId, ids)
            exitSelection()
        }
    }

    fun removeTrack(trackId: Long) {
        viewModelScope.launch {
            playlistRepository.removeTracks(playlistId, setOf(trackId))
        }
    }

    companion object {
        const val ROUTE = "playlist_detail"
        const val ARG_PLAYLIST_ID = "playlistId"

        fun route(playlistId: Long): String = "$ROUTE/$playlistId"
    }
}