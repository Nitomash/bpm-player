package com.nitomash.bpmplayer.data.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.ui.playlists.GeneratedPlaylist
import com.nitomash.bpmplayer.ui.playlists.GeneratedPlaylistStore
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.settings.AppSettings
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dataStore: DataStore<Preferences>,
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    private val generatedPlaylistStore: GeneratedPlaylistStore,
) {
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var tickerJob: Job? = null
    private var gapJob: Job? = null
    private var mode: Mode = Mode.Empty
    private var previewSnapshot: QueueSnapshot? = null
    private var currentQueue: List<PlayerTrack>? = null
    private var originalQueue: List<PlayerTrack> = emptyList()
    private var playbackOrder: List<PlayerTrack> = emptyList()
    private var currentIndex: Int = 0
    private var appliedLimits: PlaybackLimits? = null
    private var latestSettings: AppSettings? = null
    @Volatile
    private var stopOnTrackEnd = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _hasUserStarted = MutableStateFlow(false)
    val hasUserStarted: StateFlow<Boolean> = _hasUserStarted.asStateFlow()

    private val _isPreview = MutableStateFlow(false)
    val isPreview: StateFlow<Boolean> = _isPreview.asStateFlow()

    private val _previewTrackId = MutableStateFlow<Long?>(null)
    val previewTrackId: StateFlow<Long?> = _previewTrackId.asStateFlow()

    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _currentBpm = MutableStateFlow<Double?>(null)
    val currentBpm: StateFlow<Double?> = _currentBpm.asStateFlow()

    private val _currentTrackId = MutableStateFlow<Long?>(null)
    val currentTrackId: StateFlow<Long?> = _currentTrackId.asStateFlow()

    private val _currentPlaylistId = MutableStateFlow<Long?>(null)
    val currentPlaylistId: StateFlow<Long?> = _currentPlaylistId.asStateFlow()

    private val _isGeneratedQueue = MutableStateFlow(false)
    val isGeneratedQueue: StateFlow<Boolean> = _isGeneratedQueue.asStateFlow()

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

    private val _gapActive = MutableStateFlow(false)
    val gapActive: StateFlow<Boolean> = _gapActive.asStateFlow()

    private val _limits = MutableStateFlow(PlaybackLimits())

    init {
        scope.launch { restoreIfNeeded(play = false) }
        scope.launch {
            settingsRepository.observe().collect { settings ->
                latestSettings = settings
                onLimitsChanged(settings.toPlaybackLimits())
                applyPersistedPlaybackState()
            }
        }
    }

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
            restoreVolume()
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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) return
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) return
            Log.i(TAG, "player paused at end of item (mode=$mode, stopOnTrackEnd=$stopOnTrackEnd)")
            Log.i(TAG, "onPlayerPausedAtItemEnd: mode=$mode stopOnTrackEnd=$stopOnTrackEnd")
            onPlayerPausedAtItemEnd()
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
            if (playbackState != Player.STATE_ENDED) return
            if (mode == Mode.Preview) {
                restoreQueue()
                return
            }
            if (mode == Mode.Playlist && !stopOnTrackEnd) {
                when (_repeatMode.value) {
                    Player.REPEAT_MODE_ONE -> {
                        controller?.seekTo(0L)
                        controller?.play()
                        _positionMs.value = 0L
                        refreshPosition()
                        updateNavigation()
                        return
                    }
                    Player.REPEAT_MODE_ALL -> {
                        reshuffleAndRestart()
                        return
                    }
                }
            }
            if (
                mode == Mode.Playlist &&
                _limits.value.enabled &&
                !stopOnTrackEnd
            ) {
                Log.i(TAG, "queue ended with interval playback enabled, restarting from beginning")
                val current = controller ?: return
                current.seekTo(0L)
                current.pause()
                _positionMs.value = 0L
                refreshPosition()
                updateNavigation()
            } else if (mode == Mode.Playlist && !stopOnTrackEnd && currentIndex < playbackOrder.lastIndex) {
                loadTrack(currentIndex + 1, true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            cancelGap()
        }
    }

    fun openQueue(
        items: List<PlayerTrack>,
        playlistId: Long? = null,
        startIndex: Int = 0,
        generated: Boolean = false,
        randomStart: Boolean = false,
    ) {
        if (items.isEmpty()) return
        val start = startIndex.coerceIn(0, items.lastIndex)
        Log.i(TAG, "openQueue: ${items.size} items")
        cancelGap()
        mode = Mode.Playlist
        _hasUserStarted.value = true
        originalQueue = items
        _isPreview.value = false
        _previewTrackId.value = null
        _currentPlaylistId.value = playlistId
        _isGeneratedQueue.value = generated
        previewSnapshot = null
        pendingAction = null
        scope.launch {
            latestSettings = settingsRepository.current()
            val shuffle = latestSettings?.shuffleEnabled == true
            _shuffleEnabled.value = shuffle
            _repeatMode.value = latestSettings?.repeatMode ?: Player.REPEAT_MODE_OFF
            val actualStart = if (randomStart && shuffle && items.isNotEmpty()) kotlin.random.Random.nextInt(items.size) else start
            playbackOrder = buildPlaybackOrder(items, actualStart, shuffle)
            currentIndex = if (shuffle) 0 else actualStart.coerceIn(0, items.lastIndex)
            _queueSize.value = playbackOrder.size
            _currentBpm.value = playbackOrder.getOrNull(currentIndex)?.bpm
            _currentTrackId.value = playbackOrder.getOrNull(currentIndex)?.trackId
            _currentTitle.value = playbackOrder.getOrNull(currentIndex)?.title
            _currentNumber.value = currentIndex + 1
            _hasPrevious.value = currentIndex > 0 || _repeatMode.value != Player.REPEAT_MODE_OFF
            _hasNext.value = currentIndex < playbackOrder.lastIndex || _repeatMode.value != Player.REPEAT_MODE_OFF
            startPlayback { player ->
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_OFF
                val track = playbackOrder.getOrNull(currentIndex)
                if (track != null) {
                    player.setMediaItems(listOf(track).map { it.toMediaItem() }, 0, 0L)
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private fun buildPlaybackOrder(items: List<PlayerTrack>, startIndex: Int, shuffle: Boolean): List<PlayerTrack> {
        if (items.isEmpty()) return emptyList()
        if (!shuffle) return items.toList()
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        val current = items[safeIndex]
        val rest = items.filterIndexed { index, _ -> index != safeIndex }.shuffled()
        return listOf(current) + rest
    }

    fun clearPlaylistIdIf(playlistId: Long) {
        if (_currentPlaylistId.value == playlistId) {
            _currentPlaylistId.value = null
        }
    }

    fun setCurrentBpm(bpm: Double?) {
        _currentBpm.value = bpm
    }

    fun updatePlaybackOrderBpm(trackId: Long, bpm: Double?) {
        playbackOrder = playbackOrder.map { if (it.trackId == trackId) it.copy(bpm = bpm) else it }
        originalQueue = originalQueue.map { if (it.trackId == trackId) it.copy(bpm = bpm) else it }
        if (_currentTrackId.value == trackId) {
            _currentBpm.value = bpm
        }
    }

    fun previewTrack(track: PlayerTrack) {
        Log.i(TAG, "previewTrack: ${track.title}")
        cancelGap()
        val snapshot = if (mode == Mode.Preview) previewSnapshot else captureQueue()
        mode = Mode.Preview
        _isPreview.value = true
        _previewTrackId.value = track.trackId
        _currentBpm.value = track.bpm
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
        cancelGap()
        if (current == null) return
        if (snapshot == null) {
            Log.i(TAG, "preview ended, nothing to restore, stopping")
            currentQueue = null
            _isGeneratedQueue.value = false
            _currentBpm.value = null
            current.stop()
            current.clearMediaItems()
            return
        }
        val items = currentQueue?.let(::buildQueueItems) ?: snapshot.items
        Log.i(
            TAG,
            "preview ended, restoring ${items.size} items to ${snapshot.restoreIndex} " +
                "at ${snapshot.restorePositionMs}ms wasPlaying=${snapshot.wasPlaying}",
        )
        current.setMediaItems(
            items,
            snapshot.restoreIndex.coerceIn(0, items.lastIndex),
            snapshot.restorePositionMs,
        )
        if (current.playbackState == Player.STATE_IDLE) current.prepare()
        _positionMs.value = snapshot.restorePositionMs
        _durationMs.value = current.duration.takeIf { it > 0 } ?: _durationMs.value
        _currentBpm.value = currentQueue?.firstOrNull { it.trackId == _currentTrackId.value }?.bpm
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
                    applyPersistedPlaybackState()
                    if (built.isPlaying) startTicker()
                }.onFailure { e ->
                    Log.e(TAG, "controller build failed", e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun applyPersistedPlaybackState(player: MediaController? = null) {
        // Shuffle/repeat are managed by the app (playbackOrder/_repeatMode); do not let Media3 override them.
    }

    private suspend fun restoreIfNeeded(play: Boolean = false) {
        val state = loadResumeState() ?: return
        if (state.trackIds.isEmpty()) {
            clearResumeState()
            return
        }
        val entities = state.trackIds.mapNotNull { id -> musicRepository.getById(id) }
        if (entities.isEmpty()) {
            clearResumeState()
            return
        }
        val tracks = entities.map { it.toPlayerTrack() }
        val settings = settingsRepository.current()
        val shuffle = settings.shuffleEnabled
        val safeIndex = state.trackIds.indexOf(state.currentTrackId).coerceAtLeast(0).coerceAtMost(tracks.lastIndex)
        mode = Mode.Playlist
        originalQueue = tracks
        _isPreview.value = false
        _previewTrackId.value = null
        _currentPlaylistId.value = state.playlistId
        _isGeneratedQueue.value = state.playlistId == null
        if (state.playlistId == null) {
            generatedPlaylistStore.put(GeneratedPlaylist(name = "Сгенерированный плейлист", tracks = entities))
        }
        previewSnapshot = null
        pendingAction = null
        cancelGap()
        _shuffleEnabled.value = shuffle
        _repeatMode.value = settings.repeatMode
        playbackOrder = if (shuffle) buildPlaybackOrder(tracks, safeIndex, true) else tracks
        currentIndex = if (shuffle) 0 else safeIndex
        _queueSize.value = playbackOrder.size
        _currentBpm.value = playbackOrder.getOrNull(currentIndex)?.bpm
        _currentTrackId.value = playbackOrder.getOrNull(currentIndex)?.trackId
        _currentTitle.value = playbackOrder.getOrNull(currentIndex)?.title
        _currentNumber.value = currentIndex + 1
        _hasPrevious.value = currentIndex > 0 || _repeatMode.value != Player.REPEAT_MODE_OFF
        _hasNext.value = currentIndex < playbackOrder.lastIndex || _repeatMode.value != Player.REPEAT_MODE_OFF
        _positionMs.value = state.positionMs
        startPlayback { player ->
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setMediaItems(buildQueueItems(playbackOrder), currentIndex, state.positionMs)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (play) player.play() else player.pause()
        }
        clearResumeState()
    }

    fun saveResumeState() {
        val ids = originalQueue.map { it.trackId }
        val currentId = _currentTrackId.value
        if (ids.isEmpty() && currentId == null) {
            clearResumeState()
            return
        }
        val playlistId = _currentPlaylistId.value
        val position = _positionMs.value
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[KEY_TRACK_IDS] = ids.joinToString(",")
                prefs[KEY_PLAYLIST_ID] = playlistId ?: -1L
                prefs[KEY_CURRENT_TRACK_ID] = currentId ?: -1L
                prefs[KEY_POSITION_MS] = position
            }
        }
    }

    fun clearResumeState() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs.remove(KEY_TRACK_IDS)
                prefs.remove(KEY_PLAYLIST_ID)
                prefs.remove(KEY_CURRENT_TRACK_ID)
                prefs.remove(KEY_POSITION_MS)
            }
        }
    }

    private suspend fun loadResumeState(): ResumeState? {
        val prefs = dataStore.data.firstOrNull() ?: return null
        val idsRaw = prefs[KEY_TRACK_IDS] ?: return null
        val ids = idsRaw.split(',').mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return ResumeState(
            playlistId = prefs[KEY_PLAYLIST_ID]?.takeIf { it != -1L },
            trackIds = ids,
            currentTrackId = prefs[KEY_CURRENT_TRACK_ID]?.takeIf { it != -1L },
            positionMs = prefs[KEY_POSITION_MS] ?: 0L,
        )
    }

    private data class ResumeState(
        val playlistId: Long?,
        val trackIds: List<Long>,
        val currentTrackId: Long?,
        val positionMs: Long,
    )

    private fun applyQueue(player: MediaController, items: List<PlayerTrack>, startIndex: Int) {
        Log.i(TAG, "applying queue of ${items.size} items")
        player.setMediaItems(
            buildQueueItems(items),
            startIndex,
            0L,
        )
        player.prepare()
        player.play()
    }

    private fun buildQueueItems(items: List<PlayerTrack>): List<MediaItem> =
        items.map { it.toMediaItem() }

    private fun onLimitsChanged(limits: PlaybackLimits) {
        val previous = appliedLimits
        appliedLimits = limits
        _limits.value = limits
        Log.i(
            TAG,
            "limits changed: enabled=${limits.enabled} limit=${limits.limitSeconds}s " +
                "gap=${limits.gapSeconds}s mode=$mode",
        )
        if (previous != null && previous != limits) {
            restartCurrentTrack()
        }
    }

    private fun restartCurrentTrack() {
        val current = controller ?: return
        if (current.mediaItemCount == 0 || current.playbackState == Player.STATE_IDLE) return
        Log.i(TAG, "interval settings changed, restarting current track from 0")
        cancelGap()
        current.pause()
        current.seekTo(0L)
        _positionMs.value = 0L
    }

    private fun onPlayerPausedAtItemEnd() {
        val current = controller
        if (mode == Mode.Preview) {
            restoreQueue()
            return
        }
        if (current == null) {
            return
        }
        if (!current.hasNextMediaItem()) {
            Log.i(TAG, "no next item, shouldStartGap=${shouldStartGap(limits = _limits.value, isPlaylistMode = mode == Mode.Playlist, stopOnTrackEnd = stopOnTrackEnd)}")
            if (
                shouldStartGap(
                    limits = _limits.value,
                    isPlaylistMode = mode == Mode.Playlist,
                    stopOnTrackEnd = stopOnTrackEnd,
                )
            ) {
                Log.i(TAG, "last item ended with interval playback enabled, restarting from beginning")
                current.seekTo(0L)
                current.pause()
                _positionMs.value = 0L
                refreshPosition()
                updateNavigation()
                return
            }
            Log.i(TAG, "item ended without a next one, not starting a gap (mode=$mode)")
            return
        }
        if (
            !shouldStartGap(
                limits = _limits.value,
                isPlaylistMode = mode == Mode.Playlist,
                stopOnTrackEnd = stopOnTrackEnd,
            )
        ) {
            return
        }
        advanceToNextItem()
        startGap()
    }

    private fun advanceToNextItem() {
        val current = controller ?: return
        if (
            !shouldAdvanceToNextItem(
                hasNext = current.hasNextMediaItem(),
                positionMs = current.currentPosition,
                durationMs = current.duration,
            )
        ) {
            return
        }
        Log.i(TAG, "moving to next item before the gap")
        current.seekToNextMediaItem()
    }

    private fun startGap() {
        val gapMs = _limits.value.gapSeconds.toLong() * 1000L
        if (gapMs <= 0L) return
        Log.i(TAG, "starting gap of ${gapMs}ms")
        controller?.pause()
        _gapActive.value = true
        gapJob?.cancel()
        gapJob = scope.launch {
            delay(gapMs)
            _gapActive.value = false
            controller?.play()
        }
    }

    private fun cancelGap() {
        gapJob?.cancel()
        gapJob = null
        _gapActive.value = false
        restoreVolume()
    }

    fun pause() {
        cancelGap()
        controller?.pause()
    }

    fun togglePlayback() {
        if (playbackOrder.isEmpty()) return
        _hasUserStarted.value = true
        val current = controller
        if (current == null) {
            startPlayback { player ->
                player.setMediaItems(buildQueueItems(playbackOrder), currentIndex, _positionMs.value)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
            return
        }
        cancelGap()
        if (current.isPlaying) current.pause() else current.play()
    }

    fun togglePreviewPlayback(track: PlayerTrack) {
        val current = controller
        if (current != null) {
            val targetId = if (_isPreview.value) _previewTrackId.value else _currentTrackId.value
            if (current.mediaItemCount > 0 && targetId == track.trackId && !isAtTrackEnd(current)) {
                cancelGap()
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
        cancelGap()
        if (playbackOrder.isEmpty()) return
        if (currentIndex < playbackOrder.lastIndex) {
            loadTrack(currentIndex + 1, controller?.isPlaying == true)
        } else if (_repeatMode.value != Player.REPEAT_MODE_OFF) {
            reshuffleAndRestart()
        }
    }

    fun previous() {
        cancelGap()
        if (playbackOrder.isEmpty()) return
        if (currentIndex > 0) {
            loadTrack(currentIndex - 1, controller?.isPlaying == true)
        } else if (_repeatMode.value != Player.REPEAT_MODE_OFF) {
            loadTrack(playbackOrder.lastIndex, controller?.isPlaying == true)
        }
    }

    private fun loadTrack(index: Int, play: Boolean) {
        val track = playbackOrder.getOrNull(index) ?: return
        currentIndex = index
        _currentTrackId.value = track.trackId
        _currentBpm.value = track.bpm
        _currentTitle.value = track.title
        _currentNumber.value = index + 1
        _hasPrevious.value = index > 0 || _repeatMode.value != Player.REPEAT_MODE_OFF
        _hasNext.value = index < playbackOrder.lastIndex || _repeatMode.value != Player.REPEAT_MODE_OFF
        _positionMs.value = 0L
        val player = controller ?: return
        player.setMediaItems(listOf(track).map { it.toMediaItem() }, 0, 0L)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (play) player.play() else player.pause()
    }

    private fun reshuffleAndRestart() {
        if (playbackOrder.isEmpty()) return
        val previousTrackId = playbackOrder.getOrNull(playbackOrder.lastIndex)?.trackId
        if (_shuffleEnabled.value) {
            var shuffled = originalQueue.shuffled()
            var attempts = 0
            while (shuffled.isNotEmpty() && previousTrackId != null && shuffled.first().trackId == previousTrackId && attempts < 10) {
                shuffled = originalQueue.shuffled()
                attempts++
            }
            playbackOrder = shuffled
        } else {
            playbackOrder = originalQueue.toList()
        }
        currentIndex = 0
        _queueSize.value = playbackOrder.size
        _currentBpm.value = playbackOrder.firstOrNull()?.bpm
        _currentTrackId.value = playbackOrder.firstOrNull()?.trackId
        _currentTitle.value = playbackOrder.firstOrNull()?.title
        val player = controller ?: return
        player.setMediaItems(buildQueueItems(playbackOrder), 0, 0L)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }


    private var shuffleJob: Job? = null

    fun toggleShuffle() {
        val desired = !_shuffleEnabled.value
        _shuffleEnabled.value = desired
        shuffleJob?.cancel()
        shuffleJob = scope.launch {
            val curTrackId = _currentTrackId.value ?: playbackOrder.getOrNull(currentIndex)?.trackId
            val curTrack = playbackOrder.firstOrNull { it.trackId == curTrackId }
                ?: originalQueue.firstOrNull { it.trackId == curTrackId }
            val newOrder = withContext(Dispatchers.Default) {
                if (desired) {
                    val rest = originalQueue.filter { it.trackId != curTrack?.trackId }.shuffled()
                    if (curTrack != null) listOf(curTrack) + rest else originalQueue.shuffled()
                } else {
                    originalQueue.toList()
                }
            }
            if (desired != _shuffleEnabled.value) return@launch
            withContext(Dispatchers.Main) {
                if (desired != _shuffleEnabled.value) return@withContext
                playbackOrder = newOrder
                currentIndex = if (desired) 0 else originalQueue.indexOfFirst { it.trackId == curTrack?.trackId }.coerceAtLeast(0)
                _queueSize.value = playbackOrder.size
                _currentBpm.value = playbackOrder.getOrNull(currentIndex)?.bpm
                _currentTrackId.value = playbackOrder.getOrNull(currentIndex)?.trackId
                _currentTitle.value = playbackOrder.getOrNull(currentIndex)?.title
                _currentNumber.value = currentIndex + 1
                _hasPrevious.value = currentIndex > 0 || _repeatMode.value != Player.REPEAT_MODE_OFF
                _hasNext.value = currentIndex < playbackOrder.lastIndex || _repeatMode.value != Player.REPEAT_MODE_OFF
                scope.launch { settingsRepository.update { it.copy(shuffleEnabled = desired) } }
            }
            scope.launch { settingsRepository.update { it.copy(shuffleEnabled = desired) } }
        }
    }

    fun cycleRepeat() {
        val newMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = newMode
        controller?.repeatMode = Player.REPEAT_MODE_OFF
        _hasPrevious.value = currentIndex > 0 || newMode != Player.REPEAT_MODE_OFF
        _hasNext.value = currentIndex < playbackOrder.lastIndex || newMode != Player.REPEAT_MODE_OFF
        scope.launch { settingsRepository.update { it.copy(repeatMode = newMode) } }
    }

    fun seekTo(positionMs: Long) {
        val current = controller ?: return
        if (current.isPlayingAd) return
        val wasGapActive = _gapActive.value
        cancelGap()
        current.seekTo(positionMs)
        _positionMs.value = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(positionMs))
        if (wasGapActive) current.play()
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
                enforceClipLimit()
                val fading = applyClipFade()
                delay(if (fading) FADE_STEP_MS else TICKER_INTERVAL_MS)
            }
        }
    }

    private fun applyClipFade(): Boolean {
        val current = controller ?: return false
        val volume = clipFadeVolume(
            limits = _limits.value,
            positionMs = current.currentPosition,
            durationMs = current.duration,
            isPlaylistMode = mode == Mode.Playlist,
            stopOnTrackEnd = stopOnTrackEnd,
        )
        if (volume == null) {
            restoreVolume()
            return false
        }
        if (current.volume != volume) current.volume = volume
        return volume > 0f
    }

    private fun restoreVolume() {
        val current = controller ?: return
        if (current.volume != 1f) current.volume = 1f
    }

    private fun enforceClipLimit() {
        val current = controller ?: return
        val limits = _limits.value
        val positionMs = current.currentPosition
        if (
            !shouldClipNow(
                limits = limits,
                positionMs = positionMs,
                durationMs = current.duration,
                isPlaylistMode = mode == Mode.Playlist,
                gapActive = _gapActive.value,
                stopOnTrackEnd = stopOnTrackEnd,
                isPlaying = current.isPlaying,
            )
        ) {
            return
        }
        val limitMs = limits.limitSeconds.toLong() * SECOND_MS
        Log.i(TAG, "clip limit reached at ${positionMs}ms (limit ${limitMs}ms), advancing")
        current.pause()
        restoreVolume()
        if (current.hasNextMediaItem()) {
            current.seekToNextMediaItem()
            if (
                shouldStartGap(
                    limits = limits,
                    isPlaylistMode = true,
                    stopOnTrackEnd = false,
                )
            ) {
                startGap()
            } else {
                current.play()
            }
        } else {
            Log.i(TAG, "clip limit reached at ${positionMs}ms (limit ${limitMs}ms), no next item")
            current.seekTo(0L)
            _positionMs.value = 0L
            refreshPosition()
            updateNavigation()
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
        _currentTitle.value = current.currentMediaItem?.mediaMetadata?.title?.toString() ?: _currentTitle.value
        val mediaTrackId = current.currentMediaItem?.mediaId?.toLongOrNull()
        if (mediaTrackId != null && mediaTrackId != _currentTrackId.value) {
            val idx = playbackOrder.indexOfFirst { it.trackId == mediaTrackId }
            if (idx >= 0) currentIndex = idx
        }
        _currentTrackId.value = mediaTrackId ?: _currentTrackId.value
        _currentBpm.value = playbackOrder.getOrNull(currentIndex)?.bpm ?: _currentBpm.value
        _currentNumber.value = currentIndex + 1
        _queueSize.value = playbackOrder.size.takeIf { it > 0 } ?: current.mediaItemCount
        _isPlaying.value = current.isPlaying
        _hasPrevious.value = currentIndex > 0 || _repeatMode.value != Player.REPEAT_MODE_OFF
        _hasNext.value = currentIndex < playbackOrder.lastIndex || _repeatMode.value != Player.REPEAT_MODE_OFF
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
        val KEY_TRACK_IDS = stringPreferencesKey("resume_track_ids")
        val KEY_PLAYLIST_ID = longPreferencesKey("resume_playlist_id")
        val KEY_CURRENT_TRACK_ID = longPreferencesKey("resume_current_track_id")
        val KEY_POSITION_MS = longPreferencesKey("resume_position_ms")

        const val TAG = "BpmPlayback"
        const val TICKER_INTERVAL_MS = 250L
        const val FADE_STEP_MS = 50L
        const val SECOND_MS = 1000L
        const val SKIP_FORWARD_STEP_MS = 15_000L
        const val TRACK_END_TOLERANCE_MS = 1_000L
    }
}