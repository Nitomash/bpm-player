package com.nitomash.bpmplayer.domain.linkimport

import java.net.URI

enum class PublicLinkSource {
    YANDEX_DISK,
    GOOGLE_DRIVE,
    UNKNOWN,
}

object PublicLinkClassifier {

    private val YANDEX_HOST = Regex("^(?:(?:m|www)\\.)?disk(?:\\.360)?\\.yandex\\.(?:ru|com|net|kz|by|uz|com\\.tr)$")
    private val YANDEX_PUBLIC_PATH = Regex("^/(?:d|i)/[^/]+(?:/.*)?$")
    private val GOOGLE_HOSTS = setOf("drive.google.com", "docs.google.com")

    fun normalize(link: String): String {
        val trimmed = link.trim()
        return if ("://" in trimmed) trimmed else "https://$trimmed"
    }

    fun detect(link: String): PublicLinkSource {
        val uri = runCatching { URI.create(normalize(link)) }.getOrNull() ?: return PublicLinkSource.UNKNOWN
        if (uri.scheme?.lowercase() !in setOf("https", "http")) return PublicLinkSource.UNKNOWN
        val host = uri.host?.lowercase() ?: return PublicLinkSource.UNKNOWN
        return when {
            host == "yadi.sk" || host.matches(YANDEX_HOST) ->
                if (uri.path.orEmpty().matches(YANDEX_PUBLIC_PATH)) PublicLinkSource.YANDEX_DISK else PublicLinkSource.UNKNOWN

            host in GOOGLE_HOSTS -> PublicLinkSource.GOOGLE_DRIVE
            else -> PublicLinkSource.UNKNOWN
        }
    }
}
