package com.nitomash.bpmplayer.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.FolderEntity
import com.nitomash.bpmplayer.ui.common.FolderMultiSelector

enum class MassBpmScope { ALL, UNDEFINED_ONLY }

@Composable
fun MassBpmSetupDialog(
    folders: List<FolderEntity>,
    onNext: (scope: MassBpmScope, folderIds: Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var scope by rememberSaveable { mutableStateOf(MassBpmScope.ALL) }
    var allFolders by rememberSaveable { mutableStateOf(true) }
    var selectedFolderIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mass_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.mass_scope_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = scope == MassBpmScope.ALL,
                            onClick = { scope = MassBpmScope.ALL },
                        )
                        Text(
                            text = stringResource(R.string.mass_scope_all),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = scope == MassBpmScope.UNDEFINED_ONLY,
                            onClick = { scope = MassBpmScope.UNDEFINED_ONLY },
                        )
                        Text(
                            text = stringResource(R.string.mass_scope_undefined),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val resolved = if (allFolders || selectedFolderIds.isEmpty()) {
                    folders.map { it.id }.toSet()
                } else {
                    selectedFolderIds
                }
                onNext(scope, resolved)
            }) {
                Text(stringResource(R.string.mass_next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}