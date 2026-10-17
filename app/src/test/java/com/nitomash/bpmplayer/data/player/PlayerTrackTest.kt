package com.nitomash.bpmplayer.data.player

import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTrackTest {

    private fun track(
        id: Long = 1,
        uri: String = "content://tree/document/1",
        title: String = "Song",
    ) = TrackEntity(
        id = id,
        folderId = 7,
        accessUri = uri,
        relativePath = "folder/song.mp3",
        fileName = "song.mp3",
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        bpm = 128.0,
        addedAtMs = 0,
    )

    @Test
    fun `converts track to player item`() {
        val playerTrack = track().toPlayerTrack()
        assertEquals(track().id, playerTrack.trackId)
        assertEquals(track().accessUri, playerTrack.uri)
        assertEquals(track().title, playerTrack.title)
        assertEquals(track().durationMs, playerTrack.durationMs)
    }

    @Test
    fun `keeps uri and title for playback`() {
        val playerTrack = track(id = 42, uri = "content://tree/x/y", title = "Neon").toPlayerTrack()
        assertEquals(42L, playerTrack.trackId)
        assertEquals("content://tree/x/y", playerTrack.uri)
        assertEquals("Neon", playerTrack.title)
        assertEquals(180_000L, playerTrack.durationMs)
    }
}