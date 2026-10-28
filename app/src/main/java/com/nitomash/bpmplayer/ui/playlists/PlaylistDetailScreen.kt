package com.nitomash.bpmplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.playlist.PlaylistTrackSort
import com.nitomash.bpmplayer.ui.common.ConfirmDialog
import com.nitomash.bpmplayer.ui.common.ReorderHandle
import com.nitomash.bpmplayer.ui.common.ReorderRowShape
import com.nitomash.bpmplayer.ui.common.ReorderableList
import com.nitomash.bpmplayer.ui.common.SelectAll
import com.nitomash.bpmplayer.ui.common.TrackList
import com.nitomash.bpmplayer.ui.common.TrackRow
import com.nitomash.bpmplayer.ui.common.reorderRowColor
import com.nitomash.bpmplayer.ui.common.reorderRowElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onAddTracks: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val reorderIds by viewModel.reorderIds.collectAsStateWithLifecycle()
    val reorderSession by viewModel.reorderSession.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentTrackId.collectAsStateWithLifecycle()
    val currentPlaylistId by viewModel.currentPlaylistId.collectAsStateWithLifecycle()
    val isPreview by viewModel.isPreview.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TrackEntity?>(null) }

    val isReorderMode = mode == PlaylistDetailMode.REORDER
    val canReorder = tracks.size > 1
    val reorderState = reorderIds
    val selectAllLabel = stringResource(R.string.playlist_select_all)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when {
                            selectionMode -> stringResource(R.string.playlist_selected_count, selectedIds.size)
                            isReorderMode -> stringResource(R.string.playlist_reorder_hint)
                            else -> stringResource(R.string.playlist_detail_title)
                        },
                        style = if (isReorderMode) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = if (isReorderMode) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            Color.Unspecified
                        },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = viewModel::exitSelection) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.editor_cancel),
                            )
                        }
                    } else if (isReorderMode) {
                        IconButton(onClick = viewModel::exitReorder) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.editor_cancel),
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    when {
                        selectionMode -> {
                            TriStateCheckbox(
                                state = SelectAll.state(tracks.map { it.id }, selectedIds),
                                onClick = { viewModel.toggleSelectAll() },
                                modifier = Modifier.semantics {
                                    contentDescription = selectAllLabel
                                },
                            )
                            IconButton(
                                onClick = { showRemoveConfirm = true },
                                enabled = selectedIds.isNotEmpty(),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        R.string.playlist_remove_selected_confirm_title,
                                    ),
                                )
                            }
                        }

                        isReorderMode -> {
                            IconButton(onClick = viewModel::commitReorder) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.playlist_reorder_done),
                                )
                            }
                        }

                        else -> {
                            Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.playlist_sort_menu),
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                ) {
                                    PlaylistTrackSort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(playlistTrackSortLabelRes(option))) },
                                            onClick = {
                                                sortMenuOpen = false
                                                viewModel.setSort(option)
                                            },
                                            leadingIcon = {
                                                if (option == sortMode) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onAddTracks(viewModel.playlistId) }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.playlist_add_tracks),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val listModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        val header: @Composable (Modifier) -> Unit = { headerModifier ->
            PlaylistHeader(
                name = playlist?.name.orEmpty(),
                trackCount = tracks.size,
                onRename = { renameOpen = true },
                modifier = headerModifier,
            )
        }
        if (isReorderMode && reorderState != null) {
            Column(modifier = listModifier) {
                header(Modifier.padding(horizontal = 16.dp))
                key(reorderSession) {
                    ReorderableList(
                        items = tracks,
                        key = { it.id },
                        onMove = viewModel::onReorderMove,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                        itemContent = { track, rowModifier, handleModifier, isDragging ->
                            TrackRow(
                                title = track.title,
                                bpm = track.bpm,
                                bpmSource = track.bpmSource,
                                onClick = {},
                                modifier = rowModifier.shadow(
                                    reorderRowElevation(isDragging),
                                    ReorderRowShape,
                                ),
                                containerColor = reorderRowColor(isDragging),
                                leading = { ReorderHandle(handleModifier) },
                            )
                        },
                    )
                }
            }
        } else {
            TrackList(
                tracks = tracks,
                modifier = listModifier,
                header = { header(Modifier) },
                emptyText = stringResource(R.string.playlist_detail_empty),
                currentTrackId = if (!isPreview && currentPlaylistId == viewModel.playlistId) {
                    currentTrackId
                } else {
                    null
                },
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onToggleSelected = viewModel::toggleSelected,
                onTrackClick = { track -> viewModel.playFrom(tracks.indexOf(track)) },
                trackMenu = { track, onDismiss ->
                    TrackMenu(
                        canReorder = canReorder,
                        onRemove = {
                            deleteTarget = track
                            onDismiss()
                        },
                        onSelect = {
                            onDismiss()
                            viewModel.enterSelection(track.id)
                        },
                        onReorder = {
                            onDismiss()
                            viewModel.enterReorder()
                        },
                        onDismiss = onDismiss,
                    )
                },
            )
        }
    }

    if (renameOpen) {
        RenamePlaylistDialog(
            currentName = playlist?.name.orEmpty(),
            onConfirm = { name ->
                viewModel.rename(name)
                renameOpen = false
            },
            onDismiss = { renameOpen = false },
        )
    }

    if (showRemoveConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.playlist_remove_selected_confirm_title),
            text = pluralStringResource(
                R.plurals.playlist_remove_tracks_confirm_message,
                selectedIds.size,
                selectedIds.size,
            ),
            confirmLabel = stringResource(R.string.action_delete_confirm),
            onConfirm = {
                viewModel.removeSelected()
                showRemoveConfirm = false
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }

    deleteTarget?.let { track ->
        ConfirmDialog(
            title = stringResource(R.string.playlist_remove_track_confirm_title),
            text = stringResource(R.string.playlist_remove_track_confirm_message, track.title),
            confirmLabel = stringResource(R.string.action_delete_confirm),
            onConfirm = {
                viewModel.removeTrack(track.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun TrackMenu(
    canReorder: Boolean,
    onRemove: () -> Unit,
    onSelect: () -> Unit,
    onReorder: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.playlist_track_menu_remove)) },
            onClick = onRemove,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.playlist_track_menu_select)) },
            onClick = onSelect,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Checklist,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.playlist_reorder_start)) },
            onClick = onReorder,
            enabled = canReorder,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_rename_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.playlist_rename_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}