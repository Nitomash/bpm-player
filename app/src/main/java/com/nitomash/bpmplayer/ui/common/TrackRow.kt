package com.nitomash.bpmplayer.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.db.entity.BpmSource
import com.nitomash.bpmplayer.ui.editor.bpmSourceLabel
import com.nitomash.bpmplayer.ui.editor.formatBpm

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    title: String,
    bpm: Double?,
    bpmSource: BpmSource?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    highlighted: Boolean = false,
    containerColor: Color? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interactionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    val contentBackground = when {
        highlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        selected == true -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        containerColor != null -> containerColor
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReorderRowShape)
            .background(contentBackground)
            .then(interactionModifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        if (selected != null) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    bpm == null -> stringResource(R.string.bpm_unknown)
                    bpmSource != null -> stringResource(
                        R.string.bpm_value_source,
                        formatBpm(bpm),
                        stringResource(bpmSourceLabel(bpmSource)),
                    )
                    else -> stringResource(R.string.bpm_value, formatBpm(bpm))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}