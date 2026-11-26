package com.nitomash.bpmplayer.domain.linkimport

interface ImportDuplicateCleaner {
    suspend fun clean(destinationUri: String): Int
}
