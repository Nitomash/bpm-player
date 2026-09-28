package com.nitomash.bpmplayer.ui.playlists

import androidx.annotation.StringRes
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort

@StringRes
fun playlistTrackSortLabelRes(sort: PlaylistTrackSort): Int = when (sort) {
    PlaylistTrackSort.CUSTOM -> R.string.playlist_sort_custom
    PlaylistTrackSort.BPM_ASC -> R.string.playlist_sort_bpm_asc
    PlaylistTrackSort.BPM_DESC -> R.string.playlist_sort_bpm_desc
    PlaylistTrackSort.DATE_ADDED -> R.string.playlist_sort_date_added
    PlaylistTrackSort.TITLE -> R.string.playlist_sort_title
}

@StringRes
fun playlistListSortLabelRes(sort: PlaylistListSort): Int = when (sort) {
    PlaylistListSort.DATE_MODIFIED -> R.string.playlist_list_sort_modified
    PlaylistListSort.TITLE -> R.string.playlist_sort_title
    PlaylistListSort.CUSTOM -> R.string.playlist_sort_custom
}
