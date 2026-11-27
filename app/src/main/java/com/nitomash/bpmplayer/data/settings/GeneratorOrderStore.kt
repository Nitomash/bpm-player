package com.nitomash.bpmplayer.data.settings

import com.nitomash.bpmplayer.domain.playlist.PlaylistOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Singleton
class GeneratorOrderStore internal constructor(
    initialOrder: PlaylistOrder,
    private val settingsRepository: SettingsRepository,
) {
    private val _current = MutableStateFlow(initialOrder)
    val current: StateFlow<PlaylistOrder> = _current.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun set(order: PlaylistOrder) {
        _current.value = order
        scope.launch {
            settingsRepository.update { it.copy(generatorOrder = order) }
        }
    }
}
