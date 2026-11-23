package com.nitomash.bpmplayer.data.music

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackPathsTest {

    @Test
    fun `stale paths include missing and unsupported files`() {
        val existing = listOf("album/keep.mp3", "album/gone.flac", "album/cover.jpg", "album/tracks.bpmmeta")
        val scanned = setOf("album/keep.mp3", "album/cover.jpg", "album/tracks.bpmmeta")

        assertEquals(
            setOf("album/gone.flac", "album/cover.jpg", "album/tracks.bpmmeta"),
            staleTrackPaths(existing, scanned),
        )
    }

    @Test
    fun `no stale paths when every scanned file is supported`() {
        val existing = listOf("keep.mp3", "album/mix.FLAC")

        assertEquals(emptySet<String>(), staleTrackPaths(existing, existing.toSet()))
    }
}
