package com.nitomash.bpmplayer.data.playlist.xspf

object XspfWriter {

    const val NAMESPACE = "http://xspf.org/ns/0/"

    fun encode(playlist: XspfPlaylist): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<playlist xmlns=\"").append(NAMESPACE).append("\" version=\"1\">")
        append(element("title", playlist.name))
        append("<trackList>")
        playlist.tracks.forEach { track ->
            append("<track>")
            append(element("location", track.location))
            append(element("title", track.title))
            append("</track>")
        }
        append("</trackList>")
        append("</playlist>")
    }

    fun escape(text: String): String = buildString(text.length) {
        text.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(char)
            }
        }
    }

    private fun element(name: String, value: String): String =
        "<$name>${escape(value)}</$name>"
}