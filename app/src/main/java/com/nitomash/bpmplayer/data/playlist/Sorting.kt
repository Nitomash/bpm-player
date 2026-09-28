package com.nitomash.bpmplayer.data.playlist

object PlaylistTrackSorter {

    fun sort(rows: List<PlaylistTrackRow>, mode: PlaylistTrackSort): List<PlaylistTrackRow> = when (mode) {
        PlaylistTrackSort.CUSTOM -> rows.sortedWith(
            compareBy<PlaylistTrackRow> { it.position }.thenBy { it.track.id },
        )
        PlaylistTrackSort.BPM_ASC -> rows.sortedWith(bpmComparator(ascending = true))
        PlaylistTrackSort.BPM_DESC -> rows.sortedWith(bpmComparator(ascending = false))
        PlaylistTrackSort.DATE_ADDED -> rows.sortedWith(
            compareByDescending<PlaylistTrackRow> { it.addedToPlaylistAtMs }
                .thenBy { it.position }
                .thenBy { it.track.id },
        )
        PlaylistTrackSort.TITLE -> rows.sortedWith(
            compareBy<PlaylistTrackRow> { it.track.title.sortKey() }.thenBy { it.track.id },
        )
    }

    private fun bpmComparator(ascending: Boolean): Comparator<PlaylistTrackRow> {
        val direction = if (ascending) 1.0 else -1.0
        return compareBy<PlaylistTrackRow> { if (it.track.bpm == null) 1 else 0 }
            .thenBy { it.track.bpm?.times(direction) ?: 0.0 }
            .thenBy { it.track.title.sortKey() }
            .thenBy { it.track.id }
    }
}

object PlaylistListSorter {

    fun sort(summaries: List<PlaylistSummary>, mode: PlaylistListSort): List<PlaylistSummary> = when (mode) {
        PlaylistListSort.DATE_MODIFIED -> summaries.sortedWith(
            compareByDescending<PlaylistSummary> { it.updatedAtMs }.thenByDescending { it.id },
        )
        PlaylistListSort.TITLE -> summaries.sortedWith(
            compareBy<PlaylistSummary> { it.name.sortKey() }.thenBy { it.id },
        )
        PlaylistListSort.CUSTOM -> summaries.sortedWith(
            compareBy<PlaylistSummary> { it.sortPosition }.thenBy { it.id },
        )
    }
}

private fun String.sortKey(): String = trim().lowercase()
