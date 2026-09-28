package com.nitomash.bpmplayer.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.nitomash.bpmplayer.R

enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LIBRARY("library", R.string.nav_library, Icons.Filled.Home),
    PLAYLISTS("playlists", R.string.nav_playlists, Icons.AutoMirrored.Filled.List),
    PLAYER("player", R.string.nav_player, Icons.Filled.PlayCircleOutline),
}