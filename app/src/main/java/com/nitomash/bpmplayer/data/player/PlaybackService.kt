package com.nitomash.bpmplayer.data.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(playerListener)
            release()
        }
        mediaSession = null
        super.onDestroy()
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

fun PlayerTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(trackId.toString())
    .setUri(Uri.parse(uri))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
    .build()