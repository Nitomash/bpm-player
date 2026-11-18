package com.nitomash.bpmplayer.data.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackSession: PlaybackSession

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
            }
        exoPlayer = player
        val virtualPlayer = VirtualQueuePlayer(player, playbackSession)
        playbackSession.attachVirtualPlayer(virtualPlayer)
        mediaSession = MediaSession.Builder(this, virtualPlayer)
            .setCallback(
                object : MediaSession.Callback {
                    override fun onPlaybackResumption(
                        mediaSession: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                        Log.i(TAG, "onPlaybackResumption requested by ${controller.packageName}")
                        val future = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                        serviceScope.launch {
                            runCatching {
                                playbackSession.awaitResumeReady()
                                val item = playbackSession.currentResumptionMediaItem()
                                    ?: error("No resumable media item")
                                MediaSession.MediaItemsWithStartPosition(
                                    listOf(item),
                                    0,
                                    playbackSession.currentResumptionPositionMs(),
                                )
                            }.onSuccess(future::set).onFailure(future::setException)
                        }
                        return future
                    }
                },
            )
            .build()
        Log.i(TAG, "media session created with VirtualQueuePlayer")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        playbackSession.attachVirtualPlayer(null)
        runCatching { playbackSession.stopPreviewOnFocusLoss() }
        runCatching { playbackSession.saveResumeState() }
        mediaSession?.release()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        mediaSession = null
        exoPlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        runCatching { playbackSession.stopPreviewOnFocusLoss() }
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

fun PlayerTrack.toMediaItem(
    playlistId: Long? = null,
    playlistTitle: String? = null,
): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .build()
    return MediaItem.Builder()
        .setMediaId(trackId.toString())
        .setUri(Uri.parse(uri))
        .setMediaMetadata(metadata)
        .build()
}
