package com.nitomash.bpmplayer.data.playlist

object PlaylistTrackSorter {

    fun sort(rows: List<PlaylistTrackRow>, mode: PlaylistTrackSort): List<PlaylistTrackRow> {
        if (rows.size < 2) return rows
        return when (mode) {
            PlaylistTrackSort.CUSTOM -> rows.sortedWith(
                compareBy<PlaylistTrackRow> { it.position }.thenBy { it.track.id },
            )
            PlaylistTrackSort.DATE_ADDED -> rows.sortedWith(
                compareByDescending<PlaylistTrackRow> { it.addedToPlaylistAtMs }
                    .thenBy { it.position }
                    .thenBy { it.track.id },
            )
            PlaylistTrackSort.TITLE -> rows.sortedByTitleKey(
                titleOf = { it.track.title },
                comparator = compareBy<Keyed<PlaylistTrackRow>> { it.titleKey }
                    .thenBy { it.value.track.id },
            )
            PlaylistTrackSort.BPM_ASC -> rows.sortedByTitleKey(
                titleOf = { it.track.title },
                comparator = bpmComparator(ascending = true),
            )
            PlaylistTrackSort.BPM_DESC -> rows.sortedByTitleKey(
                titleOf = { it.track.title },
                comparator = bpmComparator(ascending = false),
            )
        }
    }

    private fun bpmComparator(ascending: Boolean): Comparator<Keyed<PlaylistTrackRow>> {
        val direction = if (ascending) 1.0 else -1.0
        return compareBy<Keyed<PlaylistTrackRow>> { if (it.value.track.bpm == null) 1 else 0 }
            .thenBy { it.value.track.bpm?.times(direction) ?: 0.0 }
            .thenBy { it.titleKey }
            .thenBy { it.value.track.id }
    }
}

object PlaylistListSorter {

    fun sort(summaries: List<PlaylistSummary>, mode: PlaylistListSort): List<PlaylistSummary> {
        if (summaries.size < 2) return summaries
        return when (mode) {
            PlaylistListSort.DATE_MODIFIED -> summaries.sortedWith(
                compareByDescending<PlaylistSummary> { it.updatedAtMs }.thenByDescending { it.id },
            )
            PlaylistListSort.CUSTOM -> summaries.sortedWith(
                compareBy<PlaylistSummary> { it.sortPosition }.thenBy { it.id },
            )
            PlaylistListSort.TITLE -> summaries.sortedByTitleKey(
                titleOf = { it.name },
                comparator = compareBy<Keyed<PlaylistSummary>> { it.titleKey }
                    .thenBy { it.value.id },
            )
        }
    }
}

private class Keyed<T>(val value: T, val titleKey: String)

private fun <T> List<T>.sortedByTitleKey(
    titleOf: (T) -> String,
    comparator: Comparator<Keyed<T>>,
): List<T> = map { Keyed(it, titleOf(it).sortKey()) }
    .sortedWith(comparator)
    .map { it.value }

private fun String.sortKey(): String = trim().lowercase()
