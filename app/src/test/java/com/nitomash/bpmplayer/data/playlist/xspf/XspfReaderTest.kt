package com.nitomash.bpmplayer.data.playlist.xspf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XspfReaderTest {

    @Test
    fun `playlist name and tracks are read`() {
        val playlist = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <playlist xmlns="http://xspf.org/ns/0/" version="1">
              <title>BPM set</title>
              <trackList>
                <track><location>../Music/a.mp3</location><title>A</title></track>
                <track><location>b.mp3</location><title>B</title></track>
              </trackList>
            </playlist>
            """.trimIndent(),
        )

        assertEquals("BPM set", playlist?.name)
        assertEquals(
            listOf(XspfTrack("../Music/a.mp3", "A"), XspfTrack("b.mp3", "B")),
            playlist?.tracks,
        )
    }

    @Test
    fun `prefixed elements are read by local name`() {
        val playlist = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <n0:playlist xmlns:n0="http://xspf.org/ns/0/" n0:version="1">
              <n0:title>Prefixed</n0:title>
              <n0:trackList>
                <n0:track><n0:location>a.mp3</n0:location><n0:title>A</n0:title></n0:track>
              </n0:trackList>
            </n0:playlist>
            """.trimIndent(),
        )

        assertEquals("Prefixed", playlist?.name)
        assertEquals(listOf(XspfTrack("a.mp3", "A")), playlist?.tracks)
    }

    @Test
    fun `track without title falls back to the location`() {
        val playlist = parse(
            """
            <playlist version="1">
              <trackList>
                <track><location>../Music/Sub/My Track.mp3</location></track>
              </trackList>
            </playlist>
            """.trimIndent(),
        )

        assertEquals("", playlist?.name)
        assertEquals(listOf(XspfTrack("../Music/Sub/My Track.mp3", "My Track")), playlist?.tracks)
    }

    @Test
    fun `empty track list yields no tracks`() {
        val playlist = parse(
            """
            <playlist version="1"><title>Empty</title><trackList></trackList></playlist>
            """.trimIndent(),
        )

        assertEquals("Empty", playlist?.name)
        assertEquals(emptyList<XspfTrack>(), playlist?.tracks)
    }

    @Test
    fun `track without location is skipped`() {
        val playlist = parse(
            """
            <playlist version="1">
              <trackList>
                <track><title>No location</title></track>
                <track><location>a.mp3</location></track>
              </trackList>
            </playlist>
            """.trimIndent(),
        )

        assertEquals(listOf(XspfTrack("a.mp3", "a")), playlist?.tracks)
    }

    @Test
    fun `markup entities in locations are decoded`() {
        val playlist = parse(
            """
            <playlist version="1">
              <trackList>
                <track><location>../Rock/A &amp; B.mp3</location></track>
              </trackList>
            </playlist>
            """.trimIndent(),
        )

        assertEquals(listOf(XspfTrack("../Rock/A & B.mp3", "A & B")), playlist?.tracks)
    }

    @Test
    fun `absolute location is kept as is`() {
        val playlist = parse(
            """
            <playlist version="1">
              <trackList>
                <track><location>/storage/emulated/0/Music/a.mp3</location></track>
              </trackList>
            </playlist>
            """.trimIndent(),
        )

        assertEquals(
            listOf(XspfTrack("/storage/emulated/0/Music/a.mp3", "a")),
            playlist?.tracks,
        )
    }

    @Test
    fun `playlist title after track list does not leak into tracks`() {
        val playlist = parse(
            """
            <playlist version="1">
              <trackList><track><location>a.mp3</location></track></trackList>
              <title>Trailing</title>
            </playlist>
            """.trimIndent(),
        )

        assertEquals("Trailing", playlist?.name)
        assertEquals(listOf(XspfTrack("a.mp3", "a")), playlist?.tracks)
    }

    @Test
    fun `foreign xml is rejected`() {
        assertNull(parse("""<rss version="2.0"><channel></channel></rss>"""))
    }

    @Test
    fun `empty document is rejected`() {
        assertNull(parse(""))
    }

    @Test
    fun `title is derived from a windows style location`() {
        assertEquals("My Track", XspfReader.titleOf("""C:\Music\My Track.mp3"""))
    }

    private fun parse(xml: String): XspfPlaylist? {
        return XspfReader.read(KxmlXspfTagSource(xml))
    }
}