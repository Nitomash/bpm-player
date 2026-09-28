package com.nitomash.bpmplayer.data.playlist

enum class PlaylistListSort(val value: Int) {
    CUSTOM(2),
    TITLE(1),
    DATE_MODIFIED(0),
    ;

    companion object {
        fun fromValue(value: Int): PlaylistListSort = entries.firstOrNull { it.value == value } ?: DATE_MODIFIED
    }
}
