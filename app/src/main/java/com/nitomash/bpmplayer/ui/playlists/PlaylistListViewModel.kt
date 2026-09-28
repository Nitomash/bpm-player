package com.nitomash.bpmplayer.ui.playlists

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort
import com.nitomash.bpmplayer.data.playlist.PlaylistListSorter
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import com.nitomash.bpmplayer.ui.common.BpmLog
import com.nitomash.bpmplayer.ui.common.ListReorder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackSession: PlaybackSession,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val listSort: StateFlow<PlaylistListSort> = settingsRepository.observe()
        .map { it.playlistListSort }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistListSort.DATE_MODIFIED)

    private val _reorderIds = MutableStateFlow<List<Long>?>(null)
    val reorderIds: StateFlow<List<Long>?> = _reorderIds.asStateFlow()

    private val _reorderSession = MutableStateFlow(0)
    val reorderSession: StateFlow<Int> = _reorderSession.asStateFlow()

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
        }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.delete(playlistId)
            playbackSession.clearPlaylistIdIf(playlistId)
        }
    }

    fun play(playlistId: Long, onStarted: () -> Unit) {
        viewModelScope.launch {
            val tracks = playlistRepository.getTracksInDisplayOrder(playlistId)
            if (tracks.isEmpty()) return@launch
            playbackSession.openQueue(tracks.map { it.toPlayerTrack() }, playlistId)
            onStarted()
        }
    }

    fun setListSort(sort: PlaylistListSort) {
        if (sort == listSort.value) return
        discardReorder()
        viewModelScope.launch {
            persistListSort(sort)
        }
    }

    fun enterReorder() {
        BpmLog.d("VM list: enterReorder n=${playlists.value.size} sort=${listSort.value}")
        if (playlists.value.size < 2) return
        _reorderIds.value = playlists.value.map { it.id }
        _reorderSession.value += 1
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
            BpmLog.d("VM list: committed $draft")
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
}
