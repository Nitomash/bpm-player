package com.nitomash.bpmplayer.ui.playlists

import com.nitomash.bpmplayer.domain.playlist.PlaylistOrder
data class GeneratorInputs(
    val minText: String = "",
    val maxText: String = "",
    val countText: String = "",
    val order: PlaylistOrder = PlaylistOrder.RANDOM,
    val generationScope: GenerationScope = GenerationScope.FOLDERS,
    val allFolders: Boolean = true,
    val selectedFolderIds: Set<Long> = emptySet(),
    val allPlaylists: Boolean = true,
    val selectedPlaylistIds: Set<Long> = emptySet(),
    val sortMenuOpen: Boolean = false,
    val advancedExpanded: Boolean = false,
    val orderExpanded: Boolean = false,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

object GeneratorInputStore {
    private val lock = Any()
    private var inputs: GeneratorInputs = GeneratorInputs()

    fun get(): GeneratorInputs = synchronized(lock) { inputs }

    fun update(transform: (GeneratorInputs) -> GeneratorInputs) {
        synchronized(lock) { inputs = transform(inputs) }
    }
}
