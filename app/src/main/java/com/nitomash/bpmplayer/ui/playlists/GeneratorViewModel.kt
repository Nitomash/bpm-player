package com.nitomash.bpmplayer.ui.playlists

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.music.resolveFolderIdsForGeneration
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.player.toPlayerTrack
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.domain.playlist.PlaylistGenerator
import com.nitomash.bpmplayer.domain.playlist.PlaylistNameSpec
import com.nitomash.bpmplayer.domain.playlist.PlaylistOrder
import com.nitomash.bpmplayer.domain.playlist.PlaylistTrack
import com.nitomash.bpmplayer.ui.editor.formatBpm
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackSession: PlaybackSession,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _result = MutableStateFlow<List<TrackEntity>>(emptyList())
    val result: StateFlow<List<TrackEntity>> = _result.asStateFlow()

    private val _generated = MutableStateFlow(false)
    val generated: StateFlow<Boolean> = _generated.asStateFlow()

    private val _savedToPlaylist = MutableSharedFlow<Unit>()
    val savedToPlaylist: SharedFlow<Unit> = _savedToPlaylist.asSharedFlow()

    val currentTitle = playbackSession.currentTitle
    val currentNumber = playbackSession.currentNumber
    val queueSize = playbackSession.queueSize
    val isPlaying = playbackSession.isPlaying
    val hasPrevious = playbackSession.hasPrevious
    val hasNext = playbackSession.hasNext
    val shuffleEnabled = playbackSession.shuffleEnabled

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun generate(allFolders: Boolean, folderIds: Set<Long>, minText: String, maxText: String, countText: String, order: PlaylistOrder) {
        viewModelScope.launch {
            _generating.value = true
            val resolvedIds = resolveFolderIdsForGeneration(repository.getFolders(), allFolders, folderIds)
            val tracks = repository.getTracksWithBpm()
                .filter { it.folderId in resolvedIds }
            val candidates = tracks.mapNotNull { track ->
                track.bpm?.let { bpm -> PlaylistTrack(track.id, bpm) }
            }
            val spec = PlaylistNameSpec.parse(minText, maxText, countText)
            val selected = PlaylistGenerator.generate(
                candidates = candidates,
                minBpm = spec.minBpm,
                maxBpm = spec.maxBpm,
                count = spec.trackCount,
                order = order,
            )
            val byId = tracks.associateBy { it.id }
            _result.value = selected.mapNotNull { byId[it.trackId] }
            _generated.value = true
            _generating.value = false
        }
    }

    fun saveToPlaylist(minText: String, maxText: String, order: PlaylistOrder) {
        viewModelScope.launch {
            val tracks = _result.value
            if (tracks.isEmpty()) return@launch
            val spec = PlaylistNameSpec.parse(minText, maxText, "")
            val playlistId = playlistRepository.create(renderPlaylistName(spec, order))
            playlistRepository.addTracks(playlistId, tracks.map { it.id })
            _savedToPlaylist.emit(Unit)
        }
    }

    fun openPlayer() {
        val queue = _result.value.map { it.toPlayerTrack() }
        if (queue.isEmpty()) return
        playbackSession.openQueue(queue)
    }

    private fun renderPlaylistName(spec: PlaylistNameSpec, order: PlaylistOrder): String {
        val range = when {
            spec.minBpm != null && spec.maxBpm != null -> context.getString(
                R.string.playlist_name_range_both,
                formatBpm(spec.minBpm),
                formatBpm(spec.maxBpm),
            )
            spec.minBpm != null -> context.getString(R.string.playlist_name_range_min, formatBpm(spec.minBpm))
            spec.maxBpm != null -> context.getString(R.string.playlist_name_range_max, formatBpm(spec.maxBpm))
            else -> context.getString(R.string.playlist_name_range_any)
        }
        val orderLabel = context.getString(playlistOrderLabelRes(order))
        val createdAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        return context.getString(R.string.playlist_name_full, range, orderLabel, createdAt)
    }
}