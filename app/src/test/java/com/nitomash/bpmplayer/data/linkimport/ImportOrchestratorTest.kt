package com.nitomash.bpmplayer.data.linkimport

import android.net.Uri
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.music.FolderScanResult
import com.nitomash.bpmplayer.data.music.MusicRepository
import com.nitomash.bpmplayer.data.music.SidecarTrackRecord
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRoot
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRootRepository
import com.nitomash.bpmplayer.domain.linkimport.DownloadedFileVerifier
import com.nitomash.bpmplayer.domain.linkimport.ImportDestination
import com.nitomash.bpmplayer.domain.linkimport.ImportEntry
import com.nitomash.bpmplayer.domain.linkimport.ImportFileKind
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import com.nitomash.bpmplayer.domain.linkimport.ImportJobSnapshot
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStatus
import com.nitomash.bpmplayer.domain.linkimport.ImportJobStore
import com.nitomash.bpmplayer.domain.linkimport.ImportJobUpdate
import com.nitomash.bpmplayer.domain.linkimport.ImportLimits
import com.nitomash.bpmplayer.domain.linkimport.ImportNameKey
import com.nitomash.bpmplayer.domain.linkimport.ImportOutcome
import com.nitomash.bpmplayer.domain.linkimport.ImportPlan
import com.nitomash.bpmplayer.domain.linkimport.ImportPlanBuilder
import com.nitomash.bpmplayer.domain.linkimport.ImportSourceKind
import com.nitomash.bpmplayer.domain.linkimport.PlannedDirectory
import com.nitomash.bpmplayer.domain.linkimport.PlannedFile
import com.nitomash.bpmplayer.domain.linkimport.PlaylistImportSupport
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProvider
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProviderRegistry
import com.nitomash.bpmplayer.domain.linkimport.RelativeImportPath
import com.nitomash.bpmplayer.domain.linkimport.RemoteDirectory
import com.nitomash.bpmplayer.domain.linkimport.RemoteDownload
import com.nitomash.bpmplayer.domain.linkimport.RemoteFile
import com.nitomash.bpmplayer.domain.linkimport.RemoteItem
import com.nitomash.bpmplayer.domain.linkimport.RemotePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class ImportOrchestratorTest {

    private class FakeNode(
        var name: String,
        val uri: String,
        var size: Long?,
        val isDirectory: Boolean,
    )

    private class FakeDestination : ImportDestination {
        val nodes = mutableMapOf<String, MutableList<FakeNode>>()
        val createdDirectories = mutableListOf<String>()
        val createdFiles = mutableListOf<String>()
        val renamed = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        private var nextId = 0

        fun add(parent: String, name: String, size: Long? = null, isDirectory: Boolean = false): FakeNode {
            val node = FakeNode(name, "doc:${nextId++}", size, isDirectory)
            nodes.getOrPut(parent) { mutableListOf() } += node
            return node
        }

        private fun allNodes(): List<FakeNode> = nodes.values.flatten()

        override suspend fun findChild(parent: String, name: String): String? =
            nodes[parent].orEmpty().firstOrNull { ImportNameKey.matches(it.name, name) }?.uri

        override suspend fun listChildren(parent: String): List<ImportEntry> =
            nodes[parent].orEmpty()
                .filter { !it.isDirectory }
                .map { ImportEntry(it.name, it.uri, it.size) }

        override suspend fun sizeOf(entry: String): Long? =
            allNodes().firstOrNull { it.uri == entry }?.size

        override suspend fun createDirectory(parent: String, name: String): String {
            createdDirectories += name
            return add(parent, name, isDirectory = true).uri
        }

        override suspend fun createFile(parent: String, name: String, mimeType: String): String {
            createdFiles += name
            return add(parent, name).uri
        }

        override suspend fun openOutput(entry: String): OutputStream {
            val node = allNodes().first { it.uri == entry }
            return object : ByteArrayOutputStream() {
                override fun close() {
                    node.size = size().toLong()
                    super.close()
                }
            }
        }

        override suspend fun rename(entry: String, name: String): Boolean {
            renamed += name
            allNodes().firstOrNull { it.uri == entry }?.name = name
            return true
        }

        override suspend fun delete(entry: String): Boolean {
            deleted += entry
            nodes.values.forEach { list -> list.removeAll { it.uri == entry } }
            return true
        }
    }

    private class FakeProvider : PublicLinkProvider {
        override val id = "fake"
        lateinit var root: RemoteItem

        override fun canHandle(link: String): Boolean = link == "https://fake/collection"

        override suspend fun inspect(link: String): RemoteItem = root

        override suspend fun listChildren(directory: RemoteDirectory, pageToken: String?): RemotePage =
            error("not used")

        override suspend fun openDownload(file: RemoteFile): RemoteDownload = object : RemoteDownload {
            override val contentLength: Long? = file.sizeBytes
            override val mimeType: String? = file.mimeType

            override suspend fun copyTo(output: OutputStream, onBytesCopied: suspend (Long) -> Unit) {
                val bytes = ByteArray(file.sizeBytes?.toInt() ?: 1) { 1 }
                output.write(bytes)
                onBytesCopied(bytes.size.toLong())
            }

            override fun close() = Unit
        }
    }

    private class FakeRegistry(private val provider: FakeProvider) : PublicLinkProviderRegistry {
        override fun find(link: String): PublicLinkProvider? = provider.takeIf { it.canHandle(link) }
        override fun findById(id: String): PublicLinkProvider? = provider.takeIf { it.id == id }
    }

    private class FakePlanBuilder : ImportPlanBuilder {
        lateinit var plan: ImportPlan
        override suspend fun build(root: RemoteItem, limits: ImportLimits): ImportPlan = plan
    }

    private class FakeRoots : AppLibraryRootRepository {
        override suspend fun getRoot(): AppLibraryRoot = AppLibraryRoot("tree:root", "Root")
        override suspend fun saveRoot(uri: String, displayName: String): AppLibraryRoot = error("not used")
        override suspend fun clearRoot() = error("not used")
    }

    private class FakeVerifier : DownloadedFileVerifier {
        override suspend fun verify(entry: String, kind: ImportFileKind): Boolean = true
    }

    private class FakePlaylistImport : PlaylistImportSupport {
        override suspend fun import(files: List<PlannedFile>, downloadedUris: Map<String, String>): Int = 0
    }

    private class FakeJobStore : ImportJobStore {
        var status: ImportJobStatus? = null
        val updates = mutableListOf<ImportJobUpdate>()

        override suspend fun create(jobId: ImportJobId, link: String, targetFolderName: String?) = Unit
        override suspend fun get(jobId: ImportJobId): ImportJobSnapshot? = null
        override suspend fun active(): ImportJobSnapshot? = null
        override suspend fun getStatus(jobId: ImportJobId): ImportJobStatus? = status
        override suspend fun getLink(jobId: ImportJobId): String? = null
        override suspend fun update(jobId: ImportJobId, update: ImportJobUpdate) {
            status = update.status ?: status
            updates += update
        }
        override fun observe(jobId: ImportJobId): Flow<ImportJobSnapshot?> = emptyFlow()
    }

    private class FakeMusicRepository : MusicRepository {
        override fun observeFolders(): Flow<List<FolderEntity>> = error("not used")
        override fun observeTracks(): Flow<List<TrackEntity>> = error("not used")
        override fun observeById(id: Long): Flow<TrackEntity?> = error("not used")
        override suspend fun getById(id: Long): TrackEntity? = error("not used")
        override suspend fun getTracksWithBpm(): List<TrackEntity> = error("not used")
        override suspend fun getFolders(): List<FolderEntity> = error("not used")
        override suspend fun getFolder(id: Long): FolderEntity? = error("not used")
        override suspend fun getByFolder(folderId: Long): List<TrackEntity> = error("not used")
        override suspend fun addFolder(uri: Uri): FolderScanResult = FolderScanResult(7L, 0)
        override suspend fun addSourceLinks(folderId: Long, links: List<String>) = Unit
        override suspend fun removeFolder(folderId: Long) = error("not used")
        override suspend fun rescanFolder(folderId: Long): FolderScanResult? = error("not used")
        override suspend fun updateBpm(trackId: Long, bpm: Double, source: BpmSource) = error("not used")
        override suspend fun updateBpmBatch(updates: Map<Long, Double>, source: BpmSource): Set<Long> =
            error("not used")
        override suspend fun buildSidecarRecords(folderId: Long): List<SidecarTrackRecord> = error("not used")
        override suspend fun applySidecarRecords(folderId: Long, records: List<SidecarTrackRecord>) =
            error("not used")
        override suspend fun importSidecarFile(folderId: Long, fileUri: Uri): Int = error("not used")
        override suspend fun exportSidecarFile(folderId: Long, fileUri: Uri): Boolean = error("not used")
    }

    private fun file(name: String, size: Long, parent: String = ""): PlannedFile {
        val relative = if (parent.isEmpty()) name else "$parent/$name"
        return PlannedFile(
            source = RemoteFile("fake", "f1", name, null, size, "audio/mpeg"),
            relativePath = RelativeImportPath.of(relative)!!,
            kind = ImportFileKind.AUDIO,
        )
    }

    private fun directory(name: String): PlannedDirectory =
        PlannedDirectory(RelativeImportPath.of(name)!!)

    private fun plan(file: PlannedFile? = null, directory: PlannedDirectory? = null): ImportPlan =
        ImportPlan(
            sourceName = "Collection",
            sourceKind = ImportSourceKind.DIRECTORY,
            directories = listOfNotNull(directory),
            files = listOfNotNull(file),
            skippedFiles = emptyList(),
        )

    private fun runImport(destination: FakeDestination, plan: ImportPlan): ImportOutcome.Success {
        val provider = FakeProvider().apply { root = RemoteDirectory("fake", "root", "Collection") }
        val orchestrator = ImportOrchestrator(
            roots = FakeRoots(),
            providers = FakeRegistry(provider),
            planBuilder = FakePlanBuilder().apply { this.plan = plan },
            destination = destination,
            verifier = FakeVerifier(),
            musicRepository = FakeMusicRepository(),
            playlistImport = FakePlaylistImport(),
            jobStore = FakeJobStore(),
            limits = ImportLimits(),
        )
        return runBlocking {
            orchestrator.run(
                jobId = ImportJobId("job"),
                link = "https://fake/collection",
                targetFolderName = null,
                targetDirectoryUri = "tree:target",
                onProgress = {},
            ) as ImportOutcome.Success
        }
    }

    @Test
    fun `decomposed remote file uses composed local file`() {
        val decomposed = "The Mari\u0301as - Hush.mp3"
        val composed = "The Marías - Hush.mp3"
        val destination = FakeDestination().apply { add("tree:target", composed, size = 100L) }

        val outcome = runImport(destination, plan(file(decomposed, 100L)))

        assertEquals(1, outcome.upToDateFiles)
        assertEquals(0, outcome.downloadedFiles)
        assertEquals(0, outcome.duplicateNameFiles)
        assertTrue(destination.createdFiles.isEmpty())
        assertTrue(destination.renamed.isEmpty())
        assertTrue(destination.deleted.isEmpty())
        assertEquals(listOf(composed), destination.nodes["tree:target"]!!.map { it.name })
    }

    @Test
    fun `decomposed remote directory uses composed local directory`() {
        val decomposed = "Mari\u0301as"
        val composed = "Marías"
        val destination = FakeDestination()
        val existing = destination.add("tree:target", composed, isDirectory = true)
        destination.add(existing.uri, "track.mp3", size = 50L)

        val outcome = runImport(
            destination,
            plan(file("track.mp3", 50L, parent = decomposed), directory(decomposed)),
        )

        assertEquals(1, outcome.upToDateFiles)
        assertEquals(0, outcome.downloadedFiles)
        assertEquals(0, outcome.duplicateNameFiles)
        assertTrue(destination.createdDirectories.isEmpty())
    }

    @Test
    fun `both name forms on disk are kept`() {
        val decomposed = "The Mari\u0301as - Hush.mp3"
        val composed = "The Marías - Hush.mp3"
        val destination = FakeDestination().apply {
            add("tree:target", composed, size = 100L)
            add("tree:target", decomposed, size = 100L)
        }

        val outcome = runImport(destination, plan(file(decomposed, 100L)))

        assertEquals(1, outcome.upToDateFiles)
        assertEquals(0, outcome.downloadedFiles)
        assertEquals(1, outcome.duplicateNameFiles)
        assertTrue(destination.deleted.isEmpty())
        assertEquals(2, destination.nodes["tree:target"]!!.size)
    }

    @Test
    fun `different size replaces the file found by canonical key`() {
        val decomposed = "The Mari\u0301as - Hush.mp3"
        val composed = "The Marías - Hush.mp3"
        val destination = FakeDestination().apply { add("tree:target", composed, size = 99L) }

        val outcome = runImport(destination, plan(file(decomposed, 100L)))

        assertEquals(1, outcome.downloadedFiles)
        assertEquals(0, outcome.upToDateFiles)
        assertEquals(0, outcome.duplicateNameFiles)
        assertEquals(1, destination.deleted.size)
        assertEquals(listOf(decomposed), destination.nodes["tree:target"]!!.map { it.name })
    }
}
