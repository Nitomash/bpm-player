package com.nitomash.bpmplayer.ui.importlink

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class ImportOpenRequest(
    val jobId: String?,
    val id: Long,
)

@Singleton
class ImportNavigation @Inject constructor() {

    private val sequence = AtomicLong()
    private val _openRequests = MutableSharedFlow<ImportOpenRequest>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    val openRequests: SharedFlow<ImportOpenRequest> = _openRequests.asSharedFlow()

    fun openImport(jobId: String?) {
        _openRequests.tryEmit(ImportOpenRequest(jobId, sequence.incrementAndGet()))
    }
}
