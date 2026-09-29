package com.nitomash.bpmplayer.ui.common

import com.nitomash.bpmplayer.data.db.entity.TrackEntity

class TrackIndex internal constructor(
    private val tracks: List<TrackEntity>,
    private val keys: Array<String>,
) {

    val size: Int get() = tracks.size

    fun query(rawQuery: String): List<TrackEntity> {
        val needle = titleKey(rawQuery)
        if (needle.isEmpty()) return tracks
        val matched = ArrayList<TrackEntity>()
        keys.forEachIndexed { index, key ->
            if (key.contains(needle)) matched += tracks[index]
        }
        return matched
    }
}

fun buildIndex(tracks: List<TrackEntity>): TrackIndex =
    TrackIndex(tracks, Array(tracks.size) { titleKey(tracks[it].title) })

fun titleKey(value: String): String = value.trim().lowercase()
