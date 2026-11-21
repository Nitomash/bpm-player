package com.nitomash.bpmplayer.data.linkimport.verification

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.nitomash.bpmplayer.data.music.SidecarCodec
import com.nitomash.bpmplayer.data.playlist.xspf.AndroidXspfTagSource
import com.nitomash.bpmplayer.data.playlist.xspf.XspfReader
import com.nitomash.bpmplayer.domain.linkimport.DownloadedFileVerifier
import com.nitomash.bpmplayer.domain.linkimport.ImportFileKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDownloadedFileVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadedFileVerifier {

    override suspend fun verify(entry: String, kind: ImportFileKind): Boolean = withContext(Dispatchers.IO) {
        when (kind) {
            ImportFileKind.AUDIO -> isAudio(Uri.parse(entry))
            ImportFileKind.SIDECAR -> read(entry)?.let { SidecarCodec.decode(it.toString(Charsets.UTF_8)) != null } ?: false
            ImportFileKind.PLAYLIST -> read(entry)?.let { text ->
                runCatching { XspfReader.read(AndroidXspfTagSource(text.toString(Charsets.UTF_8))) }
                    .getOrNull()
                    ?.tracks
                    ?.isNotEmpty() == true
            } ?: false
        }
    }

    private fun isAudio(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.isNotBlank() == true ||
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.isNotBlank() == true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun read(entry: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(entry))?.use { input ->
            val buffer = ByteArray(8 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_TEXT_BYTES) return null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    private companion object {
        const val MAX_TEXT_BYTES = 4 * 1024 * 1024
    }
}
