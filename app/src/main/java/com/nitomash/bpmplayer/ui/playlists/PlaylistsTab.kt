package com.nitomash.bpmplayer.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nitomash.bpmplayer.ui.NavigationMotion
import com.nitomash.bpmplayer.ui.rememberNestedOffsetPx

private const val LIST_ROUTE = "playlists_list"

@Composable
fun PlaylistsTab(
    pendingPlaylistId: Long?,
    pendingGeneratedPlaylist: Boolean,
    onPendingPlaylistConsumed: () -> Unit,
    onPlaylistStarted: () -> Unit,
    onBackToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isAtList = backStackEntry?.destination?.route == LIST_ROUTE
    val nestedOffsetPx = rememberNestedOffsetPx()
    var openedFromPlayer by rememberSaveable { mutableStateOf(false) }

    fun popNested() {
        val entry = navController.currentBackStackEntry ?: return
        val previousIsList = navController.previousBackStackEntry?.destination?.route == LIST_ROUTE
        if (entry.destination.route == LIST_ROUTE) {
            if (openedFromPlayer) {
                openedFromPlayer = false
                onBackToPlayer()
            }
            return
        }
        if (openedFromPlayer && previousIsList) {
            navController.popBackStack(LIST_ROUTE, inclusive = false)
            openedFromPlayer = false
            onBackToPlayer()
            return
        }
        navController.popBackStack()
    }

    LaunchedEffect(pendingPlaylistId) {
        val playlistId = pendingPlaylistId ?: return@LaunchedEffect
        openedFromPlayer = true
        navController.navigate(PlaylistDetailViewModel.route(playlistId)) {
            popUpTo(LIST_ROUTE)
            launchSingleTop = true
        }
        onPendingPlaylistConsumed()
    }

    LaunchedEffect(pendingGeneratedPlaylist) {
        if (!pendingGeneratedPlaylist) return@LaunchedEffect
        openedFromPlayer = true
        navController.navigate(GeneratedPlaylistViewModel.ROUTE) {
            popUpTo(LIST_ROUTE)
            launchSingleTop = true
        }
        onPendingPlaylistConsumed()
    }

    BackHandler(enabled = !isAtList || openedFromPlayer) {
        popNested()
    }

    NavHost(
        navController = navController,
        startDestination = LIST_ROUTE,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(NavigationMotion.slideSpec) { nestedOffsetPx } +
                fadeIn(NavigationMotion.fadeInSpec)
        },
        exitTransition = {
            fadeOut(NavigationMotion.fadeOutSpec)
        },
        popEnterTransition = {
            fadeIn(NavigationMotion.fadeInSpec)
        },
        popExitTransition = {
            slideOutHorizontally(NavigationMotion.slideSpec) { nestedOffsetPx } +
                fadeOut(NavigationMotion.fadeOutSpec)
        },
    ) {
        composable(LIST_ROUTE) {
            PlaylistsScreen(
                onOpenPlaylist = { playlistId ->
                    openedFromPlayer = false
                    navController.navigate(PlaylistDetailViewModel.route(playlistId)) { launchSingleTop = true }
                },
                onPlaylistStarted = onPlaylistStarted,
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
                onBack = { popNested() },
                onAddTracks = { playlistId ->
                    navController.navigate(AddTracksViewModel.route(playlistId)) { launchSingleTop = true }
                },
            )
        }
        composable(GeneratedPlaylistViewModel.ROUTE) {
            GeneratedPlaylistScreen(
                onBack = { popNested() },
                onSaved = { playlistId ->
                    navController.navigate(PlaylistDetailViewModel.route(playlistId)) {
                        popUpTo(GeneratedPlaylistViewModel.ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
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
            AddTracksScreen(onBack = { popNested() })
        }
    }
}
