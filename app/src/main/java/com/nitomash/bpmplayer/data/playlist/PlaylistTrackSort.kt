package com.nitomash.bpmplayer.data.playlist

enum class PlaylistTrackSort(val value: Int) {
    CUSTOM(0),
    TITLE(4),
    DATE_ADDED(3),
    BPM_ASC(1),
    BPM_DESC(2),
    ;

    companion object {
        fun fromValue(value: Int): PlaylistTrackSort = entries.firstOrNull { it.value == value } ?: CUSTOM
    }
}
