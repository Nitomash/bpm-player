package com.nitomash.bpmplayer.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R

@Composable
internal fun PlaylistHeader(
    name: String,
    trackCount: Int,
    modifier: Modifier = Modifier,
    onRename: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onRename == null) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onRename)
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.track_count, trackCount, trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
