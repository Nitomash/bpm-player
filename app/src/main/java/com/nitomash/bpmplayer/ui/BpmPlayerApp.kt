package com.nitomash.bpmplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.nitomash.bpmplayer.ui.player.PlayerScreen
import com.nitomash.bpmplayer.ui.playlists.AddTracksScreen
import com.nitomash.bpmplayer.ui.playlists.AddTracksViewModel
import com.nitomash.bpmplayer.ui.playlists.PlaylistDetailScreen
import com.nitomash.bpmplayer.ui.playlists.PlaylistDetailViewModel
import com.nitomash.bpmplayer.ui.playlists.PlaylistsScreen
import com.nitomash.bpmplayer.ui.settings.SETTINGS_ROUTE
import com.nitomash.bpmplayer.ui.settings.SettingsScreen
import com.nitomash.bpmplayer.ui.settings.SettingsViewModel

@Composable
fun BpmPlayerApp() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.settings.collectAsStateWithLifecycle()
    val settings = settingsState
    if (settings == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val hideTopBar = listOf(
        MassBpmViewModel.ROUTE,
        PlaylistDetailViewModel.ROUTE,
        AddTracksViewModel.ROUTE,
        ABOUT_ROUTE,
        SETTINGS_ROUTE,
    ).any { route -> currentDestination?.route?.startsWith(route) == true }

    Scaffold(
        bottomBar = {
            if (!hideTopBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
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
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.LIBRARY.route) {
                LibraryScreen(
                    onOpenMassBpm = { scope, folderIds ->
                        navController.navigate(MassBpmViewModel.route(scope, folderIds))
                    },
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                )
            }
            composable(TopLevelDestination.PLAYLISTS.route) {
                PlaylistsScreen(
                    onOpenPlaylist = { playlistId ->
                        navController.navigate(PlaylistDetailViewModel.route(playlistId))
                    },
                    onPlaylistStarted = {
                        navController.navigate(TopLevelDestination.PLAYER.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(TopLevelDestination.PLAYER.route) {
                PlayerScreen(
                    onOpenPlaylist = { playlistId ->
                        navController.navigate(PlaylistDetailViewModel.route(playlistId))
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
            ) {
                MassBpmScreen(
                    onDiscard = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = "${PlaylistDetailViewModel.ROUTE}/{${PlaylistDetailViewModel.ARG_PLAYLIST_ID}}",
                arguments = listOf(
                    navArgument(PlaylistDetailViewModel.ARG_PLAYLIST_ID) {
                        type = NavType.LongType
                    },
                ),
            ) {
                PlaylistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAddTracks = { playlistId ->
                        navController.navigate(AddTracksViewModel.route(playlistId))
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
            ) {
                AddTracksScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ABOUT_ROUTE) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    settings = settings,
                    onDetectorRangeChange = settingsViewModel::setDetectorRange,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}