package com.nitomash.bpmplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.FolderEntity

@Composable
fun FolderMultiSelector(
    folders: List<FolderEntity>,
    allSelected: Boolean,
    selectedIds: Set<Long>,
    onAllSelected: () -> Unit,
    onToggleFolder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiSelectDropdown(
        label = stringResource(R.string.playlist_folders_label),
        allLabel = stringResource(R.string.playlist_folders_all),
        emptyLabel = stringResource(R.string.playlist_folders_empty),
        options = folders.map { MultiSelectOption(it.id, it.displayName) },
        allSelected = allSelected,
        selectedIds = selectedIds,
        onAllSelected = onAllSelected,
        onToggleOption = onToggleFolder,
        modifier = modifier,
    )
}
