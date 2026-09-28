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
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val inflight = mutableSetOf<Long>()

    init {
        scope.launch { process() }
    }

    fun requestFlush(folderId: Long) {
        queue.trySend(folderId)
    }

    fun flushAll() {
        scope.launch {
            runCatching { repository.getFolders() }
                .getOrNull()
                ?.forEach { folder -> queue.trySend(folder.id) }
        }
    }

    private suspend fun process() {
        for (folderId in queue) {
            delay(DEBOUNCE_MS)
            val batch = linkedSetOf(folderId)
            while (true) {
                val next = queue.tryReceive().getOrNull() ?: break
                batch += next
            }
            batch.forEach { id ->
                if (inflight.add(id)) {
                    scope.launch {
                        try {
                            flushFolder(id)
                        } catch (e: Exception) {
                            Log.e(TAG, "flush failed for folder $id", e)
                        } finally {
                            inflight.remove(id)
                        }
                    }
                }
            }
        }
    }

    private suspend fun flushFolder(folderId: Long) {
        val folder = repository.getFolders().firstOrNull { it.id == folderId } ?: return
        val records = repository.buildSidecarRecords(folderId)
        val content = SidecarCodec.encodeAll(records)
        sidecarStore.writeSidecar(Uri.parse(folder.rootUri), folder.displayName, content)
    }

    private companion object {
        const val TAG = "BpmSidecarSync"
        const val DEBOUNCE_MS = 400L
    }
}