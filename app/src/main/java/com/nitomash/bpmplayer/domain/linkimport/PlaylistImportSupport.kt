package com.nitomash.bpmplayer.domain.linkimport

interface PlaylistImportSupport {
    suspend fun import(files: List<PlannedFile>, downloadedUris: Map<String, String>): Int
}
