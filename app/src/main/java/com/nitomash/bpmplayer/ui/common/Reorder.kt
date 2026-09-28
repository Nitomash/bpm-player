package com.nitomash.bpmplayer.ui.common

object ListReorder {

    fun moved(list: List<Long>, from: Int, to: Int): List<Long> {
        if (from == to) return list
        if (from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}
