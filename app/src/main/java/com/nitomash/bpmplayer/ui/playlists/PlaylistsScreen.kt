package com.nitomash.bpmplayer.ui.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.data.playlist.export.BundleExportError
import com.nitomash.bpmplayer.data.playlist.export.BundleExportResult
import com.nitomash.bpmplayer.data.playlist.xspf.XspfImportError
import com.nitomash.bpmplayer.ui.common.ConfirmDialog
import com.nitomash.bpmplayer.ui.common.ReorderHandle
import com.nitomash.bpmplayer.ui.common.ReorderRowShape
import com.nitomash.bpmplayer.ui.common.ReorderableList
import com.nitomash.bpmplayer.ui.common.SelectAll
import com.nitomash.bpmplayer.ui.common.reorderRowColor
import com.nitomash.bpmplayer.ui.common.reorderRowElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistListViewModel = hiltViewModel(),
    onOpenPlaylist: (Long) -> Unit = {},
    onPlaylistStarted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val listSort by viewModel.listSort.collectAsStateWithLifecycle()
    val reorderIds by viewModel.reorderIds.collectAsStateWithLifecycle()
    val reorderSession by viewModel.reorderSession.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleteSelectedTarget by remember { mutableStateOf(false) }
    var contextMenuFor by remember { mutableStateOf<Long?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val isReorderMode = reorderIds != null
    val reorderState = reorderIds
    val isHeaderCompact = isReorderMode || isSelectionMode
    val selectAllLabel = stringResource(R.string.playlist_select_all)

    var exportIds by remember { mutableStateOf<List<Long>?>(null) }
    val exportFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val ids = exportIds
        exportIds = null
        if (uri != null && ids != null) {
            viewModel.exportTo(uri, ids)
        }
    }

    var bundleExportId by remember { mutableStateOf<Long?>(null) }
    val bundleExportFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val id = bundleExportId
        bundleExportId = null
        if (uri != null && id != null) {
            viewModel.exportWithFiles(uri, id)
        }
    }

    fun requestBundleExport(id: Long) {
        bundleExportId = id
        bundleExportFolderPicker.launch(null)
    }

    val bundleProgress by viewModel.bundleExportProgress.collectAsStateWithLifecycle()

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importPlaylist(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { result ->
            val message = when {
                result.files.isEmpty() -> context.getString(R.string.playlist_export_none)
                result.failedCount > 0 -> context.getString(R.string.playlist_export_partial, result.succeededCount, result.failedCount)
                else -> context.getString(R.string.playlist_export_done, result.succeededCount)
            }
            snackbarHostState.showSnackbar(message)
            if (result.absoluteTrackCount > 0) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.playlist_export_absolute_paths, result.absoluteTrackCount),
                )
            }
        }
    }

    fun requestExport(ids: List<Long>) {
        if (ids.isEmpty()) return
        exportIds = ids
        exportFolderPicker.launch(null)
    }

    LaunchedEffect(Unit) {
        viewModel.bundleExportEvents.collect { result ->
            val message = when (result.error) {
                BundleExportError.EMPTY_PLAYLIST -> context.getString(R.string.bundle_export_error_empty)
                BundleExportError.FOLDER_CREATE_FAILED -> context.getString(R.string.bundle_export_error_folder)
                BundleExportError.COPY_FAILED -> context.getString(
                    R.string.bundle_export_error_copy,
                    result.failedFileName.orEmpty(),
                )
                BundleExportError.WRITE_FAILED -> context.getString(
                    R.string.bundle_export_error_write,
                    result.failedFileName.orEmpty(),
                )
                BundleExportError.CANCELLED -> context.getString(R.string.bundle_export_cancelled)
                null -> context.getString(R.string.bundle_export_done, result.playlistName, result.folderName.orEmpty())
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importEvents.collect { result ->
            val message = when (result.error) {
                XspfImportError.READ_FAILED -> context.getString(R.string.playlist_import_error_read)
                XspfImportError.NOT_XSPF -> context.getString(R.string.playlist_import_error_format)
                XspfImportError.EMPTY_PLAYLIST -> context.getString(R.string.playlist_import_error_empty)
                XspfImportError.BASE_UNRESOLVED -> context.getString(R.string.playlist_import_error_base)
                XspfImportError.NO_MATCHES -> context.getString(R.string.playlist_import_error_no_matches)
                null -> context.getString(R.string.playlist_import_done, result.playlistName, result.matchedCount)
            }
            snackbarHostState.showSnackbar(message)
            if (result.succeeded && result.unmatchedCount > 0) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.playlist_import_unmatched, result.unmatchedCount),
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when {
                            isReorderMode -> stringResource(R.string.playlist_reorder_hint)
                            isSelectionMode -> stringResource(R.string.playlist_selected_count, selectedIds.size)
                            else -> stringResource(R.string.nav_playlists)
                        },
                        style = if (isHeaderCompact) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = if (isHeaderCompact) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            Color.Unspecified
                        },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    if (isReorderMode) {
                        IconButton(onClick = viewModel::exitReorder) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.editor_cancel),
                            )
                        }
                    } else if (isSelectionMode) {
                        IconButton(onClick = viewModel::exitSelectionMode) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.editor_cancel),
                            )
                        }
                    }
                },
                actions = {
                    if (isReorderMode) {
                        IconButton(onClick = viewModel::commitReorder) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.playlist_reorder_done),
                            )
                        }
                    } else if (isSelectionMode) {
                        TriStateCheckbox(
                            state = SelectAll.state(playlists.map { it.id }, selectedIds),
                            onClick = { viewModel.toggleSelectAll() },
                            modifier = Modifier.semantics {
                                contentDescription = selectAllLabel
                            },
                        )
                        IconButton(
                            onClick = {
                                requestExport(playlists.filter { it.id in selectedIds }.map { it.id })
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = stringResource(R.string.playlist_menu_export),
                            )
                        }
                        IconButton(onClick = { deleteSelectedTarget = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete_confirm),
                            )
                        }
                    } else {
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
                                PlaylistListSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(playlistListSortLabelRes(option))) },
                                        onClick = {
                                            sortMenuOpen = false
                                            viewModel.setListSort(option)
                                        },
                                        leadingIcon = {
                                            if (option == listSort) {
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
                        IconButton(onClick = { viewModel.create() }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.playlist_create),
                            )
                        }
                        IconButton(onClick = { importPicker.launch(arrayOf("application/xspf+xml", "application/xml", "text/xml")) }) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = stringResource(R.string.playlist_import),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            playlists.isEmpty() -> {
                Text(
                    text = stringResource(R.string.playlist_empty_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp),
                )
            }

            reorderState != null -> {
                key(reorderSession) {
                    ReorderableList(
                        items = playlists,
                        key = { it.id },
                        onMove = viewModel::onReorderMove,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        itemContent = { summary, rowModifier, handleModifier, isDragging ->
                            PlaylistReorderRow(
                                summary = summary,
                                isDragging = isDragging,
                                modifier = rowModifier,
                                dragHandleModifier = handleModifier,
                            )
                        },
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items = playlists, key = { it.id }) { summary ->
                        Box {
                            PlaylistRow(
                                summary = summary,
                                onClick = { onOpenPlaylist(summary.id) },
                                onLongClick = { contextMenuFor = summary.id },
                                onPlay = { viewModel.play(summary.id, onPlaylistStarted) },
                                onDelete = { deleteTarget = summary },
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedIds.contains(summary.id),
                                onToggleSelected = { viewModel.toggleSelection(summary.id) },
                            )
                            DropdownMenu(
                                expanded = contextMenuFor == summary.id,
                                onDismissRequest = { contextMenuFor = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlist_menu_export)) },
                                    onClick = {
                                        contextMenuFor = null
                                        requestExport(listOf(summary.id))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlist_menu_export_files)) },
                                    onClick = {
                                        contextMenuFor = null
                                        requestBundleExport(summary.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlist_menu_select)) },
                                    onClick = {
                                        contextMenuFor = null
                                        viewModel.enterSelectionMode(summary.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlist_menu_reorder)) },
                                    onClick = {
                                        contextMenuFor = null
                                        viewModel.enterReorder()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { summary ->
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_confirm_title),
            text = stringResource(R.string.playlist_delete_confirm_message, summary.name),
            confirmLabel = stringResource(R.string.action_delete_confirm),
            onConfirm = {
                viewModel.delete(summary.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (deleteSelectedTarget) {
        val count = selectedIds.size
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_confirm_title),
            text = context.getString(R.string.playlist_delete_selected_confirm, count),
            confirmLabel = stringResource(R.string.action_delete_confirm),
            onConfirm = {
                viewModel.deleteSelected()
                deleteSelectedTarget = false
            },
            onDismiss = { deleteSelectedTarget = false },
        )
    }

    bundleProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.bundle_export_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            R.string.bundle_export_progress,
                            progress.copied,
                            progress.total,
                        ),
                    )
                    if (progress.currentFileName.isNotEmpty()) {
                        Text(
                            text = progress.currentFileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (progress.total == 0) 0f else progress.copied.toFloat() / progress.total
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelBundleExport) {
                    Text(stringResource(R.string.editor_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    summary: PlaylistSummary,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelected: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {
                if (isSelectionMode) {
                    onToggleSelected()
                } else {
                    onClick()
                }
            }, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelected() },
            )
        }
        PlaylistLabels(summary, Modifier.weight(1f))
        if (!isSelectionMode) {
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.playlist_play),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.playlist_delete_button),
                )
            }
        }
    }
}

@Composable
private fun PlaylistReorderRow(
    summary: PlaylistSummary,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .shadow(reorderRowElevation(isDragging), ReorderRowShape)
            .fillMaxWidth()
            .clip(ReorderRowShape)
            .background(reorderRowColor(isDragging))
            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReorderHandle(dragHandleModifier)
        PlaylistLabels(summary, Modifier.weight(1f))
    }
}

@Composable
private fun PlaylistLabels(summary: PlaylistSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = summary.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Text(
            text = pluralStringResource(R.plurals.track_count, summary.trackCount, summary.trackCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
