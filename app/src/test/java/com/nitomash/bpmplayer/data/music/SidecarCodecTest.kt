package com.nitomash.bpmplayer.data.music

import com.nitomash.bpmplayer.data.db.entity.BpmSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SidecarCodecTest {

    @Test
    fun `full record round trip`() {
        val records = listOf(
            SidecarTrackRecord(
                path = "tracks/01 Intro.mp3",
                title = "Intro\n\"feat.\" \\ backslash",
                artist = "Artist",
                durationMs = 212000,
                bpm = 128.0,
                source = BpmSource.TAP,
                tags = listOf(SidecarTag("warm", 4), SidecarTag("late night", 5)),
            ),
        )
        val envelope = SidecarCodec.decode(SidecarCodec.encodeAll(records))
        requireNotNull(envelope)
        assertEquals(2, envelope.version)
        assertEquals(records, envelope.records)
    }

    @Test
    fun `source round trips through codec`() {
        assertEquals(
            BpmSource.TAP,
            requireNotNull(
                SidecarCodec.decode(
                    SidecarCodec.encodeAll(listOf(SidecarTrackRecord(path = "a.mp3", bpm = 128.0, source = BpmSource.TAP))),
                ),
            ).records.single().source,
        )
        val encoded = SidecarCodec.encodeRecord(SidecarTrackRecord(path = "a.mp3", bpm = 128.0, source = BpmSource.MANUAL))
        assertTrue(encoded.contains("\"source\":\"manual\""))
    }

    @Test
    fun `unknown or missing source decodes to null`() {
        val text = "#BPM-PLAYER-META v2\n" +
            """{"path":"a.mp3","bpm":128,"source":"quantized"}""" + "\n" +
            """{"path":"b.mp3","bpm":100}"""
        val envelope = requireNotNull(SidecarCodec.decode(text))
        assertNull(envelope.records[0].source)
        assertNull(envelope.records[1].source)
    }

    @Test
    fun `legacy v1 file still decodes`() {
        val text = "#BPM-PLAYER-META v1\n" +
            """{"path":"a.mp3","bpm":120,"source":"tap"}"""
        val envelope = requireNotNull(SidecarCodec.decode(text))
        assertEquals(1, envelope.version)
        assertEquals(120.0, envelope.records[0].bpm)
        assertEquals(BpmSource.TAP, envelope.records[0].source)
    }

    @Test
    fun `nullable and empty fields are omitted`() {
        val record = SidecarTrackRecord(
            path = "a.mp3",
            title = null,
            artist = "",
            durationMs = 0,
            bpm = null,
            tags = null,
        )
        val encoded = SidecarCodec.encodeRecord(record)
        assertFalse(encoded.contains("null"))
        assertFalse(encoded.contains("title"))
        assertFalse(encoded.contains("artist"))
        assertFalse(encoded.contains("duration"))
        assertFalse(encoded.contains("bpm"))
        assertFalse(encoded.contains("tags"))
        assertEquals("""{"path":"a.mp3"}""", encoded)
    }

    @Test
    fun `empty tags list is omitted`() {
        val encoded = SidecarCodec.encodeRecord(SidecarTrackRecord(path = "a.mp3", tags = emptyList()))
        assertFalse(encoded.contains("tags"))
    }

    @Test
    fun `decode returns null without magic header`() {
        assertNull(SidecarCodec.decode("""{"path":"a.mp3"}"""))
        assertNull(SidecarCodec.decode("#SOMETHING-ELSE v1\n{\"path\":\"a.mp3\"}"))
        assertNull(SidecarCodec.decode(""))
    }

    @Test
    fun `empty records produce header only`() {
        val decoded = SidecarCodec.decode(SidecarCodec.encodeAll(emptyList()))
        requireNotNull(decoded)
        assertTrue(decoded.records.isEmpty())
    }

    @Test
    fun `blank and corrupt lines are skipped`() {
        val text = "" +
            "#BPM-PLAYER-META v1\n" +
            "\n" +
            "   \n" +
            "this is not json\n" +
            """{"path":"a.mp3","bpm":120}""" + "\n" +
            """{"broken":}""" + "\n"
        val envelope = requireNotNull(SidecarCodec.decode(text))
        assertEquals(1, envelope.records.size)
        assertEquals("a.mp3", envelope.records[0].path)
        assertEquals(120.0, envelope.records[0].bpm)
    }

    @Test
    fun `unknown fields are ignored`() {
        val text = "#BPM-PLAYER-META v1\n" +
            """{"path":"a.mp3","unknown":42,"nested":{"x":1},"bpm":100}"""
        val envelope = requireNotNull(SidecarCodec.decode(text))
        assertEquals(1, envelope.records.size)
        assertEquals(100.0, envelope.records[0].bpm)
        assertNull(envelope.records[0].title)
    }

    @Test
    fun `whole bpm is encoded without decimal point`() {
        val decoded = requireNotNull(
            SidecarCodec.decode(
                SidecarCodec.encodeAll(listOf(SidecarTrackRecord(path = "a.mp3", bpm = 128.0))),
            ),
        )
        assertEquals(128.0, decoded.records[0].bpm)
        assertTrue(SidecarCodec.encodeRecord(SidecarTrackRecord(path = "a.mp3", bpm = 128.0)).contains("\"bpm\":128"))
    }

    @Test
    fun `record without path is dropped`() {
        val text = "#BPM-PLAYER-META v1\n" +
            """{"title":"no path","bpm":100}"""
        val envelope = requireNotNull(SidecarCodec.decode(text))
        assertTrue(envelope.records.isEmpty())
    }

    @Test
    fun `unicode and cyrillic round trip`() {
        val record = SidecarTrackRecord(path = "клуб/💃 track.mp3", title = "Микс")
        val decoded = requireNotNull(SidecarCodec.decode(SidecarCodec.encodeAll(listOf(record))))
        assertEquals(record, decoded.records.single())
    }

    @Test
    fun `crlf line endings are handled`() {
        val text = "#BPM-PLAYER-META v1\r\n" +
            """{"path":"a.mp3","bpm":96}""" + "\r\n"
        val envelope = requireNotNull(SidecarCodec.decode(text))
        assertEquals(1, envelope.records.size)
    }

    @Test
    fun `encodeTo produces the same text as encodeAll`() {
        val records = listOf(
            SidecarTrackRecord(
                path = "tracks/01 Intro.mp3",
                title = "Intro\n\"feat.\" \\ backslash",
                artist = "Артист",
                durationMs = 212000,
                bpm = 128.0,
                source = BpmSource.AUTO,
                tags = listOf(SidecarTag("warm", 4), SidecarTag("late night", 5)),
            ),
            SidecarTrackRecord(path = "b.mp3", bpm = 96.5),
            SidecarTrackRecord(path = "клуб/💃 c.mp3", title = "Микс", durationMs = 0, tags = emptyList()),
        )

        val streamed = buildString { SidecarCodec.encodeTo(records, this) }

        assertEquals(SidecarCodec.encodeAll(records), streamed)
    }

    @Test
    fun `encodeTo an empty list writes the header only`() {
        assertEquals(SidecarCodec.encodeAll(emptyList()), buildString { SidecarCodec.encodeTo(emptyList(), this) })
    }
}