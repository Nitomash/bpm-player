package com.nitomash.bpmplayer.data.linkimport

import com.nitomash.bpmplayer.domain.linkimport.ImportFileKind
import com.nitomash.bpmplayer.domain.linkimport.ImportLimits
import com.nitomash.bpmplayer.domain.linkimport.ImportSourceKind
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProvider
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProviderRegistry
import com.nitomash.bpmplayer.domain.linkimport.RemoteDirectory
import com.nitomash.bpmplayer.domain.linkimport.RemoteDownload
import com.nitomash.bpmplayer.domain.linkimport.RemoteFile
import com.nitomash.bpmplayer.domain.linkimport.RemoteItem
import com.nitomash.bpmplayer.domain.linkimport.RemotePage
import com.nitomash.bpmplayer.domain.linkimport.SkipReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlanBuilderImplTest {

    private class FakeProvider : PublicLinkProvider {
        override val id = "fake"
        val pages = mutableMapOf<String, List<RemotePage>>()

        override fun canHandle(link: String): Boolean = link.startsWith("https://fake/")

        override suspend fun inspect(link: String): RemoteItem = error("not used")

        override suspend fun listChildren(directory: RemoteDirectory, pageToken: String?): RemotePage {
            val pagesForDirectory = pages[directory.remoteId].orEmpty()
            val index = pageToken?.toIntOrNull() ?: 0
            return pagesForDirectory.getOrNull(index) ?: RemotePage(emptyList(), null)
        }

        override suspend fun openDownload(file: RemoteFile): RemoteDownload = error("not used")
    }

    private class FakeRegistry(private val provider: FakeProvider) : PublicLinkProviderRegistry {
        override fun find(link: String): PublicLinkProvider? = provider.takeIf { it.canHandle(link) }
        override fun findById(id: String): PublicLinkProvider? = provider.takeIf { it.id == id }
    }

    private val provider = FakeProvider()
    private val builder = ImportPlanBuilderImpl(FakeRegistry(provider))

    private fun dir(id: String, name: String) = RemoteDirectory("fake", id, name)
    private fun file(id: String, name: String, size: Long? = null, mime: String? = null) =
        RemoteFile("fake", id, name, null, size, mime)

    private fun build(root: RemoteItem, limits: ImportLimits = ImportLimits()) = runBlocking {
        builder.build(root, limits)
    }

    @Test
    fun `single supported file is planned at root`() {
        val plan = build(file("f1", "Track.MP3"))

        assertEquals(ImportSourceKind.FILE, plan.sourceKind)
        assertEquals("Track.MP3", plan.sourceName)
        assertEquals(listOf("Track.MP3"), plan.files.map { it.relativePath.value })
        assertEquals(ImportFileKind.AUDIO, plan.files.single().kind)
        assertTrue(plan.directories.isEmpty())
        assertTrue(plan.skippedFiles.isEmpty())
    }

    @Test
    fun `unsupported single file is skipped`() {
        val plan = build(file("f1", "cover.jpg"))

        assertEquals(ImportSourceKind.FILE, plan.sourceKind)
        assertTrue(plan.files.isEmpty())
        assertEquals(listOf(SkipReason.UNSUPPORTED_FORMAT), plan.skippedFiles.map { it.reason })
    }

    @Test
    fun `nested directories and pages are traversed in order`() {
        provider.pages["root"] = listOf(
            RemotePage(listOf(file("f1", "a.mp3", 10), dir("d1", "Sub")), "1"),
            RemotePage(listOf(file("f2", "b.xspf", 20)), null),
        )
        provider.pages["d1"] = listOf(
            RemotePage(listOf(file("f3", "c.bpmmeta", 30), file("f4", "cover.jpg")), null),
        )

        val plan = build(dir("root", "Root"))

        assertEquals(ImportSourceKind.DIRECTORY, plan.sourceKind)
        assertEquals(listOf("Sub"), plan.directories.map { it.relativePath.value })
        assertEquals(
            listOf("a.mp3", "Sub/c.bpmmeta", "b.xspf"),
            plan.files.map { it.relativePath.value },
        )
        assertEquals(
            listOf(ImportFileKind.AUDIO, ImportFileKind.SIDECAR, ImportFileKind.PLAYLIST),
            plan.files.map { it.kind },
        )
        assertEquals(listOf("Sub/cover.jpg"), plan.skippedFiles.map { it.relativePath.value })
        assertEquals(listOf(SkipReason.UNSUPPORTED_FORMAT), plan.skippedFiles.map { it.reason })
        assertEquals(60L, plan.knownTotalBytes)
    }

    @Test
    fun `oversized files and invalid names are skipped`() {
        provider.pages["root"] = listOf(
            RemotePage(
                listOf(
                    file("big", "big.mp3", 101),
                    file("bad", ".."),
                    file("mime", "record", mime = "audio/mpeg"),
                ),
                null,
            ),
        )

        val plan = build(dir("root", "Root"), ImportLimits(maxKnownFileSizeBytes = 100))

        assertEquals(listOf("mime"), plan.files.map { it.source.remoteId })
        assertEquals(
            listOf(SkipReason.SIZE_LIMIT, SkipReason.INVALID_NAME),
            plan.skippedFiles.map { it.reason },
        )
        assertNull(plan.knownTotalBytes)
    }

    @Test
    fun `unknown provider is rejected`() {
        assertThrows(PublicLinkException.UnsupportedLink::class.java) {
            build(RemoteDirectory("other", "root", "Root"))
        }
    }

    @Test
    fun `depth limit is enforced`() {
        provider.pages["root"] = listOf(RemotePage(listOf(dir("d1", "Sub")), null))

        assertThrows(PublicLinkException.LimitExceeded::class.java) {
            build(dir("root", "Root"), ImportLimits(maxDepth = 0))
        }
    }

    @Test
    fun `entry limit is enforced`() {
        provider.pages["root"] = listOf(
            RemotePage(listOf(file("f1", "a.mp3"), file("f2", "b.mp3")), null),
        )

        assertThrows(PublicLinkException.LimitExceeded::class.java) {
            build(dir("root", "Root"), ImportLimits(maxEntries = 1))
        }
    }

    @Test
    fun `directory cycle is rejected`() {
        provider.pages["root"] = listOf(RemotePage(listOf(dir("root", "Loop")), null))

        assertThrows(PublicLinkException.LimitExceeded::class.java) {
            build(dir("root", "Root"))
        }
    }
}
