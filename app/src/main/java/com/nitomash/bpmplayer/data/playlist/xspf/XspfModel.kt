package com.nitomash.bpmplayer.data.playlist.xspf

data class XspfPlaylist(
    val name: String,
    val tracks: List<XspfTrack>,
)

data class XspfTrack(
    val location: String,
    val title: String,
)