package com.nitomash.bpmplayer.data.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nitomash.bpmplayer.data.player.PlaybackSession
import com.nitomash.bpmplayer.data.settings.AppSettings
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var playbackSession: PlaybackSession

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(TAG, "state changed: $playbackState (${PLAYBACK_STATES[playbackState]})")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(TAG, "isPlaying=$isPlaying")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "player error: ${error.errorCodeName}: ${error.message}", error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player).build()
        scope.launch {
            settingsRepository.observe().collect { settings ->
                applyPauseAtEndOfMediaItems(player, settings)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun applyPauseAtEndOfMediaItems(player: ExoPlayer, settings: AppSettings) {
        val enabled = settings.playbackProcessingEnabled && settings.playbackGapSeconds > 0
        if (player.pauseAtEndOfMediaItems == enabled) return
        Log.i(TAG, "pauseAtEndOfMediaItems=$enabled")
        player.setPauseAtEndOfMediaItems(enabled)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        runCatching { playbackSession.saveResumeState() }
        scope.cancel()
        mediaSession?.run {
            player.removeListener(playerListener)
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        runCatching { playbackSession.saveResumeState() }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val TAG = "BpmPlayerSvc"

        private val PLAYBACK_STATES = mapOf(
            Player.STATE_IDLE to "IDLE",
            Player.STATE_BUFFERING to "BUFFERING",
            Player.STATE_READY to "READY",
            Player.STATE_ENDED to "ENDED",
        )
    }
}

fun PlayerTrack.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(trackId.toString())
        .setUri(Uri.parse(uri))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()