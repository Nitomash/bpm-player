package com.nitomash.bpmplayer.data.playlist.export

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import com.nitomash.bpmplayer.data.playlist.xspf.StoragePath
import com.nitomash.bpmplayer.data.playlist.xspf.segmentsOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageVolumes @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val roots: Map<String, String> by lazy { buildRoots() }

    fun pathOfTree(treeUri: Uri, relativeInside: String): StoragePath? {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        return pathOfDocumentId(treeId, relativeInside)
    }

    fun pathOfDocumentDirectory(documentUri: Uri): StoragePath? {
        val documentId = runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull() ?: return null
        return pathOfDocumentId(parentDocumentId(documentId), "")
    }

    fun knownRoots(): List<String> = roots.values.toList()

    private fun pathOfDocumentId(documentId: String, relativeInside: String): StoragePath? {
        val parsed = VolumeIds.parseTreeDocumentId(documentId) ?: return null
        val root = roots[parsed.volumeId] ?: return null
        val inside = (segmentsOf(parsed.pathInside) + segmentsOf(relativeInside)).joinToString("/")
        return StoragePath.of(root, inside)
    }

    private fun parentDocumentId(documentId: String): String {
        val lastSeparator = documentId.lastIndexOf('/')
        if (lastSeparator < 0) return documentId.substringBefore(':') + ":"
        return documentId.substring(0, lastSeparator)
    }

    private fun buildRoots(): Map<String, String> {
        val primary = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
        val cards = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cardRootsFromVolumes(storageVolumePairs())
        } else {
            cardRootsFromDirs(context.getExternalFilesDirs(null))
        }
        return buildMap {
            primary?.let { put(VolumeIds.PRIMARY, it) }
            putAll(cards)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun storageVolumePairs(): List<Pair<String, String>> {
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return runCatching {
            manager.storageVolumes
                .filter { !it.isPrimary }
                .mapNotNull { volume ->
                    val uuid = volume.uuid ?: return@mapNotNull null
                    val directory = volume.directory?.absolutePath ?: return@mapNotNull null
                    uuid to directory
                }
        }.getOrDefault(emptyList())
    }
}

internal fun cardRootsFromDirs(dirs: Array<File?>): Map<String, String> =
    dirs.filterNotNull()
        .mapNotNull { dir ->
            val root = dir.volumeRootOrNull() ?: return@mapNotNull null
            root.name to root.absolutePath
        }
        .toMap()

internal fun cardRootsFromVolumes(pairs: List<Pair<String, String>>): Map<String, String> =
    pairs.filter { (uuid, directory) -> uuid.isNotBlank() && directory.isNotBlank() }.toMap()

private fun File.volumeRootOrNull(): File? {
    val packageDir = parentFile ?: return null
    val dataDir = packageDir.parentFile ?: return null
    val androidDir = dataDir.parentFile ?: return null
    if (name != FILES_DIR) return null
    if (packageDir.parentFile?.name != DATA_DIR) return null
    if (androidDir.name != ANDROID_DIR) return null
    return androidDir.parentFile
}

private const val FILES_DIR = "files"
private const val DATA_DIR = "data"
private const val ANDROID_DIR = "Android"