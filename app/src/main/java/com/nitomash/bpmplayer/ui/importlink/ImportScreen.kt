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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.nitomash.bpmplayer.domain.linkimport.ImportJobState
import com.nitomash.bpmplayer.ui.common.CompactOutlinedTextField
import com.nitomash.bpmplayer.ui.common.LoadingPlaceholder

const val IMPORT_ROUTE = "import"
const val IMPORT_ARG_JOB_ID = "jobId"

fun importRoute(jobId: String? = null): String =
    if (jobId.isNullOrBlank()) IMPORT_ROUTE else "$IMPORT_ROUTE?$IMPORT_ARG_JOB_ID=$jobId"

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
                title = { Text(stringResource(R.string.import_title)) },
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompactOutlinedTextField(
                        value = state.link,
                        onValueChange = viewModel::onLinkChange,
                        label = stringResource(R.string.import_link_label),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) viewModel.onLinkFocusLost() },
                    )

                    CompactOutlinedTextField(
                        value = state.folderName,
                        onValueChange = viewModel::onFolderNameChange,
                        label = stringResource(R.string.import_folder_label),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = viewModel::start,
                        enabled = !state.isRunning && !state.isInspecting &&
                            state.link.isNotBlank() && state.root != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_start))
                    }

                    if (state.isInspecting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    val errorRes = state.errorRes
                    if (errorRes != null) {
                        val message = stringResource(errorRes)
                        val detail = state.errorMessage
                        Text(
                            text = if (detail.isNullOrBlank()) message else "$message: $detail",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

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
                                val canChangeRoot = !state.isRunning && !state.isInspecting
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(
                                            R.string.import_root_value,
                                            state.root?.displayName
                                                ?: stringResource(R.string.import_root_not_selected),
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = stringResource(R.string.import_root_change),
                                        color = if (canChangeRoot) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        },
                                        modifier = Modifier
                                            .clickable(enabled = canChangeRoot) {
                                                treeLauncher.launch(null)
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                    )
                                }
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
                                if (jobState != null) {
                                    JobStateContent(jobState)
                                    if (state.isRunning) {
                                        TextButton(onClick = viewModel::cancel) {
                                            Text(
                                                text = stringResource(R.string.import_cancel),
                                                style = MaterialTheme.typography.bodyMedium,
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
