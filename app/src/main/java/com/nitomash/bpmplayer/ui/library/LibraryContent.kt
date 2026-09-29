package com.nitomash.bpmplayer.ui.library

import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.ui.common.buildIndex

data class LibraryContent(
    val visibleFolders: List<FolderEntity> = emptyList(),
    val sortedTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    val trackCounts: Map<Long, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = visibleFolders.isEmpty()

    fun tracksOf(folderId: Long): List<TrackEntity> = sortedTracks[folderId].orEmpty()

    fun countOf(folderId: Long): Int = trackCounts[folderId] ?: 0
}

fun buildLibraryContent(
    folders: List<FolderEntity>,
    tracks: List<TrackEntity>,
    query: String,
    sortByFolder: Map<Long, TrackSort>,
): LibraryContent {
    val visible = buildIndex(tracks).query(query)
    val byFolder = visible.groupBy { it.folderId }
    return LibraryContent(
        visibleFolders = if (query.isBlank()) folders else folders.filter { byFolder.containsKey(it.id) },
        sortedTracks = byFolder.mapValues { (folderId, list) ->
            sortTracks(list, sortByFolder[folderId] ?: TrackSort.BY_NAME)
        },
        trackCounts = byFolder.mapValues { it.value.size },
    )
}
