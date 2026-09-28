package com.nitomash.bpmplayer.ui.library

import com.nitomash.bpmplayer.data.db.entity.TrackEntity

enum class TrackSort { BY_NAME, BY_BPM }

fun sortTracks(tracks: List<TrackEntity>, sort: TrackSort): List<TrackEntity> {
    if (tracks.size < 2) return tracks
    val keyed = tracks.map { Keyed(it, titleKey(it.title)) }
    val sorted = when (sort) {
        TrackSort.BY_NAME -> keyed.sortedBy { it.titleKey }
        TrackSort.BY_BPM -> keyed.sortedWith(
            compareBy<Keyed<TrackEntity>> { if (it.value.bpm == null) 1 else 0 }
                .thenBy { it.value.bpm }
                .thenBy { it.titleKey },
        )
    }
    return sorted.map { it.value }
}

private class Keyed<T>(val value: T, val titleKey: String)

private fun titleKey(title: String): String = title.trim().lowercase()
