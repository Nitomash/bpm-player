package com.nitomash.bpmplayer.data.bpm

import kotlin.math.abs
import kotlin.math.roundToInt

class TapBpmDetector(
    private val minIntervalMs: Long = 100,
    private val seriesGapMs: Long = 3000,
) {
    private var lastTapMs: Long? = null
    private val intervals = ArrayDeque<Double>()
    private var taps = 0

    val tapCount: Int get() = taps

    fun tap(timestampMs: Long): Int? {
        val previous = lastTapMs
        if (previous != null && timestampMs - previous > seriesGapMs) {
            intervals.clear()
            taps = 0
            lastTapMs = timestampMs
            taps++
            return null
        }
        lastTapMs = timestampMs
        taps++
        if (previous == null) return null
        val interval = (timestampMs - previous).toDouble()
        if (interval < minIntervalMs) return currentBpm()
        intervals.addLast(interval)
        return currentBpm()
    }

    fun currentBpm(): Int? {
        if (intervals.isEmpty()) return null
        var period = if (intervals.size == 1) intervals.first() else meanNormalized(intervals.median())
        period = meanNormalized(period)
        return (60000.0 / period).roundToInt()
    }

    private fun ArrayDeque<Double>.median(): Double {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun meanNormalized(basePeriod: Double): Double {
        if (intervals.isEmpty()) return basePeriod
        val normalized = mutableListOf<Double>()
        for (interval in intervals) {
            val multiple = (interval / basePeriod).roundToInt().coerceAtLeast(1)
            if (abs(interval - multiple * basePeriod) <= MAX_OUTLIER_RATIO * basePeriod) {
                normalized.add(interval / multiple)
            }
        }
        if (normalized.isEmpty()) return basePeriod
        val median = normalized.median()
        val kept = normalized.filter { abs(it - median) <= JITTER_RATIO * median }
        if (kept.isEmpty()) return median
        return kept.sum() / kept.size
    }

    fun reset() {
        lastTapMs = null
        intervals.clear()
        taps = 0
    }

    private companion object {
        const val MAX_OUTLIER_RATIO = 0.35
        const val JITTER_RATIO = 0.15
    }
}