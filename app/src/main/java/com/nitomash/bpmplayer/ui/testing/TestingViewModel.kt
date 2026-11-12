package com.nitomash.bpmplayer.ui.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.testing.TesterBeacon
import com.nitomash.bpmplayer.data.testing.TesterProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestingUiState(
    val completedIds: Set<String> = emptySet(),
    val total: Int = TesterBeacon.totalCount,
) {
    val completedCount: Int get() = completedIds.intersect(TesterBeacon.allIds).size
    val percent: Int get() = if (total == 0) 0 else (completedCount * 100) / total
}

@HiltViewModel
class TestingViewModel @Inject constructor(
    private val testerProgress: TesterProgress,
) : ViewModel() {

    val uiState: StateFlow<TestingUiState> = testerProgress.observeCompletedIds()
        .map { TestingUiState(completedIds = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TestingUiState())

    fun markOpened() {
        viewModelScope.launch {
            testerProgress.mark(TesterBeacon.SCREEN_TESTING_OPENED)
        }
    }
}
