package com.nitomash.bpmplayer.domain.sharing

object CollectionShareLinks {
    private const val SEPARATOR = '.'

    fun encode(links: List<String>): String = links.joinToString(SEPARATOR.toString()) { link ->
        java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(link.trim().toByteArray(Charsets.UTF_8))
    }

    fun decode(value: String?): List<String> = value
        ?.takeIf { it.isNotBlank() }
        ?.split(SEPARATOR)
        ?.mapNotNull { part ->
            runCatching {
                String(java.util.Base64.getUrlDecoder().decode(part), Charsets.UTF_8).trim()
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        ?: emptyList()
}
