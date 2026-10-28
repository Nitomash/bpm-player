package com.nitomash.bpmplayer.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.data.music.SIDECAR_EXTENSION
import com.nitomash.bpmplayer.data.music.SIDECAR_MIME
import com.nitomash.bpmplayer.data.music.safeFileName
import com.nitomash.bpmplayer.ui.common.AddToPlaylistDropdown
import com.nitomash.bpmplayer.ui.common.SearchField
import com.nitomash.bpmplayer.ui.common.TrackRow
import com.nitomash.bpmplayer.ui.editor.BpmEditorDialog
import com.nitomash.bpmplayer.ui.editor.MassBpmScope
import com.nitomash.bpmplayer.ui.editor.MassBpmSetupDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    editorViewModel: BpmEditorViewModel = hiltViewModel(),
    onOpenMassBpm: (MassBpmScope, Set<Long>) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val selectedTrack by editorViewModel.selectedTrack.collectAsStateWithLifecycle()
    val isPlaying by editorViewModel.isPlaying.collectAsStateWithLifecycle()
    val autoDetectState by editorViewModel.autoDetectState.collectAsStateWithLifecycle()
    val folderSort by viewModel.folderSort.collectAsStateWithLifecycle()
    val collapsedFolders by viewModel.collapsedFolders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterQuery by viewModel.filterQuery.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val menuTrackId by viewModel.menuTrackId.collectAsStateWithLifecycle()
    val containing by viewModel.playlistsContainingMenuTrack.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var showMassSetup by remember { mutableStateOf(false) }
    var importTargetFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var exportTargetFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var bpmPromptArgs by remember { mutableStateOf<LibraryViewModel.SuggestBpmArgs?>(null) }
    var bpmPromptDontShow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.error.collect { detail ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.scan_error) + "\n" + detail,
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { count ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.library_import_result, count),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.folderDeleted.collect {
            snackbarHostState.showSnackbar(
                context.getString(R.string.folder_delete_result),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportError.collect {
            snackbarHostState.showSnackbar(
                context.getString(R.string.library_export_error),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.suggestBpmAfterScan.collect { args ->
            bpmPromptDontShow = false
            bpmPromptArgs = args
        }
    }

    val importFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val folder = importTargetFolder
        importTargetFolder = null
        if (uri != null && folder != null) {
            viewModel.importSidecarFile(folder.id, uri)
        }
    }

    val exportDocumentCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SIDECAR_MIME),
    ) { uri ->
        val folder = exportTargetFolder
        exportTargetFolder = null
        if (uri != null && folder != null) {
            viewModel.exportSidecarFile(folder.id, uri)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure { e ->
                android.util.Log.w("BpmLibrary", "takePersistableUriPermission failed for $uri", e)
            }
            viewModel.addFolder(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    IconButton(onClick = { moreMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.library_more),
                        )
                    }
                    DropdownMenu(
                        expanded = moreMenuOpen,
                        onDismissRequest = { moreMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_mass_bpm)) },
                            onClick = {
                                moreMenuOpen = false
                                showMassSetup = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            onClick = {
                                moreMenuOpen = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about_title)) },
                            onClick = {
                                moreMenuOpen = false
                                onOpenAbout()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { folderPicker.launch(null) }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.library_add_folder),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.scanning),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (folders.isEmpty()) {
                EmptyLibrary(modifier = Modifier.fillMaxSize())
            } else {
                SearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                if (content.isEmpty) {
                    val pullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isScanning,
                        onRefresh = { viewModel.refreshAll() },
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.library_search_empty),
                                modifier = Modifier.padding(24.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    val pullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isScanning,
                        onRefresh = { viewModel.refreshAll() },
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            content.visibleFolders.forEach { folder ->
                                val collapsed = folder.id in collapsedFolders && filterQuery.isBlank()
                                item(key = "folder-${folder.id}") {
                                    FolderHeader(
                                        folder = folder,
                                        trackCount = content.countOf(folder.id),
                                        sort = folderSort[folder.id] ?: TrackSort.BY_NAME,
                                        expanded = !collapsed,
                                        refreshEnabled = !isScanning,
                                        onToggle = { viewModel.toggleFolderCollapsed(folder.id) },
                                        onRefresh = { viewModel.rescanFolder(folder.id) },
                                        onSortChange = { viewModel.setFolderSort(folder.id, it) },
                                        onDelete = { folderToDelete = folder },
                                        onImportInfo = {
                                            importTargetFolder = folder
                                            importFilePicker.launch(arrayOf("*/*"))
                                        },
                                        onExportInfo = {
                                            exportTargetFolder = folder
                                            exportDocumentCreator.launch(
                                                "${safeFileName(folder.displayName)}.$SIDECAR_EXTENSION",
                                            )
                                        },
                                    )
                                }
                                if (!collapsed) {
                                    items(
                                        items = content.tracksOf(folder.id),
                                        key = { "track-${it.id}" },
                                    ) { track ->
                                        Box {
                                            TrackRow(
                                                title = track.title,
                                                bpm = track.bpm,
                                                bpmSource = track.bpmSource,
                                                onClick = { editorViewModel.open(track) },
                                                onLongClick = { viewModel.openPlaylistMenu(track.id) },
                                            )
                                            AddToPlaylistDropdown(
                                                expanded = menuTrackId == track.id,
                                                playlists = playlists,
                                                containing = containing,
                                                onDismiss = viewModel::closePlaylistMenu,
                                                onToggle = { playlistId ->
                                                    viewModel.toggleTrackInPlaylist(track.id, playlistId)
                                                    viewModel.closePlaylistMenu()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTrack?.let { track ->
        BpmEditorDialog(
            track = track,
            isPlaying = isPlaying,
            autoDetectState = autoDetectState,
            onTogglePlay = editorViewModel::togglePlayback,
            onSkipForward = editorViewModel::skipForward,
            onSave = { bpm, source ->
                editorViewModel.saveBpm(bpm, source)
                editorViewModel.dismiss()
            },
            onAutoDetect = editorViewModel::autoDetectBpm,
            onCancelAutoDetect = editorViewModel::cancelAutoDetect,
            onDismiss = editorViewModel::dismiss,
        )
    }

    if (showMassSetup) {
        MassBpmSetupDialog(
            folders = folders,
            onNext = { scope, folderIds ->
                showMassSetup = false
                onOpenMassBpm(scope, folderIds)
            },
            onDismiss = { showMassSetup = false },
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.folder_delete_confirm_title)) },
            text = { Text(stringResource(R.string.folder_delete_confirm_message, folder.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFolder(folder.id)
                    folderToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.editor_cancel))
                }
            },
        )
    }

    bpmPromptArgs?.let { args ->
        Dialog(onDismissRequest = { bpmPromptArgs = null }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = stringResource(R.string.library_bpm_prompt_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.library_bpm_prompt_message, args.count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bpmPromptDontShow = !bpmPromptDontShow }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = bpmPromptDontShow,
                                onCheckedChange = { bpmPromptDontShow = it },
                            )
                            Text(
                                text = stringResource(R.string.library_bpm_prompt_dont_show),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            if (bpmPromptDontShow) {
                                viewModel.suppressBpmPromptAfterScan()
                            }
                            bpmPromptArgs = null
                        }) {
                            Text(stringResource(R.string.library_bpm_prompt_later))
                        }
                        TextButton(onClick = {
                            if (bpmPromptDontShow) {
                                viewModel.suppressBpmPromptAfterScan()
                            }
                            val folderIds = args.folderIds
                            bpmPromptArgs = null
                            onOpenMassBpm(MassBpmScope.UNDEFINED_ONLY, folderIds)
                        }) {
                            Text(stringResource(R.string.library_bpm_prompt_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.library_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FolderHeader(
    folder: FolderEntity,
    trackCount: Int,
    sort: TrackSort,
    expanded: Boolean,
    refreshEnabled: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onSortChange: (TrackSort) -> Unit,
    onDelete: () -> Unit,
    onImportInfo: () -> Unit,
    onExportInfo: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var actionsMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.library_collapse else R.string.library_expand,
                ),
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = folder.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pluralStringResource(R.plurals.track_count, trackCount, trackCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { sortMenuOpen = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.library_sort),
            )
        }
        DropdownMenu(
            expanded = sortMenuOpen,
            onDismissRequest = { sortMenuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_sort_by_name)) },
                onClick = {
                    onSortChange(TrackSort.BY_NAME)
                    sortMenuOpen = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_sort_by_bpm)) },
                onClick = {
                    onSortChange(TrackSort.BY_BPM)
                    sortMenuOpen = false
                },
            )
        }
        Box {
            IconButton(onClick = { actionsMenuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.library_more),
                )
            }
            DropdownMenu(
                expanded = actionsMenuOpen,
                onDismissRequest = { actionsMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_refresh)) },
                    enabled = refreshEnabled,
                    onClick = {
                        actionsMenuOpen = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_import_info)) },
                    onClick = {
                        actionsMenuOpen = false
                        onImportInfo()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_export_info)) },
                    onClick = {
                        actionsMenuOpen = false
                        onExportInfo()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete_confirm)) },
                    onClick = {
                        actionsMenuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}