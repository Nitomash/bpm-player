package com.nitomash.bpmplayer.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.TrackEntity
import com.nitomash.bpmplayer.data.playlist.PlaylistListSort
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary
import com.nitomash.bpmplayer.domain.playlist.PlaylistOrder
import com.nitomash.bpmplayer.ui.common.BpmLog
import com.nitomash.bpmplayer.ui.common.ConfirmDialog
import com.nitomash.bpmplayer.ui.common.FolderMultiSelector
import com.nitomash.bpmplayer.ui.common.ReorderHandle
import com.nitomash.bpmplayer.ui.common.ReorderHandleWidth
import com.nitomash.bpmplayer.ui.common.ReorderRowShape
import com.nitomash.bpmplayer.ui.common.ReorderableList
import com.nitomash.bpmplayer.ui.common.reorderRowColor
import com.nitomash.bpmplayer.ui.common.reorderRowElevation
import com.nitomash.bpmplayer.ui.editor.formatBpm
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: GeneratorViewModel = hiltViewModel(),
    playlistListViewModel: PlaylistListViewModel = hiltViewModel(),
    onOpenPlaylist: (Long) -> Unit = {},
    onPlaylistStarted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val generated by viewModel.generated.collectAsStateWithLifecycle()
    val playlists by playlistListViewModel.playlists.collectAsStateWithLifecycle()
    val listSort by playlistListViewModel.listSort.collectAsStateWithLifecycle()
    val reorderIds by playlistListViewModel.reorderIds.collectAsStateWithLifecycle()
    val reorderSession by playlistListViewModel.reorderSession.collectAsStateWithLifecycle()

    var minText by rememberSaveable { mutableStateOf("") }
    var maxText by rememberSaveable { mutableStateOf("") }
    var countText by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(PlaylistOrder.RANDOM) }
    var allFolders by rememberSaveable { mutableStateOf(true) }
    var selectedFolderIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    val folders by viewModel.folders.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }

    val isReorderMode = reorderIds != null
    val reorderState = reorderIds

    LaunchedEffect(Unit) {
        viewModel.savedToPlaylist.collect {
            snackbarHostState.showSnackbar(context.getString(R.string.playlist_save_result))
        }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue != DrawerValue.Open) {
            playlistListViewModel.exitReorder()
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (isReorderMode) 0.dp else 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isReorderMode) {
                        IconButton(onClick = playlistListViewModel::exitReorder) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.editor_cancel),
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            if (isReorderMode) {
                                R.string.playlist_reorder_hint
                            } else {
                                R.string.nav_playlists
                            },
                        ),
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
                        modifier = Modifier.weight(1f),
                    )
                    if (isReorderMode) {
                        IconButton(onClick = playlistListViewModel::commitReorder) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.playlist_reorder_done),
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
                                            playlistListViewModel.setListSort(option)
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
                        IconButton(onClick = { playlistListViewModel.create() }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.playlist_create),
                            )
                        }
                    }
                }
                HorizontalDivider()
                when {
                    playlists.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.playlist_empty_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }

                    reorderState != null -> {
                        key(reorderSession) {
                            ReorderableList(
                                items = playlists,
                                key = { it.id },
                                onMove = playlistListViewModel::onReorderMove,
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
                        LazyColumn {
                            items(items = playlists, key = { it.id }) { summary ->
                                PlaylistDrawerRow(
                                    summary = summary,
                                    onClick = {
                                        closeDrawer()
                                        onOpenPlaylist(summary.id)
                                    },
                                    onLongClick = playlistListViewModel::enterReorder,
                                    onPlay = {
                                        closeDrawer()
                                        playlistListViewModel.play(summary.id, onPlaylistStarted)
                                    },
                                    onDelete = { deleteTarget = summary },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.playlist_generator_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.playlist_drawer_open),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text(stringResource(R.string.playlist_min_bpm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text(stringResource(R.string.playlist_max_bpm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it },
                    label = { Text(stringResource(R.string.playlist_count_label)) },
                    supportingText = { Text(stringResource(R.string.playlist_count_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                var orderExpanded by rememberSaveable { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = orderExpanded,
                    onExpandedChange = { orderExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = stringResource(playlistOrderLabelRes(order)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.playlist_order_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = orderExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = orderExpanded,
                        onDismissRequest = { orderExpanded = false },
                    ) {
                        PlaylistOrder.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(playlistOrderLabelRes(option))) },
                                onClick = {
                                    order = option
                                    orderExpanded = false
                                },
                            )
                        }
                    }
                }

                FolderMultiSelector(
                    folders = folders,
                    allSelected = allFolders,
                    selectedIds = selectedFolderIds,
                    onAllSelected = {
                        allFolders = true
                        selectedFolderIds = emptySet()
                    },
                    onToggleFolder = { folderId ->
                        selectedFolderIds = if (folderId in selectedFolderIds) {
                            selectedFolderIds - folderId
                        } else {
                            selectedFolderIds + folderId
                        }
                        allFolders = false
                        if (selectedFolderIds.isEmpty()) {
                            allFolders = true
                        }
                    },
                )

                Button(
                    onClick = {
                        viewModel.generate(
                            allFolders = allFolders,
                            folderIds = selectedFolderIds,
                            minText = minText,
                            maxText = maxText,
                            countText = countText,
                            order = order,
                        )
                    },
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.playlist_generate))
                }

                if (generating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    generated && result.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.playlist_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    result.isNotEmpty() -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.playlist_result_count, result.size, result.size),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                viewModel.saveToPlaylist(minText, maxText, order)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = stringResource(R.string.playlist_save),
                                )
                            }
                            IconButton(onClick = {
                                viewModel.openPlayer()
                                onPlaylistStarted()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.playlist_play),
                                )
                            }
                        }
                        result.forEach { track ->
                            ResultRow(track)
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
                playlistListViewModel.delete(summary.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDrawerRow(
    summary: PlaylistSummary,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistDrawerLabels(summary, Modifier.weight(1f))
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
        PlaylistDrawerLabels(summary, Modifier.weight(1f))
    }
}

@Composable
private fun PlaylistDrawerLabels(summary: PlaylistSummary, modifier: Modifier = Modifier) {
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

@Composable
private fun ResultRow(track: TrackEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = track.bpm?.let(::formatBpm) ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
    }
}