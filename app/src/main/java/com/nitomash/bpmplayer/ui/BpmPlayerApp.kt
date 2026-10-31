package com.nitomash.bpmplayer.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val nestedOffsetPx = remember(layoutDirection, density) {
        with(density) { NavigationMotion.nestedOffset.roundToPx() } *
            if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    }

    val selectTopLevel: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.LIBRARY.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = {
                if (isBetweenTopLevelDestinations()) {
                    fadeIn(NavigationMotion.fadeInSpec)
                } else {
                    slideInHorizontally(NavigationMotion.slideSpec) { nestedOffsetPx } +
                        fadeIn(NavigationMotion.fadeInSpec)
                }
            },
            exitTransition = {
                fadeOut(NavigationMotion.fadeOutSpec)
            },
            popEnterTransition = {
                fadeIn(NavigationMotion.fadeInSpec)
            },
            popExitTransition = {
                if (isBetweenTopLevelDestinations()) {
                    fadeOut(NavigationMotion.fadeOutSpec)
                } else {
                    slideOutHorizontally(NavigationMotion.slideSpec) { nestedOffsetPx } +
                        fadeOut(NavigationMotion.fadeOutSpec)
                }
            },
        ) {
            composable(TopLevelDestination.LIBRARY.route) { entry ->
                TopLevelScreen(
                    destination = TopLevelDestination.LIBRARY,
                    onSelect = selectTopLevel,
                    miniPlayer = { MiniPlayer(playerViewModel) },
                ) {
                    LibraryScreen(
                        onOpenMassBpm = { scope, folderIds ->
                            if (entry.isForeground()) navController.navigate(MassBpmViewModel.route(scope, folderIds)) { launchSingleTop = true }
                        },
                        onOpenSettings = { if (entry.isForeground()) navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                        onOpenAbout = { if (entry.isForeground()) navController.navigate(ABOUT_ROUTE) { launchSingleTop = true } },
                    )
                }
            }
            composable(TopLevelDestination.PLAYLISTS.route) { entry ->
                TopLevelScreen(
                    destination = TopLevelDestination.PLAYLISTS,
                    onSelect = selectTopLevel,
                    miniPlayer = { MiniPlayer(playerViewModel) },
                ) {
                    PlaylistsScreen(
                        onOpenPlaylist = { playlistId ->
                            if (entry.isForeground()) navController.navigate(PlaylistDetailViewModel.route(playlistId)) { launchSingleTop = true }
                        },
                        onPlaylistStarted = {
                            if (entry.isForeground()) {
                                navController.navigate(TopLevelDestination.PLAYER.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = false
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
                TopLevelScreen(
                    destination = TopLevelDestination.PLAYER,
                    onSelect = selectTopLevel,
                ) {
                    PlayerScreen(
                        onOpenPlaylist = { playlistId ->
                            if (entry.isForeground()) navController.navigate(PlaylistDetailViewModel.route(playlistId)) { launchSingleTop = true }
                        },
                        onOpenGenerated = {
                            if (entry.isForeground()) navController.navigate(GeneratedPlaylistViewModel.ROUTE) { launchSingleTop = true }
                        },
                    )
                }
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
                    onDiscard = { if (entry.isForeground() && entry === navController.currentBackStackEntry && entry.savedStateHandle.get<Boolean>("popped") != true) { entry.savedStateHandle["popped"] = true; navController.popBackStack() } },
                    onSaved = { if (entry.isForeground() && entry === navController.currentBackStackEntry && entry.savedStateHandle.get<Boolean>("popped") != true) { entry.savedStateHandle["popped"] = true; navController.popBackStack() } },
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
                    onBack = { if (entry.isForeground() && entry === navController.currentBackStackEntry && entry.savedStateHandle.get<Boolean>("popped") != true) { entry.savedStateHandle["popped"] = true; navController.popBackStack() } },
                    onAddTracks = { playlistId ->
                        if (entry.isForeground()) navController.navigate(AddTracksViewModel.route(playlistId)) { launchSingleTop = true }
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
                    onBack = { if (entry.isForeground() && entry === navController.currentBackStackEntry && entry.savedStateHandle.get<Boolean>("popped") != true) { entry.savedStateHandle["popped"] = true; navController.popBackStack() } },
                )
            }
            composable(GeneratedPlaylistViewModel.ROUTE) { entry ->
                GeneratedPlaylistScreen(
                    playerViewModel = playerViewModel,
                    onBack = { if (entry.isForeground() && entry === navController.currentBackStackEntry && entry.savedStateHandle.get<Boolean>("popped") != true) { entry.savedStateHandle["popped"] = true; navController.popBackStack() } },
                    onSaved = { playlistId ->
                        if (entry.isForeground() && entry === navController.currentBackStackEntry) {
                            navController.navigate(PlaylistDetailViewModel.route(playlistId)) {
                                popUpTo(GeneratedPlaylistViewModel.ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable(ABOUT_ROUTE) { entry ->
                AboutScreen(
                    onBack = { if (entry.isForeground() && entry === navController.currentBackStackEntry && entry.savedStateHandle.get<Boolean>("popped") != true) { entry.savedStateHandle["popped"] = true; navController.popBackStack() } },
                )
            }
            composable(SETTINGS_ROUTE) { entry ->
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    onDetectorRangeChange = settingsViewModel::setDetectorRange,
                    onBack = { if (entry.isForeground() && entry === navController.currentBackStackEntry && entry.savedStateHandle.get<Boolean>("popped") != true) { entry.savedStateHandle["popped"] = true; navController.popBackStack() } },
                )
            }
        }
    }
}

@Composable
private fun TopLevelScreen(
    destination: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    miniPlayer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
        miniPlayer?.invoke()
        NavigationBar {
            TopLevelDestination.entries.forEach { item ->
                NavigationBarItem(
                    selected = item == destination,
                    onClick = { onSelect(item) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(item.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(playerViewModel: PlayerViewModel) {
    val isPlaylistActive by playerViewModel.isPlaylistActive.collectAsStateWithLifecycle()

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

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isBetweenTopLevelDestinations(): Boolean =
    initialState.isTopLevelDestination() && targetState.isTopLevelDestination()

private fun NavBackStackEntry.isTopLevelDestination(): Boolean =
    TopLevelDestination.entries.any { it.route == destination.route }

private fun NavBackStackEntry.isForeground(): Boolean =
    lifecycle.currentState == Lifecycle.State.RESUMED
