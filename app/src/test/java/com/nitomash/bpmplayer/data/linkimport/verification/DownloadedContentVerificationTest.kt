package com.nitomash.bpmplayer.data.linkimport.verification

import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.music.SidecarCodec
import com.nitomash.bpmplayer.data.music.SidecarTrackRecord
import com.nitomash.bpmplayer.data.playlist.xspf.KxmlXspfTagSource
import com.nitomash.bpmplayer.data.playlist.xspf.XspfReader
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedContentVerificationTest {

    private fun parsedPlaylist(xml: String) =
        runCatching { XspfReader.read(KxmlXspfTagSource(xml)) }.getOrNull()

    @Test
    fun `playlist with tracks passes verification`() {
        val playlist = parsedPlaylist(
            """
            <playlist version="1">
              <title>Set</title>
              <trackList>
                <track><location>track.mp3</location></track>
              </trackList>
            </playlist>
            """.trimIndent(),
        )

        assertTrue(playlist?.tracks?.isNotEmpty() == true)
    }

    @Test
    fun `playlist without tracks fails verification`() {
        val playlist = parsedPlaylist(
            "<playlist version=\"1\"><trackList></trackList></playlist>",
        )
        val invalid = parsedPlaylist("<not-a-playlist/>")

        assertTrue(playlist?.tracks.isNullOrEmpty())
        assertTrue(invalid?.tracks.isNullOrEmpty())
    }

    @Test
    fun `sidecar with a valid record passes verification`() {
        val text = SidecarCodec.encodeAll(
            listOf(SidecarTrackRecord(fileName = "track.mp3", bpm = 128.0, source = BpmSource.TAP)),
        )

        assertNotNull(SidecarCodec.decode(text))
    }

    @Test
    fun `sidecar with invalid content fails verification`() {
        assertNull(SidecarCodec.decode("not a sidecar"))
    }
}
