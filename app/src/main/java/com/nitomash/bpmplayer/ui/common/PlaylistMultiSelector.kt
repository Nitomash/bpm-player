package com.nitomash.bpmplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary

@Composable
fun PlaylistMultiSelector(
    playlists: List<PlaylistSummary>,
    allSelected: Boolean,
    selectedIds: Set<Long>,
    onAllSelected: () -> Unit,
    onTogglePlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiSelectDropdown(
        label = stringResource(R.string.playlist_playlists_label),
        allLabel = stringResource(R.string.playlist_folders_all),
        emptyLabel = stringResource(R.string.playlist_playlists_empty),
        options = playlists.map { MultiSelectOption(it.id, it.name) },
        allSelected = allSelected,
        selectedIds = selectedIds,
        onAllSelected = onAllSelected,
        onToggleOption = onTogglePlaylist,
        modifier = modifier,
    )
}
