package com.nitomash.bpmplayer.data.db

import com.nitomash.bpmplayer.data.db.entity.TrackEntity

fun trackDuplicateKey(fileName: String): String = fileName.trim().lowercase()

fun firstTrackOccurrences(tracks: List<TrackEntity>): List<TrackEntity> {
    val selected = LinkedHashMap<TrackIdentityKey, TrackEntity>()
    tracks.forEach { track ->
        val key = TrackIdentityKey(track.folderId, trackDuplicateKey(track.fileName))
        val current = selected[key]
        if (current == null || track.relativePath < current.relativePath) {
            selected[key] = track
        }
    }
    return selected.values.toList()
}

private data class TrackIdentityKey(
    val folderId: Long,
    val fileNameKey: String,
)
