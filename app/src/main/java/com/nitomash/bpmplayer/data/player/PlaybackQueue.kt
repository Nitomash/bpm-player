package com.nitomash.bpmplayer.data.player

data class PlaybackQueue(
    val source: List<PlayerTrack>,
    val order: List<PlayerTrack>,
    val index: Int,
) {
    val current: PlayerTrack?
        get() = order.getOrNull(index)

    val size: Int
        get() = order.size

    fun moveTo(newIndex: Int): PlaybackQueue {
        if (order.isEmpty()) return copy(index = 0)
        return copy(index = newIndex.coerceIn(0, order.lastIndex))
    }

    companion object {
        val Empty = PlaybackQueue(emptyList(), emptyList(), 0)
    }
}
