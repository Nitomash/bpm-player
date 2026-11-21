package com.nitomash.bpmplayer.domain.linkimport

object SupportedImportFormats {

    const val SIDECAR_EXTENSION = "bpmmeta"
    const val PLAYLIST_EXTENSION = "xspf"

    val AUDIO_EXTENSIONS: Set<String> = setOf(
        "mp3", "m4a", "flac", "wav", "ogg", "oga", "opus", "aac", "wma", "aiff", "aif", "ape",
    )

    val PLAYLIST_EXTENSIONS: Set<String> = setOf(PLAYLIST_EXTENSION)

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    fun classify(fileName: String, mimeType: String? = null): ImportFileKind? {
        if (mimeType?.startsWith("audio/", ignoreCase = true) == true) return ImportFileKind.AUDIO
        return when (extensionOf(fileName)) {
            SIDECAR_EXTENSION -> ImportFileKind.SIDECAR
            in PLAYLIST_EXTENSIONS -> ImportFileKind.PLAYLIST
            in AUDIO_EXTENSIONS -> ImportFileKind.AUDIO
            else -> null
        }
    }

    fun isAudio(fileName: String, mimeType: String? = null): Boolean =
        classify(fileName, mimeType) == ImportFileKind.AUDIO
}
