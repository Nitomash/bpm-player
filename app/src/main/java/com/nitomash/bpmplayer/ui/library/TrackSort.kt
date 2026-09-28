package com.nitomash.bpmplayer.ui.library

import com.nitomash.bpmplayer.data.db.entity.TrackEntity

enum class TrackSort { BY_NAME, BY_BPM }

fun sortTracks(tracks: List<TrackEntity>, sort: TrackSort): List<TrackEntity> = when (sort) {
    TrackSort.BY_NAME -> tracks.sortedBy { titleKey(it.title) }
    TrackSort.BY_BPM -> tracks.sortedWith { a, b ->
        when {
            a.bpm == null && b.bpm == null -> titleKey(a.title).compareTo(titleKey(b.title))
            a.bpm == null -> 1
            b.bpm == null -> -1
            else -> {
                val byBpm = a.bpm.compareTo(b.bpm)
                if (byBpm != 0) byBpm else titleKey(a.title).compareTo(titleKey(b.title))
            }
        }
    }
}

private fun titleKey(title: String): String = title.trim().lowercase()