package com.nitomash.bpmplayer.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.ui.common.buildIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTracksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val playbackSession: PlaybackSession,
) : ViewModel() {

    val playlistId: Long = savedStateHandle.get<Long>(ARG_PLAYLIST_ID)
        ?: error("playlistId argument missing")

    private val _candidates = MutableStateFlow<List<TrackEntity>>(emptyList())
    val candidates: StateFlow<List<TrackEntity>> = _candidates.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val filterQuery: StateFlow<String> = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val visible: StateFlow<List<TrackEntity>> = combine(_candidates, filterQuery) { candidates, query ->
        buildIndex(candidates).query(query)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    val isPlaying: StateFlow<Boolean> = playbackSession.isPlaying
    val previewTrackId: StateFlow<Long?> = playbackSession.previewTrackId

    init {
        viewModelScope.launch {
            _candidates.value = playlistRepository.getTracksNotInPlaylist(playlistId)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelected(trackId: Long) {
        _selectedIds.value = if (trackId in _selectedIds.value) {
            _selectedIds.value - trackId
        } else {
            _selectedIds.value + trackId
        }
    }

    fun togglePreview(trackId: Long) {
        if (previewTrackId.value == trackId) {
            playbackSession.endPreview()
            return
        }
        val track = _candidates.value.firstOrNull { it.id == trackId } ?: return
        playbackSession.previewTrack(track.toPlayerTrack())
    }

    fun save() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.addTracks(playlistId, selectedInCandidateOrder(selected))
            _saved.emit(Unit)
        }
    }

    private fun selectedInCandidateOrder(selected: Set<Long>): List<Long> =
        _candidates.value.map { it.id }.filter { it in selected }

    override fun onCleared() {
        playbackSession.endPreview()
    }

    companion object {
        const val ROUTE = "playlist_add"
        const val ARG_PLAYLIST_ID = "playlistId"
        private const val SEARCH_DEBOUNCE_MS = 250L

        fun route(playlistId: Long): String = "$ROUTE/$playlistId"
    }
}