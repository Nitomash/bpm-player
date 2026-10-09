package com.nitomash.bpmplayer.ui.playlists

import androidx.annotation.StringRes
import com.nitomash.bpmplayer.R

enum class GenerationScope {
    FOLDERS,
    PLAYLISTS,
}

@StringRes
fun generationScopeLabelRes(scope: GenerationScope): Int = when (scope) {
    GenerationScope.FOLDERS -> R.string.playlist_source_folders
    GenerationScope.PLAYLISTS -> R.string.playlist_source_playlists
}
