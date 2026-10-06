package com.nitomash.bpmplayer.data.playlist.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class VolumeIdsTest {

    @Test
    fun `primary volume id is parsed`() {
        val parsed = VolumeIds.parseTreeDocumentId("primary:Music")

        assertEquals("primary", parsed?.volumeId)
        assertEquals("Music", parsed?.pathInside)
    }

    @Test
    fun `card uuid is parsed`() {
        val parsed = VolumeIds.parseTreeDocumentId("1A2B-3C4D:Music/Rock")

        assertEquals("1A2B-3C4D", parsed?.volumeId)
        assertEquals("Music/Rock", parsed?.pathInside)
    }

    @Test
    fun `volume root has an empty inside path`() {
        assertEquals("", VolumeIds.parseTreeDocumentId("primary:")?.pathInside)
    }

    @Test
    fun `leading and trailing slashes are trimmed`() {
        assertEquals("Music", VolumeIds.parseTreeDocumentId("primary:/Music/")?.pathInside)
    }

    @Test
    fun `id without separator is rejected`() {
        assertNull(VolumeIds.parseTreeDocumentId("Music"))
    }

    @Test
    fun `empty volume id is rejected`() {
        assertNull(VolumeIds.parseTreeDocumentId(":Music"))
    }

    @Test
    fun `blank id is rejected`() {
        assertNull(VolumeIds.parseTreeDocumentId(" :Music"))
    }
}

class VolumeRootsTest {

    @Test
    fun `app files dir resolves to the card volume root`() {
        val dir = File("/storage/1A2B-3C4D/Android/data/com.example/files")

        assertEquals(
            mapOf("1A2B-3C4D" to File("/storage/1A2B-3C4D").absolutePath),
            cardRootsFromDirs(arrayOf(dir)),
        )
    }

    @Test
    fun `null entries are skipped`() {
        val dir = File("/storage/1A2B-3C4D/Android/data/com.example/files")

        assertEquals(1, cardRootsFromDirs(arrayOf(null, dir, null)).size)
    }

    @Test
    fun `a path that does not look like an app files dir is skipped`() {
        assertEquals(emptyMap<String, String>(), cardRootsFromDirs(arrayOf(File("/storage/1A2B-3C4D/Music"))))
    }

    @Test
    fun `a short path is skipped`() {
        assertEquals(emptyMap<String, String>(), cardRootsFromDirs(arrayOf(File("/files"))))
    }

    @Test
    fun `volume pairs are taken as is`() {
        assertEquals(
            mapOf("1A2B-3C4D" to "/storage/1A2B-3C4D"),
            cardRootsFromVolumes(listOf("1A2B-3C4D" to "/storage/1A2B-3C4D")),
        )
    }

    @Test
    fun `blank volume pairs are dropped`() {
        assertEquals(
            emptyMap<String, String>(),
            cardRootsFromVolumes(listOf("" to "/storage/x", "uuid" to "")),
        )
    }
}