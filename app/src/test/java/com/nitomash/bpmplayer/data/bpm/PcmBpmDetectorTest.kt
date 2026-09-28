package com.nitomash.bpmplayer.data.bpm

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
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
    fun silentAudioFails() {
        val pcm = FloatArray(20 * sampleRate)
        val result = detector.detect(pcm, sampleRate)
        assertTrue("result=$result", result is BpmDetectionResult.Failure)
    }

    @Test
    fun beatStartsAfterSilentFirstThird() {
        val seconds = 90
        val period = 60.0 / 128.0
        val beatStartSample = (seconds / 3 * sampleRate).toInt()
        val pcm = FloatArray(seconds * sampleRate)
        val clickLength = (0.025 * sampleRate).toInt()
        var i = 0
        while (beatStartSample + i * period * sampleRate + clickLength < pcm.size) {
            val start = beatStartSample + (i * period * sampleRate).toInt()
            writeClick(pcm, start, clickLength)
            i++
        }
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

    private fun writeClick(samples: FloatArray, start: Int, clickLength: Int) {
        for (j in 0 until clickLength) {
            if (start + j >= samples.size) break
            val t = j.toDouble() / sampleRate
            samples[start + j] = (sin(2.0 * Math.PI * 800.0 * t) * exp(-t * 160.0)).toFloat()
        }
    }
}