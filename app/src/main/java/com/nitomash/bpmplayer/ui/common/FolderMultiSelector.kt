package com.nitomash.bpmplayer.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.FolderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderMultiSelector(
    folders: List<FolderEntity>,
    allSelected: Boolean,
    selectedIds: Set<Long>,
    onAllSelected: () -> Unit,
    onToggleFolder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var foldersExpanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = foldersExpanded,
        onExpandedChange = { foldersExpanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        val value = if (allSelected || selectedIds.isEmpty()) {
            stringResource(R.string.playlist_folders_all)
        } else {
            folders.filter { it.id in selectedIds }.joinToString(", ") { it.displayName }
        }
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.playlist_folders_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = foldersExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = foldersExpanded,
            onDismissRequest = { foldersExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_folders_all)) },
                leadingIcon = { Checkbox(checked = allSelected, onCheckedChange = null) },
                onClick = {
                    onAllSelected()
                    foldersExpanded = false
                },
            )
            if (folders.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_folders_empty)) },
                    onClick = { foldersExpanded = false },
                )
            }
            folders.forEach { folder ->
                val checked = folder.id in selectedIds
                DropdownMenuItem(
                    text = { Text(folder.displayName, maxLines = 1) },
                    leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                    onClick = {
                        onToggleFolder(folder.id)
                    },
                )
            }
        }
    }
}