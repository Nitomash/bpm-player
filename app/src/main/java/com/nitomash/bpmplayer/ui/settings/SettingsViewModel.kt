package com.nitomash.bpmplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.data.settings.AppSettings
import com.nitomash.bpmplayer.data.settings.SettingsMapper
import com.nitomash.bpmplayer.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun setDetectorRange(min: Double, max: Double) {
        val (low, high) = SettingsMapper.normalizeRange(min, max)
        update { it.copy(detectorMinBpm = low, detectorMaxBpm = high) }
    }
}
