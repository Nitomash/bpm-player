package com.nitomash.bpmplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nitomash.bpmplayer.ui.about.ABOUT_ROUTE
import com.nitomash.bpmplayer.ui.about.AboutScreen
import com.nitomash.bpmplayer.ui.editor.MassBpmScreen
import com.nitomash.bpmplayer.ui.editor.MassBpmViewModel
import com.nitomash.bpmplayer.ui.library.LibraryScreen
import com.nitomash.bpmplayer.ui.player.PlayerMiniBar
import com.nitomash.bpmplayer.ui.player.PlayerScreen
import com.nitomash.bpmplayer.ui.player.PlayerViewModel
import com.nitomash.bpmplayer.ui.playlists.AddTracksScreen
import com.nitomash.bpmplayer.ui.playlists.AddTracksViewModel
import com.nitomash.bpmplayer.ui.playlists.GeneratedPlaylistScreen
import com.nitomash.bpmplayer.ui.playlists.GeneratedPlaylistViewModel
import com.nitomash.bpmplayer.ui.playlists.PlaylistDetailScreen
import com.nitomash.bpmplayer.ui.playlists.PlaylistDetailViewModel
import com.nitomash.bpmplayer.ui.playlists.PlaylistsScreen
import com.nitomash.bpmplayer.ui.settings.SETTINGS_ROUTE
import com.nitomash.bpmplayer.ui.settings.SettingsScreen
import com.nitomash.bpmplayer.ui.settings.SettingsViewModel

@Composable
fun BpmPlayerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val hideTopBar = remember(currentRoute) { TOP_BAR_HIDDEN_ROUTES.any { currentRoute?.startsWith(it) == true } }
    val playerViewModel: PlayerViewModel = hiltViewModel()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!hideTopBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = backStackEntry?.destination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.LIBRARY.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(TopLevelDestination.LIBRARY.route) { entry ->
                ScreenWithMiniPlayer(playerViewModel) {
                    LibraryScreen(
                        onOpenMassBpm = { scope, folderIds ->
                            if (entry.isForeground()) navController.navigate(MassBpmViewModel.route(scope, folderIds))
                        },
                        onOpenSettings = { if (entry.isForeground()) navController.navigate(SETTINGS_ROUTE) },
                        onOpenAbout = { if (entry.isForeground()) navController.navigate(ABOUT_ROUTE) },
                    )
                }
            }
            composable(TopLevelDestination.PLAYLISTS.route) { entry ->
                ScreenWithMiniPlayer(playerViewModel) {
                    PlaylistsScreen(
                        onOpenPlaylist = { playlistId ->
                            if (entry.isForeground()) navController.navigate(PlaylistDetailViewModel.route(playlistId))
                        },
                        onPlaylistStarted = {
                            if (entry.isForeground()) {
                                navController.navigate(TopLevelDestination.PLAYER.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
            composable(TopLevelDestination.PLAYER.route) { entry ->
                PlayerScreen(
                    onOpenPlaylist = { playlistId ->
                        if (entry.isForeground()) navController.navigate(PlaylistDetailViewModel.route(playlistId))
                    },
                    onOpenGenerated = {
                        if (entry.isForeground()) navController.navigate(GeneratedPlaylistViewModel.ROUTE)
                    },
                )
            }
            composable(
                route = "${MassBpmViewModel.ROUTE}?allTracks={allTracks}&folderIds={folderIds}",
                arguments = listOf(
                    navArgument(MassBpmViewModel.ARG_ALL_TRACKS) {
                        type = NavType.BoolType
                        defaultValue = true
                    },
                    navArgument(MassBpmViewModel.ARG_FOLDER_IDS) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                MassBpmScreen(
                    onDiscard = { if (entry.isForeground()) navController.popBackStack() },
                    onSaved = { if (entry.isForeground()) navController.popBackStack() },
                )
            }
            composable(
                route = "${PlaylistDetailViewModel.ROUTE}/{${PlaylistDetailViewModel.ARG_PLAYLIST_ID}}",
                arguments = listOf(
                    navArgument(PlaylistDetailViewModel.ARG_PLAYLIST_ID) {
                        type = NavType.LongType
                    },
                ),
            ) { entry ->
                PlaylistDetailScreen(
                    playerViewModel = playerViewModel,
                    onBack = { if (entry.isForeground()) navController.popBackStack() },
                    onAddTracks = { playlistId ->
                        if (entry.isForeground()) navController.navigate(AddTracksViewModel.route(playlistId))
                    },
                )
            }
            composable(
                route = "${AddTracksViewModel.ROUTE}/{${AddTracksViewModel.ARG_PLAYLIST_ID}}",
                arguments = listOf(
                    navArgument(AddTracksViewModel.ARG_PLAYLIST_ID) {
                        type = NavType.LongType
                    },
                ),
            ) { entry ->
                AddTracksScreen(
                    onBack = { if (entry.isForeground()) navController.popBackStack() },
                )
            }
            composable(GeneratedPlaylistViewModel.ROUTE) { entry ->
                GeneratedPlaylistScreen(
                    playerViewModel = playerViewModel,
                    onBack = { if (entry.isForeground()) navController.popBackStack() },
                )
            }
            composable(ABOUT_ROUTE) { entry ->
                AboutScreen(
                    onBack = { if (entry.isForeground()) navController.popBackStack() },
                )
            }
            composable(SETTINGS_ROUTE) { entry ->
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                val value = settings ?: return@composable
                SettingsScreen(
                    settings = value,
                    onDetectorRangeChange = settingsViewModel::setDetectorRange,
                    onBack = { if (entry.isForeground()) navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun ScreenWithMiniPlayer(
    playerViewModel: PlayerViewModel,
    content: @Composable () -> Unit,
) {
    val isPlaylistActive by playerViewModel.isPlaylistActive.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
        PlayerMiniBar(
            isVisible = isPlaylistActive,
            isPlaying = playerViewModel.isPlaying,
            hasNext = playerViewModel.hasNext,
            hasPrevious = playerViewModel.hasPrevious,
            onTogglePlayback = playerViewModel::togglePlayback,
            onNext = playerViewModel::next,
            onPrevious = playerViewModel::previous,
        )
    }
}

private fun NavBackStackEntry.isForeground(): Boolean =
    lifecycle.currentState == Lifecycle.State.RESUMED

private val TOP_BAR_HIDDEN_ROUTES = listOf(
    MassBpmViewModel.ROUTE,
    PlaylistDetailViewModel.ROUTE,
    AddTracksViewModel.ROUTE,
    GeneratedPlaylistViewModel.ROUTE,
    ABOUT_ROUTE,
    SETTINGS_ROUTE,
)
