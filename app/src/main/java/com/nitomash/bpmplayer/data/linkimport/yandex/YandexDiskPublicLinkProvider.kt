package com.nitomash.bpmplayer.data.linkimport.yandex

import com.nitomash.bpmplayer.data.linkimport.remote.OkHttpRemoteDownload
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkProvider
import com.nitomash.bpmplayer.domain.linkimport.RemoteDirectory
import com.nitomash.bpmplayer.domain.linkimport.RemoteDownload
import com.nitomash.bpmplayer.domain.linkimport.RemoteFile
import com.nitomash.bpmplayer.domain.linkimport.RemoteItem
import com.nitomash.bpmplayer.domain.linkimport.RemotePage
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YandexDiskPublicLinkProvider @Inject constructor(
    private val api: YandexDiskApi,
) : PublicLinkProvider {

    override val id: String = PROVIDER_ID

    override fun canHandle(link: String): Boolean {
        val uri = parsePublicLink(link) ?: return false
        return uri.path.matches(PUBLIC_PATH) && isYandexDiskHost(uri.host?.lowercase())
    }

    override suspend fun inspect(link: String): RemoteItem {
        val publicKey = normalizePublicLink(link)
        val resource = api.resource(publicKey, null)
        val name = resource.name ?: publicKey.substringAfterLast('/')
        val remoteId = YandexResourceId.encode(publicKey, resource.path.orEmpty())
        return when {
            resource.isDirectory -> RemoteDirectory(id, remoteId, name, resource.modifiedAtMs)
            resource.isFile -> RemoteFile(
                providerId = id,
                remoteId = remoteId,
                displayName = name,
                modifiedAtMs = resource.modifiedAtMs,
                sizeBytes = resource.sizeBytes,
                mimeType = resource.mimeType,
            )

            else -> throw PublicLinkException.Protocol("unknown resource type")
        }
    }

    override suspend fun listChildren(directory: RemoteDirectory, pageToken: String?): RemotePage {
        val (publicKey, path) = YandexResourceId.decode(directory.remoteId)
        val offset = pageToken?.toIntOrNull() ?: 0
        val response = api.resource(publicKey, path, PAGE_SIZE, offset)
        val items = response.items.orEmpty().mapNotNull { resource ->
            val name = resource.name
                ?: resource.path?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val childId = YandexResourceId.encode(publicKey, resource.path ?: path)
            when {
                resource.isDirectory -> RemoteDirectory(id, childId, name, resource.modifiedAtMs)
                resource.isFile -> RemoteFile(
                    providerId = id,
                    remoteId = childId,
                    displayName = name,
                    modifiedAtMs = resource.modifiedAtMs,
                    sizeBytes = resource.sizeBytes,
                    mimeType = resource.mimeType,
                )

                else -> null
            }
        }
        val next = if (items.size == PAGE_SIZE) (offset + PAGE_SIZE).toString() else null
        return RemotePage(items, next)
    }

    override suspend fun openDownload(file: RemoteFile): RemoteDownload {
        val (publicKey, path) = YandexResourceId.decode(file.remoteId)
        val href = api.downloadHref(
            publicKey,
            path ?: throw PublicLinkException.Protocol("missing file path"),
        )
        return OkHttpRemoteDownload(api.open(href))
    }

    private companion object {
        const val PROVIDER_ID = "yandex_disk"
        const val PAGE_SIZE = 200
        val PUBLIC_PATH = Regex("^/(?:d|i)/[^/]+(?:/.*)?$")
        val YANDEX_HOST = Regex("^(?:m|www|disk(?:\\.360)?)\\.yandex\\.(?:ru|com|net|kz|by|uz|com\\.tr)$")

        fun parsePublicLink(link: String): URI? = runCatching {
            val normalized = normalizePublicLink(link)
            URI.create(normalized).takeIf { it.scheme == "https" || it.scheme == "http" }
        }.getOrNull()

        fun normalizePublicLink(link: String): String {
            val trimmed = link.trim()
            return if ("://" in trimmed) trimmed else "https://$trimmed"
        }

        fun isYandexDiskHost(host: String?): Boolean = host == "yadi.sk" || host?.matches(YANDEX_HOST) == true
    }
}

internal object YandexResourceId {

    private const val SEPARATOR = '\u001F'

    fun encode(publicKey: String, path: String?): String =
        publicKey + SEPARATOR + (path.orEmpty())

    fun decode(id: String): Pair<String, String?> {
        val index = id.indexOf(SEPARATOR)
        if (index < 0) throw PublicLinkException.Protocol("invalid remote id")
        val path = id.substring(index + 1)
        return id.substring(0, index) to path.takeIf { it.isNotEmpty() }
    }
}
