package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidecarImportTest {

    private fun track(id: Long, relativePath: String, bpm: Double? = null): TrackEntity = TrackEntity(
        id = id,
        folderId = 1,
        accessUri = "content://tree/1/document/$id",
        relativePath = relativePath,
        fileName = relativePath.substringAfterLast('/'),
        title = "",
        artist = "",
        album = "",
        durationMs = 0,
        bpm = bpm,
        addedAtMs = 0,
    )

    @Test
    fun `paths are matched after normalization`() {
        val tracks = listOf(track(10, "sub/01.mp3"), track(11, "other.mp3"))
        val records = listOf(
            SidecarTrackRecord(path = "sub\\01.mp3", bpm = 120.0),
            SidecarTrackRecord(path = "nope.mp3", bpm = 90.0),
        )
        val patches = planImport(tracks, records)
        assertEquals(1, patches.size)
        assertEquals(10, patches[0].trackId)
        assertEquals(120.0, patches[0].bpm)
    }

    @Test
    fun `only present fields are applied`() {
        val tracks = listOf(track(10, "a.mp3", bpm = 100.0))
        val records = listOf(SidecarTrackRecord(path = "a.mp3", title = "Title"))
        val patch = planImport(tracks, records).single()
        assertEquals("Title", patch.title)
        assertNull(patch.artist)
        assertNull(patch.durationMs)
        assertNull(patch.bpm)
        assertNull(patch.source)
        assertNull(patch.tags)
    }

    @Test
    fun `source is carried into patch when present`() {
        val tracks = listOf(track(10, "a.mp3"))
        val records = listOf(SidecarTrackRecord(path = "a.mp3", bpm = 128.0, source = BpmSource.TAP))
        val patch = planImport(tracks, records).single()
        assertEquals(BpmSource.TAP, patch.source)
    }

    @Test
    fun `record without file is ignored`() {
        val patches = planImport(
            tracks = listOf(track(10, "a.mp3")),
            records = listOf(SidecarTrackRecord(path = "ghost.mp3", bpm = 120.0)),
        )
        assertEquals(0, patches.size)
    }

    @Test
    fun `file without record is ignored`() {
        val patches = planImport(
            tracks = listOf(track(10, "a.mp3"), track(11, "b.mp3")),
            records = listOf(SidecarTrackRecord(path = "a.mp3", bpm = 120.0)),
        )
        assertEquals(1, patches.size)
        assertEquals(10, patches[0].trackId)
    }

    @Test
    fun `bpm outside editor range is dropped`() {
        val tracks = listOf(track(10, "a.mp3"))
        val records = listOf(SidecarTrackRecord(path = "a.mp3", bpm = 1200.0))
        assertNull(planImport(tracks, records).single().bpm)
    }

    @Test
    fun `empty tags list is kept as clear signal`() {
        val tracks = listOf(track(10, "a.mp3"))
        val records = listOf(SidecarTrackRecord(path = "a.mp3", bpm = 120.0, tags = emptyList()))
        val patch = planImport(tracks, records).single()
        assertEquals(emptyList<SidecarTag>(), patch.tags)
    }

    @Test
    fun `track to record omits empty and unknown values`() {
        val trackEntity = track(10, "a.mp3")
        val record = trackToRecord(trackEntity, emptyList())
        assertNull(record.title)
        assertNull(record.artist)
        assertNull(record.durationMs)
        assertNull(record.bpm)
        assertNull(record.tags)
    }

    @Test
    fun `track to record keeps meaningful values`() {
        val trackEntity = track(10, "a.mp3").copy(
            title = "A",
            artist = "B",
            durationMs = 300_000,
            bpm = 128.0,
            bpmSource = BpmSource.TAP,
        )
        val record = trackToRecord(
            track = trackEntity,
            tags = listOf(SidecarTag("warm", 4)),
        )
        assertEquals("A", record.title)
        assertEquals("B", record.artist)
        assertEquals(300_000L, record.durationMs)
        assertEquals(128.0, record.bpm)
        assertEquals(listOf(SidecarTag("warm", 4)), record.tags)
    }

@Test
    fun `normalize path handles separators and whitespace`() {
        assertEquals("a/b/c.mp3", normalizePath(" a\\b//c.mp3 "))
        assertEquals("a/b.mp3", normalizePath("a\\b.mp3"))
        assertEquals("a.mp3", normalizePath("a.mp3"))
    }

    @Test
    fun `safe file name replaces invalid characters`() {
        assertEquals("BPM_My music", safeFileName("BPM/My music"))
        assertEquals("a_b", safeFileName("a\\b"))
        assertEquals("a_b_c", safeFileName("a:b*c"))
        assertEquals("_", safeFileName("::"))
    }

    @Test
    fun `merge sidecar sources prefixes nested paths and keeps root`() {
        val sources = listOf(
            SidecarSource(directoryPath = "sub", records = listOf(SidecarTrackRecord(path = "a.mp3", bpm = 120.0))),
            SidecarSource(directoryPath = "", records = listOf(SidecarTrackRecord(path = "b.mp3", bpm = 90.0))),
        )
        val merged = mergeSidecarSources(sources)
        assertEquals(2, merged.size)
        assertEquals("sub/a.mp3", merged[0].path)
        assertEquals("b.mp3", merged[1].path)
    }

    @Test
    fun `merge sidecar sources nests deeper directories`() {
        val sources = listOf(
            SidecarSource(directoryPath = "sub/deep", records = listOf(SidecarTrackRecord(path = "a.mp3"))),
            SidecarSource(directoryPath = "sub", records = listOf(SidecarTrackRecord(path = "b.mp3"))),
        )
        val merged = mergeSidecarSources(sources)
        assertEquals("sub/deep/a.mp3", merged[0].path)
        assertEquals("sub/b.mp3", merged[1].path)
    }

    @Test
    fun `merge sidecar sources with duplicate paths last wins`() {
        val sources = listOf(
            SidecarSource(directoryPath = "sub", records = listOf(SidecarTrackRecord(path = "a.mp3", bpm = 100.0))),
            SidecarSource(directoryPath = "", records = listOf(SidecarTrackRecord(path = "sub/a.mp3", bpm = 130.0))),
        )
        val tracks = listOf(track(10, "sub/a.mp3"))
        val patches = planImport(tracks, mergeSidecarSources(sources))
        assertEquals(1, patches.size)
        assertEquals(130.0, patches[0].bpm)
    }

    @Test
    fun `merge sidecar sources with empty list`() {
        assertEquals(emptyList<SidecarTrackRecord>(), mergeSidecarSources(emptyList()))
    }

    @Test
    fun `import by name matches track by file name ignoring directories`() {
        val tracks = listOf(track(10, "sub/01.mp3"))
        val records = listOf(SidecarTrackRecord(path = "other/deep/01.mp3", bpm = 120.0))
        val patches = planImportByName(tracks, records)
        assertEquals(1, patches.size)
        assertEquals(10, patches[0].trackId)
        assertEquals(120.0, patches[0].bpm)
    }

    @Test
    fun `import by name first duplicate record wins`() {
        val tracks = listOf(track(10, "01.mp3"))
        val records = listOf(
            SidecarTrackRecord(path = "a/01.mp3", bpm = 100.0),
            SidecarTrackRecord(path = "b/01.mp3", bpm = 130.0),
        )
        val patch = planImportByName(tracks, records).single()
        assertEquals(100.0, patch.bpm)
    }

    @Test
    fun `import by name ignores records without matching file`() {
        val tracks = listOf(track(10, "01.mp3"))
        val records = listOf(
            SidecarTrackRecord(path = "a/01.mp3", bpm = 120.0),
            SidecarTrackRecord(path = "ghost.mp3", bpm = 90.0),
        )
        val patches = planImportByName(tracks, records)
        assertEquals(1, patches.size)
        assertEquals(10, patches[0].trackId)
    }

    @Test
    fun `import by name ignores tracks without record`() {
        val tracks = listOf(track(10, "01.mp3"), track(11, "02.mp3"))
        val records = listOf(SidecarTrackRecord(path = "01.mp3", bpm = 120.0))
        val patches = planImportByName(tracks, records)
        assertEquals(1, patches.size)
        assertEquals(10, patches[0].trackId)
    }

    @Test
    fun `import by name only source fields are applied`() {
        val tracks = listOf(track(10, "01.mp3"))
        val records = listOf(SidecarTrackRecord(path = "anywhere/01.mp3", title = "Title", bpm = 128.0))
        val patch = planImportByName(tracks, records).single()
        assertEquals("Title", patch.title)
        assertEquals(128.0, patch.bpm)
        assertNull(patch.artist)
    }
}