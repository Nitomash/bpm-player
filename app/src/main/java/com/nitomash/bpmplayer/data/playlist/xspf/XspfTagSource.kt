package com.nitomash.bpmplayer.data.playlist.xspf

sealed interface XspfToken {
    data class Start(val name: String) : XspfToken
    data class End(val name: String) : XspfToken
    data object DocumentEnd : XspfToken
}

interface XspfTagSource {
    fun next(): XspfToken
    fun readText(): String
}