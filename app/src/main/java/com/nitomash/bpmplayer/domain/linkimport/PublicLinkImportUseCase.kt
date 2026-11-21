package com.nitomash.bpmplayer.domain.linkimport

import kotlinx.coroutines.flow.Flow

interface PublicLinkImportUseCase {
    suspend fun resolveName(link: String): String
    suspend fun inspect(link: String): ImportPreview
    suspend fun enqueue(request: ImportRequest): ImportJobId
    suspend fun cancel(jobId: ImportJobId)
    suspend fun resume(jobId: ImportJobId?): ImportJobSnapshot?
    fun observe(jobId: ImportJobId): Flow<ImportJobState>
}
