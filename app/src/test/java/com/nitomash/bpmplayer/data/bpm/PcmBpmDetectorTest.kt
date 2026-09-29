package com.nitomash.bpmplayer.data.bpm

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

class PcmBpmDetectorTest {

    private val detector = PcmBpmDetector()
    private val sampleRate = 44100

    @Test
    fun steadyClicksAt128Bpm() {
        val pcm = clickTrack(bpm = 128.0, seconds = 45)
        val result = detector.detect(pcm, sampleRate)
        assertTrue("result=$result", result is BpmDetectionResult.Success)
        result as BpmDetectionResult.Success
        assertTrue("bpm=${result.bpm}", kotlin.math.abs(result.bpm - 128.0) < 1.5)
        assertTrue("quality=${result.quality}", result.quality > 0.5f)
    }

    @Test
    fun steadyClicksAt92Bpm() {
        val pcm = clickTrack(bpm = 92.0, seconds = 45)
        val result = detector.detect(pcm, sampleRate)
        assertTrue("result=$result", result is BpmDetectionResult.Success)
        result as BpmDetectionResult.Success
        assertTrue("bpm=${result.bpm}", kotlin.math.abs(result.bpm - 92.0) < 1.5)
    }

    @Test
    fun offbeatDottedFiguresDoNotPullTempoAboveTheBeatGrid() {
        val pcm = beatWithOffbeatDottedFigure(bpm = 100.0, seconds = 45)
        val result = detector.detect(pcm, sampleRate)
        assertTrue("result=$result", result is BpmDetectionResult.Success)
        result as BpmDetectionResult.Success
        assertTrue("bpm=${result.bpm}", kotlin.math.abs(result.bpm - 100.0) <= 1.0)
    }

    @Test
    fun silentAudioFails() {
        val pcm = FloatArray(20 * sampleRate)
        val result = detector.detect(pcm, sampleRate)
        assertTrue("result=$result", result is BpmDetectionResult.Failure)
    }

    @Test
    fun decodedSpanYieldsTwoFullNonOverlappingWindows() {
        val windows = mutableListOf<Pair<Int, Double>>()
        val probing = PcmBpmDetector(onWindowTiming = { windows.add(it.attempt to it.windowSeconds) })
        val pcm = noisyClickTrack(bpm = 120.0, seconds = 80, noiseClicks = 900)

        probing.detect(pcm, sampleRate)

        println("80s track windows: $windows")
        assertEquals("windows=$windows", 2, windows.size)
        assertEquals(0, windows[0].first)
        assertEquals(1, windows[1].first)
        for (i in 0..1) {
            assertTrue("w$i=${windows[i].second}", windows[i].second > 23.0)
        }
    }

    @Test
    fun sixtySecondTrackRejectsThirdWindowShorterThanHalfWindow() {
        val windows = mutableListOf<Pair<Int, Double>>()
        val probing = PcmBpmDetector(onWindowTiming = { windows.add(it.attempt to it.windowSeconds) })
        val pcm = noisyClickTrack(bpm = 120.0, seconds = 60, noiseClicks = 700)

        probing.detect(pcm, sampleRate)

        println("60s track windows: $windows")
        assertEquals("windows=$windows", 2, windows.size)
    }

    @Test
    fun beatStartsAfterSilentFirstThird() {
        val pcm = lateStartClickTrack(bpm = 128.0, seconds = 90, rate = sampleRate)
        val result = detector.detect(pcm, sampleRate)
        assertTrue("result=$result", result is BpmDetectionResult.Success)
        result as BpmDetectionResult.Success
        assertTrue("bpm=${result.bpm}", kotlin.math.abs(result.bpm - 128.0) < 1.5)
    }

    @Test
    fun cancelledDetectionThrows() {
        val pcm = clickTrack(bpm = 128.0, seconds = 45)
        assertThrows(CancellationException::class.java) {
            detector.detect(pcm, sampleRate) { false }
        }
    }

    @Test
    fun rangeAboveActualBpmKeepsResult() {
        val pcm = clickTrack(bpm = 128.0, seconds = 45)
        val result = detector.detect(pcm, sampleRate, minBpm = 100.0, maxBpm = 180.0)
        assertTrue("result=$result", result is BpmDetectionResult.Success)
        result as BpmDetectionResult.Success
        assertTrue("bpm=${result.bpm}", kotlin.math.abs(result.bpm - 128.0) < 1.5)
    }

    @Test
    fun resultNeverEscapesGivenRange() {
        val pcm = clickTrack(bpm = 128.0, seconds = 45)
        val ranges = listOf(40.0 to 160.0, 100.0 to 180.0, 60.0 to 70.0, 170.0 to 190.0)

        for ((min, max) in ranges) {
            val result = detector.detect(pcm, sampleRate, minBpm = min, maxBpm = max)
            if (result is BpmDetectionResult.Success) {
                assertTrue("range=$min-$max bpm=${result.bpm}", result.bpm in min..max)
            }
        }
    }

    @Test
    fun detectedBpmStaysInsideGivenRange() {
        val pcm = clickTrack(bpm = 92.0, seconds = 45)
        val result = detector.detect(pcm, sampleRate, minBpm = 85.0, maxBpm = 95.0)
        assertTrue("result=$result", result is BpmDetectionResult.Success)
        result as BpmDetectionResult.Success
        assertTrue("bpm=${result.bpm}", result.bpm in 85.0..95.0)
    }

    @Test
    fun goldenResultsAreStable() {
        val cases = listOf(
            "steady128" to detector.detect(clickTrack(bpm = 128.0, seconds = 45), sampleRate),
            "steady92" to detector.detect(clickTrack(bpm = 92.0, seconds = 45), sampleRate),
            "late128" to detector.detect(
                lateStartClickTrack(bpm = 128.0, seconds = 90, rate = sampleRate),
                sampleRate,
            ),
            "steady128narrow" to detector.detect(
                clickTrack(bpm = 128.0, seconds = 45),
                sampleRate,
                minBpm = 100.0,
                maxBpm = 180.0,
            ),
            "silent" to detector.detect(FloatArray(20 * sampleRate), sampleRate),
            "outOfRange" to detector.detect(
                clickTrack(bpm = 128.0, seconds = 45),
                sampleRate,
                minBpm = 170.0,
                maxBpm = 190.0,
            ),
        )

        assertEquals(
            """
            steady128 -> OK bpm=128.0 q=1.0
            steady92 -> OK bpm=92.0 q=1.0
            late128 -> OK bpm=128.0 q=1.0
            steady128narrow -> OK bpm=128.0 q=1.0
            silent -> FAIL NO_ONSETS
            outOfRange -> FAIL NO_ONSETS
            """.trimIndent(),
            cases.joinToString("\n") { (name, result) -> "$name -> ${render(result)}" },
        )
    }

    @Test
    fun detectionIsDeterministicAcrossRuns() {
        val pcm = clickTrack(bpm = 92.0, seconds = 45)
        val results = List(3) { detector.detect(pcm, sampleRate) }
        assertEquals(results[0], results[1])
        assertEquals(results[1], results[2])
    }

    @Test
    fun cancellationAnywhereInThePipelineThrows() {
        val pcm = clickTrack(bpm = 128.0, seconds = 20)
        var calls = 0
        detector.detect(pcm, sampleRate) { calls++; true }
        val total = calls
        assertTrue("isActive was never called", total > 1)

        val thresholds = listOf(0, 1, total / 4, total / 2, (total * 3) / 4, total - 1)
        for (threshold in thresholds) {
            var seen = 0
            assertThrows("threshold=$threshold of $total", CancellationException::class.java) {
                detector.detect(pcm, sampleRate) { seen++; seen <= threshold }
            }
        }
    }

    @Test
    fun `detection does not modify the input samples`() {
        val pcm = clickTrack(bpm = 128.0, seconds = 45)
        val before = pcm.copyOf()

        detector.detect(pcm, sampleRate)

        assertTrue("input samples were modified", pcm.contentEquals(before))
    }

    private fun render(result: BpmDetectionResult): String = when (result) {
        is BpmDetectionResult.Success -> "OK bpm=${result.bpm} q=${round4(result.quality)}"
        is BpmDetectionResult.Failure -> "FAIL ${result.reason}"
    }

    private fun round4(value: Float): Double = (value * 10000.0).roundToInt() / 10000.0

    private fun clickTrack(bpm: Double, seconds: Int): FloatArray {
        val period = 60.0 / bpm
        val samples = FloatArray(seconds * sampleRate)
        val clickLength = (0.025 * sampleRate).toInt()
        var i = 0
        while (i * period * sampleRate + clickLength < samples.size) {
            val start = (i * period * sampleRate).toInt()
            writeClick(samples, start, clickLength)
            i++
        }
        return samples
    }

    private fun lateStartClickTrack(bpm: Double, seconds: Int, rate: Int): FloatArray {
        val period = 60.0 / bpm
        val beatStartSample = (seconds / 3 * rate).toInt()
        val samples = FloatArray(seconds * rate)
        val clickLength = (0.025 * rate).toInt()
        var i = 0
        while (beatStartSample + i * period * rate + clickLength < samples.size) {
            writeClick(samples, beatStartSample + (i * period * rate).toInt(), clickLength)
            i++
        }
        return samples
    }

    private fun beatWithOffbeatDottedFigure(bpm: Double, seconds: Int): FloatArray {
        val samples = FloatArray(seconds * sampleRate)
        val beatPeriod = 60.0 / bpm
        val figurePeriod = 60.0 / (bpm * 4.0 / 3.0)
        val clickLength = (0.025 * sampleRate).toInt()
        var i = 0
        while (i * beatPeriod * sampleRate + clickLength < samples.size) {
            writeClick(samples, (i * beatPeriod * sampleRate).toInt(), clickLength)
            i++
        }
        val figureStart = 0.30
        var j = 1
        while (figureStart + j * figurePeriod * sampleRate + clickLength < samples.size) {
            val start = ((figureStart + j * figurePeriod) * sampleRate).toInt()
            if (start + clickLength < samples.size) {
                writeQuietClick(samples, start, clickLength)
            }
            j++
        }
        return samples
    }

    private fun noisyClickTrack(bpm: Double, seconds: Int, noiseClicks: Int): FloatArray {
        val samples = clickTrack(bpm, seconds)
        val clickLength = (0.025 * sampleRate).toInt()
        var seed = 12345L
        repeat(noiseClicks) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val position = ((seed ushr 33).toDouble() / Int.MAX_VALUE) * (seconds - 1)
            val start = (position * sampleRate).toInt()
            if (start + clickLength < samples.size) writeQuietClick(samples, start, clickLength)
        }
        return samples
    }

    private fun writeClick(samples: FloatArray, start: Int, clickLength: Int) {
        writeTone(samples, start, clickLength, 1.0f)
    }

    private fun writeQuietClick(samples: FloatArray, start: Int, clickLength: Int) {
        writeTone(samples, start, clickLength, 0.45f)
    }

    private fun writeTone(samples: FloatArray, start: Int, clickLength: Int, amplitude: Float) {
        for (j in 0 until clickLength) {
            if (start + j >= samples.size) break
            val t = j.toDouble() / sampleRate
            val envelope = exp(-t * 160.0)
            val body = sin(2.0 * Math.PI * 800.0 * t) + 0.9 * sin(2.0 * Math.PI * 150.0 * t)
            samples[start + j] = (body * envelope * amplitude).toFloat()
        }
    }
}