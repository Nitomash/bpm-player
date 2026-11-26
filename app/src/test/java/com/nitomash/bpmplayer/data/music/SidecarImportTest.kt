package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `matches track by file name`() {
        val tracks = listOf(track(10, "sub/01.mp3"), track(11, "other.mp3"))
        val records = listOf(
            SidecarTrackRecord(fileName = "01.mp3", bpm = 120.0),
            SidecarTrackRecord(fileName = "nope.mp3", bpm = 90.0),
        )
        val patches = planImport(tracks, records)
        assertEquals(1, patches.size)
        assertEquals(10, patches[0].trackId)
        assertEquals(120.0, patches[0].bpm)
    }

    @Test
    fun `matches file name case insensitively`() {
        val tracks = listOf(track(10, "Track.MP3"))
        val records = listOf(SidecarTrackRecord(fileName = "track.mp3", bpm = 120.0))
        val patch = planImport(tracks, records).single()
        assertEquals(10, patch.trackId)
    }

    @Test
    fun `legacy path hint disambiguates duplicate file names`() {
        val tracks = listOf(track(10, "a/01.mp3"), track(11, "b/01.mp3"))
        val records = listOf(SidecarTrackRecord(fileName = "01.mp3", legacyPath = "b/01.mp3", bpm = 120.0))
        val patch = planImport(tracks, records).single()
        assertEquals(11, patch.trackId)
    }

    @Test
    fun `duration disambiguates duplicate file names`() {
        val tracks = listOf(
            track(10, "a/01.mp3").copy(durationMs = 180_000),
            track(11, "b/01.mp3").copy(durationMs = 240_000),
        )
        val records = listOf(SidecarTrackRecord(fileName = "01.mp3", durationMs = 240_000, bpm = 120.0))
        val patch = planImport(tracks, records).single()
        assertEquals(11, patch.trackId)
    }

    @Test
    fun `ambiguous duplicate file names without hints are ignored`() {
        val tracks = listOf(track(10, "a/01.mp3"), track(11, "b/01.mp3"))
        val records = listOf(SidecarTrackRecord(fileName = "01.mp3", bpm = 120.0))
        assertTrue(planImport(tracks, records).isEmpty())
    }

    @Test
    fun `duration mismatch prevents match`() {
        val tracks = listOf(track(10, "a.mp3").copy(durationMs = 180_000))
        val records = listOf(SidecarTrackRecord(fileName = "a.mp3", durationMs = 90_000, bpm = 120.0))
        assertTrue(planImport(tracks, records).isEmpty())
    }

    @Test
    fun `title and artist disambiguate duplicate file names`() {
        val tracks = listOf(
            track(10, "a/01.mp3").copy(title = "Alpha", artist = "X"),
            track(11, "b/01.mp3").copy(title = "Beta", artist = "Y"),
        )
        val records = listOf(SidecarTrackRecord(fileName = "01.mp3", title = "Beta", artist = "Y", bpm = 120.0))
        val patch = planImport(tracks, records).single()
        assertEquals(11, patch.trackId)
    }

    @Test
    fun `later record wins for the same track`() {
        val tracks = listOf(track(10, "01.mp3"))
        val records = listOf(
            SidecarTrackRecord(fileName = "01.mp3", bpm = 100.0),
            SidecarTrackRecord(fileName = "01.mp3", bpm = 130.0),
        )
        val patch = planImport(tracks, records).single()
        assertEquals(130.0, patch.bpm)
    }

    @Test
    fun `only present fields are applied`() {
        val tracks = listOf(track(10, "a.mp3", bpm = 100.0))
        val records = listOf(SidecarTrackRecord(fileName = "a.mp3", title = "Title"))
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
        val records = listOf(SidecarTrackRecord(fileName = "a.mp3", bpm = 128.0, source = BpmSource.TAP))
        val patch = planImport(tracks, records).single()
        assertEquals(BpmSource.TAP, patch.source)
    }

    @Test
    fun `record without file is ignored`() {
        val patches = planImport(
            tracks = listOf(track(10, "a.mp3")),
            records = listOf(SidecarTrackRecord(fileName = "ghost.mp3", bpm = 120.0)),
        )
        assertEquals(0, patches.size)
    }

    @Test
    fun `file without record is ignored`() {
        val patches = planImport(
            tracks = listOf(track(10, "a.mp3"), track(11, "b.mp3")),
            records = listOf(SidecarTrackRecord(fileName = "a.mp3", bpm = 120.0)),
        )
        assertEquals(1, patches.size)
        assertEquals(10, patches[0].trackId)
    }

    @Test
    fun `bpm outside editor range is dropped`() {
        val tracks = listOf(track(10, "a.mp3"))
        val records = listOf(SidecarTrackRecord(fileName = "a.mp3", bpm = 1200.0))
        assertNull(planImport(tracks, records).single().bpm)
    }

    @Test
    fun `empty tags list is kept as clear signal`() {
        val tracks = listOf(track(10, "a.mp3"))
        val records = listOf(SidecarTrackRecord(fileName = "a.mp3", bpm = 120.0, tags = emptyList()))
        val patch = planImport(tracks, records).single()
        assertEquals(emptyList<SidecarTag>(), patch.tags)
    }

    @Test
    fun `track to record omits empty and unknown values`() {
        val trackEntity = track(10, "a.mp3")
        val record = trackToRecord(trackEntity, emptyList())
        assertEquals("a.mp3", record.fileName)
        assertNull(record.legacyPath)
        assertNull(record.title)
        assertNull(record.artist)
        assertNull(record.durationMs)
        assertNull(record.bpm)
        assertNull(record.tags)
    }

    @Test
    fun `track to record keeps meaningful values`() {
        val trackEntity = track(10, "sub/a.mp3").copy(
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
        assertEquals("a.mp3", record.fileName)
        assertNull(record.legacyPath)
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
    fun `merge sidecar sources prefixes nested legacy paths and keeps root`() {
        val sources = listOf(
            SidecarSource(directoryPath = "sub", records = listOf(SidecarTrackRecord(fileName = "a.mp3", legacyPath = "a.mp3", bpm = 120.0))),
            SidecarSource(directoryPath = "", records = listOf(SidecarTrackRecord(fileName = "b.mp3", legacyPath = "b.mp3", bpm = 90.0))),
        )
        val merged = mergeSidecarSources(sources)
        assertEquals(2, merged.size)
        assertEquals("sub/a.mp3", merged[0].legacyPath)
        assertEquals("b.mp3", merged[1].legacyPath)
    }

    @Test
    fun `merge sidecar sources nests deeper directories`() {
        val sources = listOf(
            SidecarSource(directoryPath = "sub/deep", records = listOf(SidecarTrackRecord(fileName = "a.mp3", legacyPath = "a.mp3"))),
            SidecarSource(directoryPath = "sub", records = listOf(SidecarTrackRecord(fileName = "b.mp3", legacyPath = "b.mp3"))),
        )
        val merged = mergeSidecarSources(sources)
        assertEquals("sub/deep/a.mp3", merged[0].legacyPath)
        assertEquals("sub/b.mp3", merged[1].legacyPath)
    }

    @Test
    fun `merge sidecar sources preserves root records without legacy path`() {
        val record = SidecarTrackRecord(fileName = "a.mp3", bpm = 120.0)
        val merged = mergeSidecarSources(listOf(SidecarSource(directoryPath = "sub", records = listOf(record))))
        assertEquals("a.mp3", merged.single().fileName)
        assertNull(merged.single().legacyPath)
    }

    @Test
    fun `merge sidecar sources with duplicate legacy paths last wins`() {
        val sources = listOf(
            SidecarSource(directoryPath = "sub", records = listOf(SidecarTrackRecord(fileName = "a.mp3", legacyPath = "a.mp3", bpm = 100.0))),
            SidecarSource(directoryPath = "", records = listOf(SidecarTrackRecord(fileName = "a.mp3", legacyPath = "sub/a.mp3", bpm = 130.0))),
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
    fun `later record without bpm does not erase earlier bpm`() {
        val tracks = listOf(track(10, "a.mp3"))
        val patches = planImport(
            tracks,
            listOf(
                SidecarTrackRecord(fileName = "a.mp3", bpm = 120.0, source = BpmSource.MANUAL),
                SidecarTrackRecord(fileName = "a.mp3"),
            ),
        )
        assertEquals(120.0, patches.single().bpm)
        assertEquals(BpmSource.MANUAL, patches.single().source)
    }

    @Test
    fun `significant record after empty record wins`() {
        val tracks = listOf(track(10, "a.mp3"))
        val patches = planImport(
            tracks,
            listOf(
                SidecarTrackRecord(fileName = "a.mp3"),
                SidecarTrackRecord(
                    fileName = "a.mp3",
                    bpm = 96.0,
                    source = BpmSource.TAP,
                    tags = listOf(SidecarTag("warm", 4)),
                ),
            ),
        )
        assertEquals(96.0, patches.single().bpm)
        assertEquals(BpmSource.TAP, patches.single().source)
        assertEquals(listOf(SidecarTag("warm", 4)), patches.single().tags)
    }

    @Test
    fun `empty tags record does not clear existing tags`() {
        val tracks = listOf(track(10, "a.mp3"))
        val patches = planImport(
            tracks,
            listOf(
                SidecarTrackRecord(fileName = "a.mp3", tags = listOf(SidecarTag("warm", 4))),
                SidecarTrackRecord(fileName = "a.mp3", tags = emptyList()),
            ),
        )
        assertEquals(listOf(SidecarTag("warm", 4)), patches.single().tags)
    }

    @Test
    fun `blank title and zero duration do not overwrite meaningful values`() {
        val tracks = listOf(track(10, "a.mp3"))
        val patches = planImport(
            tracks,
            listOf(
                SidecarTrackRecord(fileName = "a.mp3", title = "Custom", durationMs = 180_000),
                SidecarTrackRecord(fileName = "a.mp3", title = " ", durationMs = 0),
            ),
        )
        assertEquals("Custom", patches.single().title)
        assertEquals(180_000L, patches.single().durationMs)
    }
}
