package com.nitomash.bpmplayer.data.generation

import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.resolveFolderIdsForGeneration
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary

fun selectTracksByFolders(
    allTracks: List<TrackEntity>,
    folders: List<FolderEntity>,
    allFolders: Boolean,
    selectedIds: Set<Long>,
): List<TrackEntity> {
    val resolvedIds = resolveFolderIdsForGeneration(folders, allFolders, selectedIds)
    return allTracks.filter { it.folderId in resolvedIds }
}

fun resolvePlaylistIdsForGeneration(
    playlists: List<PlaylistSummary>,
    allPlaylists: Boolean,
    selectedIds: Set<Long>,
): List<Long> {
    if (allPlaylists || selectedIds.isEmpty()) return playlists.map { it.id }
    return playlists.filter { it.id in selectedIds }.map { it.id }
}

fun selectTracksByPlaylists(
    playlistIds: List<Long>,
    tracksByPlaylist: Map<Long, List<TrackEntity>>,
): List<TrackEntity> {
    val seen = LinkedHashMap<Long, TrackEntity>()
    playlistIds.forEach { playlistId ->
        tracksByPlaylist[playlistId].orEmpty().forEach { track ->
            seen.putIfAbsent(track.id, track)
        }
    }
    return seen.values.toList()
}
