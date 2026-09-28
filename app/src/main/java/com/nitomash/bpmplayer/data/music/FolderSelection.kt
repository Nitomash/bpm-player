package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.FolderEntity

fun resolveFolderIdsForGeneration(
    folders: List<FolderEntity>,
    allFolders: Boolean,
    selectedIds: Set<Long>,
): Set<Long> {
    if (allFolders || selectedIds.isEmpty()) return folders.map { it.id }.toSet()
    return folders.filter { it.id in selectedIds }.map { it.id }.toSet()
}