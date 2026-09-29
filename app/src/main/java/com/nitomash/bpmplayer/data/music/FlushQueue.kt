package com.nitomash.bpmplayer.data.music

class FlushQueue {

    private val lock = Any()
    private val pending = LinkedHashSet<Long>()

    val hasWork: Boolean
        get() = synchronized(lock) { pending.isNotEmpty() }

    fun enqueue(folderId: Long): Boolean = synchronized(lock) { pending.add(folderId) }

    fun drain(): List<Long> = synchronized(lock) {
        if (pending.isEmpty()) {
            emptyList()
        } else {
            val ids = pending.toList()
            pending.clear()
            ids
        }
    }
}
