package com.nitomash.bpmplayer.data.bpm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MonoPcmReaderTest {

    @Test
    fun `stereo interleaved is averaged per frame not read as consecutive samples`() {
        val left = floatArrayOf(1.0f, 0.0f, 0.5f, -0.5f)
        val right = floatArrayOf(0.0f, 1.0f, 0.5f, 0.5f)
        val sink = MonoPcmSink(64)

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = interleave16(left, right),
            offset = 0,
            byteCount = left.size * 2 * BYTES_PER_16BIT_SAMPLE,
            channels = 2,
            floatEncoding = false,
            sink = sink,
        )

        assertEquals(4, frames)
        assertEquals(4, sink.size)
        assertFloatArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f, 0.0f), sink.toArray())
    }

    @Test
    fun `identical channels reproduce the original signal`() {
        val mono = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f, 0.5f)
        val sink = MonoPcmSink(64)

        MonoPcmReader.appendMonoFrames(
            buffer = interleave16(mono, mono),
            offset = 0,
            byteCount = mono.size * 2 * BYTES_PER_16BIT_SAMPLE,
            channels = 2,
            floatEncoding = false,
            sink = sink,
        )

        assertFloatArrayEquals(mono, sink.toArray())
    }

    @Test
    fun `mono buffer keeps every sample unchanged`() {
        val mono = floatArrayOf(0.25f, -0.25f, 0.75f)
        val sink = MonoPcmSink(64)

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = mono16(mono),
            offset = 0,
            byteCount = mono.size * BYTES_PER_16BIT_SAMPLE,
            channels = 1,
            floatEncoding = false,
            sink = sink,
        )

        assertEquals(3, frames)
        assertFloatArrayEquals(mono, sink.toArray())
    }

    @Test
    fun `frame count is divided by channels so stereo halves the frame count`() {
        val stereoBytes = 8 * 2 * BYTES_PER_16BIT_SAMPLE
        val monoBytes = 8 * BYTES_PER_16BIT_SAMPLE

        assertEquals(8, MonoPcmReader.frameCount(stereoBytes, channels = 2, floatEncoding = false))
        assertEquals(8, MonoPcmReader.frameCount(monoBytes, channels = 1, floatEncoding = false))
    }

    @Test
    fun `float encoding uses four bytes per sample`() {
        val values = floatArrayOf(0.5f, -0.5f, 0.25f, 0.125f)
        val buffer = ByteBuffer.allocate(values.size * BYTES_PER_FLOAT_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        for (value in values) buffer.putFloat(value)
        buffer.flip()
        val sink = MonoPcmSink(64)

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = buffer,
            offset = 0,
            byteCount = values.size * BYTES_PER_FLOAT_SAMPLE,
            channels = 1,
            floatEncoding = true,
            sink = sink,
        )

        assertEquals(4, frames)
        assertFloatArrayEquals(values, sink.toArray())
    }

    @Test
    fun `surround 5_1 is averaged across all six channels`() {
        val perChannel = floatArrayOf(0.6f, -0.6f)
        val buffer = ByteBuffer.allocate(perChannel.size * 6 * BYTES_PER_16BIT_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until perChannel.size) {
            for (channel in 0 until 6) {
                buffer.putShort(toPcm16(if (channel % 2 == 0) perChannel[index] else -perChannel[index]))
            }
        }
        buffer.flip()
        val sink = MonoPcmSink(64)

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = buffer,
            offset = 0,
            byteCount = perChannel.size * 6 * BYTES_PER_16BIT_SAMPLE,
            channels = 6,
            floatEncoding = false,
            sink = sink,
        )

        assertEquals(2, frames)
        assertFloatArrayEquals(floatArrayOf(0f, 0f), sink.toArray())
    }

    @Test
    fun `offset skips leading bytes that the codec does not fill`() {
        val sink = MonoPcmSink(64)
        val buffer = ByteBuffer.allocate(3 * BYTES_PER_16BIT_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(999)
        buffer.putShort(toPcm16(0.5f))
        buffer.putShort(toPcm16(-0.5f))
        buffer.flip()

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = buffer,
            offset = 2,
            byteCount = 2 * BYTES_PER_16BIT_SAMPLE,
            channels = 1,
            floatEncoding = false,
            sink = sink,
        )

        assertEquals(2, frames)
        assertFloatArrayEquals(floatArrayOf(0.5f, -0.5f), sink.toArray())
    }

    @Test
    fun `trailing partial frame is dropped`() {
        val sink = MonoPcmSink(64)
        val buffer = ByteBuffer.allocate(2 * 2 * BYTES_PER_16BIT_SAMPLE + 1).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(toPcm16(0.5f))
        buffer.putShort(toPcm16(0.5f))
        buffer.putShort(toPcm16(-0.25f))
        buffer.putShort(toPcm16(-0.25f))
        buffer.put(0x7F)
        buffer.flip()
        val byteCount = buffer.capacity()

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = buffer,
            offset = 0,
            byteCount = byteCount,
            channels = 2,
            floatEncoding = false,
            sink = sink,
        )

        assertEquals(2, frames)
        assertFloatArrayEquals(floatArrayOf(0.5f, -0.25f), sink.toArray())
    }

    @Test
    fun `sink stops at its limit without overflowing`() {
        val sink = MonoPcmSink(2)
        val values = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = mono16(values),
            offset = 0,
            byteCount = values.size * BYTES_PER_16BIT_SAMPLE,
            channels = 1,
            floatEncoding = false,
            sink = sink,
        )

        assertEquals(2, frames)
        assertTrue(sink.isFull)
        assertFloatArrayEquals(floatArrayOf(0.1f, 0.2f), sink.toArray())
    }

    @Test
    fun `raising the limit later keeps already appended samples`() {
        val sink = MonoPcmSink(1)
        val values = floatArrayOf(0.1f, 0.2f)
        MonoPcmReader.appendMonoFrames(
            buffer = mono16(values),
            offset = 0,
            byteCount = values.size * BYTES_PER_16BIT_SAMPLE,
            channels = 1,
            floatEncoding = false,
            sink = sink,
        )
        assertEquals(1, sink.size)

        sink.limit = 8
        sink.add(0.9f)

        assertFloatArrayEquals(floatArrayOf(0.1f, 0.9f), sink.toArray())
    }

    @Test
    fun `zero channels produce no frames instead of dividing by zero`() {
        val sink = MonoPcmSink(64)
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

        val frames = MonoPcmReader.appendMonoFrames(
            buffer = buffer,
            offset = 0,
            byteCount = 8,
            channels = 0,
            floatEncoding = false,
            sink = sink,
        )

        assertEquals(0, frames)
        assertEquals(0, sink.size)
    }

    private fun interleave16(left: FloatArray, right: FloatArray): ByteBuffer {
        val frames = maxOf(left.size, right.size)
        val buffer = ByteBuffer.allocate(frames * 2 * BYTES_PER_16BIT_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until frames) {
            buffer.putShort(toPcm16(left.getOrElse(index) { 0f }))
            buffer.putShort(toPcm16(right.getOrElse(index) { 0f }))
        }
        buffer.flip()
        return buffer
    }

    private fun mono16(values: FloatArray): ByteBuffer {
        val buffer = ByteBuffer.allocate(values.size * BYTES_PER_16BIT_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        for (value in values) buffer.putShort(toPcm16(value))
        buffer.flip()
        return buffer
    }

    private fun toPcm16(value: Float): Short = (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    private fun assertFloatArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals("length", expected.size, actual.size)
        for (index in expected.indices) {
            assertEquals("index $index", expected[index], actual[index], TOLERANCE)
        }
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
