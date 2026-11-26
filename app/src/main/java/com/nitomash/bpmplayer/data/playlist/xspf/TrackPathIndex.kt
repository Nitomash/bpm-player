package com.nitomash.bpmplayer.data.playlist.xspf

import com.nitomash.bpmplayer.domain.linkimport.ImportNameKey

class TrackPathIndex private constructor(
    private val exact: Map<String, Long>,
    private val canonical: Map<String, Long>,
) {

    fun find(absolutePath: String): Long? =
        exact[absolutePath] ?: canonical[ImportNameKey.of(absolutePath)]

    companion object {
        fun of(library: Map<String, Long>): TrackPathIndex {
            val canonical = linkedMapOf<String, Long>()
            library.entries.forEach { (path, id) ->
                val key = ImportNameKey.of(path)
                if (!canonical.containsKey(key) || path == key) canonical[key] = id
            }
            return TrackPathIndex(library, canonical)
        }
    }
}
