package com.nitomash.bpmplayer.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.ui.common.SearchField
import com.nitomash.bpmplayer.ui.common.TrackList
import com.nitomash.bpmplayer.ui.common.filterTracksByTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTracksScreen(
    viewModel: AddTracksViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val previewTrackId by viewModel.previewTrackId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onBack() }
    }

    val visible = remember(candidates, searchQuery) {
        filterTracksByTitle(candidates, searchQuery)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.playlist_add_tracks)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.playlist_add_action, selectedIds.size))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SearchField(
                query = searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            TrackList(
                tracks = visible,
                modifier = Modifier.fillMaxSize(),
                emptyText = stringResource(R.string.playlist_add_empty),
                selectionMode = true,
                selectedIds = selectedIds,
                onToggleSelected = viewModel::toggleSelected,
                onTrackClick = {},
                trailing = { track ->
                    val showPause = previewTrackId == track.id && isPlaying
                    IconButton(onClick = { viewModel.togglePreview(track.id) }) {
                        Icon(
                            imageVector = if (showPause) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = stringResource(
                                if (showPause) R.string.editor_pause else R.string.editor_play,
                            ),
                        )
                    }
                },
            )
        }
    }
}