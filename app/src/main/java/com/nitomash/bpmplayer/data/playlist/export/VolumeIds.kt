package com.nitomash.bpmplayer.data.playlist.export

object VolumeIds {

    const val PRIMARY = "primary"

    data class VolumePath(val volumeId: String, val pathInside: String)

    fun parseTreeDocumentId(treeDocumentId: String): VolumePath? {
        val separator = treeDocumentId.indexOf(':')
        if (separator <= 0) return null
        val volumeId = treeDocumentId.substring(0, separator)
        if (volumeId.isBlank()) return null
        return VolumePath(volumeId, treeDocumentId.substring(separator + 1).trim('/'))
    }
}