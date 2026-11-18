package com.nitomash.bpmplayer.data.player

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class VirtualQueuePlayer(
    player: Player,
    private val playbackSession: PlaybackSession,
) : ForwardingSimpleBasePlayer(player) {

    val wrappedPlayer: Player = player

    fun notifyStateChanged() {
        invalidateState()
    }

    override fun getState(): State {
        val state = super.getState()
        val commandsBuilder = state.availableCommands.buildUpon()
        val hasQueue = playbackSession.queueSize.value > 0
        if (hasQueue) {
            commandsBuilder.add(Player.COMMAND_SEEK_TO_NEXT)
            commandsBuilder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            commandsBuilder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            commandsBuilder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            commandsBuilder.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            commandsBuilder.add(Player.COMMAND_SEEK_FORWARD)
            commandsBuilder.add(Player.COMMAND_SEEK_BACK)
        } else {
            commandsBuilder.remove(Player.COMMAND_SEEK_TO_NEXT)
            commandsBuilder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            commandsBuilder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            commandsBuilder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            commandsBuilder.remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            commandsBuilder.remove(Player.COMMAND_SEEK_FORWARD)
            commandsBuilder.remove(Player.COMMAND_SEEK_BACK)
        }
        return state.buildUpon()
            .setAvailableCommands(commandsBuilder.build())
            .setShuffleModeEnabled(playbackSession.shuffleEnabled.value)
            .setRepeatMode(playbackSession.repeatMode.value)
            .build()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        return when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> {
                playbackSession.next()
                Futures.immediateVoidFuture()
            }

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> {
                playbackSession.previous()
                Futures.immediateVoidFuture()
            }

            Player.COMMAND_SEEK_FORWARD -> {
                playbackSession.skipForward()
                Futures.immediateVoidFuture()
            }

            Player.COMMAND_SEEK_BACK -> {
                playbackSession.skipBack()
                Futures.immediateVoidFuture()
            }

            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                playbackSession.seekTo(positionMs)
                Futures.immediateVoidFuture()
            }

            Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> {
                playbackSession.seekToDefaultPosition()
                Futures.immediateVoidFuture()
            }

            else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        playbackSession.setRepeatMode(repeatMode)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        playbackSession.setShuffleEnabled(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }
}
