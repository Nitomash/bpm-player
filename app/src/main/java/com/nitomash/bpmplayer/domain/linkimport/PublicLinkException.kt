package com.nitomash.bpmplayer.domain.linkimport

sealed class PublicLinkException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object UnsupportedLink : PublicLinkException("Unsupported link")
    data object LinkNotFound : PublicLinkException("Link not found")
    data object LinkNotPublic : PublicLinkException("Resource is not public")
    data object AccessDenied : PublicLinkException("Access denied")
    data object RateLimited : PublicLinkException("Too many requests")
    data object RootMissing : PublicLinkException("Import folder is not selected")
    data class Network(val exception: Throwable) : PublicLinkException("Network error", exception)
    data class Protocol(val detail: String) : PublicLinkException("Protocol error: $detail")
    data class LimitExceeded(val detail: String) : PublicLinkException("Import limit exceeded: $detail")
}
