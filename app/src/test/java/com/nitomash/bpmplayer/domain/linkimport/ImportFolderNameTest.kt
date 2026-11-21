package com.nitomash.bpmplayer.domain.linkimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportFolderNameTest {

    private val directory = RemoteDirectory("fake", "root", "My Set", 100L)
    private val file = RemoteFile("fake", "root", "summer mix.mp3", 100L, 1024L)

    @Test
    fun `requested name takes priority`() {
        assertEquals("Custom", ImportFolderName.resolve("Custom", directory))
        assertEquals("Custom", ImportFolderName.resolve("Custom", file))
    }

    @Test
    fun `source directory name is used when nothing requested`() {
        assertEquals("My Set", ImportFolderName.resolve(null, directory))
        assertEquals("My Set", ImportFolderName.resolve("   ", directory))
    }

    @Test
    fun `extension is stripped from a source file`() {
        assertEquals("summer mix", ImportFolderName.resolve(null, file))
    }

    @Test
    fun `fallback is used for an unnamed source`() {
        assertEquals("Импорт", ImportFolderName.resolve(null, RemoteDirectory("fake", "root", "")))
    }

    @Test
    fun `target name is always a single safe segment`() {
        assertEquals("Mix", ImportFolderName.resolve("Sets/Mix", directory))
        assertEquals("Mix", ImportFolderName.from("Sets/Mix"))
        assertNull(ImportFolderName.from(".."))
        assertNull(ImportFolderName.from(null))
    }
}
