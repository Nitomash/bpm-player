package com.nitomash.bpmplayer.data.bpm

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

class BpmAudioDecoder(
    private val maxDecodeSeconds: Int = 40,
    private val decodeTimeoutMs: Long = 60_000,
) {
    suspend fun decode(context: Context, uri: Uri): DecodedPcm? = decodeOnIo(context, uri)

    private suspend fun decodeOnIo(context: Context, uri: Uri): DecodedPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val startedAt = SystemClock.elapsedRealtime()
        try {
            runCatching { extractor.setDataSource(context, uri, null) }
                .onFailure { e ->
                    Log.w(TAG, "decode: setDataSource failed: ${e.message}")
                    return null
                }
            val trackIndex = findAudioTrack(extractor) ?: run {
                Log.w(TAG, "decode: audio track not found")
                return null
            }
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: run {
                Log.w(TAG, "decode: no mime in track format")
                return null
            }
            extractor.selectTrack(trackIndex)
            Log.i(TAG, "decode: start track=$trackIndex mime=$mime")

            codec = createDecoder(mime)
            val rate = runCatching { trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(0)
            val inputChannels = runCatching { trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(DEFAULT_CHANNELS)
            val sampleRate = if (rate > 0) rate else DEFAULT_SAMPLE_RATE
            val pcm = MonoPcmSink(maxDecodeSeconds * sampleRate)
            val bufferInfo = MediaCodec.BufferInfo()
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var currentRate = sampleRate
            var currentChannels = inputChannels
            var saturated = false
            var inputChunks = 0
            var outputChunks = 0
            var decodedBytes = 0
            var lastError: String? = null
            summary.mime = mime
            summary.codecName = codec?.name.orEmpty()
            summary.sampleRate = currentRate
            summary.pcmEncoding = pcmEncoding

            codec.configure(trackFormat, null, null, 0)
            codec.start()
            summary.codecActive = true
            Log.i(TAG, "decode: codec active (sync) name=${codec.name} rate=$currentRate channels=$inputChannels")

            val progress = withTimeoutOrNull(decodeTimeoutMs) {
                var eosSent = false
                while (true) {
                    ensureActive()
                    if (lastError != null) break
                    if (!eosSent) {
                        val inputIndex = runCatching { codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US) }
                            .onFailure { lastError = "input dequeue: ${it.message}" }
                            .getOrDefault(MediaCodec.INFO_TRY_AGAIN_LATER)
                        if (inputIndex >= 0) {
                            val inputBuffer = runCatching { codec.getInputBuffer(inputIndex) }.getOrNull()
                            if (inputBuffer != null) {
                                inputChunks++
                                summary.inputChunks = inputChunks
                                val sampleSize = runCatching { extractor.readSampleData(inputBuffer, 0) }
                                    .onFailure { lastError = "readSampleData: ${it.message}" }
                                    .getOrDefault(-1)
                                if (sampleSize >= 0) {
                                    val sampleTime = runCatching { extractor.sampleTime }.getOrDefault(0L)
                                    runCatching {
                                        codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                                        extractor.advance()
                                    }
                                } else {
                                    eosSent = true
                                    runCatching { codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM) }
                                }
                            } else {
                                eosSent = true
                                runCatching { codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM) }
                            }
                        }
                    }
                    val outputIndex = runCatching { codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US) }
                        .onFailure { lastError = "output dequeue: ${it.message}" }
                        .getOrDefault(MediaCodec.INFO_TRY_AGAIN_LATER)
                    when (outputIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> runCatching {
                            val format = codec.outputFormat
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                currentRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                summary.sampleRate = currentRate
                                pcm.limit = maxDecodeSeconds * currentRate
                            }
                            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                currentChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                summary.pcmEncoding = pcmEncoding
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                        else -> if (outputIndex >= 0) {
                            if (!saturated) {
                                val outputBuffer = runCatching { codec.getOutputBuffer(outputIndex) }.getOrNull()
                                if (outputBuffer != null) {
                                    val frames = MonoPcmReader.appendMonoFrames(
                                        buffer = outputBuffer,
                                        offset = bufferInfo.offset,
                                        byteCount = bufferInfo.size,
                                        channels = currentChannels,
                                        floatEncoding = pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT,
                                        sink = pcm,
                                    )
                                    if (frames > 0) {
                                        outputChunks++
                                        decodedBytes += bufferInfo.size
                                        summary.outputChunks = outputChunks
                                        summary.pcmBytes = decodedBytes
                                    }
                                    if (pcm.isFull) saturated = true
                                }
                            }
                            runCatching { codec.releaseOutputBuffer(outputIndex, false) }
                            if (saturated || bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                break
                            }
                        }
                    }
                }
                true
            } ?: false
            if (!progress) {
                Log.w(TAG, "decode: timeout after ${elapsedMs(startedAt)}ms (${summary.shortString()})")
                return null
            }
            if (lastError != null) {
                throw BpmDecodeException("$lastError (${summary.shortString()})")
            }
            Log.i(
                TAG,
                "decode: done in=${elapsedMs(startedAt)}ms input=$inputChunks output=$outputChunks " +
                    "sat=$saturated frames=${pcm.size} rate=$currentRate ch=$currentChannels " +
                    "enc=$pcmEncoding (${summary.shortString()})",
            )
            if (pcm.size == 0 || currentRate <= 0) return null
            return DecodedPcm(pcm.toArray(), currentRate)
        } catch (e: CancellationException) {
            Log.w(TAG, "decode: cancelled after ${elapsedMs(startedAt)}ms (${summary.shortString()})")
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "decode: error ${e.message} after ${elapsedMs(startedAt)}ms (${summary.shortString()})")
            throw e
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun createDecoder(mime: String): MediaCodec {
        val software = findPreferredDecoder(mime).firstOrNull()
        if (software != null) {
            Log.i(TAG, "decode: trying software decoder $software for $mime")
            runCatching { return MediaCodec.createByCodecName(software) }.onFailure { e ->
                Log.w(TAG, "decode: software decoder failed: ${e.message}")
            }
        }
        return MediaCodec.createDecoderByType(mime)
    }

    private fun findPreferredDecoder(mime: String): List<String> {
        val preferred = ArrayList<String>()
        val fallback = ArrayList<String>()
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in list.codecInfos) {
            if (info.isEncoder || !info.supportedTypes.contains(mime)) continue
            val name = info.name
            val isSoftware = name.startsWith("c2.android.") || name.startsWith("OMX.google.")
            val isSupplier = name.contains(".sec.") || name.contains(".c2.sec") ||
                name.startsWith("c2.sec.") || name.startsWith("OMX.SEC.")
            when {
                isSoftware -> preferred += name
                !isSupplier -> fallback += name
            }
        }
        return preferred + fallback
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun elapsedMs(startedAt: Long): Long = SystemClock.elapsedRealtime() - startedAt

    private val summary = DecodeSummary()

    private data class DecodeSummary(
        var mime: String = "",
        var codecName: String = "",
        var codecActive: Boolean = false,
        var sampleRate: Int = 0,
        var pcmEncoding: Int = 0,
        var inputChunks: Int = 0,
        var outputChunks: Int = 0,
        var pcmBytes: Int = 0,
    ) {
        fun shortString(): String =
            "$mime/$codecName rate=$sampleRate enc=$pcmEncoding in=$inputChunks out=$outputChunks bytes=$pcmBytes active=$codecActive"
    }

    companion object {
        private const val DEFAULT_CHANNELS = 2
        private const val TAG = "BpmDetect"
        private const val DEFAULT_SAMPLE_RATE = 44_100
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }

    data class DecodedPcm(val samples: FloatArray, val sampleRate: Int)
}
