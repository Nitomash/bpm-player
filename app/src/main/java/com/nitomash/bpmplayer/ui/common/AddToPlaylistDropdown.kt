package com.nitomash.bpmplayer.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.playlist.PlaylistSummary

@Composable
fun AddToPlaylistDropdown(
    expanded: Boolean,
    playlists: List<PlaylistSummary>,
    containing: Set<Long>,
    onDismiss: () -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.player_add_to_playlist),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        if (playlists.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_empty_list)) },
                onClick = {},
                enabled = false,
            )
        } else {
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = playlist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        if (playlist.id in containing) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = { onToggle(playlist.id) },
                )
            }
        }
    }
}
