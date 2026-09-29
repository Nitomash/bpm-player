package com.nitomash.bpmplayer.data.bpm

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
object BpmDetectionDispatcher {

    /**
     * One-time snapshot, taken when the process first touches this class, which happens while
     * the app is still in the foreground and its cpuset is at its widest.
     *
     * Deliberately not adaptive: a shrinking cpuset means the system handed our cores to
     * someone else, not that we lost capacity. The scheduler already timeshares our threads
     * across the cores we were left with, so shrinking this pool on top of that would halve
     * throughput for no gain. A fixed pool could not shrink anyway.
     */
    val permits: Int = permitsFor(Runtime.getRuntime().availableProcessors())

    fun create(): CloseableCoroutineDispatcher =
        Executors.newFixedThreadPool(permits) { runnable ->
            Thread(runnable, THREAD_NAME).apply { isDaemon = true }
        }.asCoroutineDispatcher()

    fun permitsFor(availableProcessors: Int): Int = max(1, availableProcessors / 2)

    private const val THREAD_NAME = "bpm-detect"
}
