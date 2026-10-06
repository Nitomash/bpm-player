package com.nitomash.bpmplayer.data.playlist

object PlaylistNaming {

    fun unique(base: String, taken: Set<String>): String {
        val name = base.ifBlank { FALLBACK }
        if (name !in taken) return name
        var index = 1
        while ("$name ($index)" in taken) index++
        return "$name ($index)"
    }

    private const val FALLBACK = "Импортированный плейлист"
}