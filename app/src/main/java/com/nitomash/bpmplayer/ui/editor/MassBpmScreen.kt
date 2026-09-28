package com.nitomash.bpmplayer.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.ui.common.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MassBpmScreen(
    viewModel: MassBpmViewModel = hiltViewModel(),
    onDiscard: () -> Unit = {},
    onSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val detecting by viewModel.detecting.collectAsStateWithLifecycle()
    val selectedTarget by viewModel.selectedTarget.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val autoDetectState by viewModel.autoDetectState.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasChanges.collectAsStateWithLifecycle()
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val requestBack: () -> Unit = {
        if (hasChanges) showDiscardConfirm = true else onDiscard()
    }
    BackHandler(onBack = requestBack)

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mass_title)) },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (detecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        TextButton(onClick = viewModel::cancelDetection) {
                            Text(stringResource(R.string.editor_cancel))
                        }
                    } else {
                        TextButton(onClick = viewModel::startDetection) {
                            Text(stringResource(R.string.mass_detect))
                        }
                    }
                    IconButton(
                        onClick = viewModel::saveAll,
                        enabled = hasChanges,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.editor_save),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.mass_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items.groupBy { it.folderId }.toList(), key = { it.first }) { (_, folderItems) ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folderItems.first().folderName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.track_count,
                                    folderItems.size,
                                    folderItems.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    folderItems.forEach { item ->
                        TrackRow(
                            title = item.title,
                            bpm = item.bpm,
                            bpmSource = item.bpmSource,
                            onClick = { viewModel.openTarget(item.trackId) },
                            trailing = {
                                when (item.status) {
                                    BatchTrackStatus.DETECTING -> CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    BatchTrackStatus.FAILED -> Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    else -> {}
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    selectedTarget?.let { track ->
        BpmEditorDialog(
            track = track,
            isPlaying = isPlaying,
            autoDetectState = autoDetectState,
            onTogglePlay = viewModel::togglePlayback,
            onSkipForward = viewModel::skipForward,
            onSave = { bpm, source -> viewModel.saveFromEditor(bpm, source) },
            onAutoDetect = viewModel::autoDetectSingle,
            onCancelAutoDetect = viewModel::cancelSingleDetect,
            onDismiss = viewModel::dismissEditor,
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.mass_discard_confirm_title)) },
            text = { Text(stringResource(R.string.mass_discard_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    viewModel.discard()
                    onDiscard()
                }) {
                    Text(stringResource(R.string.mass_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.mass_discard_stay))
                }
            },
        )
    }
}