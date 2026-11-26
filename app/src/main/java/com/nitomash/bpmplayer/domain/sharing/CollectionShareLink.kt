package com.nitomash.bpmplayer.domain.sharing

import com.nitomash.bpmplayer.domain.linkimport.PublicLinkClassifier
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkSource

object CollectionShareLink {

    const val SCHEME = "https"
    const val HOST = "bpmplayer.nitomash.com"
    const val PATH = "/c/v1"

    const val KEY_YANDEX = "ya"
    const val KEY_GOOGLE = "gd"

    private const val MAX_URL_LENGTH = 8_192
    private const val MAX_PARAMETER_LENGTH = 2_048
    private const val MAX_VALUE_LENGTH = 1_024
    private const val MAX_LINKS = 32
    private const val BASE64URL_PATTERN = "[A-Za-z0-9_-]+"

    private val knownKeys = setOf(KEY_YANDEX, KEY_GOOGLE)
    private val base64Url = Regex("^$BASE64URL_PATTERN$")

    fun build(links: List<String>): String {
        val normalized = links.map { it.trim() }
        require(normalized.isNotEmpty()) { "No links to share" }
        require(normalized.size <= MAX_LINKS) { "Too many links: ${normalized.size}" }

        val encoded = normalized.map { link ->
            require(link.isNotBlank() && "\u0000" !in link) { "Invalid link" }
            val value = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(link.toByteArray(Charsets.UTF_8))
            require(value.length <= MAX_VALUE_LENGTH) { "Link is too long" }
            keyOf(link) to value
        }
        val query = encoded.joinToString("&") { (key, value) -> "$key=$value" }
        val result = "$SCHEME://$HOST$PATH?$query"
        require(result.length <= MAX_URL_LENGTH) { "Share URL is too long" }
        return result
    }

    fun parse(url: String): List<String> {
        require(url.length <= MAX_URL_LENGTH) { "Share URL is too long" }
        val uri = runCatching { java.net.URI.create(url.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid share URL") }
        require(uri.scheme?.lowercase() == SCHEME) { "Only HTTPS is supported" }
        require(uri.host?.lowercase() == HOST) { "Unexpected host" }
        require(uri.rawPath == PATH) { "Unexpected path" }
        require(!uri.rawQuery.isNullOrBlank()) { "Missing query" }
        require(uri.rawFragment.isNullOrEmpty()) { "Fragments are not allowed" }
        require(uri.userInfo == null) { "User info is not allowed" }
        require(uri.port == -1) { "Explicit port is not allowed" }

        val parameters = uri.rawQuery!!.split('&')
        require(parameters.isNotEmpty() && parameters.size <= MAX_LINKS) { "Unexpected number of parameters" }

        val decoded = parameters.map { parameter ->
            require(parameter.length <= MAX_PARAMETER_LENGTH) { "Parameter is too long" }
            val separator = parameter.indexOf('=')
            require(separator > 0) { "Malformed parameter" }
            val key = parameter.substring(0, separator).lowercase()
            val value = parameter.substring(separator + 1)
            require(key in knownKeys) { "Unknown parameter: $key" }
            require(value.length <= MAX_VALUE_LENGTH && value.matches(base64Url)) { "Invalid encoded link" }
            val bytes = runCatching { java.util.Base64.getUrlDecoder().decode(value) }
                .getOrElse { throw IllegalArgumentException("Invalid base64url value") }
            val link = String(bytes, Charsets.UTF_8).trim()
            require(link.isNotBlank() && "\u0000" !in link && !link.contains('\n') && !link.contains('\r')) {
                "Invalid decoded link"
            }
            key to link
        }
        require(decoded.isNotEmpty()) { "No links in share URL" }
        return decoded.map { it.second }
    }

    private fun keyOf(link: String): String = when (PublicLinkClassifier.detect(link)) {
        PublicLinkSource.YANDEX_DISK -> KEY_YANDEX
        PublicLinkSource.GOOGLE_DRIVE -> KEY_GOOGLE
        PublicLinkSource.UNKNOWN -> throw IllegalArgumentException("Unsupported provider link")
    }
}
