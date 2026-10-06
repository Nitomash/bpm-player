package com.nitomash.bpmplayer.data.playlist.xspf

object XspfReader {

    private const val ROOT = "playlist"
    private const val TITLE = "title"
    private const val TRACK_LIST = "trackList"
    private const val TRACK = "track"
    private const val LOCATION = "location"

    fun read(source: XspfTagSource): XspfPlaylist? {
        var sawRoot = false
        var playlistName = ""
        val tracks = mutableListOf<XspfTrack>()
        var inTrackList = false
        var location: String? = null
        var trackTitle = ""

        var token = source.next()
        while (token != XspfToken.DocumentEnd) {
            when (token) {
                is XspfToken.Start -> when (token.name) {
                    ROOT -> sawRoot = true
                    TRACK_LIST -> inTrackList = true
                    TRACK -> if (inTrackList) {
                        location = null
                        trackTitle = ""
                    }
                    LOCATION -> if (inTrackList) location = source.readText()
                    TITLE -> {
                        val text = source.readText()
                        if (inTrackList) trackTitle = text else playlistName = text
                    }
                }

                is XspfToken.End -> when (token.name) {
                    TRACK -> {
                        val value = location
                        if (value != null) {
                            tracks += XspfTrack(location = value, title = trackTitle.ifBlank { titleOf(value) })
                        }
                        location = null
                    }
                    TRACK_LIST -> inTrackList = false
                }

                XspfToken.DocumentEnd -> Unit
            }
            token = source.next()
        }

        if (!sawRoot) return null
        return XspfPlaylist(name = playlistName, tracks = tracks)
    }

    fun titleOf(location: String): String =
        location.substringAfterLast('/').substringBeforeLast('.').substringAfterLast('\\')
}