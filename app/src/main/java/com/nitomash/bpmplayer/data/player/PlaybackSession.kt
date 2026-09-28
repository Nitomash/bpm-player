package com.nitomash.bpmplayer.data.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSession @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var tickerJob: Job? = null
    private var mode: Mode = Mode.Empty
    private var previewSnapshot: QueueSnapshot? = null
    @Volatile
    private var stopOnTrackEnd = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPreview = MutableStateFlow(false)
    val isPreview: StateFlow<Boolean> = _isPreview.asStateFlow()

    private val _previewTrackId = MutableStateFlow<Long?>(null)
    val previewTrackId: StateFlow<Long?> = _previewTrackId.asStateFlow()

    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _currentTrackId = MutableStateFlow<Long?>(null)
    val currentTrackId: StateFlow<Long?> = _currentTrackId.asStateFlow()

    private val _currentPlaylistId = MutableStateFlow<Long?>(null)
    val currentPlaylistId: StateFlow<Long?> = _currentPlaylistId.asStateFlow()

    private val _currentNumber = MutableStateFlow(0)
    val currentNumber: StateFlow<Int> = _currentNumber.asStateFlow()

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
                refreshPosition()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousTrackId = _currentTrackId.value
            updateNavigation()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                if (_currentTrackId.value != previousTrackId) {
                    _positionMs.value = 0L
                }
                if (stopOnTrackEnd) {
                    Log.i(TAG, "Track ended while editor is open, stopping playback")
                    controller?.pause()
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            _queueSize.value = timeline.windowCount
            updateNavigation()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
            updateNavigation()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
            updateNavigation()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && mode == Mode.Preview) {
                restoreQueue()
            }
        }
    }

    fun openQueue(items: List<PlayerTrack>, playlistId: Long? = null, startIndex: Int = 0) {
        if (items.isEmpty()) return
        val start = startIndex.coerceIn(0, items.lastIndex)
        Log.i(TAG, "openQueue: ${items.size} items")
        mode = Mode.Playlist
        _isPreview.value = false
        _previewTrackId.value = null
        _currentPlaylistId.value = playlistId
        previewSnapshot = null
        pendingAction = null
        startPlayback { player -> applyQueue(player, items, start) }
    }

    fun clearPlaylistIdIf(playlistId: Long) {
        if (_currentPlaylistId.value == playlistId) {
            _currentPlaylistId.value = null
        }
    }

    fun previewTrack(track: PlayerTrack) {
        Log.i(TAG, "previewTrack: ${track.title}")
        val snapshot = if (mode == Mode.Preview) previewSnapshot else captureQueue()
        mode = Mode.Preview
        _isPreview.value = true
        _previewTrackId.value = track.trackId
        startPlayback { player -> startPreview(player, track, snapshot) }
    }

    fun endPreview() {
        if (mode != Mode.Preview) return
        restoreQueue()
    }

    fun setStopOnTrackEnd(enabled: Boolean) {
        stopOnTrackEnd = enabled
    }

    private fun captureQueue(): QueueSnapshot? {
        val current = controller ?: return null
        val count = current.mediaItemCount
        if (count == 0) return null
        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items += current.getMediaItemAt(i)
        }
        return QueueSnapshot(
            items = items,
            restoreIndex = current.currentMediaItemIndex.coerceIn(0, items.lastIndex),
            restorePositionMs = if (isAtTrackEnd(current)) 0L else current.currentPosition,
            wasPlaying = current.isPlaying,
        )
    }

    private fun startPreview(player: MediaController, track: PlayerTrack, snapshot: QueueSnapshot?) {
        Log.i(TAG, "startPreview: ${track.title}, snapshot=${snapshot?.items?.size ?: 0} items")
        previewSnapshot = snapshot
        player.setMediaItems(listOf(track.toMediaItem()))
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    private fun restoreQueue() {
        val current = controller
        val snapshot = previewSnapshot
        mode = if (snapshot == null) Mode.Empty else Mode.Playlist
        _isPreview.value = false
        _previewTrackId.value = null
        previewSnapshot = null
        pendingAction = null
        if (current == null) return
        if (snapshot == null) {
            Log.i(TAG, "preview ended, nothing to restore, stopping")
            current.stop()
            current.clearMediaItems()
            return
        }
        Log.i(
            TAG,
            "preview ended, restoring ${snapshot.items.size} items to ${snapshot.restoreIndex} " +
                "at ${snapshot.restorePositionMs}ms wasPlaying=${snapshot.wasPlaying}",
        )
        current.setMediaItems(
            snapshot.items,
            snapshot.restoreIndex.coerceIn(0, snapshot.items.lastIndex),
            snapshot.restorePositionMs,
        )
        if (current.playbackState == Player.STATE_IDLE) current.prepare()
        _positionMs.value = snapshot.restorePositionMs
        _durationMs.value = current.duration.takeIf { it > 0 } ?: _durationMs.value
        if (snapshot.wasPlaying) current.play() else current.pause()
    }

    private fun startPlayback(action: (MediaController) -> Unit) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java),
        )
        val current = controller
        if (current != null) {
            action(current)
            pendingAction = null
        } else {
            pendingAction = action
            connect()
        }
    }

    private fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val built = future.get()
                    built.addListener(listener)
                    controller = built
                    Log.i(TAG, "controller connected")
                    pendingAction?.let { action ->
                        action(built)
                        pendingAction = null
                    }
                    updateNavigation()
                    if (built.isPlaying) startTicker()
                }.onFailure { e ->
                    Log.e(TAG, "controller build failed", e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun applyQueue(player: MediaController, items: List<PlayerTrack>, startIndex: Int) {
        Log.i(TAG, "applying queue of ${items.size} items")
        player.setMediaItems(
            items.map { it.toMediaItem() },
            startIndex,
            0L,
        )
        player.prepare()
        player.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayback() {
        val current = controller ?: return
        if (current.isPlaying) current.pause() else current.play()
    }

    fun togglePreviewPlayback(track: PlayerTrack) {
        val current = controller
        if (current != null) {
            val targetId = if (_isPreview.value) _previewTrackId.value else _currentTrackId.value
            if (current.mediaItemCount > 0 && targetId == track.trackId && !isAtTrackEnd(current)) {
                if (current.isPlaying) current.pause() else current.play()
                return
            }
        }
        previewTrack(track)
    }

    private fun isAtTrackEnd(player: MediaController): Boolean {
        val duration = player.duration
        return duration > 0L && player.currentPosition >= duration - TRACK_END_TOLERANCE_MS
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val current = controller ?: return
        current.shuffleModeEnabled = !current.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val current = controller ?: return
        current.repeatMode = when (current.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun seekTo(positionMs: Long) {
        val current = controller ?: return
        if (current.isPlayingAd) return
        current.seekTo(positionMs)
        _positionMs.value = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(positionMs))
    }

    fun skipForward() {
        val current = controller ?: return
        val duration = current.duration
        if (duration <= 0L) return
        val target = current.currentPosition + SKIP_FORWARD_STEP_MS
        if (target >= duration) {
            seekTo(duration)
            if (!_isPreview.value) current.pause()
            return
        }
        seekTo(target)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                refreshPosition()
                delay(TICKER_INTERVAL_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun refreshPosition() {
        val current = controller ?: return
        if (current.isPlayingAd) return
        val duration = current.duration.takeIf { it > 0 } ?: 0L
        _durationMs.value = duration
        _positionMs.value = current.currentPosition.coerceIn(0L, duration)
    }

    private fun updateNavigation() {
        val current = controller ?: return
        _currentTitle.value = current.currentMediaItem?.mediaMetadata?.title?.toString()
        _currentTrackId.value = current.currentMediaItem?.mediaId?.toLongOrNull()
        _currentNumber.value = current.currentMediaItemIndex + 1
        _queueSize.value = current.mediaItemCount
        _isPlaying.value = current.isPlaying
        _repeatMode.value = current.repeatMode
        _shuffleEnabled.value = current.shuffleModeEnabled
        _hasPrevious.value = current.hasPreviousMediaItem()
        _hasNext.value = current.hasNextMediaItem()
        refreshPosition()
    }

    private enum class Mode {
        Empty,
        Playlist,
        Preview,
    }

    private data class QueueSnapshot(
        val items: List<MediaItem>,
        val restoreIndex: Int,
        val restorePositionMs: Long,
        val wasPlaying: Boolean,
    )

    private companion object {
        const val TAG = "BpmPlayback"
        const val TICKER_INTERVAL_MS = 500L
        const val SKIP_FORWARD_STEP_MS = 15_000L
        const val TRACK_END_TOLERANCE_MS = 1_000L
    }
}