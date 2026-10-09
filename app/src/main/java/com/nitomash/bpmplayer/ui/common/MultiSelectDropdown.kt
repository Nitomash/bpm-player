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

data class MultiSelectOption(val id: Long, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectDropdown(
    label: String,
    allLabel: String,
    emptyLabel: String,
    options: List<MultiSelectOption>,
    allSelected: Boolean,
    selectedIds: Set<Long>,
    onAllSelected: () -> Unit,
    onToggleOption: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        val value = if (allSelected || selectedIds.isEmpty()) {
            allLabel
        } else {
            options.filter { it.id in selectedIds }.joinToString(", ") { it.label }
        }
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                leadingIcon = { Checkbox(checked = allSelected, onCheckedChange = null) },
                onClick = {
                    onAllSelected()
                    expanded = false
                },
            )
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(emptyLabel) },
                    onClick = { expanded = false },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, maxLines = 1) },
                    leadingIcon = { Checkbox(checked = option.id in selectedIds, onCheckedChange = null) },
                    onClick = { onToggleOption(option.id) },
                )
            }
        }
    }
}
