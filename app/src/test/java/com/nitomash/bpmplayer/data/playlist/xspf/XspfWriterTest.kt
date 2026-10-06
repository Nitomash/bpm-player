package com.nitomash.bpmplayer.data.playlist.xspf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XspfWriterTest {

    @Test
    fun `document matches the expected bytes`() {
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<playlist xmlns=\"http://xspf.org/ns/0/\" version=\"1\">" +
                "<title>BPM set</title>" +
                "<trackList>" +
                "<track><location>../Music/a.mp3</location><title>A</title></track>" +
                "<track><location>b.mp3</location><title>B</title></track>" +
                "</trackList>" +
                "</playlist>",
            XspfWriter.encode(
                XspfPlaylist(
                    name = "BPM set",
                    tracks = listOf(
                        XspfTrack("../Music/a.mp3", "A"),
                        XspfTrack("b.mp3", "B"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `empty playlist still emits trackList`() {
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<playlist xmlns=\"http://xspf.org/ns/0/\" version=\"1\">" +
                "<title>Empty</title><trackList></trackList></playlist>",
            XspfWriter.encode(XspfPlaylist(name = "Empty", tracks = emptyList())),
        )
    }

    @Test
    fun `an ampersand is escaped exactly once`() {
        val xml = XspfWriter.encode(
            XspfPlaylist(
                name = "A & B",
                tracks = listOf(XspfTrack("../Rock/Nico & Vinz - Intrigued.mp3", "084 Nico & Vinz")),
            ),
        )

        assertTrue(xml.contains("../Rock/Nico &amp; Vinz - Intrigued.mp3"))
        assertTrue(xml.contains("<title>084 Nico &amp; Vinz</title>"))
        assertTrue(xml.contains("<title>A &amp; B</title>"))
        assertFalse("double escaping leaked into the file", xml.contains("&amp;amp;"))
    }

    @Test
    fun `the document stays parseable after escaping`() {
        val xml = XspfWriter.encode(
            XspfPlaylist(
                name = "Rock & Roll",
                tracks = listOf(XspfTrack("../Rock/Coldplay - Up&Up.mp3", "Up&Up")),
            ),
        )

        val reread = XspfReader.read(KxmlXspfTagSource(xml))

        assertEquals("Rock & Roll", reread?.name)
        assertEquals(listOf(XspfTrack("../Rock/Coldplay - Up&Up.mp3", "Up&Up")), reread?.tracks)
    }

    @Test
    fun `escaping is not applied twice to an already escaped entity`() {
        assertEquals("&amp;amp;", XspfWriter.escape("&amp;"))
    }

    @Test
    fun `escape covers the three markup characters`() {
        assertEquals("&amp;&lt;&gt;", XspfWriter.escape("&<>"))
    }

    @Test
    fun `escape leaves cyrillic and spaces alone`() {
        assertEquals("Музыка №1", XspfWriter.escape("Музыка №1"))
    }

    @Test
    fun `declaration uses utf-8`() {
        assertTrue(XspfWriter.encode(XspfPlaylist("Мой плейлист", emptyList())).startsWith(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
        ))
    }
}