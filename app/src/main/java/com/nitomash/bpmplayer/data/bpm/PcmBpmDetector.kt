package com.nitomash.bpmplayer.data.bpm

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.TarsosDSPAudioInputStream
import be.tarsos.dsp.onsets.ComplexOnsetDetector
import be.tarsos.dsp.onsets.OnsetHandler
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class PcmBpmDetector(
    private val windowSeconds: Int = 25,
    private val nextWindowStepSeconds: Int = 25,
    private val maxAttempts: Int = 2,
    private val bufferSize: Int = 512,
    private val onsetThreshold: Double = 0.3,
    private val onWindowTiming: (WindowTiming) -> Unit = {},
) {
    fun detect(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Double = DEFAULT_MIN_BPM,
        maxBpm: Double = DEFAULT_MAX_BPM,
        label: String = "",
        isActive: () -> Boolean = { true },
    ): BpmDetectionResult {
        if (sampleRate <= 0 || samples.size < MIN_WINDOW_SAMPLES) {
            return BpmDetectionResult.Failure(BpmDetectionFailure.NO_ONSETS)
        }
        val windowSamples = windowSeconds * sampleRate
        val stepSamples = nextWindowStepSeconds * sampleRate
        val baseStart = 0
        val spanEnd = (baseStart + (maxAttempts - 1) * stepSamples + windowSamples).coerceAtMost(samples.size)
        val spanSamples = spanEnd - baseStart
        if (spanSamples < windowSamples / 2) {
            return BpmDetectionResult.Failure(BpmDetectionFailure.NO_ONSETS)
        }

        val collectStarted = System.nanoTime()
        val onsets = collectOnsets(samples, baseStart, spanEnd, sampleRate, isActive)
        val collectMs = nanosToMs(collectStarted)

        if (!isActive()) throw CancellationException("BPM detection cancelled")

        if (onsets.size < MIN_ONSETS) {
            onWindowTiming(WindowTiming(0, label, 0.0, onsets.size, 0, collectMs, 0, 0, 0, null))
            return BpmDetectionResult.Failure(BpmDetectionFailure.NO_ONSETS)
        }

var best: WindowScore? = null
        for (attempt in 0 until maxAttempts) {
            if (!isActive()) throw CancellationException("BPM detection cancelled")
            val winStart = attempt * stepSamples
            if (winStart >= spanSamples) break
            val winEnd = (winStart + windowSamples).coerceAtMost(spanSamples)
            if (winEnd - winStart < windowSamples / 2) break
            val startSec = winStart.toDouble() / sampleRate
            val endSec = winEnd.toDouble() / sampleRate
            val windowOnsets = onsetsInWindow(onsets, startSec, endSec)
            if (windowOnsets.size < MIN_ONSETS) continue
            val strengths = onsetStrengths(samples, windowOnsets, baseStart, sampleRate)
            val dump = if (attempt == 0 && label.isNotEmpty()) formatOnsets(windowOnsets, strengths) else ""
            val score = evaluate(
                attempt, label, windowOnsets, strengths, sampleRate, spanSamples, collectMs,
                minBpm, maxBpm, isActive, dump,
            )
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
        label: String,
        onsets: DoubleArray,
        strengths: DoubleArray,
        sampleRate: Int,
        spanSamples: Int,
        collectMs: Long,
        minBpm: Double,
        maxBpm: Double,
        isActive: () -> Boolean,
        onsetDump: String = "",
    ): WindowScore? {
        if (!isActive()) throw CancellationException("BPM detection cancelled")
        val windowSecondsValue = onsets[onsets.size - 1] - onsets[0]
        val buildStarted = System.nanoTime()
        val intervals = buildIntervals(onsets.sortedArray())
        val buildMs = nanosToMs(buildStarted)
        if (intervals.size < MIN_VOTES) {
            val windowShare = if (spanSamples > 0) intervalMs(onsets, spanSamples, sampleRate, collectMs) else 0L
            onWindowTiming(WindowTiming(attempt, label, windowSecondsValue, onsets.size, intervals.size, windowShare, buildMs, 0, 0, null))
            return null
        }

        val scoreStarted = System.nanoTime()
        val periodScore = scorePeriods(onsets, strengths, minBpm, maxBpm)
        val scoreMs = nanosToMs(scoreStarted)

        val refineStarted = System.nanoTime()
        val refined = periodScore?.let { refine(intervals, it, minBpm, maxBpm) }
        val refineMs = nanosToMs(refineStarted)
        val windowShare = if (spanSamples > 0) intervalMs(onsets, spanSamples, sampleRate, collectMs) else 0L
        val ranked = periodScore?.top?.map { it.bpm to it.coverage } ?: emptyList()
        onWindowTiming(
            WindowTiming(
                attempt, label, windowSecondsValue, onsets.size, intervals.size, windowShare,
                buildMs, scoreMs, refineMs, refined?.quality, ranked, onsetDump,
            ),
        )
        return refined
    }

    private fun intervalMs(onsets: DoubleArray, spanSamples: Int, sampleRate: Int, collectMs: Long): Long {
        val spanSec = spanSamples.toDouble() / sampleRate
        return (collectMs * (onsets[onsets.size - 1] - onsets[0]).coerceAtMost(spanSec) / spanSec).toLong()
    }

    private fun collectOnsets(
        samples: FloatArray,
        from: Int,
        to: Int,
        sampleRate: Int,
        isActive: () -> Boolean,
    ): DoubleArray {
        val collected = DoubleBuffer()
        val detector = ComplexOnsetDetector(bufferSize, onsetThreshold)
        detector.setHandler(OnsetHandler { time, _ -> collected.add(time) })
        val dispatcher = AudioDispatcher(InMemoryPcmStream(samples, from, to - from, sampleRate), bufferSize, bufferSize / 2)
        dispatcher.addAudioProcessor(detector)
        dispatcher.addAudioProcessor(object : AudioProcessor {
            override fun process(audioEvent: AudioEvent): Boolean = isActive()
            override fun processingFinished() {}
        })
        dispatcher.run()
        return collected.toArray()
    }

    private fun onsetStrengths(samples: FloatArray, onsets: DoubleArray, from: Int, sampleRate: Int): DoubleArray {
        val radius = (0.01 * sampleRate).toInt().coerceAtLeast(1)
        val strengths = DoubleArray(onsets.size)
        for (i in onsets.indices) {
            val center = from + (onsets[i] * sampleRate).toInt()
            val low = (center - radius).coerceAtLeast(0)
            val high = (center + radius).coerceAtMost(samples.size - 1)
            var peak = 0.0
            if (low <= high) {
                for (j in low..high) {
                    val value = abs(samples[j]).toDouble()
                    if (value > peak) peak = value
                }
            }
            strengths[i] = peak
        }
        return strengths
    }

    private fun formatOnsets(onsets: DoubleArray, strengths: DoubleArray): String {
        val sb = StringBuilder(onsets.size * 10)
        for (i in onsets.indices) {
            if (i > 0) sb.append(',')
            sb.append((onsets[i] * 1000).roundToInt())
            sb.append('/')
            sb.append((strengths[i] * 1000).roundToInt())
        }
        return sb.toString()
    }

    private fun onsetsInWindow(onsets: DoubleArray, startSec: Double, endSec: Double): DoubleArray {
        var matched = 0
        for (time in onsets) {
            if (time >= startSec && time < endSec) matched++
        }
        val window = DoubleArray(matched)
        var index = 0
        for (time in onsets) {
            if (time >= startSec && time < endSec) window[index++] = time
        }
        return window
    }

    private fun buildIntervals(sortedOnsets: DoubleArray): DoubleArray {
        val intervals = DoubleBuffer()
        for (i in sortedOnsets.indices) {
            for (j in i + 1 until sortedOnsets.size) {
                val gap = sortedOnsets[j] - sortedOnsets[i]
                if (gap > MAX_INTERVAL_SECONDS) break
                if (gap < MIN_INTERVAL_SECONDS) continue
                intervals.add(gap)
            }
        }
        return intervals.toArray()
    }

    private fun scorePeriods(
        onsets: DoubleArray,
        strengths: DoubleArray,
        minBpm: Double,
        maxBpm: Double,
    ): PeriodScore? {
        if (onsets.size < MIN_ONSETS) return null
        val duration = onsets[onsets.size - 1] - onsets[0]
        if (duration <= 0.0) return null
        val scored = ArrayList<PeriodScore>(0)
        var best: PeriodScore? = null
        var bpm = minBpm
        while (bpm <= maxBpm) {
            val coverage = coverageAt(onsets, strengths, bpm)
            if (coverage >= 0f) {
                val candidate = PeriodScore(bpm, coverage)
                scored += candidate
                val current = best
                if (current == null || coverage > current.coverage) {
                    best = candidate
                }
            }
            bpm += BPM_STEP
        }
        val chosen = best ?: return null
        return chosen.copy(top = rankedTop(scored))
    }

    private fun rankedTop(scored: List<PeriodScore>): List<PeriodScore> =
        scored.sortedByDescending { it.coverage }.take(TOP_CANDIDATES)

    private fun coverageAt(onsets: DoubleArray, strengths: DoubleArray, bpm: Double): Float {
        val period = 60.0 / bpm
        val bins = phaseBins(period)
        val binWidth = period / bins
        val sums = DoubleArray(bins)
        var total = 0.0
        for (i in onsets.indices) {
            val phase = onsets[i] - floor(onsets[i] / period) * period
            var index = (phase / binWidth).toInt()
            if (index >= bins) index = bins - 1
            if (index < 0) index = 0
            sums[index] += strengths[i]
            total += strengths[i]
        }
        if (total <= 0.0) return -1f
        var captured = 0.0
        for (i in 0 until bins) {
            var near = 0.0
            for (offset in -TOLERANCE_BINS..TOLERANCE_BINS) {
                near += sums[(i + offset + bins) % bins]
            }
            if (near > captured) captured = near
        }
        if (captured <= 0.0) return -1f
        return (captured / total).toFloat()
    }

    private fun phaseBins(period: Double): Int = ceil(TOLERANCE_BINS / PERIOD_TOLERANCE).toInt()

    private fun refine(
        intervals: DoubleArray,
        score: PeriodScore,
        minBpm: Double,
        maxBpm: Double,
    ): WindowScore? {
        val period = 60.0 / score.bpm
        val aligned = DoubleArray(intervals.size)
        var alignedSize = 0
        for (gap in intervals) {
            val k = (gap / period).roundToInt()
            if (k in 1..MAX_MULTIPLE && abs(gap - k * period) <= period * PERIOD_TOLERANCE) {
                aligned[alignedSize++] = gap / k
            }
        }
        if (alignedSize == 0) return null
        val sorted = if (alignedSize == aligned.size) aligned else aligned.copyOf(alignedSize)
        sorted.sort()
        val precisePeriod = sorted[sorted.size / 2]
        val preciseBpm = (60.0 / precisePeriod).coerceIn(minBpm, maxBpm)
        return WindowScore(roundBpm(preciseBpm), score.coverage)
    }

    private fun roundBpm(bpm: Double): Double = (bpm * 10).roundToInt() / 10.0

    private fun nanosToMs(startedNs: Long): Long = (System.nanoTime() - startedNs) / 1_000_000

    private data class WindowScore(val bpm: Double, val quality: Float)

    private data class PeriodScore(
        val bpm: Double,
        val coverage: Float,
        val top: List<PeriodScore> = emptyList(),
    )

    data class WindowTiming(
        val attempt: Int,
        val label: String,
        val windowSeconds: Double,
        val onsetCount: Int,
        val intervalCount: Int,
        val collectMs: Long,
        val buildMs: Long,
        val scoreMs: Long,
        val refineMs: Long,
        val quality: Float?,
        val topCandidates: List<Pair<Double, Float>> = emptyList(),
        val onsetDump: String = "",
    )

    companion object {
        const val MIN_WINDOW_SAMPLES = 1024
        const val MIN_ONSETS = 4
        const val MIN_VOTES = 3
        const val TOLERANCE_BINS = 3
        const val TOP_CANDIDATES = 8
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

private class DoubleBuffer(initialCapacity: Int = 64) {

    private var data = DoubleArray(initialCapacity)

    var size = 0
        private set

    fun add(value: Double) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size] = value
        size++
    }

    fun toArray(): DoubleArray = if (size == data.size) data else data.copyOf(size)
}

private class InMemoryPcmStream(
    private val samples: FloatArray,
    private val from: Int,
    private val length: Int,
    sampleRate: Int,
) : TarsosDSPAudioInputStream {
    private var position = 0
    private val format = TarsosDSPAudioFormat(
        TarsosDSPAudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat(), 16, 1, 2, sampleRate.toFloat(), false,
    )

    override fun getFormat(): TarsosDSPAudioFormat = format

    override fun getFrameLength(): Long = length.toLong()

    override fun skip(n: Long): Long {
        val skipped = n.coerceAtMost((length - position).toLong())
        position += skipped.toInt()
        return skipped
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= length) return -1
        var written = 0
        while (written + 2 <= len && position < length) {
            val sample = (samples[from + position].coerceIn(-1f, 1f) * 32767f).roundToInt()
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
