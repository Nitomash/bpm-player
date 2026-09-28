package com.nitomash.bpmplayer.data.bpm

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val BYTES_PER_16BIT_SAMPLE = 2
internal const val BYTES_PER_FLOAT_SAMPLE = 4

private const val PCM_16_SCALE = 32768f

internal class MonoPcmSink(initialLimit: Int) {

    private var data = FloatArray(initialLimit.coerceAtLeast(1))

    var limit = data.size
        set(value) {
            val sanitized = value.coerceAtLeast(1)
            if (sanitized > data.size) data = data.copyOf(sanitized)
            field = sanitized
        }

    var size = 0
        private set

    val isFull: Boolean get() = size >= limit

    fun add(value: Float) {
        if (size == data.size) return
        data[size] = value
        size++
    }

    fun toArray(): FloatArray = if (size == data.size) data else data.copyOf(size)
}

internal object MonoPcmReader {

    fun frameCount(byteCount: Int, channels: Int, floatEncoding: Boolean): Int {
        if (channels <= 0) return 0
        val bytesPerSample = if (floatEncoding) BYTES_PER_FLOAT_SAMPLE else BYTES_PER_16BIT_SAMPLE
        return byteCount / bytesPerSample / channels
    }

    fun appendMonoFrames(
        buffer: ByteBuffer,
        offset: Int,
        byteCount: Int,
        channels: Int,
        floatEncoding: Boolean,
        sink: MonoPcmSink,
    ): Int {
        val frameCount = frameCount(byteCount, channels, floatEncoding)
        if (frameCount <= 0) return 0
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.limit(offset + byteCount)
        buffer.position(offset)
        var appended = 0
        while (appended < frameCount && !sink.isFull) {
            var sum = 0f
            var channel = 0
            while (channel < channels) {
                sum += if (floatEncoding) buffer.float else buffer.short / PCM_16_SCALE
                channel++
            }
            sink.add(sum / channels)
            appended++
        }
        return appended
    }
}
