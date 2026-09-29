package com.nitomash.bpmplayer.data.music

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SidecarSyncManager @Inject constructor(
    private val repository: MusicRepository,
    private val sidecarStore: SidecarStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = FlushQueue()
    private val wakeup = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { process() }
    }

    fun requestFlush(folderId: Long) {
        if (queue.enqueue(folderId)) wakeup.trySend(Unit)
    }

    fun flushAll() {
        scope.launch {
            runCatching { repository.getFolders() }
                .getOrNull()
                ?.forEach { folder -> requestFlush(folder.id) }
        }
    }

    private suspend fun process() {
        while (true) {
            if (!queue.hasWork) wakeup.receive()
            delay(DEBOUNCE_MS)
            val batch = queue.drain()
            for (folderId in batch) {
                try {
                    flushFolder(folderId)
                } catch (e: Exception) {
                    Log.e(TAG, "flush failed for folder $folderId", e)
                }
            }
        }
    }

    private suspend fun flushFolder(folderId: Long) {
        val folder = repository.getFolders().firstOrNull { it.id == folderId } ?: return
        val records = repository.buildSidecarRecords(folderId)
        sidecarStore.writeSidecar(Uri.parse(folder.rootUri), folder.displayName, records)
    }

    private companion object {
        const val TAG = "BpmSidecarSync"
        const val DEBOUNCE_MS = 400L
    }
}
