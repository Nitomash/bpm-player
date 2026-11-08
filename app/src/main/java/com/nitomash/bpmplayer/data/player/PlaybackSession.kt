package com.nitomash.bpmplayer.data.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.ui.playlists.GeneratedPlaylist
import com.nitomash.bpmplayer.ui.playlists.GeneratedPlaylistStore
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PlaybackSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val resumeStateStore: ResumeStateStore,
    private val musicRepository: MusicRepository,
    private val generatedPlaylistStore: GeneratedPlaylistStore,
) {
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var tickerJob: Job? = null
    private var gapJob: Job? = null
    private var shuffleJob: Job? = null
    private var mode: Mode = Mode.Empty
    private var previewSnapshot: QueueSnapshot? = null
    private var queue: PlaybackQueue = PlaybackQueue.Empty
    private var appliedLimits: PlaybackLimits? = null
    @Volatile
    private var stopOnTrackEnd = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resumeWrites = Channel<ResumeWrite>(Channel.UNLIMITED)
    private var resumeLoaded = false
    private var lastResumeSaveMs = 0L

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _hasUserStarted = MutableStateFlow(false)
    val hasUserStarted: StateFlow<Boolean> = _hasUserStarted.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

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
        scope.launch(Dispatchers.IO) { processResumeWrites() }
        scope.launch { restoreIfNeeded() }
        scope.launch {
            settingsRepository.observe().collect { settings ->
                onLimitsChanged(settings.toPlaybackLimits())
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
                saveResumeState()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            restoreVolume()
            updateNavigation()
            saveResumeState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
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
            when (playbackState) {
                Player.STATE_READY -> refreshPosition()
                Player.STATE_ENDED -> finishSegment(SegmentEnd.EndOfMedia)
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
        _isPreview.value = false
        _previewTrackId.value = null
        _currentPlaylistId.value = playlistId
        _isGeneratedQueue.value = generated
        previewSnapshot = null
        pendingAction = null
        scope.launch {
            val settings = settingsRepository.current()
            val shuffle = settings.shuffleEnabled
            _shuffleEnabled.value = shuffle
            _repeatMode.value = settings.repeatMode
            val actualStart = if (randomStart && shuffle) Random.nextInt(items.size) else start
            queue = PlaybackQueue(
                source = items.toList(),
                order = buildPlaybackOrder(items, actualStart, shuffle),
                index = if (shuffle) 0 else actualStart.coerceIn(0, items.lastIndex),
            )
            syncQueueMetadata()
            startPlayback { loadTrack(queue.index, play = true) }
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

    fun setCurrentPlaylistId(playlistId: Long?) {
        _currentPlaylistId.value = playlistId
        _isGeneratedQueue.value = playlistId == null
    }

    fun setCurrentBpm(bpm: Double?) {
        _currentBpm.value = bpm
    }

    fun updatePlaybackOrderBpm(trackId: Long, bpm: Double?) {
        queue = queue.copy(
            source = queue.source.map { if (it.trackId == trackId) it.copy(bpm = bpm) else it },
            order = queue.order.map { if (it.trackId == trackId) it.copy(bpm = bpm) else it },
        )
        if (_currentTrackId.value == trackId) {
            _currentBpm.value = bpm
        }
    }

    fun previewTrack(track: PlayerTrack) {
        Log.i(TAG, "previewTrack: ${track.title}")
        cancelGap()
        val snapshot = if (mode == Mode.Preview) previewSnapshot else captureQueue()
        previewSnapshot = snapshot
        mode = Mode.Preview
        _isPreview.value = true
        _previewTrackId.value = track.trackId
        _currentTrackId.value = track.trackId
        _currentTitle.value = track.title
        _currentBpm.value = track.bpm
        startPlayback { player -> startPreview(player, track) }
    }

    fun endPreview() {
        if (mode != Mode.Preview) return
        restoreQueue()
    }

    fun setStopOnTrackEnd(enabled: Boolean) {
        stopOnTrackEnd = enabled
    }

    private fun captureQueue(): QueueSnapshot? {
        if (queue.size == 0) return null
        val player = controller
        val positionMs = when {
            player == null -> _positionMs.value
            isAtTrackEnd(player) -> 0L
            else -> player.currentPosition
        }
        return QueueSnapshot(
            index = queue.index,
            positionMs = positionMs,
            wasPlaying = player?.isPlaying == true,
        )
    }

    private fun startPreview(player: MediaController, track: PlayerTrack) {
        player.setMediaItems(listOf(track.toMediaItem()), 0, 0L)
        player.prepare()
        player.play()
    }

    private fun restoreQueue() {
        val snapshot = previewSnapshot
        previewSnapshot = null
        _isPreview.value = false
        _previewTrackId.value = null
        pendingAction = null
        cancelGap()
        val player = controller
        if (snapshot == null) {
            Log.i(TAG, "preview ended, nothing to restore, stopping")
            mode = Mode.Empty
            queue = PlaybackQueue.Empty
            _isGeneratedQueue.value = false
            _currentBpm.value = null
            _currentTrackId.value = null
            _currentTitle.value = null
            _currentNumber.value = 0
            _queueSize.value = 0
            _hasPrevious.value = false
            _hasNext.value = false
            _positionMs.value = 0L
            _durationMs.value = 0L
            player?.stop()
            player?.clearMediaItems()
            return
        }
        Log.i(
            TAG,
            "preview ended, restoring index ${snapshot.index} at ${snapshot.positionMs}ms " +
                "wasPlaying=${snapshot.wasPlaying}",
        )
        mode = Mode.Playlist
        loadTrack(snapshot.index, play = snapshot.wasPlaying, positionMs = snapshot.positionMs)
    }

    private fun startPlayback(
        foreground: Boolean = true,
        action: (MediaController) -> Unit,
    ) {
        if (foreground) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java),
            )
        }
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

    private suspend fun restoreIfNeeded() {
        val state = runCatching { resumeStateStore.load() }
            .onFailure { Log.w(TAG, "cannot load resume state", it) }
            .getOrNull()
        resumeLoaded = true
        if (_hasUserStarted.value) {
            Log.i(TAG, "restore skipped: playback already started")
            return
        }
        if (state == null) return
        _isRestoring.value = true
        try {
            val entities = state.trackIds.mapNotNull { id -> musicRepository.getById(id) }
            if (entities.isEmpty()) {
                Log.i(TAG, "resume state refers to missing tracks, dropping it")
                clearResumeState()
                return
            }
            val tracks = entities.map { it.toPlayerTrack() }
            val settings = settingsRepository.current()
            val shuffle = settings.shuffleEnabled
            val safeIndex = state.trackIds.indexOf(state.currentTrackId).coerceAtLeast(0).coerceAtMost(tracks.lastIndex)
            mode = Mode.Playlist
            _isPreview.value = false
            _previewTrackId.value = null
            _currentPlaylistId.value = state.playlistId
            _isGeneratedQueue.value = state.playlistId == null
            if (state.playlistId == null) {
                generatedPlaylistStore.put(GeneratedPlaylist(name = state.name ?: "Сгенерированный плейлист", tracks = entities))
            }
            previewSnapshot = null
            pendingAction = null
            cancelGap()
            _shuffleEnabled.value = shuffle
            _repeatMode.value = settings.repeatMode
            queue = PlaybackQueue(
                source = tracks,
                order = buildPlaybackOrder(tracks, safeIndex, shuffle),
                index = if (shuffle) 0 else safeIndex,
            )
            syncQueueMetadata()
            _positionMs.value = state.positionMs
            startPlayback(foreground = false) {
                loadTrack(queue.index, play = false, positionMs = state.positionMs)
            }
        } finally {
            _isRestoring.value = false
        }
    }

    fun saveResumeState() {
        if (!resumeLoaded || mode == Mode.Preview) return
        val ids = queue.source.map { it.trackId }
        if (ids.isEmpty()) return
        val playlistId = _currentPlaylistId.value
        val name = if (playlistId == null) generatedPlaylistStore.current.value?.name else null
        resumeWrites.trySend(
            ResumeWrite.Save(
                ResumeState(
                    playlistId = playlistId,
                    trackIds = ids,
                    currentTrackId = _currentTrackId.value,
                    positionMs = _positionMs.value,
                    name = name,
                ),
            ),
        )
    }

    private fun clearResumeState() {
        resumeWrites.trySend(ResumeWrite.Clear)
    }

    private suspend fun processResumeWrites() {
        for (write in resumeWrites) {
            runCatching {
                when (write) {
                    ResumeWrite.Clear -> resumeStateStore.clear()
                    is ResumeWrite.Save -> resumeStateStore.save(write.state)
                }
            }.onFailure { Log.w(TAG, "cannot write resume state", it) }
        }
    }

    private sealed interface ResumeWrite {
        data object Clear : ResumeWrite
        data class Save(val state: ResumeState) : ResumeWrite
    }

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

    private fun finishSegment(cause: SegmentEnd) {
        val player = controller ?: return
        if (mode == Mode.Preview) {
            restoreQueue()
            return
        }
        if (mode != Mode.Playlist) return
        if (stopOnTrackEnd) {
            Log.i(TAG, "segment finished while editor holds playback ($cause), pausing")
            player.pause()
            player.seekTo(0L)
            _positionMs.value = 0L
            return
        }
        if (queue.size == 0) return
        val advance = queue.advance(repeatModeValue())
        val pause = shouldPauseAfterSegment(
            limits = _limits.value,
            isPlaylistMode = true,
            stopOnTrackEnd = false,
            hasNextTarget = advance != QueueAdvance.EndOfQueue,
        )
        Log.i(TAG, "finishSegment($cause): advance=$advance pause=$pause index=${queue.index}/${queue.size}")
        when (advance) {
            is QueueAdvance.To -> {
                loadTrack(advance.index, play = !pause)
                if (pause) startGap()
            }
            QueueAdvance.RestartQueue -> {
                restartQueue(play = !pause)
                if (pause) startGap()
            }
            QueueAdvance.EndOfQueue -> loadTrack(queue.index, play = false)
        }
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
        if (queue.size == 0 && mode != Mode.Preview) return
        _hasUserStarted.value = true
        val current = controller
        if (current == null) {
            if (mode == Mode.Preview) return
            startPlayback { loadTrack(queue.index, play = true, positionMs = _positionMs.value) }
            return
        }
        cancelGap()
        when {
            current.isPlaying -> current.pause()
            current.playbackState == Player.STATE_ENDED -> {
                if (mode == Mode.Preview) restoreQueue() else loadTrack(queue.index, play = true)
            }
            else -> current.play()
        }
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
        if (queue.size == 0) return
        leavePreview()
        _hasUserStarted.value = true
        val play = isPlayIntentActive()
        cancelGap()
        val q = queue
        when {
            q.index < q.order.lastIndex -> loadTrack(q.index + 1, play)
            repeatModeValue() != RepeatMode.OFF -> restartQueue(play)
        }
    }

    fun previous() {
        if (queue.size == 0) return
        leavePreview()
        _hasUserStarted.value = true
        val play = isPlayIntentActive()
        cancelGap()
        val q = queue
        when {
            q.index > 0 -> loadTrack(q.index - 1, play)
            repeatModeValue() != RepeatMode.OFF -> loadTrack(q.order.lastIndex, play)
        }
    }

    private fun loadTrack(index: Int, play: Boolean, positionMs: Long = 0L) {
        val track = queue.order.getOrNull(index) ?: return
        queue = queue.moveTo(index)
        syncQueueMetadata()
        _positionMs.value = positionMs.coerceAtLeast(0L)
        _durationMs.value = 0L
        val player = controller ?: return
        if (!play) player.pause()
        player.setMediaItems(listOf(track.toMediaItem()), 0, _positionMs.value)
        player.prepare()
        if (play) player.play()
    }

    private fun restartQueue(play: Boolean) {
        val q = queue
        if (q.size == 0) return
        val order = if (_shuffleEnabled.value) reshuffled(q) else q.source.toList()
        queue = q.copy(order = order, index = 0)
        loadTrack(0, play)
    }

    private fun reshuffled(q: PlaybackQueue): List<PlayerTrack> {
        val previousId = q.order.lastOrNull()?.trackId
        var order = q.source.shuffled()
        var attempts = 0
        while (order.size > 1 && previousId != null && order.first().trackId == previousId && attempts < 10) {
            order = q.source.shuffled()
            attempts++
        }
        return order
    }

    fun toggleShuffle() {
        val desired = !_shuffleEnabled.value
        _shuffleEnabled.value = desired
        shuffleJob?.cancel()
        shuffleJob = scope.launch {
            val q = queue
            val curTrack = q.current
            val newOrder = withContext(Dispatchers.Default) {
                if (q.size == 0) {
                    emptyList()
                } else if (desired) {
                    val rest = q.source.filter { it.trackId != curTrack?.trackId }.shuffled()
                    if (curTrack != null) listOf(curTrack) + rest else q.source.shuffled()
                } else {
                    q.source.toList()
                }
            }
            if (desired != _shuffleEnabled.value) return@launch
            withContext(Dispatchers.Main) {
                if (desired != _shuffleEnabled.value) return@withContext
                val newIndex = if (desired) {
                    0
                } else {
                    newOrder.indexOfFirst { it.trackId == curTrack?.trackId }.coerceAtLeast(0)
                }
                queue = q.copy(order = newOrder, index = newIndex.coerceIn(0, newOrder.lastIndex.coerceAtLeast(0)))
                if (previewSnapshot != null) {
                    previewSnapshot = previewSnapshot?.copy(index = queue.index)
                }
                if (mode == Mode.Preview) {
                    _queueSize.value = queue.size
                    syncNavigationFlags()
                } else {
                    syncQueueMetadata()
                }
                settingsRepository.update { it.copy(shuffleEnabled = desired) }
            }
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
        syncNavigationFlags()
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
                saveResumeStateIfDue()
                delay(if (fading) FADE_STEP_MS else TICKER_INTERVAL_MS)
            }
        }
    }

    private fun saveResumeStateIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastResumeSaveMs < RESUME_SAVE_INTERVAL_MS) return
        lastResumeSaveMs = now
        saveResumeState()
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
        finishSegment(SegmentEnd.LimitReached)
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun refreshPosition() {
        val current = controller ?: return
        if (current.isPlayingAd) return
        val duration = current.duration
        val position = current.currentPosition.coerceAtLeast(0L)
        if (duration > 0L) {
            _durationMs.value = duration
            _positionMs.value = position.coerceIn(0L, duration)
        } else {
            _durationMs.value = 0L
            if (position > 0L) _positionMs.value = position
        }
    }

    private fun syncQueueMetadata() {
        val q = queue
        val current = q.current
        _queueSize.value = q.size
        _currentTrackId.value = current?.trackId
        _currentBpm.value = current?.bpm
        _currentTitle.value = current?.title
        _currentNumber.value = if (current == null) 0 else q.index + 1
        syncNavigationFlags()
    }

    private fun syncNavigationFlags() {
        val q = queue
        _hasPrevious.value = q.index > 0 || repeatModeValue() != RepeatMode.OFF
        _hasNext.value = q.index < q.order.lastIndex || repeatModeValue() != RepeatMode.OFF
    }

    private fun updateNavigation() {
        val current = controller ?: return
        _isPlaying.value = current.isPlaying
        if (mode != Mode.Preview) {
            syncQueueMetadata()
        } else {
            _queueSize.value = queue.size
        }
        refreshPosition()
    }

    private fun leavePreview() {
        if (mode != Mode.Preview) return
        mode = Mode.Playlist
        _isPreview.value = false
        _previewTrackId.value = null
        previewSnapshot = null
    }

    private fun repeatModeValue(): RepeatMode = when (_repeatMode.value) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    private fun isPlayIntentActive(): Boolean =
        controller?.playWhenReady == true || _gapActive.value

    private enum class Mode {
        Empty,
        Playlist,
        Preview,
    }

    private enum class SegmentEnd {
        EndOfMedia,
        LimitReached,
    }

    private data class QueueSnapshot(
        val index: Int,
        val positionMs: Long,
        val wasPlaying: Boolean,
    )

    private companion object {
        const val TAG = "BpmPlayback"
        const val TICKER_INTERVAL_MS = 250L
        const val RESUME_SAVE_INTERVAL_MS = 15_000L
        const val FADE_STEP_MS = 50L
        const val SECOND_MS = 1000L
        const val SKIP_FORWARD_STEP_MS = 15_000L
        const val TRACK_END_TOLERANCE_MS = 1_000L
    }
}
