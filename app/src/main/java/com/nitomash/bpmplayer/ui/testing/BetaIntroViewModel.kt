package com.nitomash.bpmplayer.ui.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.testing.TesterBeacon
import com.nitomash.bpmplayer.data.testing.TesterProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BetaIntroViewModel @Inject constructor(
    private val testerProgress: TesterProgress,
) : ViewModel() {

    val introAccepted: StateFlow<Boolean> = testerProgress.observeIntroAccepted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun accept() {
        viewModelScope.launch {
            testerProgress.acceptIntro()
        }
    }

    fun mark(beacon: TesterBeacon) {
        viewModelScope.launch {
            testerProgress.mark(beacon)
        }
    }
}
