package com.nitomash.bpmplayer.ui.playlists

import androidx.annotation.StringRes
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.domain.playlist.PlaylistOrder

@StringRes
fun playlistOrderLabelRes(order: PlaylistOrder): Int = when (order) {
    PlaylistOrder.RANDOM -> R.string.playlist_order_random
    PlaylistOrder.ASCENDING -> R.string.playlist_order_ascending
    PlaylistOrder.DESCENDING -> R.string.playlist_order_descending
    PlaylistOrder.TITLE -> R.string.playlist_order_title
}