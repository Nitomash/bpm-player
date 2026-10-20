package com.nitomash.bpmplayer.data.playlist.export

import com.nitomash.bpmplayer.data.music.SidecarTag
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleExportPlannerTest {

    @Test
    fun `free folder name is kept`() {
        assertEquals("Микс", BundleExportPlanner.planFolderName("Микс", emptySet()))
    }

    @Test
    fun `existing folder name gets index`() {
        assertEquals("Микс (1)", BundleExportPlanner.planFolderName("Микс", setOf("Микс")))
    }

    @Test
    fun `folder name collisions skip occupied indexes`() {
        assertEquals(
            "Микс (3)",
            BundleExportPlanner.planFolderName("Микс", setOf("Микс", "Микс (1)", "Микс (2)")),
        )
    }

    @Test
    fun `illegal folder characters are sanitized`() {
        assertEquals("a_b", BundleExportPlanner.planFolderName("a/b", emptySet()))
    }

    @Test
    fun `unique file names are kept as is`() {
        val jobs = BundleExportPlanner.planJobs(listOf(track("a.mp3"), track("b.mp3")))
        assertEquals(listOf("a.mp3", "b.mp3"), jobs.map { it.targetName })
    }

    @Test
    fun `duplicate file names get indexes`() {
        val first = track("song.mp3", trackId = 1)
        val second = track("song.mp3", trackId = 2)
        val jobs = BundleExportPlanner.planJobs(listOf(first, first, second))
        assertEquals(listOf("song.mp3", "song (1).mp3", "song (2).mp3"), jobs.map { it.targetName })
    }

    @Test
    fun `extension is preserved on collision`() {
        val jobs = BundleExportPlanner.planJobs(listOf(track("a.flac"), track("a.flac")))
        assertEquals(listOf("a.flac", "a (1).flac"), jobs.map { it.targetName })
    }

    @Test
    fun `extensionless name gets index without extension`() {
        val jobs = BundleExportPlanner.planJobs(listOf(track("noext"), track("noext")))
        assertEquals(listOf("noext", "noext (1)"), jobs.map { it.targetName })
    }

    @Test
    fun `empty track list gives no jobs`() {
        assertTrue(BundleExportPlanner.planJobs(emptyList()).isEmpty())
    }

    @Test
    fun `xspf locations are target file names`() {
        val jobs = BundleExportPlanner.planJobs(listOf(track("song.mp3", title = "Song")))
        val xspf = BundleExportPlanner.buildXspf("Микс", jobs)
        assertEquals("Микс", xspf.name)
        assertEquals(1, xspf.tracks.size)
        assertEquals("song.mp3", xspf.tracks[0].location)
        assertEquals("Song", xspf.tracks[0].title)
    }

    @Test
    fun `xspf title falls back to file name without extension`() {
        val jobs = BundleExportPlanner.planJobs(listOf(track("cool-song.mp3", title = "")))
        val xspf = BundleExportPlanner.buildXspf("Микс", jobs)
        assertEquals("cool-song", xspf.tracks[0].title)
    }

    @Test
    fun `record path is copied file name`() {
        val jobs = BundleExportPlanner.planJobs(listOf(track("song.mp3", trackId = 7)))
        val records = BundleExportPlanner.buildRecords(jobs, emptyMap())
        assertEquals(1, records.size)
        assertEquals("song.mp3", records[0].path)
    }

    @Test
    fun `record carries metadata and tags`() {
        val jobs = BundleExportPlanner.planJobs(
            listOf(track("song.mp3", trackId = 7, title = "Song", artist = "Artist", durationMs = 1000, bpm = 128.0)),
        )
        val tags = mapOf(7L to listOf(SidecarTag("deep", 4)))
        val record = BundleExportPlanner.buildRecords(jobs, tags).single()
        assertEquals("Song", record.title)
        assertEquals("Artist", record.artist)
        assertEquals(1000L, record.durationMs)
        assertEquals(128.0, record.bpm!!, 0.0)
        assertEquals(BpmSource.MANUAL, record.source)
        assertEquals(listOf(SidecarTag("deep", 4)), record.tags)
    }

    @Test
    fun `blank optional fields become null`() {
        val jobs = BundleExportPlanner.planJobs(listOf(track("song.mp3", title = "", artist = "", durationMs = 0)))
        val record = BundleExportPlanner.buildRecords(jobs, emptyMap()).single()
        assertNull(record.title)
        assertNull(record.artist)
        assertNull(record.durationMs)
        assertNull(record.tags)
    }

    private fun track(
        fileName: String,
        trackId: Long = 1,
        title: String = "",
        artist: String = "",
        durationMs: Long = 0,
        bpm: Double? = null,
    ) = ExportTrack(
        trackId = trackId,
        title = title,
        fileName = fileName,
        relativePath = fileName,
        folderRootUri = "",
        accessUri = "",
        artist = artist,
        durationMs = durationMs,
        bpm = bpm,
        bpmSource = if (bpm != null) BpmSource.MANUAL else null,
    )
}
