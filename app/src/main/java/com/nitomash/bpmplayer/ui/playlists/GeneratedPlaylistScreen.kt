package com.nitomash.bpmplayer.ui.playlists

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.ui.common.TrackList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratedPlaylistScreen(
    viewModel: GeneratedPlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSaved: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
    val currentPlaylistId by viewModel.currentPlaylistId.collectAsStateWithLifecycle()
    val isPreview by viewModel.isPreview.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.savedEvents.collect(onSaved)
    }

    val tracks = playlist?.tracks.orEmpty()
    val playlistId = playlist?.savedPlaylistId

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.playlist_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (playlist != null && playlistId == null) {
                        IconButton(onClick = viewModel::save) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = stringResource(R.string.editor_save),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        TrackList(
            tracks = tracks,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            header = {
                PlaylistHeader(
                    name = playlist?.name.orEmpty(),
                    trackCount = tracks.size,
                )
            },
            emptyText = stringResource(R.string.playlist_empty),
            currentTrackId = if (!isPreview && currentPlaylistId == playlistId) currentTrackId else null,
            onToggleSelected = {},
            onTrackClick = { track -> viewModel.playFrom(tracks.indexOf(track)) },
        )
    }
}
