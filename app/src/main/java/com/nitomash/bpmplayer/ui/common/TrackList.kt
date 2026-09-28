package com.nitomash.bpmplayer.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.data.db.entity.TrackEntity

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun TrackList(
    tracks: List<TrackEntity>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    emptyText: String,
    currentTrackId: Long? = null,
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelected: (Long) -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    trailing: (@Composable (TrackEntity) -> Unit)? = null,
    trackMenu: (@Composable (track: TrackEntity, onDismiss: () -> Unit) -> Unit)? = null,
) {
    var menuTrackId by rememberSaveable { mutableStateOf<Long?>(null) }
    if (tracks.isEmpty()) {
        if (header != null) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                header()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyMessage(emptyText)
                }
            }
        } else {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyMessage(emptyText)
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header?.let { header ->
            item(key = "header") { header() }
        }
        items(items = tracks, key = { "track-${it.id}" }) { track ->
            Box(modifier = Modifier.fillMaxWidth()) {
                TrackRow(
                    title = track.title,
                    bpm = track.bpm,
                    bpmSource = track.bpmSource,
                    selected = if (selectionMode) track.id in selectedIds else null,
                    highlighted = !selectionMode && track.id == currentTrackId,
                    onClick = {
                        if (selectionMode) {
                            onToggleSelected(track.id)
                        } else {
                            onTrackClick(track)
                        }
                    },
                    onLongClick = if (selectionMode || trackMenu == null) {
                        null
                    } else {
                        { menuTrackId = track.id }
                    },
                    trailing = { trailing?.invoke(track) },
                )
                if (menuTrackId == track.id) {
                    trackMenu?.invoke(track) { menuTrackId = null }
                }
            }
        }
    }
}