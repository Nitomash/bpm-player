package com.nitomash.bpmplayer.domain.linkimport

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportCountersTest {

    @Test
    fun `rejected file is counted as processed but not downloaded`() {
        val counters = ImportCounters().fileRejected()

        assertEquals(0, counters.downloadedFiles)
        assertEquals(1, counters.invalidFiles)
        assertEquals(1, counters.processedFiles)
        assertEquals(2, counters.totalSkipped(1))
    }

    @Test
    fun `downloaded file adds its size and is counted once`() {
        val counters = ImportCounters()
            .fileDownloaded(100L)
            .fileDownloaded(null)

        assertEquals(2, counters.downloadedFiles)
        assertEquals(0, counters.invalidFiles)
        assertEquals(2, counters.processedFiles)
        assertEquals(100L, counters.downloadedBytes)
        assertEquals(3, counters.totalSkipped(3))
    }

    @Test
    fun `already present file is processed but not downloaded`() {
        val counters = ImportCounters().fileAlreadyPresent()

        assertEquals(0, counters.downloadedFiles)
        assertEquals(1, counters.upToDateFiles)
        assertEquals(1, counters.processedFiles)
        assertEquals(0L, counters.downloadedBytes)
        assertEquals(2, counters.totalSkipped(2))
    }

    @Test
    fun `mixed files keep counts independent`() {
        val counters = ImportCounters()
            .fileDownloaded(512L)
            .fileRejected()
            .fileDownloaded(256L)
            .fileRejected()

        assertEquals(2, counters.downloadedFiles)
        assertEquals(2, counters.invalidFiles)
        assertEquals(4, counters.processedFiles)
        assertEquals(768L, counters.downloadedBytes)
        assertEquals(5, counters.totalSkipped(3))
    }
}
