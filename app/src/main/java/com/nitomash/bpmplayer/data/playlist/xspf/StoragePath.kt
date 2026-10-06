package com.nitomash.bpmplayer.data.playlist.xspf

class StoragePath private constructor(
    val volumeRoot: String,
    val segments: List<String>,
) {
    val relativePath: String
        get() = segments.joinToString("/")

    val absolutePath: String
        get() = if (segments.isEmpty()) volumeRoot else segments.joinToString("/", prefix = "$volumeRoot/")

    fun resolve(relative: String): StoragePath =
        StoragePath(volumeRoot, segments + segmentsOf(relative))

    override fun toString(): String = absolutePath

    companion object {
        fun of(volumeRoot: String, relativePath: String): StoragePath =
            StoragePath(normalizeRoot(volumeRoot), segmentsOf(relativePath))
    }
}

internal fun normalizeRoot(path: String): String {
    val collapsed = path.trim().replace('\\', '/').replace(REPEATED_SLASH, "/")
    return collapsed.trimEnd('/').ifEmpty { "/" }
}

private val REPEATED_SLASH = Regex("/+")

internal fun segmentsOf(path: String): List<String> =
    path.trim().replace('\\', '/')
        .split('/')
        .filter { it.isNotEmpty() && it != "." && it != ".." }