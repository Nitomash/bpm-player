package com.nitomash.bpmplayer.data.linkimport.yandex

import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import com.nitomash.bpmplayer.domain.linkimport.RemoteDirectory
import com.nitomash.bpmplayer.domain.linkimport.RemoteFile
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexDiskPublicLinkProviderTest {

    private data class ResourceCall(val publicKey: String, val path: String?, val limit: Int?, val offset: Int?)

    private class FakeApi : YandexDiskApi {
        val calls = mutableListOf<ResourceCall>()
        val responses = mutableListOf<YandexResource>()

        override suspend fun resource(
            publicKey: String,
            path: String?,
            limit: Int?,
            offset: Int?,
        ): YandexResource {
            calls += ResourceCall(publicKey, path, limit, offset)
            return responses.removeAt(0)
        }

        override suspend fun downloadHref(publicKey: String, path: String): String = error("not used")

        override suspend fun open(url: String): Response = error("not used")
    }

    private val api = FakeApi()
    private val provider = YandexDiskPublicLinkProvider(api)
    private val publicKey = "https://disk.yandex.ru/d/abc"

    private fun resource(
        type: String,
        path: String,
        name: String? = null,
        size: Long? = null,
        mime: String? = null,
        items: List<YandexResource>? = null,
    ) = YandexResource(type, path, name, size, mime, 1_700_000_000_000L, items, null, null)

    @Test
    fun `only yandex hosts are handled`() {
        assertTrue(provider.canHandle("https://disk.yandex.ru/d/abc"))
        assertTrue(provider.canHandle("https://DISK.YANDEX.COM/d/abc"))
        assertTrue(provider.canHandle("https://yadi.sk/d/abc"))
        assertFalse(provider.canHandle("https://drive.google.com/file/d/abc"))
        assertFalse(provider.canHandle("not a url"))
    }

    @Test
    fun `inspect maps a directory resource`() {
        api.responses += resource("dir", "/disk/Set", "Мой сет")

        val item = runBlocking { provider.inspect(" $publicKey ") } as RemoteDirectory

        assertEquals("yandex_disk", item.providerId)
        assertEquals("Мой сет", item.displayName)
        assertEquals(1_700_000_000_000L, item.modifiedAtMs)
        assertEquals(publicKey to "/disk/Set", YandexResourceId.decode(item.remoteId))
        assertEquals(ResourceCall(publicKey, null, null, null), api.calls.single())
    }

    @Test
    fun `inspect maps a file and falls back to the link name`() {
        api.responses += resource("file", "/disk/track.mp3", size = 1024, mime = "audio/mpeg")

        val item = runBlocking { provider.inspect(publicKey) } as RemoteFile

        assertEquals("abc", item.displayName)
        assertEquals(1024L, item.sizeBytes)
        assertEquals("audio/mpeg", item.mimeType)
        assertEquals(publicKey to "/disk/track.mp3", YandexResourceId.decode(item.remoteId))
    }

    @Test
    fun `list children maps items and keeps pagination token`() {
        val directory = RemoteDirectory(
            providerId = provider.id,
            remoteId = YandexResourceId.encode(publicKey, "/disk/Set"),
            displayName = "Set",
        )
        api.responses += resource(
            "dir",
            "/disk/Set",
            items = (0 until 200).map { resource("file", "/disk/Set/$it.mp3", "$it.mp3") },
        )
        api.responses += resource(
            "dir",
            "/disk/Set",
            items = listOf(resource("file", "/disk/Set/last.bpmmeta", "last.bpmmeta")),
        )

        val first = runBlocking { provider.listChildren(directory, null) }
        assertEquals(200, first.items.size)
        assertEquals("200", first.nextPageToken)
        assertEquals(
            publicKey to "/disk/Set/0.mp3",
            YandexResourceId.decode(first.items.first().remoteId),
        )
        assertEquals(ResourceCall(publicKey, "/disk/Set", 200, 0), api.calls[0])

        val second = runBlocking { provider.listChildren(directory, first.nextPageToken) }
        assertEquals(1, second.items.size)
        assertEquals("last.bpmmeta", (second.items.single() as RemoteFile).displayName)
        assertNull(second.nextPageToken)
        assertEquals(ResourceCall(publicKey, "/disk/Set", 200, 200), api.calls[1])
    }

    @Test
    fun `resource id encodes and decodes public key and path`() {
        val id = YandexResourceId.encode(publicKey, "/disk/A/B.mp3")
        assertEquals(publicKey to "/disk/A/B.mp3", YandexResourceId.decode(id))

        val rootId = YandexResourceId.encode(publicKey, null)
        assertEquals(publicKey to null, YandexResourceId.decode(rootId))
        assertThrows(PublicLinkException.Protocol::class.java) {
            YandexResourceId.decode("malformed")
        }
    }

    @Test
    fun `download of a file without a path is rejected`() {
        val file = RemoteFile(provider.id, publicKey, "track.mp3")

        assertThrows(PublicLinkException.Protocol::class.java) {
            runBlocking { provider.openDownload(file) }
        }
        assertTrue(api.calls.isEmpty())
    }
}
