package com.nitomash.bpmplayer.ui.common

object ListReorder {

    fun moved(list: List<Long>, from: Int, to: Int): List<Long> {
        if (from == to) return list
        if (from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun <T> applyDraft(items: List<T>, draft: List<Long>, idOf: (T) -> Long): List<T> {
        if (items.size < 2) return items
        val positions = HashMap<Long, Int>(draft.size * 2)
        draft.forEachIndexed { index, id -> positions[id] = index }
        return items.sortedWith { a, b ->
            val aId = idOf(a)
            val bId = idOf(b)
            val byPosition = (positions[aId] ?: Int.MAX_VALUE).compareTo(positions[bId] ?: Int.MAX_VALUE)
            if (byPosition != 0) byPosition else aId.compareTo(bId)
        }
    }
}
