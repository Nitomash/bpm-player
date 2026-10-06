package com.nitomash.bpmplayer.data.playlist.xspf

object XspfFileName {

    const val EXTENSION = "xspf"
    private const val FALLBACK = "playlist"
    private const val MAX_BASE_LENGTH = 96
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\u0000-\u001F]+")

    fun sanitize(name: String): String {
        val cleaned = ILLEGAL.replace(name, "_")
            .trim()
            .trim('.', ' ')
            .take(MAX_BASE_LENGTH)
            .trim()
        return cleaned.ifBlank { FALLBACK }
    }

    fun unique(baseName: String, taken: Set<String>): String {
        val plain = "$baseName.$EXTENSION"
        if (plain !in taken) return plain
        var index = 1
        while ("$baseName ($index).$EXTENSION" in taken) index++
        return "$baseName ($index).$EXTENSION"
    }
}