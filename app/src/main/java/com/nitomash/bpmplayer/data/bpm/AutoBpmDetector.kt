package com.nitomash.bpmplayer.data.bpm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class AutoBpmDetector(
    private val context: Context,
    private val audioDecoder: BpmAudioDecoder,
    private val pcmBpmDetector: PcmBpmDetector,
    private val settingsRepository: SettingsRepository,
    private val dispatcher: CoroutineDispatcher,
    private val semaphore: Semaphore,
) {
    suspend fun detect(
        uri: Uri,
        savedBpm: Double? = null,
        savedSource: BpmSource? = null,
        isActive: () -> Boolean = { true },
    ): BpmDetectionResult =
        withContext(dispatcher) {
            semaphore.withPermit { detectWithinPermit(uri, savedBpm, savedSource, isActive) }
        }

    private suspend fun detectWithinPermit(
        uri: Uri,
        savedBpm: Double?,
        savedSource: BpmSource?,
        isActive: () -> Boolean,
    ): BpmDetectionResult {
        val settings = settingsRepository.current()
        val label = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        val startNs = System.nanoTime()
        val pcm = try {
            audioDecoder.decode(context, uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BpmDetect", "decode internal error: $label ${e.message}")
            return BpmDetectionResult.Failure(BpmDetectionFailure.INTERNAL_ERROR)
        }
        val decodeMs = (System.nanoTime() - startNs) / 1_000_000
        if (pcm == null) {
            Log.w("BpmDetect", "decode failed for $label")
            return BpmDetectionResult.Failure(BpmDetectionFailure.DECODE_FAILED)
        }
        val detectStartNs = System.nanoTime()
        val result = pcmBpmDetector.detect(
            samples = pcm.samples,
            sampleRate = pcm.sampleRate,
            minBpm = settings.detectorMinBpm,
            maxBpm = settings.detectorMaxBpm,
            isActive = isActive,
            label = label,
        )
        val detectMs = (System.nanoTime() - detectStartNs) / 1_000_000
        Log.i(
            "BpmDetect",
            "result track=$label saved=${savedBpm ?: "-"} src=${savedSource?.name?.lowercase()
                ?: "-"} " +
                "decode=${decodeMs}ms detect=${detectMs}ms samples=${pcm.samples.size} " +
                "rate=${pcm.sampleRate} " +
                "range=${settings.detectorMinBpm}-${settings.detectorMaxBpm} bpm=$result",
        )
        return result
    }
}
