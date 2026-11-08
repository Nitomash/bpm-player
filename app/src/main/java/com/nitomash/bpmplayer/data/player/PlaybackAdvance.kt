package com.nitomash.bpmplayer.data.player

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

sealed interface QueueAdvance {
    data class To(val index: Int) : QueueAdvance
    data object EndOfQueue : QueueAdvance
    data object RestartQueue : QueueAdvance
}

fun PlaybackQueue.advance(repeat: RepeatMode): QueueAdvance = when {
    size == 0 -> QueueAdvance.EndOfQueue
    repeat == RepeatMode.ONE -> QueueAdvance.To(index.coerceIn(0, size - 1))
    index < order.lastIndex -> QueueAdvance.To(index + 1)
    repeat == RepeatMode.ALL -> QueueAdvance.RestartQueue
    else -> QueueAdvance.EndOfQueue
}
