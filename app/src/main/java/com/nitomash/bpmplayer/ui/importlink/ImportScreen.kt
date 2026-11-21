package com.nitomash.bpmplayer.ui.importlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.domain.linkimport.ImportJobState
import com.nitomash.bpmplayer.domain.sharing.CollectionShareLinks
import com.nitomash.bpmplayer.ui.common.CompactOutlinedTextField
import com.nitomash.bpmplayer.ui.common.LoadingPlaceholder

const val IMPORT_ROUTE = "import"
const val IMPORT_ARG_JOB_ID = "jobId"
const val IMPORT_ARG_FOLDER_ID = "folderId"
const val IMPORT_ARG_LINKS = "links"
const val IMPORT_ROUTE_PATTERN =
    "$IMPORT_ROUTE?$IMPORT_ARG_JOB_ID={$IMPORT_ARG_JOB_ID}&" +
        "$IMPORT_ARG_FOLDER_ID={$IMPORT_ARG_FOLDER_ID}&$IMPORT_ARG_LINKS={$IMPORT_ARG_LINKS}"

fun importRoute(
    jobId: String? = null,
    folderId: Long? = null,
    links: List<String> = emptyList(),
): String {
    val params = buildList {
        jobId?.takeIf { it.isNotBlank() }?.let { add("$IMPORT_ARG_JOB_ID=$it") }
        folderId?.takeIf { it > 0 }?.let { add("$IMPORT_ARG_FOLDER_ID=$it") }
        if (links.isNotEmpty()) add("$IMPORT_ARG_LINKS=${CollectionShareLinks.encode(links)}")
    }
    return if (params.isEmpty()) IMPORT_ROUTE else "$IMPORT_ROUTE?${params.joinToString("&")}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onRootPicked(uri)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isUpdateMode) R.string.import_update_title else R.string.import_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.loaded) {
            LoadingPlaceholder(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isUpdateMode) {
                item(key = "update_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.folderDisplayName?.let { name ->
                            Text(
                                text = stringResource(R.string.import_update_folder, name),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RootInfoRow(
                                    rootName = state.root?.displayName
                                        ?: stringResource(R.string.import_root_not_selected),
                                    canChange = false,
                                    onChange = {},
                                )
                            }
                        }
                        ErrorText(state)
                        if (state.isInspecting) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                itemsIndexed(
                    items = state.updateLinks,
                    key = { index, _ -> "update-link-$index" },
                ) { _, item ->
                    UpdateLinkCard(item = item)
                }
                item(key = "update_controls") {
                    UpdateControls(
                        state = state,
                        onCancel = viewModel::cancelAll,
                        onResume = viewModel::resumeAll,
                    )
                }
            } else if (state.isCollectionMode) {
                item(key = "collection_content") {
                    CollectionImportContent(
                        state = state,
                        onFolderNameChange = viewModel::onFolderNameChange,
                        onTargetFolderPicked = viewModel::onTargetFolderPicked,
                        onRootPicked = { treeLauncher.launch(null) },
                        onStart = viewModel::startCollection,
                        onCancel = viewModel::cancelAll,
                        onResume = viewModel::resumeAll,
                    )
                }
                itemsIndexed(
                    items = state.updateLinks,
                    key = { index, _ -> "collection-link-$index" },
                ) { _, item ->
                    UpdateLinkCard(item = item)
                }
            } else {
                item(key = "new_import") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompactOutlinedTextField(
                            value = state.link,
                            onValueChange = viewModel::onLinkChange,
                            label = stringResource(R.string.import_link_label),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (!it.isFocused) viewModel.onLinkFocusLost() },
                        )

                        if (state.isSidecar) {
                            TargetFolderDropdown(
                                folders = state.folders,
                                selectedFolderId = state.selectedFolderId,
                                onSelect = viewModel::onTargetFolderPicked,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            CompactOutlinedTextField(
                                value = state.folderName,
                                onValueChange = viewModel::onFolderNameChange,
                                label = stringResource(R.string.import_folder_label),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Button(
                            onClick = viewModel::start,
                            enabled = !state.isRunning && !state.isInspecting &&
                                state.link.isNotBlank() && state.root != null &&
                                (!state.isSidecar || state.selectedFolderId != null) &&
                                state.jobState !is ImportJobState.Cancelled &&
                                state.jobState !is ImportJobState.Failed,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.import_start))
                        }

                        if (state.isInspecting) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        ErrorText(state)

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = state.sourceName
                                        ?: stringResource(R.string.import_info_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    RootInfoRow(
                                        rootName = state.root?.displayName
                                            ?: stringResource(R.string.import_root_not_selected),
                                        canChange = !state.isRunning && !state.isInspecting,
                                        onChange = { treeLauncher.launch(null) },
                                    )
                                    val preview = state.preview
                                    if (preview != null) {
                                        Text(
                                            text = stringResource(
                                                R.string.import_preview_summary,
                                                preview.supportedFileCount,
                                                preview.directoriesCount,
                                            ),
                                        )
                                    }
                                    val jobState = state.jobState
                                    if (jobState != null) JobStateContent(jobState)
                                    if (state.isRunning) {
                                        TextButton(onClick = viewModel::cancel) {
                                            Text(
                                                text = stringResource(R.string.import_cancel),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    } else if (jobState is ImportJobState.Cancelled ||
                                        jobState is ImportJobState.Failed
                                    ) {
                                        Button(
                                            onClick = viewModel::start,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(stringResource(R.string.import_resume))
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
}

@Composable
private fun ErrorText(state: ImportUiState) {
    val errorRes = state.errorRes ?: return
    val message = stringResource(errorRes)
    val detail = state.errorMessage
    Text(
        text = if (detail.isNullOrBlank()) message else "$message: $detail",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetFolderDropdown(
    folders: List<FolderEntity>,
    selectedFolderId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = folders.firstOrNull { it.id == selectedFolderId }?.displayName
        ?: stringResource(R.string.import_folder_select)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        CompactOutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = stringResource(R.string.import_folder_label),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            if (folders.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_folder_empty)) },
                    onClick = { expanded = false },
                    enabled = false,
                )
            } else {
                folders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.displayName) },
                        onClick = {
                            onSelect(folder.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootInfoRow(
    rootName: String,
    canChange: Boolean,
    onChange: () -> Unit,
) {
    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.import_root_value, rootName),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.import_root_change),
                color = if (canChange) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier
                    .clickable(enabled = canChange, onClick = onChange)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun UpdateLinkCard(
    item: UpdateLinkState,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.preview?.sourceName ?: stringResource(R.string.import_info_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                val preview = item.preview
                if (preview != null) {
                    Text(
                        stringResource(
                            R.string.import_preview_summary,
                            preview.supportedFileCount,
                            preview.directoriesCount,
                        ),
                    )
                }
                val jobState = item.jobState
                if (jobState != null) JobStateContent(jobState)
            }
        }
    }
}

@Composable
private fun CollectionImportContent(
    state: ImportUiState,
    onFolderNameChange: (String) -> Unit,
    onTargetFolderPicked: (Long) -> Unit,
    onRootPicked: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactOutlinedTextField(
            value = state.folderName,
            onValueChange = onFolderNameChange,
            label = stringResource(R.string.import_folder_label),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isSidecar) {
            TargetFolderDropdown(
                folders = state.folders,
                selectedFolderId = state.selectedFolderId,
                onSelect = onTargetFolderPicked,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = onStart,
            enabled = !state.isRunning && !state.isInspecting &&
                state.folderName.isNotBlank() && state.root != null &&
                (!state.isSidecar || state.selectedFolderId != null),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_start))
        }
        if (state.isInspecting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ErrorText(state)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RootInfoRow(
                    rootName = state.root?.displayName
                        ?: stringResource(R.string.import_root_not_selected),
                    canChange = !state.isRunning && !state.isInspecting,
                    onChange = onRootPicked,
                )
            }
        }
        UpdateControls(
            state = state,
            onCancel = onCancel,
            onResume = onResume,
        )
    }
}

@Composable
private fun UpdateControls(
    state: ImportUiState,
    onCancel: () -> Unit,
    onResume: () -> Unit,
) {
    val canResume = state.updateLinks.any { item ->
        item.jobState is ImportJobState.Cancelled || item.jobState is ImportJobState.Failed
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isRunning) {
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.import_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (canResume) {
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_resume))
            }
        }
    }
}

@Composable
private fun JobStateContent(state: ImportJobState) {
    when (state) {
        ImportJobState.Queued -> Text(stringResource(R.string.import_state_queued))
        is ImportJobState.Planning -> Text(stringResource(R.string.import_state_planning))
        is ImportJobState.Downloading -> {
            Text(stringResource(R.string.import_progress, state.completedFiles, state.totalFiles))
            if (state.currentPath.isNotBlank()) {
                Text(state.currentPath)
            }
            LinearProgressIndicator(
                progress = {
                    if (state.totalFiles == 0) 0f else state.completedFiles.toFloat() / state.totalFiles
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is ImportJobState.Indexing -> Text(stringResource(R.string.import_state_indexing))
        is ImportJobState.Completed -> Text(
            stringResource(
                R.string.import_state_completed,
                state.totalFiles - state.upToDateFiles,
            ),
        )

        is ImportJobState.Cancelled -> Text(stringResource(R.string.import_state_cancelled))
        is ImportJobState.Failed -> Text(
            stringResource(R.string.import_state_failed, state.message),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
