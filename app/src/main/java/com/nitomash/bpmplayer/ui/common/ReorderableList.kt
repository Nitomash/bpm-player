package com.nitomash.bpmplayer.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nitomash.bpmplayer.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val ReorderItemSpacing = 8.dp

private val ReorderDraggingElevation = 4.dp

val ReorderHandleWidth: Dp = 64.dp

val ReorderRowShape: RoundedCornerShape = RoundedCornerShape(8.dp)

@Composable
fun reorderRowElevation(isDragging: Boolean): Dp {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) ReorderDraggingElevation else 0.dp,
        label = "reorderRowElevation",
    )
    return elevation
}

@Composable
fun reorderRowColor(isDragging: Boolean): Color = if (isDragging) {
    MaterialTheme.colorScheme.primaryContainer
} else {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
}

@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    itemContent: @Composable (
        item: T,
        modifier: Modifier,
        dragHandleModifier: Modifier,
        isDragging: Boolean,
    ) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ReorderItemSpacing),
    ) {
        items(items = items, key = key) { item ->
            ReorderableItem(reorderState, key = key(item)) { isDragging ->
                itemContent(
                    item,
                    Modifier,
                    Modifier.draggableHandle(),
                    isDragging,
                )
            }
        }
    }
}

@Composable
fun ReorderHandle(modifier: Modifier = Modifier) {
    val handleDescription = stringResource(R.string.playlist_drag_handle)
    Box(
        modifier = modifier
            .size(ReorderHandleWidth, 56.dp)
            .semantics { contentDescription = handleDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
