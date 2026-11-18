package com.nitomash.bpmplayer.data.playlist.xspf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nitomash.bpmplayer.data.playlist.PlaylistNaming
import com.nitomash.bpmplayer.data.playlist.PlaylistRepository
import com.nitomash.bpmplayer.data.playlist.export.StorageVolumes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XspfImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val index: LibraryTrackIndex,
    private val volumes: StorageVolumes,
) {

    suspend fun import(documentUri: Uri): XspfImportResult = importAll(listOf(documentUri)).single()

    suspend fun importAll(documentUris: List<Uri>): List<XspfImportResult> {
        if (documentUris.isEmpty()) return emptyList()
        val batch = beginBatch()
        return documentUris.map { batch.import(it) }
    }

    suspend fun beginBatch(): Batch = withContext(Dispatchers.IO) {
        Batch(
            library = index.build(),
            roots = volumes.knownRoots(),
            taken = repository.getPlaylistNames().toMutableSet(),
        )
    }

    fun documentDisplayName(documentUri: Uri): String =
        rawDisplayNameOf(documentUri) ?: FALLBACK_NAME

    inner class Batch internal constructor(
        private val library: Map<String, Long>,
        private val roots: List<String>,
        private val taken: MutableSet<String>,
    ) {
        fun documentDisplayName(documentUri: Uri): String =
            this@XspfImporter.documentDisplayName(documentUri)

        suspend fun import(documentUri: Uri): XspfImportResult = withContext(Dispatchers.IO) {
            importOne(documentUri, library, roots, taken)
        }
    }

    private suspend fun importOne(
        documentUri: Uri,
        library: Map<String, Long>,
        roots: List<String>,
        taken: MutableSet<String>,
    ): XspfImportResult {
        val fallbackName = displayNameOf(documentUri)
        val xml = readText(documentUri)
            ?: return failure(fallbackName, XspfImportError.READ_FAILED)

        val playlist = parse(xml)
            ?: return failure(fallbackName, XspfImportError.NOT_XSPF)

        if (playlist.tracks.isEmpty()) {
            return failure(playlist.name.ifBlank { fallbackName }, XspfImportError.EMPTY_PLAYLIST)
        }

        val base = volumes.pathOfDocumentDirectory(documentUri)
            ?: return failure(playlist.name.ifBlank { fallbackName }, XspfImportError.BASE_UNRESOLVED)

        val trackIds = playlist.tracks.mapNotNull { track ->
            val resolved = XspfLocation.resolve(base, track.location, roots) ?: return@mapNotNull null
            library[resolved.absolutePath]
        }

        if (trackIds.isEmpty()) {
            return failure(
                playlist.name.ifBlank { fallbackName },
                XspfImportError.NO_MATCHES,
                total = playlist.tracks.size,
            )
        }

        val name = PlaylistNaming.unique(
            XspfFileName.sanitize(playlist.name.ifBlank { fallbackName }),
            taken,
        )
        taken.add(name)
        val playlistId = repository.createWithTracks(name, trackIds)

        return XspfImportResult(
            playlistName = name,
            playlistId = playlistId,
            matchedCount = trackIds.size,
            totalCount = playlist.tracks.size,
            error = null,
        )
    }

    private fun parse(xml: String) = runCatching {
        XspfReader.read(AndroidXspfTagSource(xml))
    }.getOrNull()

    private fun readText(documentUri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(documentUri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()

    private fun rawDisplayNameOf(documentUri: Uri): String? {
        val column = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        return runCatching {
            context.contentResolver
                .query(documentUri, arrayOf(column), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    cursor.getString(0)
                }
        }.getOrNull()
    }

    private fun displayNameOf(documentUri: Uri): String =
        rawDisplayNameOf(documentUri)?.substringBeforeLast('.') ?: FALLBACK_NAME

    private fun failure(
        name: String,
        error: XspfImportError,
        total: Int = 0,
    ) = XspfImportResult(
        playlistName = name,
        playlistId = null,
        matchedCount = 0,
        totalCount = total,
        error = error,
    )

    private companion object {
        const val FALLBACK_NAME = "Импортированный плейлист"
    }
}