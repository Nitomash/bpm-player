package com.nitomash.bpmplayer.ui.common

import com.nitomash.bpmplayer.data.db.entity.TrackEntity

fun filterTracksByTitle(tracks: List<TrackEntity>, query: String): List<TrackEntity> {
    val needle = titleKey(query)
    if (needle.isEmpty()) return tracks
    return tracks.filter { titleKey(it.title).contains(needle) }
}

private fun titleKey(value: String): String = value.trim().lowercase()