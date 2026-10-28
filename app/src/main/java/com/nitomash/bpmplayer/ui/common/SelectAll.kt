package com.nitomash.bpmplayer.ui.common

import androidx.compose.ui.state.ToggleableState

object SelectAll {

    fun state(allIds: List<Long>, selectedIds: Set<Long>): ToggleableState = when {
        allIds.isEmpty() || selectedIds.isEmpty() -> ToggleableState.Off
        selectedIds.containsAll(allIds) -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    fun toggled(allIds: List<Long>, selectedIds: Set<Long>): Set<Long> =
        if (state(allIds, selectedIds) == ToggleableState.On) emptySet() else allIds.toSet()

    fun toggledWithin(allIds: List<Long>, selectedIds: Set<Long>): Set<Long> =
        if (state(allIds, selectedIds) == ToggleableState.On) {
            selectedIds - allIds.toSet()
        } else {
            selectedIds + allIds
        }
}