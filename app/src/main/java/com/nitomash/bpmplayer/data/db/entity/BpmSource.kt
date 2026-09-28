package com.nitomash.bpmplayer.data.db.entity

enum class BpmSource(val value: Int) {
    MANUAL(0),
    TAP(1),
    AUTO(2),
    ;

    companion object {
        fun fromValue(value: Int): BpmSource? = entries.firstOrNull { it.value == value }
    }
}