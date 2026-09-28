package com.nitomash.bpmplayer.data.bpm

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.TarsosDSPAudioInputStream
import be.tarsos.dsp.onsets.ComplexOnsetDetector
import be.tarsos.dsp.onsets.OnsetHandler
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

class PcmBpmDetector(
    private val windowSeconds: Int = 25,
    private val nextWindowStepSeconds: Int = 10,
    private val maxAttempts: Int = 3,
    private val bufferSize: Int = 512,
    private val onsetThreshold: Double = 0.3,
    private val onWindowTiming: (WindowTiming) -> Unit = {},
) {
    fun detect(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Double = DEFAULT_MIN_BPM,
        maxBpm: Double = DEFAULT_MAX_BPM,
        isActive: () -> Boolean = { true },
    ): BpmDetectionResult {
        if (sampleRate <= 0 || samples.size < MIN_WINDOW_SAMPLES) {
            return BpmDetectionResult.Failure(BpmDetectionFailure.NO_ONSETS)
        }
        val windowSamples = windowSeconds * sampleRate
        val stepSamples = nextWindowStepSeconds * sampleRate
        val baseStart = if (samples.size > windowSamples) samples.size / 3 else 0
        val spanEnd = (baseStart + (maxAttempts - 1) * stepSamples + windowSamples).coerceAtMost(samples.size)
        if (spanEnd - baseStart < windowSamples / 2) {
            return BpmDetectionResult.Failure(BpmDetectionFailure.NO_ONSETS)
        }

        val collectStarted = System.nanoTime()
        val onsets = collectOnsets(samples.copyOfRange(baseStart, spanEnd), sampleRate, isActive)
        val collectMs = nanosToMs(collectStarted)

        if (!isActive()) throw CancellationException("BPM detection cancelled")

        if (onsets.size < MIN_ONSETS) {
            onWindowTiming(WindowTiming(0, 0.0, onsets.size, 0, collectMs, 0, 0, 0, null))
            return BpmDetectionResult.Failure(BpmDetectionFailure.NO_ONSETS)
        }

        var best: WindowScore? = null
        for (attempt in 0 until maxAttempts) {
            if (!isActive()) throw CancellationException("BPM detection cancelled")
            val winStart = attempt * stepSamples
            val winEnd = (winStart + windowSamples).coerceAtMost(spanEnd - baseStart)
            if (winStart >= spanEnd - baseStart) break
            if (winEnd - winStart < windowSamples / 2) break
            val startSec = winStart.toDouble() / sampleRate
            val endSec = winEnd.toDouble() / sampleRate
            val windowOnsets = onsets.filter { it >= startSec && it < endSec }
            if (windowOnsets.size < MIN_ONSETS) continue
            val score = evaluate(attempt, windowOnsets, sampleRate, spanEnd - baseStart, collectMs, minBpm, maxBpm, isActive)
            if (score != null) {
                if (best == null || score.quality > best.quality) {
                    best = score
                }
                if (score.quality >= GOOD_QUALITY) break
            }
        }
        val winner = best ?: return BpmDetectionResult.Failure(BpmDetectionFailure.NO_ONSETS)
        return if (winner.quality >= MIN_ACCEPTABLE_QUALITY) {
            BpmDetectionResult.Success(roundBpm(winner.bpm), winner.quality)
        } else {
            BpmDetectionResult.Failure(BpmDetectionFailure.WEAK_BEAT)
        }
    }

    private fun evaluate(
        attempt: Int,
        onsets: List<Double>,
        sampleRate: Int,
        spanSamples: Int,
        collectMs: Long,
        minBpm: Double,
        maxBpm: Double,
        isActive: () -> Boolean,
    ): WindowScore? {
        if (!isActive()) throw CancellationException("BPM detection cancelled")
        val windowSecondsValue = (onsets.lastOrNull() ?: 0.0) - (onsets.firstOrNull() ?: 0.0)
        val intervals = buildIntervals(onsets.sorted())
        val buildMs = 0L
        if (intervals.size < MIN_VOTES) {
            val windowShare = if (spanSamples > 0) intervalMs(onsets, spanSamples, sampleRate, collectMs) else 0L
            onWindowTiming(WindowTiming(attempt, windowSecondsValue, onsets.size, intervals.size, windowShare, buildMs, 0, 0, null))
            return null
        }

        val scoreStarted = System.nanoTime()
        val (bestBpm, bestScore) = scoreIntervals(intervals, minBpm, maxBpm)
        val scoreMs = nanosToMs(scoreStarted)

        val refineStarted = System.nanoTime()
        val refined = refine(intervals, bestBpm, bestScore, minBpm, maxBpm)
        val refineMs = nanosToMs(refineStarted)
        val windowShare = if (spanSamples > 0) intervalMs(onsets, spanSamples, sampleRate, collectMs) else 0L
        onWindowTiming(WindowTiming(attempt, windowSecondsValue, onsets.size, intervals.size, windowShare, buildMs, scoreMs, refineMs, refined?.quality))
        return refined
    }

    private fun intervalMs(onsets: List<Double>, spanSamples: Int, sampleRate: Int, collectMs: Long): Long {
        val spanSec = spanSamples.toDouble() / sampleRate
        val first = onsets.firstOrNull() ?: return 0L
        val last = onsets.lastOrNull() ?: return 0L
        return (collectMs * (last - first).coerceAtMost(spanSec) / spanSec).toLong()
    }

    private fun collectOnsets(samples: FloatArray, sampleRate: Int, isActive: () -> Boolean): List<Double> {
        val onsets = mutableListOf<Double>()
        val detector = ComplexOnsetDetector(bufferSize, onsetThreshold)
        detector.setHandler(OnsetHandler { time, _ -> onsets += time })
        val dispatcher = AudioDispatcher(InMemoryPcmStream(samples, sampleRate), bufferSize, bufferSize / 2)
        dispatcher.addAudioProcessor(detector)
        dispatcher.addAudioProcessor(object : AudioProcessor {
            override fun process(audioEvent: AudioEvent): Boolean = isActive()
            override fun processingFinished() {}
        })
        dispatcher.run()
        return onsets
    }

    private fun buildIntervals(sortedOnsets: List<Double>): List<Double> {
        val intervals = ArrayList<Double>()
        for (i in sortedOnsets.indices) {
            for (j in i + 1 until sortedOnsets.size) {
                val gap = sortedOnsets[j] - sortedOnsets[i]
                if (gap > MAX_INTERVAL_SECONDS) break
                if (gap < MIN_INTERVAL_SECONDS) continue
                intervals += gap
            }
        }
        return intervals
    }

    private fun scoreIntervals(intervals: List<Double>, minBpm: Double, maxBpm: Double): Pair<Double, Int> {
        var bestBpm = minBpm
        var bestScore = 0
        var bpm = minBpm
        while (bpm <= maxBpm) {
            val score = scoreBpm(intervals, bpm)
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
            bpm += BPM_STEP
        }
        return bestBpm to bestScore
    }

    private fun refine(
        intervals: List<Double>,
        bestBpm: Double,
        bestScore: Int,
        minBpm: Double,
        maxBpm: Double,
    ): WindowScore? {
        if (bestScore == 0) return null
        val quality = bestScore.toFloat() / intervals.size.toFloat()
        val period = 60.0 / bestBpm
        val aligned = mutableListOf<Double>()
        for (gap in intervals) {
            val k = (gap / period).roundToInt()
            if (k in 1..MAX_MULTIPLE && kotlin.math.abs(gap - k * period) <= period * PERIOD_TOLERANCE) {
                aligned += gap / k
            }
        }
        if (aligned.isEmpty()) return null
        aligned.sort()
        val precisePeriod = aligned[aligned.size / 2]
        val preciseBpm = (60.0 / precisePeriod).coerceIn(minBpm, maxBpm)
        return WindowScore(roundBpm(preciseBpm), quality)
    }

    private fun scoreBpm(intervals: List<Double>, bpm: Double): Int {
        val period = 60.0 / bpm
        var score = 0
        for (gap in intervals) {
            val k = (gap / period).roundToInt()
            if (k in 1..MAX_MULTIPLE && kotlin.math.abs(gap - k * period) <= period * PERIOD_TOLERANCE) {
                score++
            }
        }
        return score
    }

    private fun roundBpm(bpm: Double): Double = (bpm * 10).roundToInt() / 10.0

    private fun nanosToMs(startedNs: Long): Long = (System.nanoTime() - startedNs) / 1_000_000

    private data class WindowScore(val bpm: Double, val quality: Float)

    data class WindowTiming(
        val attempt: Int,
        val windowSeconds: Double,
        val onsetCount: Int,
        val intervalCount: Int,
        val collectMs: Long,
        val buildMs: Long,
        val scoreMs: Long,
        val refineMs: Long,
        val quality: Float?,
    )

    companion object {
        const val MIN_WINDOW_SAMPLES = 1024
        const val MIN_ONSETS = 4
        const val MIN_VOTES = 3
        const val MIN_ACCEPTABLE_QUALITY = 0.10f
        const val GOOD_QUALITY = 0.55f
        const val MIN_INTERVAL_SECONDS = 0.15
        const val MAX_INTERVAL_SECONDS = 3.0
        const val MAX_MULTIPLE = 8
        const val PERIOD_TOLERANCE = 0.06
        const val BPM_STEP = 0.5
        const val DEFAULT_MIN_BPM = 40.0
        const val DEFAULT_MAX_BPM = 160.0
    }
}

private class InMemoryPcmStream(
    private val samples: FloatArray,
    sampleRate: Int,
) : TarsosDSPAudioInputStream {
    private var position = 0
    private val format = TarsosDSPAudioFormat(
        TarsosDSPAudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat(), 16, 1, 2, sampleRate.toFloat(), false,
    )

    override fun getFormat(): TarsosDSPAudioFormat = format

    override fun getFrameLength(): Long = samples.size.toLong()

    override fun skip(n: Long): Long {
        val skipped = n.coerceAtMost((samples.size - position).toLong())
        position += skipped.toInt()
        return skipped
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= samples.size) return -1
        var written = 0
        while (written + 2 <= len && position < samples.size) {
            val sample = (samples[position].coerceIn(-1f, 1f) * 32767f).roundToInt()
            val low = sample and 0xFF
            val high = (sample shr 8) and 0xFF
            b[off + written] = low.toByte()
            b[off + written + 1] = high.toByte()
            position++
            written += 2
        }
        return written
    }

    override fun close() {}
}