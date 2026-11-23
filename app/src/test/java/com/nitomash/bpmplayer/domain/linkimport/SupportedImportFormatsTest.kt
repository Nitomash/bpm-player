package com.nitomash.bpmplayer.domain.linkimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedImportFormatsTest {

    @Test
    fun `audio extensions are classified as audio`() {
        listOf("mix.mp3", "mix.M4A", "mix.flac", "mix.wav", "mix.ogg", "mix.opus", "mix.aiff")
            .forEach { assertEquals(ImportFileKind.AUDIO, SupportedImportFormats.classify(it)) }
    }

    @Test
    fun `sidecar and playlist extensions have their own kinds`() {
        assertEquals(ImportFileKind.SIDECAR, SupportedImportFormats.classify("tracks.BPMMETA"))
        assertEquals(ImportFileKind.PLAYLIST, SupportedImportFormats.classify("set.xspf"))
    }

    @Test
    fun `audio mime type wins over unknown extension`() {
        assertEquals(ImportFileKind.AUDIO, SupportedImportFormats.classify("no-extension", "audio/mpeg"))
        assertEquals(ImportFileKind.AUDIO, SupportedImportFormats.classify("file.bin", "AUDIO/FLAC"))
    }

    @Test
    fun `non audio mime type does not override a known extension`() {
        assertEquals(ImportFileKind.PLAYLIST, SupportedImportFormats.classify("set.xspf", "application/octet-stream"))
    }

    @Test
    fun `supported audio extension depends only on file extension`() {
        assertTrue(SupportedImportFormats.isSupportedAudioExtension("mix.MP3"))
        assertTrue(SupportedImportFormats.isSupportedAudioExtension("album/mix.flac"))
        assertFalse(SupportedImportFormats.isSupportedAudioExtension("album/cover.jpg"))
        assertFalse(SupportedImportFormats.isSupportedAudioExtension("no-extension"))
    }

    @Test
    fun `unknown files are not supported`() {
        assertNull(SupportedImportFormats.classify("cover.jpg"))
        assertNull(SupportedImportFormats.classify("notes.txt"))
        assertNull(SupportedImportFormats.classify("track"))
        assertFalse(SupportedImportFormats.isAudio("cover.jpg", "image/jpeg"))
    }
}
