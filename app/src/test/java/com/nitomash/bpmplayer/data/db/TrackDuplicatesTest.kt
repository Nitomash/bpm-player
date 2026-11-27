package com.nitomash.bpmplayer.data.db

import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackDuplicatesTest {

    @Test
    fun `duplicate key ignores case and surrounding spaces`() {
        assertEquals("track.mp3", trackDuplicateKey("  Track.MP3  "))
    }

    @Test
    fun `duplicate key normalizes unicode names`() {
        assertEquals(
            trackDuplicateKey("The Marías.mp3"),
            trackDuplicateKey("The Mari\u0301as.mp3"),
        )
    }

    @Test
    fun `keeps track with lexicographically first relative path`() {
        val tracks = listOf(
            track(id = 1, path = "Z/Track.mp3", fileName = "track.mp3"),
            track(id = 2, path = "A/Track.MP3", fileName = "Track.MP3"),
            track(id = 3, path = "M/other.mp3", fileName = "other.mp3"),
        )

        assertEquals(listOf(2L, 3L), firstTrackOccurrences(tracks).map { it.id })
    }

    @Test
    fun `same file name in different folders is not a duplicate`() {
        val tracks = listOf(
            track(id = 1, folderId = 10, path = "a/track.mp3", fileName = "track.mp3"),
            track(id = 2, folderId = 11, path = "b/track.mp3", fileName = "track.mp3"),
        )

        assertEquals(listOf(1L, 2L), firstTrackOccurrences(tracks).map { it.id })
    }

    @Test
    fun `different extensions are not duplicates`() {
        val tracks = listOf(
            track(id = 1, path = "a/track.mp3", fileName = "track.mp3"),
            track(id = 2, path = "b/track.flac", fileName = "track.flac"),
        )

        assertEquals(listOf(1L, 2L), firstTrackOccurrences(tracks).map { it.id })
    }

    @Test
    fun `keeps first path when only one track has the key`() {
        val track = track(id = 7, path = "x/solo.mp3", fileName = "solo.mp3")

        assertEquals(listOf(7L), firstTrackOccurrences(listOf(track)).map { it.id })
    }

    private fun track(
        id: Long,
        folderId: Long = 1L,
        path: String,
        fileName: String,
    ) = TrackEntity(
        id = id,
        folderId = folderId,
        accessUri = "uri$id",
        relativePath = path,
        fileName = fileName,
        title = fileName.substringBeforeLast('.'),
        artist = "",
        album = "",
        durationMs = 0L,
        bpm = null,
        addedAtMs = 0L,
    )
}
