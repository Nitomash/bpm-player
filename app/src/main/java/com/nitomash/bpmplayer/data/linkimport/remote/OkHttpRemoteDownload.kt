package com.nitomash.bpmplayer.data.linkimport.remote

import com.nitomash.bpmplayer.domain.linkimport.RemoteDownload
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Response
import java.io.OutputStream
import java.io.IOException

class OkHttpRemoteDownload(
    private val response: Response,
) : RemoteDownload {

    override val contentLength: Long? get() = response.body?.contentLength().takeIf { it != -1L }
    override val mimeType: String? get() = response.body?.contentType()?.toString()

    override suspend fun copyTo(
        output: OutputStream,
        onBytesCopied: suspend (downloadedBytes: Long) -> Unit,
    ) {
        val body = response.body ?: throw IOException("Empty download body")
        body.byteStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                total += read
                onBytesCopied(total)
            }
            output.flush()
        }
    }

    override fun close() {
        runCatching { response.close() }
    }
}
